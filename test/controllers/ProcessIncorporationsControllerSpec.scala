/*
 * Copyright 2022 HM Revenue & Customs
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
import models.IncorpStatus
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import utils.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProcessIncorporationsControllerSpec extends PlaySpec with MockitoSugar with LogCapturing with Eventually {

  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  val regId = "1234"
  val incDate = DateTime.parse("2000-12-12")
  val transactionId = "trans-12345"
  val crn = "crn-12345"

  val mockProcessIncorporationService = mock[ProcessIncorporationService]
  val mockCorpRegTaxService = mock[CorporationTaxRegistrationService]
  val mockSubmissionService = mock[SubmissionService]

  class Setup {
    val controller = new ProcessIncorporationsController(mockProcessIncorporationService,
      mockCorpRegTaxService,
      mockSubmissionService,
      stubControllerComponents(playBodyParsers = stubPlayBodyParsers(mat))) {
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

  "ProcessAdminIncorp" must {

    "read rejected json from request into IncorpStatus case class" in {
      rejectedIncorpJson.as[IncorpStatus](IncorpStatus.reads) mustBe rejectedIncorpStatus
    }

    "read accepted json from request into IncorpStatus case class" in {
      acceptedIncorpJson.as[IncorpStatus](IncorpStatus.reads) mustBe acceptedIncorpStatus
    }

    "return a 200 response " in new Setup {

      when(mockProcessIncorporationService.processIncorporationUpdate(any(), any())(any())).thenReturn(Future.successful(true))

      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)

      val result = call(controller.processAdminIncorporation, request)

      status(result) mustBe 200

    }
  }

  "ProcessIncorp" must {

    "read json from request into IncorpStatus case class" in {
      rejectedIncorpJson.as[IncorpStatus](IncorpStatus.reads) mustBe rejectedIncorpStatus
    }

    "return a 200 response " in new Setup {
      when(mockProcessIncorporationService.processIncorporationUpdate(any(), any())(any())).thenReturn(Future.successful(true))

      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)
      val result = call(controller.processIncorporationNotification, request)

      status(result) mustBe 200
    }
  }

  "Failing Topup" must {
    "log the correct error message" in new Setup {
      when(mockProcessIncorporationService.processIncorporationUpdate(any(), any())(any())).thenReturn(Future.failed(new RuntimeException))

      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)
      withCaptureOfLoggingFrom(Logger(controller.getClass)) { logEvents =>
        intercept[RuntimeException](await(call(controller.processIncorporationNotification, request)))
        eventually {
          logEvents.size mustBe 2
          val res = logEvents.map(_.getMessage) contains "FAILED_DES_TOPUP"

          res mustBe true
        }
      }
    }
  }

  "Invalid Data" must {

    "return a 202 response for non admin flow" in new Setup {
      when(mockProcessIncorporationService.processIncorporationUpdate(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockSubmissionService.setupPartialForTopupOnLocked(any())(any(), any())).thenReturn(Future.successful(false))
      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)
      val result = call(controller.processIncorporationNotification, request)

      status(result) mustBe 202
    }


    "return a 500 response for admin flow" in new Setup {
      when(mockProcessIncorporationService.processIncorporationUpdate(any(), any())(any())).thenReturn(Future.successful(false))
      val request = FakeRequest().withBody[JsObject](rejectedIncorpJson)
      val result = call(controller.processAdminIncorporation, request)

      status(result) mustBe 400

    }
  }

}
