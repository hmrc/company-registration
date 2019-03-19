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

import javax.inject.Inject

import models.TradingDetails
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TradingDetailsServiceImpl @Inject()(val repositories: Repositories) extends TradingDetailsService {
  lazy val corporationTaxRegistrationRepository = repositories.cTRepository
}

trait TradingDetailsService extends BaseController {

  val corporationTaxRegistrationRepository : CorporationTaxRegistrationMongoRepository

  def retrieveTradingDetails(registrationID : String) : Future[Option[TradingDetails]] = {
    corporationTaxRegistrationRepository.retrieveTradingDetails(registrationID).map {
      tradingDetails => tradingDetails
    }
  }

  def updateTradingDetails(registrationID : String, tradingDetails: TradingDetails) : Future[Option[TradingDetails]] = {
    corporationTaxRegistrationRepository.updateTradingDetails(registrationID, tradingDetails).map {
      tradingDetails => tradingDetails
    }
  }
}
