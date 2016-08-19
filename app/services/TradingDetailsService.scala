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

import models.TradingDetails
import uk.gov.hmrc.play.microservice.controller.BaseController
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TradingDetailsService extends TradingDetailsService {
  val corporationTaxRegistrationRepository = Repositories.cTRepository
}

trait TradingDetailsService extends BaseController {

  val corporationTaxRegistrationRepository : CorporationTaxRegistrationMongoRepository

  def retrieveTradingDetails(registrationID : String) : Future[Option[TradingDetails]] = {
    corporationTaxRegistrationRepository.retrieveTradingDetails(registrationID).map {
      case tradingDetails => tradingDetails
      case None => None
    }
  }

  def updateTradingDetails(registrationID : String, tradingDetails: TradingDetails) : Future[Option[TradingDetails]] = {
    corporationTaxRegistrationRepository.updateTradingDetails(registrationID, tradingDetails).map {
      tradingDetails => tradingDetails
    }
  }
}
