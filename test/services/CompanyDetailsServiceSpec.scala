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

import fixtures.CompanyDetailsFixture
import mocks.SCRSMocks
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class CompanyDetailsServiceSpec extends UnitSpec with MockitoSugar with SCRSMocks with CompanyDetailsFixture {

  trait Setup {
    val service = new CompanyDetailsService {
      override val corporationTaxRegistrationRepository = mockCTDataRepository
    }
  }

  val registrationID = "12345"
  

  "retrieveCompanyDetails" should {
    "return the CompanyDetails when a company details record is found" in new Setup {
      CTDataRepositoryMocks.retrieveCompanyDetails(Some(validCompanyDetails))

      await(service.retrieveCompanyDetails(registrationID)) shouldBe Some(validCompanyDetails)
    }

    "return a None when the record to retrieve is not found in the repository" in new Setup {
      CTDataRepositoryMocks.retrieveCompanyDetails(None)

      await(service.retrieveCompanyDetails(registrationID)) shouldBe None
    }
  }

  "updateCompanyDetails" should {
    "return a CompanyDetailsResponse when a company detaisl record is updated" in new Setup {
      CTDataRepositoryMocks.updateCompanyDetails(Some(validCompanyDetails))

      await(service.updateCompanyDetails(registrationID, validCompanyDetails)) shouldBe Some(validCompanyDetails)
    }

    "return a None when the record to update is not found in the repository" in new Setup {
      CTDataRepositoryMocks.updateCompanyDetails(None)

      await(service.updateCompanyDetails(registrationID, validCompanyDetails)) shouldBe None
    }
  }
}
