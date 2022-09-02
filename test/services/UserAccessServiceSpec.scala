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

package services

import connectors._
import fixtures.{BusinessRegistrationFixture, CorporationTaxRegistrationFixture}
import helpers.MockHelper
import models.{Email, UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UserAccessServiceSpec extends PlaySpec with MockitoSugar with BusinessRegistrationFixture
  with CorporationTaxRegistrationFixture with BeforeAndAfterEach with MockHelper {

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
      implicit val ec: ExecutionContext = global
    }
  }

  override def beforeEach() {
    resetMocks(mocks)
  }

  "UserAccessService" must {
    "use the correct threshold from config" in new Setup {
      service.threshold mustBe 10F
    }
  }

  "checkUserAccess" must {

    val regId = "12345"
    val internalId = "int-123"
    val dateTime = DateTime.now(DateTimeZone.UTC)

    "return a UserAccessSuccessResponse with the created and confirmation ref flags set to false" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId))))
      when(mockBRConnector.updateLastSignedIn(any(), any())(any()))
        .thenReturn(Future.successful(dateTime.toString))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any[Option[DateTime]]()))
        .thenReturn(Future.successful(Some(draftCorporationTaxRegistration(regId))))

      await(service.checkUserAccess(internalId)) mustBe Right(UserAccessSuccessResponse(regId, false, false, false))
    }

    "return a UserAccessLimitReachedResponse when the throttle service returns a false" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationNotFoundResponse))
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(false))
      when(mockBRConnector.createMetadataEntry(any()))
        .thenReturn(Future.successful(validBusinessRegistrationResponse))
      when(mockCTService.createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(Future.successful(validDraftCorporationTaxRegistration))

      await(service.checkUserAccess("321")) mustBe Left(Json.toJson(UserAccessLimitReachedResponse(true)))
    }

    "fail if the registration is missing" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId))))
      when(mockBRConnector.updateLastSignedIn(any(), any())(any()))
        .thenReturn(Future.successful(dateTime.toString))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any[Option[DateTime]]()))
        .thenReturn(Future.successful(None))

      intercept[MissingRegistration] {
        await(service.checkUserAccess(internalId))
      }
    }

    "be successful with no conf refs but with email info" in new Setup {
      val expectedEmail = Email("a@a.a", "GG", true, false, false)
      val draftCTReg = draftCorporationTaxRegistration(regId).copy(verifiedEmail = Some(expectedEmail))

      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationNotFoundResponse))
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(true))
      when(mockBRConnector.createMetadataEntry(any()))
        .thenReturn(Future.successful(businessRegistrationResponse(regId)))
      when(mockCTService.createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(Future.successful(draftCTReg))

      await(service.checkUserAccess(internalId)) mustBe
        Right(UserAccessSuccessResponse(regId, true, false, false, Some(expectedEmail)))
    }

    "return a UserAccessSuccessResponse with the created flag set to true" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationNotFoundResponse))
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(true))
      when(mockBRConnector.createMetadataEntry(any()))
        .thenReturn(Future.successful(businessRegistrationResponse(regId)))
      when(mockCTService.createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(Future.successful(draftCorporationTaxRegistration(regId)))

      await(service.checkUserAccess("321")) mustBe Right(UserAccessSuccessResponse("12345", true, false, false))
    }

    "return a UserAccessSuccessResponse with the confirmation refs flag set to true" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId))))
      when(mockBRConnector.updateLastSignedIn(any(), any())(any()))
        .thenReturn(Future.successful(dateTime.toString))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(eqTo(regId), any()))
        .thenReturn(Future.successful(Some(validHeldCTRegWithData(regId))))

      await(service.checkUserAccess(internalId)) mustBe Right(UserAccessSuccessResponse(regId, false, true, true))
    }

    "return an error when retrieving metadata returns a forbidden response" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationForbiddenResponse))

      val ex = intercept[Exception](await(service.checkUserAccess(internalId)))
      ex.getMessage mustBe "Something went wrong"
    }
  }
}
