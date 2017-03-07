/*
 * Copyright 2017 HM Revenue & Customs
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

import javax.inject.{Singleton,Inject}
import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.play.graphite.MicroserviceMetrics

@Singleton
class MetricsServiceImp @Inject() (metricsInstance: Metrics) extends MetricsService {
  val metrics = metricsInstance

  override val ctutrConfirmationCounter: Counter = metrics.defaultRegistry.counter("ctutr-confirmation-counter")

  override val retrieveAccountingDetailsCRTimer: Timer = metrics.defaultRegistry.timer("retrieve-accounting-details-from-CR-timer")
  override val updateAccountingDetailsCRTimer: Timer = metrics.defaultRegistry.timer("update-accounting-details-from-CR-timer")

  override val retrieveCompanyDetailsCRTimer: Timer = metrics.defaultRegistry.timer("retrieve-company-details-from-CR-timer")
  override val updateCompanyDetailsCRTimer: Timer = metrics.defaultRegistry.timer("update-company-details-from-CR-timer")

  override val retrieveContactDetailsCRTimer: Timer = metrics.defaultRegistry.timer("retrieve-contact-details-from-CR-timer")
  override val updateContactDetailsCRTimer: Timer = metrics.defaultRegistry.timer("update-contact-details-from-CR-timer")

  override val createCorporationTaxRegistrationCRTimer: Timer = metrics.defaultRegistry.timer("create-corporation-tax-registration-CR-timer")
  override val retrieveCorporationTaxRegistrationCRTimer: Timer = metrics.defaultRegistry.timer("retrieve-corporation-tax-registration-CR-timer")
  override val retrieveFullCorporationTaxRegistrationCRTimer: Timer = metrics.defaultRegistry.timer("retrieve-full-corporation-tax-registration-CR-timer")
  override val updateReferencesCRTimer: Timer = metrics.defaultRegistry.timer("update-references-CR-timer")
  override val retrieveConfirmationReferenceCRTimer: Timer = metrics.defaultRegistry.timer("retrieve-confirmation-reference-CR-timer")
  override val acknowledgementConfirmationCRTimer: Timer = metrics.defaultRegistry.timer("acknowledgement-confirmation-CR-timer")

  override val updateCompanyEndDateCRTimer: Timer = metrics.defaultRegistry.timer("update-company-end-date-CR-timer")

  override val retrieveTradingDetailsCRTimer: Timer = metrics.defaultRegistry.timer("retrieve-trading-details-CR-timer")
  override val updateTradingDetailsCRTimer: Timer = metrics.defaultRegistry.timer("update-trading-details-CR-timer")

  override val userAccessCRTimer: Timer = metrics.defaultRegistry.timer("user-access-CR-timer")


}

trait MetricsService {

  val ctutrConfirmationCounter : Counter

  val retrieveAccountingDetailsCRTimer : Timer
  val updateAccountingDetailsCRTimer : Timer

  val retrieveCompanyDetailsCRTimer : Timer
  val updateCompanyDetailsCRTimer : Timer

  val retrieveContactDetailsCRTimer : Timer
  val updateContactDetailsCRTimer : Timer

  val createCorporationTaxRegistrationCRTimer : Timer
  val retrieveCorporationTaxRegistrationCRTimer : Timer
  val retrieveFullCorporationTaxRegistrationCRTimer : Timer
  val updateReferencesCRTimer : Timer
  val retrieveConfirmationReferenceCRTimer : Timer
  val acknowledgementConfirmationCRTimer : Timer

  val updateCompanyEndDateCRTimer : Timer

  val retrieveTradingDetailsCRTimer : Timer
  val updateTradingDetailsCRTimer : Timer

  val userAccessCRTimer : Timer


}
