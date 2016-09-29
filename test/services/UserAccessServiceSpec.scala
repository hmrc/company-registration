/*
 * Copyright 2016 HM Revenue & Customs
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
import models.{UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class UserAccessServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with BusinessRegistrationFixture with CorporationTaxRegistrationFixture {

  val mockBusinessRegistrationConnector = mock[BusinessRegistrationConnector]
  val mockCorporationTaxRegistrationMongoRepository = mock[CorporationTaxRegistrationMongoRepository]
  val mockCorporationTaxRegistrationService = mock[CorporationTaxRegistrationService]
  val mockThrottleService= mock[ThrottleService]

  implicit val hc = HeaderCarrier()

  trait Setup {
    val service = new UserAccessService {
      val threshold = 10
      val bRConnector = mockBusinessRegistrationConnector
      val cTService = mockCorporationTaxRegistrationService
      val cTRepository = mockCorporationTaxRegistrationMongoRepository
      val throttleService = mockThrottleService
    }
  }

  "UserAccessService" should {
    "use the correct business registration connector" in {
      UserAccessService.bRConnector shouldBe BusinessRegistrationConnector
    }
    "use the correct company registration repository" in {
      UserAccessService.cTRepository shouldBe Repositories.cTRepository
    }
    "use the correct company registration service" in {
      UserAccessService.cTService shouldBe CorporationTaxRegistrationService
    }
    "use the correct throttle service" in {
      UserAccessService.throttleService shouldBe ThrottleService
    }
  }

  "checkUserAccess" should {

    "return a 200 with false" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse))
      await(service.checkUserAccess("123")) shouldBe Right(Json.toJson(UserAccessSuccessResponse("12345",created = false)))
    }

    "return a 429 with limit reached" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(BusinessRegistrationNotFoundResponse)
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(false))
      when(mockBusinessRegistrationConnector.createMetadataEntry(Matchers.any()))
        .thenReturn(validBusinessRegistrationResponse)
      when(mockCorporationTaxRegistrationService
        .createCorporationTaxRegistrationRecord(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(validCorporationTaxRegistrationResponse)


      await(service.checkUserAccess("321")) shouldBe Left(Json.toJson(UserAccessLimitReachedResponse(true)))
    }
    "return a 200 with a regId and created set to true" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(BusinessRegistrationNotFoundResponse)
      when(mockThrottleService.checkUserAccess)
        .thenReturn(Future(true))
      when(mockBusinessRegistrationConnector.createMetadataEntry(Matchers.any()))
        .thenReturn(validBusinessRegistrationResponse)
      when(mockCorporationTaxRegistrationService
        .createCorporationTaxRegistrationRecord(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(validCorporationTaxRegistrationResponse)


      await(service.checkUserAccess("321")) shouldBe Right(Json.toJson(UserAccessSuccessResponse("12345",created = true)))
    }
    "return an error" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(BusinessRegistrationForbiddenResponse)
      val ex = intercept[Exception]{
        await(service.checkUserAccess("555"))
      }
      ex.getMessage shouldBe "Something went wrong"
    }
  }

}
