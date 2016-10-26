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

import fixtures.AccountingDetailsFixture
import helpers.SCRSSpec
import org.joda.time.DateTime

class AccountingDetailsServiceSpec extends SCRSSpec with AccountingDetailsFixture {

  trait Setup {
    val service = new AccountingDetailsService {
      override val corporationTaxRegistrationRepository = mockCTDataRepository
    }
  }

  val registrationID = "12345"

  "retrieveAccountingDetails" should {
    "return a AccountingDetailsResponse when a company details record is found" in new Setup {
      CTDataRepositoryMocks.retrieveAccountingDetails(Some(validAccountingDetails))

      await(service.retrieveAccountingDetails(registrationID)) shouldBe Some(validAccountingDetails)
    }

    "return a None when the record to retrieve is not found in the repository" in new Setup {
      CTDataRepositoryMocks.retrieveAccountingDetails(None)

      await(service.retrieveAccountingDetails(registrationID)) shouldBe None
    }
  }

  "updateAccountingDetails" should {
    "return an AccountingDetailsResponse when a accounting details record is updated" in new Setup {
      CTDataRepositoryMocks.updateAccountingDetails(Some(validAccountingDetails))

      await(service.updateAccountingDetails(registrationID, validAccountingDetails)) shouldBe Some(validAccountingDetails)
    }

    "return a None when the record to update is not found in the repository" in new Setup {
      CTDataRepositoryMocks.updateAccountingDetails(None)

      await(service.updateAccountingDetails(registrationID, validAccountingDetails)) shouldBe None
    }
  }


  "Calculate dates for CT submission" when {
    import AccountingDetailsService.calculateSubmissionDates
    def date(s: String): DateTime = DateTime.parse(s)

    "checking the dates" should {
      "calculate the correct end of month next year" in {
        import AccountingDetailsService.endOfMonthPlusOneYear

        endOfMonthPlusOneYear(date("2017-01-01")) shouldBe date("2018-01-31")
        endOfMonthPlusOneYear(date("2016-12-31")) shouldBe date("2017-12-31")
        endOfMonthPlusOneYear(date("2017-02-01")) shouldBe date("2018-02-28")
        endOfMonthPlusOneYear(date("2019-02-01")) shouldBe date("2020-02-29")
        endOfMonthPlusOneYear(date("2019-02-28")) shouldBe date("2020-02-29")
        endOfMonthPlusOneYear(date("2019-03-01")) shouldBe date("2020-03-31")
        endOfMonthPlusOneYear(date("2016-02-29")) shouldBe date("2017-02-28")
        endOfMonthPlusOneYear(date("2016-03-01")) shouldBe date("2017-03-31")
      }
    }

    "called with 'Do not intend to start trading' selected" should {

      "return a 5 years from the Incorporation date for 'companyActiveDate' and 'startDateOfFirstAccountingPeriod' dates and plus 1 year (end of month for Intended date)" in {
        val dateOfIncorp: DateTime = date("2020-1-1")
        val targetActiveDate = date("2025-1-1")
        val targetIntendedDate = date("2026-1-31")

        val result = calculateSubmissionDates(dateOfIncorp, DoNotIntendToTrade, None)

        result.companyActiveDate                 shouldBe targetActiveDate
        result.startDateOfFirstAccountingPeriod  shouldBe targetActiveDate
        result.intendedAccountsPreparationDate   shouldBe targetIntendedDate
      }

      "return correct intendedAccountsPreperationDate when called on a leap year edge case" in {
        val dateOfIncorp: DateTime = date("2014-2-28")
        val targetResult = date("2019-2-28")
        val successfulEdgeCase = date("2020-2-29")

        val result = calculateSubmissionDates(dateOfIncorp, DoNotIntendToTrade, None)

        result.companyActiveDate                 shouldBe targetResult
        result.startDateOfFirstAccountingPeriod  shouldBe targetResult
        result.intendedAccountsPreparationDate   shouldBe successfulEdgeCase
      }

      "return correct intendedAccountsPreperationDate when called on a non-leap year edge case" in {
        val dateOfIncorp: DateTime = date("2016-2-28")
        val successfulEdgeCase = date("2022-2-28")

        val result = calculateSubmissionDates(dateOfIncorp, DoNotIntendToTrade, None)

        result.intendedAccountsPreparationDate   shouldBe successfulEdgeCase
      }

    }

    "called with 'Date of incorporation' selected" should {

      "return the date of Incorporation and Accounting preparation date as provided by user" in {
        val dateOfIncorp = date("2020-1-1")
        val providedDate = date("2020-1-1")

        val result = calculateSubmissionDates(dateOfIncorp, ActiveOnIncorporation, Some(providedDate))

        result.companyActiveDate                  shouldBe dateOfIncorp
        result.startDateOfFirstAccountingPeriod   shouldBe dateOfIncorp
        result.intendedAccountsPreparationDate    shouldBe providedDate
      }

      "return the date of Incorporation and Accounting preparation date calculated as the end of the month 1 year from the Incorporation date" in {
        val dateOfIncorp = date("2020-1-1")
        val targetPrepDate = date("2021-1-31")

        val result = calculateSubmissionDates(dateOfIncorp, ActiveOnIncorporation, None)

        result.companyActiveDate                  shouldBe dateOfIncorp
        result.startDateOfFirstAccountingPeriod   shouldBe dateOfIncorp
        result.intendedAccountsPreparationDate    shouldBe targetPrepDate
      }

    }

    "called with 'Future start date' provided" should {

      "return the date of Incorporation and Accounting preparation date as provided by user" in {
        val futureDate = date("2020-1-1")
        val providedDate = date("2021-1-1")
        val dateOfIncorp: DateTime = date("2020-1-1")

        val result = calculateSubmissionDates(dateOfIncorp, ActiveInFuture(futureDate), Some(providedDate))

        result.companyActiveDate                  shouldBe futureDate
        result.startDateOfFirstAccountingPeriod   shouldBe futureDate
        result.intendedAccountsPreparationDate    shouldBe providedDate
      }

      "return the date of Incorporation and Accounting preparation date calculated as the end of the month 1 year from the Future date" in {
        val futureDate = date("2020-1-1")
        val dateOfIncorp: DateTime = date("2021-6-8")
        val targetPrepDate = date("2021-1-31")

        val result = calculateSubmissionDates(dateOfIncorp, ActiveInFuture(futureDate), None)

        result.companyActiveDate                  shouldBe futureDate
        result.startDateOfFirstAccountingPeriod   shouldBe futureDate
        result.intendedAccountsPreparationDate    shouldBe targetPrepDate
      }

    }
  }
}
