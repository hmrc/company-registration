/*
 * Copyright 2016 HM Revenue & Customs
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

import connectors.Authority
import controllers.test.EmailController
import fixtures.AuthFixture
import helpers.SCRSSpec
import models.Email
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.EmailService
import org.mockito.Mockito._
import play.api.test.Helpers._

import scala.concurrent.Future

class EmailControllerSpec extends SCRSSpec with MockitoSugar with AuthFixture{

  val mockEmailService = mock[EmailService]

  class Setup {
    val emailController = new EmailController {
      val emailService = mockEmailService
      val auth = mockAuthConnector
      val resourceConn = mockCTDataRepository
    }
  }

  val registrationId = "12345"
  val email = Email("testAddress", "GG", linkSent = true, verified = true)


  "retrieveEmail" should {

    "return a 200 and an Email json object" in new Setup {
      when(mockEmailService.retrieveEmail(Matchers.eq(registrationId)))
        .thenReturn(Future.successful(Some(email)))

      AuthorisationMocks.mockSuccessfulAuthorisation(registrationId, validAuthority)

      val result = await(emailController.retrieveEmail(registrationId)(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.toJson(email)
    }

    "return a 403 when the user is not authenticated or logged in" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised

      val result = await(emailController.retrieveEmail(registrationId)(FakeRequest()))
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is logged in but not authorised to access the resource" in new Setup {
      AuthorisationMocks.mockNotAuthorised(registrationId, validAuthority)

      val result = await(emailController.retrieveEmail(registrationId)(FakeRequest()))
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 when the user is logged in but the record to authorise against doesn't exist" in new Setup {
      AuthorisationMocks.mockAuthResourceNotFound(validAuthority)

      val result = await(emailController.retrieveEmail(registrationId)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
    }
  }

  "updateEmail" should {

    val emailJson = Json.toJson(email)

    val request = FakeRequest().withJsonBody(emailJson)

    "return a 200 and an email json object" in new Setup {
      when(mockEmailService.updateEmail(Matchers.eq(registrationId), Matchers.eq(email)))
        .thenReturn(Future.successful(Some(email)))

      AuthorisationMocks.mockSuccessfulAuthorisation(registrationId, validAuthority)

      val result = await(call(emailController.updateEmail(registrationId), request))
      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe emailJson
    }

    "return a 403 when the user is not authenticated or logged in" in new Setup {
      AuthorisationMocks.mockNotLoggedInOrAuthorised

      val result = await(call(emailController.updateEmail(registrationId), request))
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is logged in but not authorised to access the resource" in new Setup {
      AuthorisationMocks.mockNotAuthorised(registrationId, validAuthority)

      val result = await(call(emailController.updateEmail(registrationId), request))
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 when the user is logged in but the record to authorise against doesn't exist" in new Setup {
      AuthorisationMocks.mockAuthResourceNotFound(validAuthority)

      val result = await(call(emailController.updateEmail(registrationId), request))
      status(result) shouldBe NOT_FOUND
    }
  }
}
