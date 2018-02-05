/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import fixtures.AuthFixture
import models.IncorpStatus
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.call
import services.{MetricsService, RegistrationHoldingPenService}
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import play.api.Logger
import play.api.libs.json.Reads._

import scala.concurrent.Future

class ProcessIncorporationsControllerSpec extends UnitSpec with MockitoSugar with AuthFixture  with LogCapturing with Eventually {

  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  val regId = "1234"
  val incDate = DateTime.parse("2000-12-12")
  val transactionId = "trans-12345"
  val crn = "crn-12345"

  val mockRegHoldingPenService = mock[RegistrationHoldingPenService]

  class Setup {
    val controller = new ProcessIncorporationsController {
      override val regHoldingPenService = mockRegHoldingPenService
    }
  }

  val rejectedIncorpJson = Json.parse(
    s"""
      |{
      |  "SCRSIncorpStatus":{
      |    "IncorpSubscriptionKey":{
      |      "subscriber":"abc123",
      |      "discriminator":"CT100",
      |      "transactionId":"$transactionId"
      |    },
      |    "SCRSIncorpSubscription":{
      |      "callbackUrl":"www.testUpdate.com"
      |    },
      |    "IncorpStatusEvent":{
      |      "status":"rejected",
      |      "description":"description"
      |    }
      |  }
      |}
    """.stripMargin).as[JsObject]

  val rejectedIncorpStatus = IncorpStatus(transactionId, "rejected", None, Some("description"), None)

  val acceptedIncorpJson = Json.parse(
    s"""
       |{
       |  "SCRSIncorpStatus":{
       |    "IncorpSubscriptionKey":{
       |      "subscriber":"abc123",
       |      "discriminator":"CT100",
       |      "transactionId":"$transactionId"
       |    },
       |    "SCRSIncorpSubscription":{
       |      "callbackUrl":"www.testUpdate.com"
       |    },
       |    "IncorpStatusEvent":{
       |       "status":"accepted",
       |      "crn":"$crn",
       |      "incorporationDate":${incDate.getMillis}
       |    }
       |  }
       |}
    """.stripMargin).as[JsObject]

  val DESFailedJson = Json.parse(
    s"""
       |{
       |}
    """.stripMargin).as[JsObject]

  val acceptedIncorpStatus = IncorpStatus(transactionId, "accepted", Some(crn), None, Some(incDate))

  "ProcessAdminIncorp" should {

    "read rejected json from request into IncorpStatus case class" in {
      rejectedIncorpJson.as[IncorpStatus](IncorpStatus.reads) shouldBe rejectedIncorpStatus
    }

    "read accepted json from request into IncorpStatus case class" in {
      acceptedIncorpJson.as[IncorpStatus](IncorpStatus.reads) shouldBe acceptedIncorpStatus
    }

    "return a 200 response " in new Setup {

      when(mockRegHoldingPenService.updateIncorp(any(), any())(any())).thenReturn(Future.successful(true))

      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)

      val result = await(call(controller.processAdminIncorp, request))

      status(result) shouldBe 200

    }
  }

  "ProcessIncorp" should {

    "read json from request into IncorpStatus case class" in {
      rejectedIncorpJson.as[IncorpStatus](IncorpStatus.reads) shouldBe rejectedIncorpStatus
    }

    "return a 200 response " in new Setup {

      when(mockRegHoldingPenService.updateIncorp(any(), any())(any())).thenReturn(Future.successful(true))

      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)

      val result = await(call(controller.processIncorp, request))

      status(result) shouldBe 200

    }
  }

  "Failing Topup" should {

    "log the correct error message" in new Setup {

      when(mockRegHoldingPenService.updateIncorp(any(), any())(any())).thenReturn(Future.failed(new RuntimeException))

      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)

      withCaptureOfLoggingFrom(Logger) { logEvents =>

        intercept[RuntimeException](await(call(controller.processIncorp, request)))

          eventually {

            logEvents.size shouldBe 2

            val res = logEvents.map(_.getMessage) contains "FAILED_DES_TOPUP"

            res shouldBe true

        }
      }
    }
  }

  "Invalid Data" should {

    "return a 500 response for non admin flow" in new Setup {

      when(mockRegHoldingPenService.updateIncorp(any(), any())(any())).thenReturn(Future.successful(false))

      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)

      val result = await(call(controller.processIncorp, request))

      status(result) shouldBe 400

    }


  "return a 500 response for admin flow" in new Setup {

    when(mockRegHoldingPenService.updateIncorp(any(), any())(any())).thenReturn(Future.successful(false))

    val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)

    val result = await(call(controller.processAdminIncorp, request))

    status(result) shouldBe 400

  }
}

}
