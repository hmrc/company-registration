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

import models.{CompanyDetails, CompanyDetailsResponse, HandoffCHData}
import play.api.libs.json.{JsObject, JsValue}
import repositories.{CorporationTaxRegistrationMongoRepository, HandoffRepository, Repositories}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object HandoffCHDataService extends HandoffCHDataService {
  override val handoffRepository = Repositories.handoffRepository
}

trait HandoffCHDataService {

  val handoffRepository : HandoffRepository

  def retrieveHandoffCHData(registrationID: String): Future[Option[JsValue]] = {
    handoffRepository.fetchHandoffCHData(registrationID).map{
      case Some(chData) => Some(chData.ch)
      case _ => None
    }
  }

  def storeHandoffCHData(registrationID: String, chData: JsValue) : Future[Boolean] = {
    handoffRepository.storeHandoffCHData( HandoffCHData(registrationID, ch = chData) ) map {
      case Some(data) => true
      case None => false
    }
  }
}
