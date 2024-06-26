/*
 * Copyright 2024 HM Revenue & Customs
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

import helpers.BaseSpec
import models.AccountPrepDetails
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class PrepareAccountServiceSpec extends BaseSpec {

  class Setup {
    val prepareAccountService: PrepareAccountService = new PrepareAccountService {
      override val repository: CorporationTaxRegistrationMongoRepository = mockCTDataRepository
      implicit val ec: ExecutionContext = global
    }
  }

  "updateEndDate" must {

    val rID = "testRegID"

    val prepareAccountModel = AccountPrepDetails(AccountPrepDetails.COMPANY_DEFINED, Some(LocalDate.parse("1980-12-12")))

    "return a PrepareAccountModel on successful update" in new Setup {
      when(mockCTDataRepository.updateCompanyEndDate(ArgumentMatchers.eq(rID), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(prepareAccountModel)))

      val result: Future[Option[AccountPrepDetails]] = prepareAccountService.updateEndDate(rID)
      await(result) mustBe Some(prepareAccountModel)
    }

    "return None when a None is returned from the repository" in new Setup {
      when(mockCTDataRepository.updateCompanyEndDate(ArgumentMatchers.eq(rID), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val result: Future[Option[AccountPrepDetails]] = prepareAccountService.updateEndDate(rID)
      await(result) mustBe None
    }
  }
}
