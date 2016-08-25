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

//import models.{ContactDetailsResponse, ContactDetails}
import models.{AccountingDetails, AccountingDetailsResponse}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AccountingDetailsService extends AccountingDetailsService {
  override val corporationTaxRegistrationRepository = Repositories.cTRepository
}

trait AccountingDetailsService {

  val corporationTaxRegistrationRepository : CorporationTaxRegistrationMongoRepository

  def retrieveAccountingDetails(registrationID: String): Future[Option[AccountingDetailsResponse]] = {
    corporationTaxRegistrationRepository.retrieveAccountingDetails(registrationID).map{
      case Some(accountingDetails) => Some(accountingDetails.toAccountingDetailsResponse(registrationID))
      case _ => None
    }
  }

  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetails): Future[Option[AccountingDetailsResponse]] = {
    corporationTaxRegistrationRepository.updateAccountingDetails(registrationID, accountingDetails).map{
      case Some(details) => Some(details.toAccountingDetailsResponse(registrationID))
      case _ => None
    }
  }
}
