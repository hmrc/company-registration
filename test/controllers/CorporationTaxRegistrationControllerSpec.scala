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

package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import connectors.AuthConnector
import fixtures.{AuthFixture, CorporationTaxRegistrationFixture}
import helpers.SCRSSpec
import mocks.MockMetricsService
import models.{AcknowledgementReferences, ConfirmationReferences}
import org.mockito.Matchers
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.CorporationTaxRegistrationService
import play.api.test.Helpers._
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Matchers.any
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class CorporationTaxRegistrationControllerSpec extends SCRSSpec with CorporationTaxRegistrationFixture with AuthFixture {

  implicit val system = ActorSystem("CR")
  implicit val materializer = ActorMaterializer()

  class Setup {
    val controller = new CorporationTaxRegistrationController {
      val ctService = mockCTDataService
      val resourceConn = mockCTDataRepository
      val auth = mockAuthConnector
      val metrics = MockMetricsService
    }
  }

  val internalId = "int-12345"
  val regId = "reg-12345"
  val authority = buildAuthority(internalId)


  "createCorporationTaxRegistration" should {

    "return a 201 when a new entry is created from the parsed json" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(authority))

      when(mockCTDataService.createCorporationTaxRegistrationRecord(eqTo(internalId), eqTo(regId), eqTo("en")))
        .thenReturn(Future.successful(draftCorporationTaxRegistration(regId)))

      val request = FakeRequest().withJsonBody(Json.toJson(validCorporationTaxRegistrationRequest))
      val response = buildCTRegistrationResponse(regId)

      val result = call(controller.createCorporationTaxRegistration(regId), request)
      await(jsonBodyOf(result)) shouldBe Json.toJson(response)
      status(result) shouldBe CREATED
    }

    "return a 403 - forbidden when the user is not authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)

      val request = FakeRequest().withJsonBody(Json.toJson(validDraftCorporationTaxRegistration))
      val result = call(controller.createCorporationTaxRegistration(regId), request)
      status(result) shouldBe FORBIDDEN
    }
  }

  "retrieveCorporationTaxRegistration" should {

    "return a 200 and a CorporationTaxRegistration model is one is found" in new Setup {
      val regId = "0123456789"

      when(mockCTDataService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any()))
        .thenReturn(Future.successful(Some(validDraftCorporationTaxRegistration)))

      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.contains(regId))).
        thenReturn(Future.successful(Some((regId, validAuthority.ids.internalId))))

      val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())

      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(Some(validCorporationTaxRegistrationResponse))
    }

    "return a 404 if a CT registration record cannot be found" in new Setup {
      val regId = "testRegId"
      CTServiceMocks.retrieveCTDataRecord(regId, None)
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.contains(regId))).
        thenReturn(Future.successful(Some((regId, validAuthority.ids.internalId))))

      val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 - forbidden when the user is not authenticated" in new Setup {
      val regId = "testRegId"
      AuthenticationMocks.getCurrentAuthority(None)

      when(mockCTDataRepository.getInternalId(Matchers.any())).
        thenReturn(Future.successful(None))

      val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 - forbidden when the user is logged in but not authorised to access the resource" in new Setup {
      val regId = "testRegId"
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.contains(regId))).
        thenReturn(Future.successful(Some((regId, validAuthority.ids.internalId + "xxx"))))

      val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 - not found logged in the requested document doesn't exist" in new Setup {
      val regId = "testRegId"
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.contains(regId))).thenReturn(Future.successful(None))

      val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }

  "retrieveFullCorporationTaxRegistration" should {

    val registrationID = "testRegID"

    "return a 200 and a CorporationTaxRegistration model is found" in new Setup {
      CTServiceMocks.retrieveCTDataRecord(registrationID, Some(validDraftCorporationTaxRegistration))
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.contains(registrationID))).
        thenReturn(Future.successful(Some((registrationID, validAuthority.ids.internalId))))

      val result = call(controller.retrieveFullCorporationTaxRegistration(registrationID), FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(validDraftCorporationTaxRegistration)
    }

    "return a 404 if a CT registration record cannot be found" in new Setup {
      CTServiceMocks.retrieveCTDataRecord(registrationID, None)
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.contains(registrationID))).
        thenReturn(Future.successful(Some((registrationID, validAuthority.ids.internalId))))

      val result = call(controller.retrieveFullCorporationTaxRegistration(registrationID), FakeRequest())
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 - forbidden when the user is not authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)

      when(mockCTDataRepository.getInternalId(Matchers.any())).
        thenReturn(Future.successful(None))

      val result = call(controller.retrieveFullCorporationTaxRegistration(registrationID), FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 - forbidden when the user is logged in but not authorised to access the resource" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.contains(registrationID))).
        thenReturn(Future.successful(Some((registrationID, validAuthority.ids.internalId + "xxx"))))

      val result = call(controller.retrieveFullCorporationTaxRegistration(registrationID), FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 - not found logged in the requested document doesn't exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.contains(registrationID))).thenReturn(Future.successful(None))

      val result = call(controller.retrieveFullCorporationTaxRegistration(registrationID), FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }

  "retrieveConfirmationReference" should {

    val regId = "testRegId"

    "return a 200 and an acknowledgement ref is one exists" in new Setup {
      val expected = ConfirmationReferences("BRCT00000000123", "tx", "py", "12.00")
      when(mockCTDataService.retrieveConfirmationReference(Matchers.eq(regId)))
        .thenReturn(Future.successful(Some(expected)))

      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, Some((regId, validAuthority.ids.internalId)))

      val result = controller.retrieveConfirmationReference(regId)(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(expected)
    }

    "return a 404 if a record cannot be found" in new Setup {
      when(mockCTDataService.retrieveConfirmationReference(Matchers.eq(regId)))
        .thenReturn(None)
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, Some((regId, validAuthority.ids.internalId)))

      val result = controller.retrieveConfirmationReference(regId)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is not authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, None)

      val result = controller.retrieveConfirmationReference(regId)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is logged in but not authorised to access the resource" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, Some((regId, "xxx")))

      val result = controller.retrieveConfirmationReference(regId)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 when the user is logged in but the record doesn't exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, None)

      val result = controller.retrieveConfirmationReference(regId)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }

  "updateReferences" should {

    val regId = "testRegId"
    implicit val hc = HeaderCarrier()

    "return a 200 and an acknowledgement ref is one exists" in new Setup {
      val expectedRefs = ConfirmationReferences("BRCT00000000123", "tx", "py", "12.00")
      when(mockCTDataService.updateConfirmationReferences(Matchers.contains(regId), Matchers.any())(Matchers.any[HeaderCarrier], Matchers.any()))
        .thenReturn(Future.successful(Some(expectedRefs)))

      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, Some((regId, validAuthority.ids.internalId)))

      when(mockCTDataService.retrieveConfirmationReference(Matchers.eq(regId)))
        .thenReturn(Future.successful(Some(expectedRefs)))

      val result = controller.updateReferences(regId)(FakeRequest().withBody(Json.toJson(ConfirmationReferences("testTransactionId", "testPaymentRef", "testPaymentAmount", ""))))
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(expectedRefs)
    }

    "return a 404 if a record cannot be found" in new Setup {
      when(mockCTDataService.updateConfirmationReferences(Matchers.contains(regId), Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(None))
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, Some((regId, validAuthority.ids.internalId)))

      when(mockCTDataService.retrieveConfirmationReference(Matchers.eq(regId)))
        .thenReturn(Future.successful(None))

      val result = controller.updateReferences(regId)(FakeRequest().withBody(Json.toJson(ConfirmationReferences("testTransactionId", "testPaymentRef", "testPaymentAmount", ""))))
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is not authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, None)

      val result = controller.updateReferences(regId)(FakeRequest().withBody(Json.toJson(ConfirmationReferences("testTransactionId", "testPaymentRef", "testPaymentAmount", ""))))
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is logged in but not authorised to access the resource" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, Some((regId, "xxx")))

      val result = controller.updateReferences(regId)(FakeRequest().withBody(Json.toJson(ConfirmationReferences("testTransactionId", "testPaymentRef", "testPaymentAmount", ""))))
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 when the user is logged in but the record doesn't exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId(validAuthority.ids.internalId, None)

      val result = controller.updateReferences(regId)(FakeRequest().withBody(Json.toJson(ConfirmationReferences("testTransactionId", "testPaymentRef", "testPaymentAmount", ""))))
      status(result) shouldBe NOT_FOUND
    }
  }

  "acknowledgementConfirmation" should {
    "return a bad request" when {
      "given invalid json" in new Setup {
        val request = FakeRequest().withJsonBody(Json.toJson(""))
        val result = controller.acknowledgementConfirmation("TestAckRef")(request).run
        status(result) shouldBe BAD_REQUEST
      }
    }

    "return an OK" when {

      val ackRef = "TestAckRef"

      val refs = AcknowledgementReferences("aaa", "bbb", "ccc")

      implicit val format = AcknowledgementReferences.apiFormat
      val jsonBody = Json.toJson(refs)

      val request = FakeRequest().withBody(jsonBody)

      "a CT record cannot be found against the given ack ref" in new Setup {
        when(mockCTDataService.updateCTRecordWithAckRefs(Matchers.eq(ackRef), Matchers.eq(refs)))
          .thenReturn(Future.successful(Some(validHeldCorporationTaxRegistration)))

        val result = controller.acknowledgementConfirmation(ackRef)(request)
        status(result) shouldBe OK
      }
    }

    "return an AckRefNotFound" when {

      val ackRef = "TestAckRef"

      val refs = AcknowledgementReferences("aaa", "bbb", "ccc")

      implicit val format = AcknowledgementReferences.apiFormat
      val jsonBody = Json.toJson(refs)

      val request = FakeRequest().withBody(jsonBody)

      "a CT record cannot be found against the given ack ref" in new Setup {
        when(mockCTDataService.updateCTRecordWithAckRefs(eqTo(ackRef), eqTo(refs)))
          .thenReturn(Future.successful(None))

        val result = controller.acknowledgementConfirmation(ackRef)(request)
        status(result) shouldBe NOT_FOUND
      }
    }
  }
}
