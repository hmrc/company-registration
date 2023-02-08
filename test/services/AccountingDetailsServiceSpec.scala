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

import fixtures.AccountingDetailsFixture
import mocks.SCRSMocks
import models.SubmissionDates
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._

import java.time.LocalDate

class AccountingDetailsServiceSpec extends PlaySpec with MockitoSugar with SCRSMocks with AccountingDetailsFixture {

  trait Setup {
    val service = new AccountingDetailsService {
      override val corporationTaxRegistrationMongoRepository = mockCTDataRepository
      override val doNotIndendToTradeDefaultDate = "1900-01-01"
    }
  }

  val registrationID = "12345"

  "retrieveAccountingDetails" must {
    "return a AccountingDetailsResponse when a company details record is found" in new Setup {
      CTDataRepositoryMocks.retrieveAccountingDetails(Some(validAccountingDetails))

      await(service.retrieveAccountingDetails(registrationID)) mustBe Some(validAccountingDetails)
    }

    "return a None when the record to retrieve is not found in the repository" in new Setup {
      CTDataRepositoryMocks.retrieveAccountingDetails(None)

      await(service.retrieveAccountingDetails(registrationID)) mustBe None
    }
  }

  "updateAccountingDetails" must {
    "return an AccountingDetailsResponse when a accounting details record is updated" in new Setup {
      CTDataRepositoryMocks.updateAccountingDetails(Some(validAccountingDetails))

      await(service.updateAccountingDetails(registrationID, validAccountingDetails)) mustBe Some(validAccountingDetails)
    }

    "return a None when the record to update is not found in the repository" in new Setup {
      CTDataRepositoryMocks.updateAccountingDetails(None)

      await(service.updateAccountingDetails(registrationID, validAccountingDetails)) mustBe None
    }
  }


  "Calculate dates for CT submission" when {
    def date(s: String): LocalDate = LocalDate.parse(s)

    "checking the dates" must {
      "calculate the correct end of month next year" in new Setup {

        service.endOfMonthPlusOneYear(date("2017-01-01")) mustBe date("2018-01-31")
        service.endOfMonthPlusOneYear(date("2016-12-31")) mustBe date("2017-12-31")
        service.endOfMonthPlusOneYear(date("2017-02-01")) mustBe date("2018-02-28")
        service.endOfMonthPlusOneYear(date("2019-02-01")) mustBe date("2020-02-29")
        service.endOfMonthPlusOneYear(date("2019-02-28")) mustBe date("2020-02-29")
        service.endOfMonthPlusOneYear(date("2019-03-01")) mustBe date("2020-03-31")
        service.endOfMonthPlusOneYear(date("2016-02-29")) mustBe date("2017-02-28")
        service.endOfMonthPlusOneYear(date("2016-03-01")) mustBe date("2017-03-31")
      }
    }

    "called with 'Do not intend to start trading' selected" must {

      "return a 5 years from the Incorporation date for 'companyActiveDate' and 'startDateOfFirstAccountingPeriod' " +
        "dates and plus 1 year (end of month for Intended date)" in new Setup {
        val targetDate: LocalDate = date(service.doNotIndendToTradeDefaultDate)
        val result: SubmissionDates = service.calculateSubmissionDates(targetDate, DoNotIntendToTrade, None)

        result.companyActiveDate mustBe targetDate
        result.startDateOfFirstAccountingPeriod mustBe targetDate
        result.intendedAccountsPreparationDate mustBe targetDate
      }
    }

    "called with 'Date of incorporation' selected" must {

      "return the date of Incorporation and Accounting preparation date as provided by user" in new Setup {
        val dateOfIncorp = date("2020-01-01")
        val providedDate = date("2020-01-01")

        val result: SubmissionDates = service.calculateSubmissionDates(dateOfIncorp, ActiveOnIncorporation, Some(providedDate))

        result.companyActiveDate mustBe dateOfIncorp
        result.startDateOfFirstAccountingPeriod mustBe dateOfIncorp
        result.intendedAccountsPreparationDate mustBe providedDate
      }

      "return the date of Incorporation and Accounting preparation date calculated as the end of the month 1 year from the Incorporation date" in new Setup {
        val dateOfIncorp = date("2020-01-01")
        val targetPrepDate = date("2021-01-31")

        val result: SubmissionDates = service.calculateSubmissionDates(dateOfIncorp, ActiveOnIncorporation, None)

        result.companyActiveDate mustBe dateOfIncorp
        result.startDateOfFirstAccountingPeriod mustBe dateOfIncorp
        result.intendedAccountsPreparationDate mustBe targetPrepDate
      }

    }

    "called with 'Future start date' provided" must {

      "return the date of Incorporation and Accounting preparation date as provided by user" in new Setup {
        val futureDate = date("2020-01-01")
        val providedDate = date("2021-01-01")
        val dateOfIncorp = date("2020-01-01")

        val result: SubmissionDates = service.calculateSubmissionDates(dateOfIncorp, ActiveInFuture(futureDate), Some(providedDate))

        result.companyActiveDate mustBe futureDate
        result.startDateOfFirstAccountingPeriod mustBe futureDate
        result.intendedAccountsPreparationDate mustBe providedDate
      }

      "return the date of Incorporation and Accounting preparation date calculated as the end of the month 1 year from the Future date" in new Setup {
        val futureDate = date("2020-01-01")
        val dateOfIncorp: LocalDate = date("2019-06-08")
        val targetPrepDate = date("2021-01-31")

        val result: SubmissionDates = service.calculateSubmissionDates(dateOfIncorp, ActiveInFuture(futureDate), None)

        result.companyActiveDate mustBe futureDate
        result.startDateOfFirstAccountingPeriod mustBe futureDate
        result.intendedAccountsPreparationDate mustBe targetPrepDate
      }

    }
  }
}
