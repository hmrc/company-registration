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

import models.{AccountingDetails, SubmissionDates}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AccountingDetailsServiceImpl @Inject()(val repositories: Repositories, servicesConfig: ServicesConfig) extends AccountingDetailsService {
  lazy val corporationTaxRegistrationMongoRepository: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
  lazy val doNotIntendToTradeConf: String = servicesConfig.getConfString("doNotIndendToTradeDefaultDate", throw new RuntimeException("Unable to retrieve doNotIndendToTradeDefaultDate from config"))
  override val doNotIndendToTradeDefaultDate = new String(Base64.getDecoder.decode(doNotIntendToTradeConf.getBytes()), "UTF-8")
}

sealed trait ActiveDate

case object DoNotIntendToTrade extends ActiveDate

case object ActiveOnIncorporation extends ActiveDate

case class ActiveInFuture(date: LocalDate) extends ActiveDate

trait AccountingDetailsService {

  val corporationTaxRegistrationMongoRepository: CorporationTaxRegistrationMongoRepository
  val doNotIndendToTradeDefaultDate: String

  def retrieveAccountingDetails(registrationID: String): Future[Option[AccountingDetails]] = {
    corporationTaxRegistrationMongoRepository.retrieveAccountingDetails(registrationID)
  }

  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetails): Future[Option[AccountingDetails]] = {
    corporationTaxRegistrationMongoRepository.updateAccountingDetails(registrationID, accountingDetails)
  }

  def calculateSubmissionDates(incorporationDate: LocalDate, activeDate: ActiveDate, accountingDate: Option[LocalDate]): SubmissionDates = {

    (activeDate, accountingDate) match {
      case (DoNotIntendToTrade, _) =>
        val defaultDate = LocalDate.parse(doNotIndendToTradeDefaultDate)
        SubmissionDates(defaultDate, defaultDate, defaultDate)

      case (ActiveOnIncorporation, Some(date)) =>
        SubmissionDates(incorporationDate, incorporationDate, date)

      case (ActiveOnIncorporation, None) =>
        SubmissionDates(incorporationDate, incorporationDate, endOfMonthPlusOneYear(incorporationDate))

      case (ActiveInFuture(givenDate), Some(date)) =>
        SubmissionDates(givenDate, givenDate, date)

      case (ActiveInFuture(givenDate), None) =>
        SubmissionDates(givenDate, givenDate, endOfMonthPlusOneYear(givenDate))
    }
  }

  private[services] def endOfMonthPlusOneYear(date: LocalDate): LocalDate = date withDayOfMonth 1 plusYears 1 plusMonths 1 minusDays 1
}
