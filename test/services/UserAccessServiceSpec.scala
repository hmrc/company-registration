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

package services

import connectors.{BusinessRegistrationConnector, _}
import fixtures.{BusinessRegistrationFixture, CorporationTaxRegistrationFixture}
import models.{Email, UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import org.mockito.Matchers
import org.mockito.Matchers.{any, anyString}
import org.scalatest.mock.MockitoSugar
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Alpha.M

class UserAccessServiceSpec
  extends UnitSpec with MockitoSugar with WithFakeApplication with BusinessRegistrationFixture with CorporationTaxRegistrationFixture
  with BeforeAndAfterEach {

  val mockBRConnector = mock[BusinessRegistrationConnector]
  val mockCTRepo = mock[CorporationTaxRegistrationMongoRepository]
  val mockCTService = mock[CorporationTaxRegistrationService]
  val mockThrottleService = mock[ThrottleService]

  implicit val hc = HeaderCarrier()

  trait mockService extends UserAccessService {
    val threshold = 10
    val brConnector = mockBRConnector
    val ctService = mockCTService
    val ctRepository = mockCTRepo
    val throttleService = mockThrottleService
  }

  trait Setup {
    val service = new mockService {}
  }

  // TODO - check SCRSSpec
  override def beforeEach() {
    reset(mockBRConnector)
    reset(mockCTRepo)
    reset(mockCTService)
    reset(mockThrottleService)
  }


  "UserAccessService" should {
    "use the correct business registration connector" in {
      UserAccessService.brConnector shouldBe BusinessRegistrationConnector
    }
    "use the correct company registration repository" in {
      UserAccessService.ctRepository shouldBe Repositories.cTRepository
    }
    "use the correct company registration service" in {
      UserAccessService.ctService shouldBe CorporationTaxRegistrationService
    }
    "use the correct throttle service" in {
      UserAccessService.throttleService shouldBe ThrottleService
    }
  }

  "checkUserAccess (isolated)" should {

    trait SetupNoProcess {
      val service = new mockService {
        override def createResponse(regId: String, created: Boolean): Future[UserAccessSuccessResponse] = {
          Future.successful(UserAccessSuccessResponse(regId, created, false))
        }
      }
    }

    "return a 200 with false" in new SetupNoProcess {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse))
      await(service.checkUserAccess("123")) shouldBe Right(UserAccessSuccessResponse("12345", false, false))
    }

    "return a 429 with limit reached" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationNotFoundResponse)
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(false))
      when(mockBRConnector.createMetadataEntry(any()))
        .thenReturn(validBusinessRegistrationResponse)
      when(mockCTService
        .createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(validDraftCorporationTaxRegistration)


      await(service.checkUserAccess("321")) shouldBe Left(Json.toJson(UserAccessLimitReachedResponse(true)))
    }

    "return a 200 with a regId and created set to true" in new SetupNoProcess {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationNotFoundResponse)
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(true))
      when(mockBRConnector.createMetadataEntry(any()))
        .thenReturn(validBusinessRegistrationResponse)
      when(mockCTService
        .createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(validDraftCorporationTaxRegistration)


      await(service.checkUserAccess("321")) shouldBe Right(UserAccessSuccessResponse("12345", true, false))
    }

    "return an error" in new Setup {
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationForbiddenResponse)
      val ex = intercept[Exception] {
        await(service.checkUserAccess("555"))
      }
      ex.getMessage shouldBe "Something went wrong"
    }
  }

  "checkUserAccess (with CR doc info)" should {

    "be successful return a 200 with false" in new Setup {
      val regId = "12345"
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId)))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(Matchers.eq(regId)))
        .thenReturn(Some(draftCorporationTaxRegistration(regId)))

      await(service.checkUserAccess("123")) shouldBe Right(UserAccessSuccessResponse("12345", false, false))
    }

    "fail if the registration is missing" in new Setup {
      val regId = "12345"
      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId)))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(Matchers.eq(regId)))
        .thenReturn(None)

      intercept[MissingRegistration] {
        await(service.checkUserAccess("123"))
      }
    }

    "be successful with no conf refs but with email info" in new Setup {
      val regId = "12345"
      when(mockBRConnector.retrieveMetadata(any(), any())).thenReturn(BusinessRegistrationNotFoundResponse)
      when(mockThrottleService.checkUserAccess).thenReturn(Future(true))
      when(mockBRConnector.createMetadataEntry(any())).thenReturn(validBusinessRegistrationResponse)
      when(mockCTService
        .createCorporationTaxRegistrationRecord(anyString(), anyString(), anyString()))
        .thenReturn(validDraftCorporationTaxRegistration)

      val expectedEmail = Email("a@a.a", "GG",true, false, false)
      val draft = draftCorporationTaxRegistration(regId).copy(verifiedEmail = Some(expectedEmail))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(Matchers.eq(regId)))
        .thenReturn(Some(draft))

      await(service.checkUserAccess("321")) shouldBe
        Right(UserAccessSuccessResponse("12345", true, false, Some(expectedEmail)))
    }

    "be successful with some conf references" in new Setup {
      val regId = "12345"

      when(mockBRConnector.retrieveMetadata(any(), any()))
        .thenReturn(BusinessRegistrationSuccessResponse(businessRegistrationResponse(regId)))
      when(mockCTService.retrieveCorporationTaxRegistrationRecord(Matchers.eq(regId)))
        .thenReturn(Some(validHeldCTRegWithData(regId)))

      await(service.checkUserAccess("321")) shouldBe Right(UserAccessSuccessResponse("12345", false, true))
    }

  }
}
