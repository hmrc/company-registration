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

package controllers.admin

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import models.HO6RegistrationInformation
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import services.admin.AdminService
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.mvc.Result
import services.CorporationTaxRegistrationService

import scala.concurrent.Future

class AdminControllerSpec extends UnitSpec with MockitoSugar {

  implicit val act = ActorSystem()
  implicit val mat = ActorMaterializer()

  val mockAdminService: AdminService = mock[AdminService]
  val mockCTService: CorporationTaxRegistrationService = mock[CorporationTaxRegistrationService]

  trait Setup {
    val controller = new AdminController {
      override val adminService: AdminService = mockAdminService
      override val ctService: CorporationTaxRegistrationService = mockCTService
    }
  }

  val regId = "reg-12345"

  "fetchHO6RegistrationInformation" should {

    "return a 200 and HO6 registration information as json" in new Setup {

      val regInfo = HO6RegistrationInformation("draft" , Some("testCompanyName"), Some("ho5"))


      val expectedJson = Json.parse(
        """
          |{
          |  "status":"draft",
          |  "companyName":"testCompanyName",
          |  "registrationProgress":"ho5"
          |}
        """.stripMargin)

      when(mockAdminService.fetchHO6RegistrationInformation(eqTo(regId)))
        .thenReturn(Future.successful(Some(regInfo)))

      val result = await(controller.fetchHO6RegistrationInformation(regId)(FakeRequest()))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe expectedJson
    }

    "return a 404 if a company registration document can't be found for teh supplied reg id" in new Setup {

      when(mockAdminService.fetchHO6RegistrationInformation(eqTo(regId)))
        .thenReturn(Future.successful(None))

      val result = await(controller.fetchHO6RegistrationInformation(regId)(FakeRequest()))

      status(result) shouldBe 404
    }
  }

  "migrateHeldSubmissions" should {

    "return a 200 and an accurate json response when all held submissions migrated successfully" in new Setup {

      when(mockAdminService.migrateHeldSubmissions(any(), any()))
        .thenReturn(Future.successful(List(true, true, true)))

      val result: Result = await(controller.migrateHeldSubmissions(FakeRequest()))

      val expected: JsObject = Json.parse(
        """
          |{
          |  "total-attempted-migrations":3,
          |  "total-success":3
          |}
        """.stripMargin).as[JsObject]

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe expected
    }

    "return a 200 and an accurate json response when all held submissions migrated unsuccessfully" in new Setup {

      when(mockAdminService.migrateHeldSubmissions(any(), any()))
        .thenReturn(Future.successful(List(false, false, false)))

      val result: Result = await(controller.migrateHeldSubmissions(FakeRequest()))

      val expected: JsObject = Json.parse(
        """
          |{
          |  "total-attempted-migrations":3,
          |  "total-success":0
          |}
        """.stripMargin).as[JsObject]

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe expected
    }

    "return a 200 and an accurate json response when some held submissions migrated successfully" in new Setup {

      when(mockAdminService.migrateHeldSubmissions(any(), any()))
        .thenReturn(Future.successful(List(true, false, true)))

      val result: Result = await(controller.migrateHeldSubmissions(FakeRequest()))

      val expected: JsObject = Json.parse(
        """
          |{
          |  "total-attempted-migrations":3,
          |  "total-success":2
          |}
        """.stripMargin).as[JsObject]

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe expected
    }
  }
}
