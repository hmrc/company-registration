/*
 * Copyright 2023 HM Revenue & Customs
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

import fixtures.CorporationTaxRegistrationFixture
import helpers.BaseSpec
import models.TradingDetails
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TradingDetailsServiceSpec extends BaseSpec with CorporationTaxRegistrationFixture {

  class Setup {
    val mockRepositories = mock[Repositories]

    object TestService extends TradingDetailsService(mockRepositories,stubControllerComponents()) {
      override lazy val corporationTaxRegistrationMongoRepository: CorporationTaxRegistrationMongoRepository = mockCTDataRepository
    }

  }

  "retrieveTradingDetails" must {
    "fetch trading details if a record exists against the given registration ID" in new Setup {
      CTDataRepositoryMocks.retrieveCorporationTaxRegistration(Some(validDraftCorporationTaxRegistration))

      when(mockCTDataRepository.retrieveTradingDetails(ArgumentMatchers.anyString())).thenReturn(Future.successful(Some(TradingDetails("true"))))

      val result = TestService.retrieveTradingDetails("testRegID")
      await(result) mustBe Some(TradingDetails("true"))
    }

    "return an 'empty' trading details model if no record exists against the given registration ID" in new Setup {
      CTDataRepositoryMocks.retrieveCorporationTaxRegistration(Some(validDraftCorporationTaxRegistration))

      when(mockCTDataRepository.retrieveTradingDetails(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(None))

      val result = TestService.retrieveTradingDetails("testRegID")
      await(result) mustBe None
    }
  }

  "updateTradingDetails" must {
    "update the trading details record against a given regID" in new Setup {
      CTDataRepositoryMocks.retrieveCorporationTaxRegistration(Some(validDraftCorporationTaxRegistration))

      when(mockCTDataRepository.updateTradingDetails(ArgumentMatchers.anyString(), ArgumentMatchers.eq[TradingDetails](TradingDetails("true"))))
        .thenReturn(Future.successful(Some(TradingDetails("true"))))

      val result = TestService.updateTradingDetails("testRegID", TradingDetails("true"))
      await(result) mustBe Some(TradingDetails("true"))
    }
  }
}
