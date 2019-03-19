/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.LocalTime

import auth.CryptoSCRS
import fixtures.CorporationTaxRegistrationFixture
import helpers.BaseSpec
import mocks.{AuthorisationMocks, MockMetricsService}
import models.validation.{APIValidation, MongoValidation}
import models.{AcknowledgementReferences, ConfirmationReferences}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SubmissionService
import uk.gov.hmrc.auth.core.InsufficientConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import utils.AlertLogging

import scala.concurrent.Future

class SubmissionControllerSpec extends BaseSpec with AuthorisationMocks with CorporationTaxRegistrationFixture {

  val mockSubmissionService = mock[SubmissionService]

  class Setup (nowTime: LocalTime = LocalTime.parse("13:00:00")){
    val controller = new SubmissionController {
      val submissionService = mockSubmissionService
      val resource = mockResource
      val authConnector = mockAuthConnector
      val metricsService = MockMetricsService
      val alertLogging: AlertLogging = new AlertLogging {}
      override val cryptoSCRS: CryptoSCRS = mockInstanceOfCrypto
    }
  }

  val regId = "reg-12345"
  val internalId = "int-12345"
  val authProviderId = "auth-prov-id-12345"

  "handleUserSubmission" should {

    val credentials = Credentials(authProviderId, "testProviderType")

    val confRefs = ConfirmationReferences("testTransactionId", "testPaymentRef", Some("testPaymentAmount"), Some(""))
    val request = FakeRequest().withBody(Json.toJson(confRefs))

    "return a 200 and an acknowledgement ref is one exists" in new Setup {
      mockAuthorise(Future.successful(new ~(internalId, credentials)))
      mockGetInternalId(Future.successful(internalId))

      val expectedRefs = ConfirmationReferences("BRCT00000000123", "tx", Some("py"), Some("12.00"))

      when(mockSubmissionService.handleSubmission(eqTo(regId), any(), any(), eqTo(false))(any(), any()))
        .thenReturn(Future.successful(expectedRefs))

      when(mockCTDataService.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(expectedRefs)))

      val result = await(controller.handleUserSubmission(regId)(request))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(expectedRefs)
    }

    "return a 403 when the user is not authenticated" in new Setup {
      mockAuthorise(Future.failed(InsufficientConfidenceLevel()))

      val result = await(controller.handleUserSubmission(regId)(request))
      status(result) shouldBe FORBIDDEN
    }
  }

  "acknowledgementConfirmation" should {

    def request(ctutr: Boolean, code: String) = FakeRequest().withBody(
      Json.toJson(AcknowledgementReferences(if (ctutr) Option("aaa") else None, "bbb", code))(AcknowledgementReferences.format(APIValidation, mockInstanceOfCrypto))
    )

    "return a bad request" when {
      "given invalid json" in new Setup {
        val request = FakeRequest().withBody(Json.toJson(""))
        val result = await(controller.acknowledgementConfirmation("TestAckRef")(request))
        status(result) shouldBe BAD_REQUEST
      }
      "given an Accepted response without a CTUTR" in new Setup {
        val json = Json.toJson(AcknowledgementReferences(None, "bbb", "04"))(AcknowledgementReferences.format(MongoValidation, mockInstanceOfCrypto))

        val request = FakeRequest().withBody(Json.toJson(json))
        val result = await(controller.acknowledgementConfirmation("TestAckRef")(request))
        status(result) shouldBe BAD_REQUEST
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
          status(result) shouldBe OK
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

          val ctutrExists = controller.acknowledgementConfirmation(ackRef)(request(ctutr = true, rejections.head))
          status(ctutrExists) shouldBe OK
        }

        "has no CTUTR" in new Setup {
            when(mockSubmissionService.updateCTRecordWithAckRefs(ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
              .thenReturn(Future.successful(rejected))
            rejections.tail foreach { code =>
              val result = controller.acknowledgementConfirmation(ackRef)(request(ctutr = false, code))
              status(result) shouldBe OK
            }
        }
      }

      "return an AckRefNotFound" when {
        val ackRef = "TestAckRef"

        "a CT record cannot be found against the given ack ref" in new Setup {
          when(mockSubmissionService.updateCTRecordWithAckRefs(eqTo(ackRef), any()))
            .thenReturn(Future.successful(None))

          val result = controller.acknowledgementConfirmation(ackRef)(request(ctutr = true, "04"))
          status(result) shouldBe NOT_FOUND
        }
      }

      "return a RuntimeException" when {
        val ackRef = "TestAckRef"

        "the status provided is not recognised by the contract" in new Setup {
          val result = controller.acknowledgementConfirmation(ackRef)(request(ctutr = true, "I'm a surprise"))
          intercept[RuntimeException](await(result))
        }
      }
    }
  }
}
