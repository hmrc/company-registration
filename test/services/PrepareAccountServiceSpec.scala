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

import helpers.SCRSSpec
import mocks.SCRSMocks
import models.AccountPrepDetails
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class PrepareAccountServiceSpec extends UnitSpec with MockitoSugar with SCRSMocks {

  class Setup {
    val prepareAccountService = new PrepareAccountService {
      override val repository = mockCTDataRepository
    }
  }

  "updateEndDate" should {

    val rID = "testRegID"

    val prepareAccountModel = AccountPrepDetails(AccountPrepDetails.COMPANY_DEFINED, Some(DateTime.parse("1980-12-12")))

    "return a PrepareAccountModel on successful update" in new Setup {
      when(mockCTDataRepository.updateCompanyEndDate(ArgumentMatchers.eq(rID), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(prepareAccountModel)))

      val result = prepareAccountService.updateEndDate(rID)
      await(result) shouldBe Some(prepareAccountModel)
    }

    "return None when a None is returned from the repository" in new Setup {
      when(mockCTDataRepository.updateCompanyEndDate(ArgumentMatchers.eq(rID), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val result = prepareAccountService.updateEndDate(rID)
      await(result) shouldBe None
    }
  }
}
