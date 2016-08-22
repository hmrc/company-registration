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

import java.util.UUID

import fixtures.{CorporationTaxRegistrationFixture, MongoFixture}
import helpers.SCRSSpec
import models.TradingDetails
import repositories.Repositories
import org.mockito.Mockito._
import org.mockito.Matchers

import scala.concurrent.Future

class TradingDetailsServiceSpec extends SCRSSpec with CorporationTaxRegistrationFixture with MongoFixture {

  implicit val mongo = mongoDB

  class Setup {
    object TestService extends TradingDetailsService {
      val corporationTaxRegistrationRepository = mockCTDataRepository
    }
  }

  "TradingDetailsService" should {
    "use the correct CTRegRepo" in {
      TradingDetailsService.corporationTaxRegistrationRepository shouldBe Repositories.cTRepository
    }
  }

  "retrieveTradingDetails" should {
    "fetch trading details if a record exists against the given registration ID" in new Setup {
      CTDataRepositoryMocks.retrieveCorporationTaxRegistration(Some(validCorporationTaxRegistration))

      when(mockCTDataRepository.retrieveTradingDetails(Matchers.anyString())).thenReturn(Future.successful(Some(TradingDetails(true))))

      val result = TestService.retrieveTradingDetails("testRegID")
      await(result) shouldBe Some(TradingDetails(true))
    }

    "return an 'empty' trading details model if no record exists against the given registration ID" in new Setup {
      CTDataRepositoryMocks.retrieveCorporationTaxRegistration(Some(validCorporationTaxRegistration))

      when(mockCTDataRepository.retrieveTradingDetails(Matchers.anyString()))
        .thenReturn(Future.successful(None))

      val result = TestService.retrieveTradingDetails("testRegID")
      await(result) shouldBe None
    }
  }

  "updateTradingDetails" should {
    "update the trading details record against a given regID" in new Setup {
      CTDataRepositoryMocks.retrieveCorporationTaxRegistration(Some(validCorporationTaxRegistration))

      when(mockCTDataRepository.updateTradingDetails(Matchers.anyString(), Matchers.eq[TradingDetails](TradingDetails(true))))
        .thenReturn(Future.successful(Some(TradingDetails(true))))

      val result = TestService.updateTradingDetails("testRegID", TradingDetails(true))
      await(result) shouldBe Some(TradingDetails(true))
    }
  }
}
