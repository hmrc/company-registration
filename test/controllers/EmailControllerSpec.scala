/*
 * Copyright 2020 HM Revenue & Customs
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

import helpers.BaseSpec
import mocks.AuthorisationMocks
import models.Email
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, MissingCTDocument, Repositories}
import services.EmailService
import uk.gov.hmrc.auth.core.MissingBearerToken

import scala.concurrent.Future

class EmailControllerSpec extends BaseSpec with AuthorisationMocks {

  val mockEmailService: EmailService = mock[EmailService]
  val mockRepositories: Repositories = mock[Repositories]
  override val mockResource: CorporationTaxRegistrationMongoRepository = mockTypedResource[CorporationTaxRegistrationMongoRepository]

  class Setup {
    val emailController: EmailController =
      new EmailController(
        mockEmailService,
        mockAuthConnector,
        mockRepositories,
        stubControllerComponents()
      ) {
        override lazy val resource: CorporationTaxRegistrationMongoRepository = mockResource

      }
  }

  val registrationID = "reg-12345"
  val internalId = "int-12345"
  val otherInternalID = "other-int-12345"

  val email: Email = Email(
    address = "testAddress",
    emailType = "GG",
    linkSent = true,
    verified = true,
    returnLinkEmailSent = true
  )

  val emailJson: JsValue = Json.toJson(email)

  "retrieveEmail" should {

    "return a 200 and an Email json object if the user is authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      when(mockEmailService.retrieveEmail(eqTo(registrationID)))
        .thenReturn(Future.successful(Some(email)))

      val result: Future[Result] = emailController.retrieveEmail(registrationID)(FakeRequest())
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(email)
    }

    "return a 401 when the user is not logged in" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result: Future[Result] = emailController.retrieveEmail(registrationID)(FakeRequest())
      status(result) shouldBe UNAUTHORIZED
    }

    "return a 403 when the user is logged in but not authorised to access the resource" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(otherInternalID))

      val result: Future[Result] = emailController.retrieveEmail(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 when the user is logged in but a CT record doesn't exist" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.failed(new MissingCTDocument("hfbhdbf")))

      val result: Future[Result] = emailController.retrieveEmail(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }

  "updateEmail" should {

    val request = FakeRequest().withBody(Json.toJson(email))

    "return a 200 and an email json object when the user is authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      when(mockEmailService.updateEmail(eqTo(registrationID), eqTo(email)))
        .thenReturn(Future.successful(Some(email)))

      val result: Future[Result] = emailController.updateEmail(registrationID)(request)
      status(result) shouldBe OK
      contentAsJson(result) shouldBe emailJson
    }

    "return a 401 when the user is not logged in" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result: Future[Result] = emailController.updateEmail(registrationID)(request)
      status(result) shouldBe UNAUTHORIZED
    }

    "return a 403 when the user is logged in but not authorised to access the resource" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(otherInternalID))

      val result: Future[Result] = emailController.updateEmail(registrationID)(request)
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 when the user is authorised but the CT document doesn't exist" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.failed(new MissingCTDocument("hfbhdbf")))

      val result: Future[Result] = emailController.updateEmail(registrationID)(request)
      status(result) shouldBe NOT_FOUND
    }
  }
}
