/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.admin.AdminService
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._

import scala.concurrent.Future

class AdminControllerSpec extends UnitSpec with MockitoSugar {

  implicit val act = ActorSystem()
  implicit val mat = ActorMaterializer()

  val mockAdminService = mock[AdminService]

  trait Setup {
    val controller = new AdminController {
      override val adminService = mockAdminService
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
}
