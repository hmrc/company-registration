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

import fixtures.CorporationTaxRegistrationFixture
import helpers.BaseSpec
import mocks.{AuthorisationMocks, MockMetricsService}
import models.{AcknowledgementReferences, ConfirmationReferences}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CompanyRegistrationDoesNotExist, RegistrationProgressUpdated}
import uk.gov.hmrc.auth.core.InsufficientConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}

import scala.concurrent.Future

class CorporationTaxRegistrationControllerSpec extends BaseSpec with AuthorisationMocks with CorporationTaxRegistrationFixture {

  class Setup {
    val controller = new CorporationTaxRegistrationController {
      val ctService = mockCTDataService
      val resource = mockResource
      val authConnector = mockAuthClientConnector
      val metricsService = MockMetricsService
    }
  }

  val regId = "reg-12345"
  val internalId = "int-12345"
  val authProviderId = "auth-prov-id-12345"

  "createCorporationTaxRegistration" should {

    val request = FakeRequest().withBody(Json.toJson(validCorporationTaxRegistrationRequest))

    "return a 201 when a new entry is created from the parsed json" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      when(mockCTDataService.createCorporationTaxRegistrationRecord(eqTo(internalId), eqTo(regId), eqTo("en")))
        .thenReturn(Future.successful(draftCorporationTaxRegistration(regId)))
      val response = buildCTRegistrationResponse(regId)

      val result = await(controller.createCorporationTaxRegistration(regId)(request))
      status(result) shouldBe CREATED
      contentAsJson(result) shouldBe Json.toJson(response)
    }

    "return a 403 when the user is not authorised" in new Setup {
      mockAuthorise(Future.failed(InsufficientConfidenceLevel()))

      val result = controller.createCorporationTaxRegistration(regId)(request)
      status(result) shouldBe FORBIDDEN
    }
  }

  "retrieveCorporationTaxRegistration" should {

    val ctRegistrationResponse = buildCTRegistrationResponse(regId)

    "return a 200 and a CorporationTaxRegistration model is one is found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      when(mockCTDataService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any()))
        .thenReturn(Future.successful(Some(draftCorporationTaxRegistration(regId))))

      val result = await(controller.retrieveCorporationTaxRegistration(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(ctRegistrationResponse)
    }

    "return a 404 if a CT registration record cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      CTServiceMocks.retrieveCTDataRecord(regId, None)

      val result = await(controller.retrieveCorporationTaxRegistration(regId)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is not authenticated" in new Setup {
      mockAuthorise(Future.failed(InsufficientConfidenceLevel()))

      val result = controller.retrieveCorporationTaxRegistration(regId)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }
  }

  "retrieveFullCorporationTaxRegistration" should {

    "return a 200 and a CorporationTaxRegistration model is found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      CTServiceMocks.retrieveCTDataRecord(regId, Some(validDraftCorporationTaxRegistration))

      val result = await(controller.retrieveFullCorporationTaxRegistration(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(validDraftCorporationTaxRegistration)
    }

    "return a 404 if a CT registration record cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      CTServiceMocks.retrieveCTDataRecord(regId, None)

      val result = await(controller.retrieveFullCorporationTaxRegistration(regId)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is not authenticated" in new Setup {
      mockAuthorise(Future.failed(InsufficientConfidenceLevel()))

      val result = controller.retrieveFullCorporationTaxRegistration(regId)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }
  }

  "retrieveConfirmationReference" should {

    "return a 200 and an acknowledgement ref is one exists" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      val expected = ConfirmationReferences("BRCT00000000123", "tx", Some("py"), Some("12.00"))
      when(mockCTDataService.retrieveConfirmationReferences(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Some(expected)))

      val result = await(controller.retrieveConfirmationReference(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(expected)
    }

    "return a 404 if a record cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      when(mockCTDataService.retrieveConfirmationReferences(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(None))

      val result = controller.retrieveConfirmationReference(regId)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is not authenticated" in new Setup {
      mockAuthorise(Future.failed(InsufficientConfidenceLevel()))

      val result = controller.retrieveConfirmationReference(regId)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }
  }

  "updateReferences" should {

    val credentials = Credentials(authProviderId, "testProviderType")

    val confRefs = ConfirmationReferences("testTransactionId", "testPaymentRef", Some("testPaymentAmount"), Some(""))
    val request = FakeRequest().withBody(Json.toJson(confRefs))

    "return a 200 and an acknowledgement ref is one exists" in new Setup {
      mockAuthorise(Future.successful(new ~(internalId, credentials)))
      mockGetInternalId(Future.successful(Some(internalId)))

      val expectedRefs = ConfirmationReferences("BRCT00000000123", "tx", Some("py"), Some("12.00"))

      when(mockCTDataService.handleSubmission(eqTo(regId), any(), any())(any(), any(), eqTo(false)))
        .thenReturn(Future.successful(expectedRefs))

      when(mockCTDataService.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(expectedRefs)))

      val result = await(controller.handleSubmission(regId)(request))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(expectedRefs)
    }

    "return a 403 when the user is not authenticated" in new Setup {
      mockAuthorise(Future.failed(InsufficientConfidenceLevel()))

      val result = await(controller.handleSubmission(regId)(request))
      status(result) shouldBe FORBIDDEN
    }
  }

  "acknowledgementConfirmation" should {

    def request(ctutr: Boolean, code: String) = FakeRequest().withBody(Json.toJson(AcknowledgementReferences(if (ctutr) Option("aaa") else None, "bbb", code)))

    "return a bad request" when {
      "given invalid json" in new Setup {
        val request = FakeRequest().withBody(Json.toJson(""))
        val result = await(controller.acknowledgementConfirmation("TestAckRef")(request))
        status(result) shouldBe BAD_REQUEST
      }
      "given an Accepted response without a CTUTR" in new Setup {
        val json = Json.toJson(AcknowledgementReferences(None, "bbb", "04"))

        val request = FakeRequest().withBody(Json.toJson(json))
        val result = await(controller.acknowledgementConfirmation("TestAckRef")(request))
        status(result) shouldBe BAD_REQUEST
      }
    }

    "return an OK" when {
      val ackRef = "TestAckRef"
      val accepted = List("04", "05")

      "given an Accepted response with a CTUTR" in new Setup {
        when(mockCTDataService.updateCTRecordWithAckRefs(ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
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
          when(mockCTDataService.updateCTRecordWithAckRefs(ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
            .thenReturn(Future.successful(rejected))

          val ctutrExists = controller.acknowledgementConfirmation(ackRef)(request(ctutr = true, rejections.head))
          status(ctutrExists) shouldBe OK
        }

        "has no CTUTR" in new Setup {
            when(mockCTDataService.updateCTRecordWithAckRefs(ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
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
          when(mockCTDataService.updateCTRecordWithAckRefs(eqTo(ackRef), any()))
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

  "updateRegistrationProgress" should {

    def progressRequest(progress: String) = Json.parse(s"""{"registration-progress":"${progress}"}""")

    "Extract the progress correctly from the message and request doc is updated" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      val progress = "HO5"
      val request = FakeRequest().withBody(progressRequest(progress))
      when(mockCTDataService.updateRegistrationProgress(ArgumentMatchers.eq(regId), ArgumentMatchers.any[String]())).
        thenReturn(Future.successful(RegistrationProgressUpdated))
      val response = await(controller.updateRegistrationProgress(regId)(request))

      status(response) shouldBe OK

      val captor = ArgumentCaptor.forClass[String, String](classOf[String])
      verify(mockCTDataService, times(1)).updateRegistrationProgress(eqTo(regId), captor.capture())
      captor.getValue shouldBe progress
    }

    "Return not found is the doc couldn't be updated" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      val progress = "N/A"
      val request = FakeRequest().withBody(progressRequest(progress))

      when(mockCTDataService.updateRegistrationProgress(eqTo(regId), any())).
        thenReturn(Future.successful(CompanyRegistrationDoesNotExist))

      val result = await(controller.updateRegistrationProgress(regId)(request))
      status(result) shouldBe NOT_FOUND
    }
  }
}
