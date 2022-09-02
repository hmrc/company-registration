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

import fixtures.CompanyDetailsFixture
import helpers.BaseSpec
import mocks.{AuthorisationMocks, MockMetricsService}
import models.ErrorResponse
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, MissingCTDocument, Repositories}
import services.PrepareAccountService
import uk.gov.hmrc.auth.core.MissingBearerToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CompanyDetailsControllerSpec extends BaseSpec with AuthorisationMocks with CompanyDetailsFixture {

  val mockPrepareAccountService: PrepareAccountService = mock[PrepareAccountService]
  val mockRepositories: Repositories = mock[Repositories]
  override val mockResource: CorporationTaxRegistrationMongoRepository = mockTypedResource[CorporationTaxRegistrationMongoRepository]

  trait Setup {
    val controller: CompanyDetailsController =
      new CompanyDetailsController(
        MockMetricsService,
        mockCompanyDetailsService,
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

  val companyDetailsResponseJson: JsValue = Json.toJson(Some(validCompanyDetailsResponse(registrationID)))

  "retrieveCompanyDetails" must {

    "return a 200 and a Company details record when authorised" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetails))

      val result: Future[Result] = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe companyDetailsResponseJson

    }

    "return a 404 if company details are not found on the CT document" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, None)

      val result: Future[Result] = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe ErrorResponse.companyDetailsNotFound
    }

    "return a 404 when the CT document cannot be found" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.failed(new MissingCTDocument("testRegId")))

      val result: Future[Result] = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) mustBe NOT_FOUND
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(otherInternalID))

      val result: Future[Result] = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) mustBe FORBIDDEN
    }

    "return a 401 when the user is not logged in" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result: Future[Result] = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) mustBe UNAUTHORIZED
    }
  }

  "updateCompanyDetails" must {

    val request = FakeRequest().withBody(Json.toJson(validCompanyDetails))

    "return a 200 and a company details response if the user is authorised" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, Some(validCompanyDetails))

      val result: Future[Result] = controller.updateCompanyDetails(registrationID)(request)
      status(result) mustBe OK
      contentAsJson(result) mustBe companyDetailsResponseJson
    }

    "return a 404 if the record to update does not exist" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, None)

      val result: Future[Result] = controller.updateCompanyDetails(registrationID)(request)
      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe ErrorResponse.companyDetailsNotFound
    }

    "return a 404 when the user is authorised but the CT document does not exist" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.failed(new MissingCTDocument("testRegId")))

      val result: Future[Result] = controller.updateCompanyDetails(registrationID)(request)
      status(result) mustBe NOT_FOUND
    }

    "return a 401 when the user is not logged in" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result: Future[Result] = controller.updateCompanyDetails(registrationID)(request)
      status(result) mustBe UNAUTHORIZED
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(otherInternalID))

      val result: Future[Result] = controller.updateCompanyDetails(registrationID)(request)
      status(result) mustBe FORBIDDEN
    }

    "verify that metrics are captured on a successful update" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, Some(validCompanyDetails))

      val result: Future[Result] = controller.updateCompanyDetails(registrationID)(request)
      status(result) mustBe OK
    }
  }

  "saveHandOff2ReferenceAndGenerateAckRef" must {
    val ackRefJsObject = Json.obj("acknowledgement-reference" -> "testAckRef")
    val requestContainingTxId = FakeRequest().withBody(Json.obj("transaction_id" -> "testTransactionId"))
    "return 200 as service returned jsObject" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))
      CompanyDetailsServiceMocks.saveTxidAndGenerateAckRef(Future.successful(ackRefJsObject))

      val result: Future[Result] = controller.saveHandOff2ReferenceAndGenerateAckRef("testRegId")(requestContainingTxId)
      status(result) mustBe 200
      contentAsJson(result).as[JsObject] mustBe ackRefJsObject
    }
    "return exception if service returned exception" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))
      CompanyDetailsServiceMocks.saveTxidAndGenerateAckRef(Future.failed(new Exception("")))

      intercept[Exception](await(
        controller.saveHandOff2ReferenceAndGenerateAckRef("testRegId")(requestContainingTxId)
      ))

    }
    "return 400 if json incorrect" in new Setup {
      val requestNOTContainingTxId: FakeRequest[JsObject] = FakeRequest().withBody(Json.obj())
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))
      val result: Future[Result] = controller.saveHandOff2ReferenceAndGenerateAckRef("testRegId")(requestNOTContainingTxId)
      status(result) mustBe 400
    }
  }
}