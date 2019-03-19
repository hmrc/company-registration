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
import models.validation.MongoValidation
import models.{CHROAddress, ConfirmationReferences, CorporationTaxRegistration, PPOBAddress}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.InsufficientConfidenceLevel
import utils.AlertLogging

import scala.concurrent.Future

class CorporationTaxRegistrationControllerSpec extends BaseSpec with AuthorisationMocks with CorporationTaxRegistrationFixture {
  class Setup (nowTime: LocalTime = LocalTime.parse("13:00:00")){
    val controller = new CorporationTaxRegistrationController {
      val ctService = mockCTDataService
      val resource = mockResource
      val authConnector = mockAuthConnector
      val metricsService = MockMetricsService
      val alertLogging: AlertLogging = new AlertLogging {
      }
      override val cryptoSCRS: CryptoSCRS = mockInstanceOfCrypto
    }
  }

  val regId = "reg-12345"
  val internalId = "int-12345"
  val authProviderId = "auth-prov-id-12345"

  "createCorporationTaxRegistration" should {

    val request = FakeRequest().withBody(Json.toJson(validCorporationTaxRegistrationRequest))

    "return a 201 when a new entry is created from the parsed json" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

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
      mockGetInternalId(Future.successful(internalId))

      when(mockCTDataService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any()))
        .thenReturn(Future.successful(Some(draftCorporationTaxRegistration(regId))))

      val result = await(controller.retrieveCorporationTaxRegistration(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(ctRegistrationResponse)
    }

    "return a 404 if a CT registration record cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

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
      mockGetInternalId(Future.successful(internalId))

      CTServiceMocks.retrieveCTDataRecord(regId, Some(validDraftCorporationTaxRegistration))

      val result = await(controller.retrieveFullCorporationTaxRegistration(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(validDraftCorporationTaxRegistration)(CorporationTaxRegistration.format(MongoValidation, mockInstanceOfCrypto))
    }

    "return a 404 if a CT registration record cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

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
      mockGetInternalId(Future.successful(internalId))

      val expected = ConfirmationReferences("BRCT00000000123", "tx", Some("py"), Some("12.00"))
      when(mockCTDataService.retrieveConfirmationReferences(ArgumentMatchers.eq(regId)))
        .thenReturn(Future.successful(Some(expected)))

      val result = await(controller.retrieveConfirmationReference(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(expected)
    }

    "return a 404 if a record cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

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

  "updateRegistrationProgress" should {

    def progressRequest(progress: String) = Json.parse(s"""{"registration-progress":"${progress}"}""")

    "Extract the progress correctly from the message and request doc is updated" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      val progress = "HO5"
      val request = FakeRequest().withBody(progressRequest(progress))
      when(mockCTDataService.updateRegistrationProgress(ArgumentMatchers.eq(regId), ArgumentMatchers.any[String]())).
        thenReturn(Future.successful(Some("")))
      val response = await(controller.updateRegistrationProgress(regId)(request))

      status(response) shouldBe OK

      val captor = ArgumentCaptor.forClass[String, String](classOf[String])
      verify(mockCTDataService, times(1)).updateRegistrationProgress(eqTo(regId), captor.capture())
      captor.getValue shouldBe progress
    }

    "Return not found is the doc couldn't be updated" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      val progress = "N/A"
      val request = FakeRequest().withBody(progressRequest(progress))

      when(mockCTDataService.updateRegistrationProgress(eqTo(regId), any())).
        thenReturn(Future.successful(None))

      val result = await(controller.updateRegistrationProgress(regId)(request))
      status(result) shouldBe NOT_FOUND
    }
  }

  "roAddressValid" should {

    val cHROAddress = Json.toJson(CHROAddress("p","14 St Test Walk",Some("Test"),"c","l",Some("pb"),Some("TE1 1ST"),Some("r")))

    "return an OK if the RO address can be converted to a PPOB address" in new Setup {
      when(mockCTDataService.convertROToPPOBAddress(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(PPOBAddress("test", "test", None, None, None, None, None, "test"))))

      val request = FakeRequest().withBody(cHROAddress)
      val response = await(controller.roAddressValid()(request))

      status(response) shouldBe OK
    }

    "return a Bad Request if the RO address cannot be converted to a PPOB address" in new Setup {
      when(mockCTDataService.convertROToPPOBAddress(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val request = FakeRequest().withBody(cHROAddress)
      val response = await(controller.roAddressValid()(request))

      status(response) shouldBe BAD_REQUEST
    }
  }
}
