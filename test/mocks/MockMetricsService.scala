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

package mocks

import com.codahale.metrics.{Counter, Timer}
import org.scalatest.mock.MockitoSugar
import services._

object MockMetricsService extends MetricsService with MockitoSugar {
  val fakeCounter = mock[Counter]
  lazy val mockContext = mock[Timer.Context]
  val mockTimer = new Timer()


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

}
