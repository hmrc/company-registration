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

import fixtures.CorporationTaxRegistrationFixture
import helpers.BaseSpec
import mocks.{AuthorisationMocks, MockMetricsService}
import models.validation.{APIValidation, MongoValidation}
import models.{AcknowledgementReferences, ConfirmationReferences}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.auth.core.InsufficientConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import utils.AlertLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmissionControllerSpec extends BaseSpec with AuthorisationMocks with CorporationTaxRegistrationFixture {

  val mockRepositories: Repositories = mock[Repositories]
  val mockAlertLogging: AlertLogging = mock[AlertLogging]
  override val mockResource: CorporationTaxRegistrationMongoRepository = mockTypedResource[CorporationTaxRegistrationMongoRepository]

  class Setup {
    val controller: SubmissionController =
      new SubmissionController(
        MockMetricsService,
        mockAuthConnector,
        mockSubmissionService,
        mockRepositories,
        mockAlertLogging,
        mockInstanceOfCrypto,
        stubControllerComponents()
      ) {
        override lazy val resource: CorporationTaxRegistrationMongoRepository = mockResource
      }
  }

  val regId = "reg-12345"
  val internalId = "int-12345"
  val authProviderId = "auth-prov-id-12345"

  "handleUserSubmission" must {

    val credentials = Credentials(authProviderId, "testProviderType")

    val confRefs = ConfirmationReferences("testTransactionId", "testPaymentRef", Some("testPaymentAmount"), Some(""))
    val request = FakeRequest().withBody(Json.toJson(confRefs))

    "return a 200 and an acknowledgement ref is one exists" in new Setup {
      mockAuthorise(Future.successful(new ~(Some(internalId), Some(credentials))))
      mockGetInternalId(Future.successful(internalId))

      val expectedRefs: ConfirmationReferences = ConfirmationReferences("BRCT00000000123", "tx", Some("py"), Some("12.00"))

      when(mockSubmissionService.handleSubmission(eqTo(regId), any(), any(), eqTo(false))(any(), any()))
        .thenReturn(Future.successful(expectedRefs))

      when(mockCTDataService.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(expectedRefs)))

      val result: Future[Result] = controller.handleUserSubmission(regId)(request)
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(expectedRefs)
    }

    "return a forbidden if internalId not retrieved from Auth" in new Setup {

      mockAuthorise(Future.successful(new ~(None, Some(credentials))))

      val result: Future[Result] = controller.handleUserSubmission(regId)(request)
      status(result) mustBe FORBIDDEN
    }

    "return a forbidden if credentials are not retrieved from Auth" in new Setup {

      mockAuthorise(Future.successful(new ~(Some(internalId), None)))
      mockGetInternalId(Future.successful(internalId))

      val result: Future[Result] = controller.handleUserSubmission(regId)(request)
      status(result) mustBe FORBIDDEN
    }

    "return a 403 when the user is not authenticated" in new Setup {
      mockAuthorise(Future.failed(InsufficientConfidenceLevel()))

      val result: Future[Result] = controller.handleUserSubmission(regId)(request)
      status(result) mustBe FORBIDDEN
    }
  }

  "acknowledgementConfirmation" must {

    def request(ctutr: Boolean, code: String): FakeRequest[JsValue] = FakeRequest().withBody(
      Json.toJson(AcknowledgementReferences(if (ctutr) Some("testCtutr") else None, "testTimestamp", code))(AcknowledgementReferences.format(APIValidation, mockInstanceOfCrypto))
    )

    "return a bad request" when {
      "given invalid json" in new Setup {
        val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(""))
        val result: Future[Result] = controller.acknowledgementConfirmation("TestAckRef")(request)
        status(result) mustBe BAD_REQUEST
      }
      "given an Accepted response without a CTUTR" in new Setup {
        val json: JsValue = Json.toJson(AcknowledgementReferences(None, "testTimestamp", "04"))(AcknowledgementReferences.format(MongoValidation, mockInstanceOfCrypto))

        val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(json))
        val result: Future[Result] = controller.acknowledgementConfirmation("TestAckRef")(request)
        status(result) mustBe BAD_REQUEST
      }
    }

    "return an OK" when {
      val ackRef = "TestAckRef"
      val accepted = List("04", "05")

      "given an Accepted response with a CTUTR" in new Setup {
        when(mockSubmissionService.updateCTRecordWithAckRefs(ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some(validHeldCorporationTaxRegistration)))

        accepted foreach { code =>
          val result = controller.acknowledgementConfirmation(ackRef)(request(ctutr = true, code))
          status(result) mustBe OK
        }
      }

      "given any Rejected response" which {
        val rejections = List("06", "07", "08", "09", "10")

        val rejected = Some(
          validHeldCorporationTaxRegistration
            .copy(acknowledgementReferences = Some(AcknowledgementReferences(None, "Timestamp", "06")))
        )

        "has a CTUTR" in new Setup {
          when(mockSubmissionService.updateCTRecordWithAckRefs(ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
            .thenReturn(Future.successful(rejected))

          val ctutrExists: Future[Result] = controller.acknowledgementConfirmation(ackRef)(request(ctutr = true, rejections.head))
          status(ctutrExists) mustBe OK
        }

        "has no CTUTR" in new Setup {
          when(mockSubmissionService.updateCTRecordWithAckRefs(ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
            .thenReturn(Future.successful(rejected))
          rejections.tail foreach { code =>
            val result = controller.acknowledgementConfirmation(ackRef)(request(ctutr = false, code))
            status(result) mustBe OK
          }
        }
      }

      "return an AckRefNotFound" when {
        val ackRef = "TestAckRef"

        "a CT record cannot be found against the given ack ref" in new Setup {
          when(mockSubmissionService.updateCTRecordWithAckRefs(eqTo(ackRef), any()))
            .thenReturn(Future.successful(None))

          val result: Future[Result] = controller.acknowledgementConfirmation(ackRef)(request(ctutr = true, "04"))
          status(result) mustBe NOT_FOUND
        }
      }

      "return a RuntimeException" when {
        val ackRef = "TestAckRef"

        "the status provided is not recognised by the contract" in new Setup {
          val result: Future[Result] = controller.acknowledgementConfirmation(ackRef)(request(ctutr = true, ""))
          intercept[RuntimeException](await(result))
        }
      }
    }
  }
}
