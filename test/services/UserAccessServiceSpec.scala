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

package services

import connectors.{BusinessRegistrationConnector, _}
import fixtures.{BusinessRegistrationFixture, CorporationTaxRegistrationFixture}
import helpers.MockHelper
import models.{Email, UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserAccessServiceSpec
  extends UnitSpec with MockitoSugar with BusinessRegistrationFixture with CorporationTaxRegistrationFixture
  with BeforeAndAfterEach with MockHelper {

  val mockBRConnector = mock[BusinessRegistrationConnector]
  val mockCTRepo = mock[CorporationTaxRegistrationMongoRepository]
  val mockCTService = mock[CorporationTaxRegistrationService]
  val mockThrottleService = mock[ThrottleService]

  val mocks = collectMocks(mockBRConnector, mockCTRepo, mockCTService, mockThrottleService)

  implicit val hc = HeaderCarrier()

  trait Setup {
    val service = new UserAccessService {
      val threshold = 10
      val brConnector = mockBRConnector
      val ctService = mockCTService
      val ctRepository = mockCTRepo
      val throttleService = mockThrottleService
    }
  }

  override def beforeEach() {
    resetMocks(mocks)
  }

  "UserAccessService" should {
    "use the correct threshold from config" in new Setup {
      service.threshold shouldBe 10
    }
  }

  "checkUserAccess" should {

    val regId = "12345"
    val internalId = "int-123"
    val dateTime = DateTime.now(DateTimeZone.UTC)

    "return a UserAccessSuccessResponse with the created and confirmation ref flags set to false" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId)))
      when(mockBRConnector.updateLastSignedIn(any(), any())(any()))
        .thenReturn(Future.successful(dateTime.toString))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any[Option[DateTime]]()))
        .thenReturn(Some(draftCorporationTaxRegistration(regId)))

      await(service.checkUserAccess(internalId)) shouldBe Right(UserAccessSuccessResponse(regId, false, false, false))
    }

    "return a UserAccessLimitReachedResponse when the throttle service returns a false" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationNotFoundResponse)
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(false))
      when(mockBRConnector.createMetadataEntry(any()))
        .thenReturn(validBusinessRegistrationResponse)
      when(mockCTService.createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(validDraftCorporationTaxRegistration)

      await(service.checkUserAccess("321")) shouldBe Left(Json.toJson(UserAccessLimitReachedResponse(true)))
    }

    "fail if the registration is missing" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId)))
      when(mockBRConnector.updateLastSignedIn(any(), any())(any()))
        .thenReturn(Future.successful(dateTime.toString))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any[Option[DateTime]]()))
        .thenReturn(None)

      intercept[MissingRegistration] {
        await(service.checkUserAccess(internalId))
      }
    }

    "be successful with no conf refs but with email info" in new Setup {
      val expectedEmail = Email("a@a.a", "GG",true, false, false)
      val draftCTReg = draftCorporationTaxRegistration(regId).copy(verifiedEmail = Some(expectedEmail))

      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationNotFoundResponse)
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(true))
      when(mockBRConnector.createMetadataEntry(any()))
        .thenReturn(businessRegistrationResponse(regId))
      when(mockCTService.createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(draftCTReg)

      await(service.checkUserAccess(internalId)) shouldBe
        Right(UserAccessSuccessResponse(regId, true, false, false, Some(expectedEmail)))
    }

    "return a UserAccessSuccessResponse with the created flag set to true" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationNotFoundResponse)
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(true))
      when(mockBRConnector.createMetadataEntry(any()))
        .thenReturn(businessRegistrationResponse(regId))
      when(mockCTService.createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(draftCorporationTaxRegistration(regId))

      await(service.checkUserAccess("321")) shouldBe Right(UserAccessSuccessResponse("12345", true, false, false))
    }

    "return a UserAccessSuccessResponse with the confirmation refs flag set to true" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId)))
      when(mockBRConnector.updateLastSignedIn(any(), any())(any()))
        .thenReturn(Future.successful(dateTime.toString))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any()))
        .thenReturn(Some(validHeldCTRegWithData(regId)))

      await(service.checkUserAccess(internalId)) shouldBe Right(UserAccessSuccessResponse(regId, false, true, true))
    }

    "return an error when retrieving metadata returns a forbidden response" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationForbiddenResponse)

      val ex = intercept[Exception](await(service.checkUserAccess(internalId)))
      ex.getMessage shouldBe "Something went wrong"
    }
  }
}
