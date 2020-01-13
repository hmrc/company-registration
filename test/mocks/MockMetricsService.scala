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

package mocks

import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.Metrics
import org.scalatest.mockito.MockitoSugar
import repositories.{CorporationTaxRegistrationMongoRepository, CorporationTaxRegistrationRepository}
import services._
import uk.gov.hmrc.lock.LockKeeper

object MockMetricsService extends MetricsService with MockitoSugar {
  override val metrics = mock[Metrics]
  val fakeCounter = mock[Counter]
  lazy val mockContext = mock[Timer.Context]
  val mockTimer = new Timer()

  val ctRepository = mock[CorporationTaxRegistrationMongoRepository]

  override val ctutrConfirmationCounter: Counter = fakeCounter

  val retrieveAccountingDetailsCRTimer : Timer = mockTimer
  val updateAccountingDetailsCRTimer : Timer = mockTimer

  val retrieveCompanyDetailsCRTimer : Timer = mockTimer
  val updateCompanyDetailsCRTimer : Timer = mockTimer

  val retrieveContactDetailsCRTimer : Timer = mockTimer
  val updateContactDetailsCRTimer : Timer = mockTimer

  val createCorporationTaxRegistrationCRTimer : Timer = mockTimer
  val retrieveCorporationTaxRegistrationCRTimer : Timer = mockTimer
  val retrieveFullCorporationTaxRegistrationCRTimer : Timer = mockTimer
  val updateReferencesCRTimer : Timer = mockTimer
  val retrieveConfirmationReferenceCRTimer : Timer = mockTimer
  val acknowledgementConfirmationCRTimer : Timer = mockTimer

  val updateCompanyEndDateCRTimer : Timer = mockTimer

  val retrieveTradingDetailsCRTimer : Timer = mockTimer
  val updateTradingDetailsCRTimer : Timer = mockTimer

  val userAccessCRTimer : Timer = mockTimer

  val desSubmissionCRTimer : Timer = mockTimer
  override val lockKeeper: LockKeeper = mock[LockKeeper]
}
