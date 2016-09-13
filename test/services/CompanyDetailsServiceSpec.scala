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

import fixtures.CompanyDetailsFixture
import helpers.SCRSSpec
import repositories.Repositories

class CompanyDetailsServiceSpec extends SCRSSpec with CompanyDetailsFixture {

  trait Setup {
    val service = new CompanyDetailsService {
      override val corporationTaxRegistrationRepository = mockCTDataRepository
    }
  }

//  val registrationID = UUID.randomUUID().toString
  val registrationID = "12345"


  "CompanyDetailsService" should {
    "use the correct repository" in {
      CompanyDetailsService.corporationTaxRegistrationRepository shouldBe Repositories.cTRepository
    }
  }

  "retrieveCompanyDetails" should {
    "return a CompanyDetailsResponse when a company details record is found" in new Setup {
      CTDataRepositoryMocks.retrieveCompanyDetails(Some(validCompanyDetails))

      await(service.retrieveCompanyDetails(registrationID)) shouldBe Some(validCompanyDetailsResponse)
    }

    "return a None when the record to retrieve is not found in the repository" in new Setup {
      CTDataRepositoryMocks.retrieveCompanyDetails(None)

      await(service.retrieveCompanyDetails(registrationID)) shouldBe None
    }
  }

  "updateCompanyDetails" should {
    "return a CompanyDetailsResponse when a company detaisl record is updated" in new Setup {
      CTDataRepositoryMocks.updateCompanyDetails(Some(validCompanyDetails))

      await(service.updateCompanyDetails(registrationID, validCompanyDetails)) shouldBe Some(validCompanyDetailsResponse)
    }

    "return a None when the record to update is not found in the repository" in new Setup {
      CTDataRepositoryMocks.updateCompanyDetails(None)

      await(service.updateCompanyDetails(registrationID, validCompanyDetails)) shouldBe None
    }
  }
}
