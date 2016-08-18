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

import models.{ContactDetailsResponse, ContactDetails, CompanyDetails, CompanyDetailsResponse}
import repositories.{Repositories, CorporationTaxRegistrationMongoRepository}

import scala.concurrent.Future

object ContactDetailsService extends ContactDetailsService {
  override val corporationTaxRegistrationRepository = Repositories.cTRepository
}

trait ContactDetailsService {

  val corporationTaxRegistrationRepository : CorporationTaxRegistrationMongoRepository

  def retrieveContactDetails(registrationID: String): Future[Option[ContactDetailsResponse]] = {
    corporationTaxRegistrationRepository.retrieveContactDetails(registrationID).map{
      case Some(contactDetails) => Some(contactDetails.toCompanyDetailsResponse(registrationID))
      case _ => None
    }
  }

  def updateContactDetails(registrationID: String, contactDetails: ContactDetails): Future[Option[ContactDetailsResponse]] = {
    corporationTaxRegistrationRepository.updateContactDetails(registrationID, contactDetails).map{
      case Some(details) => Some(details.toCompanyDetailsResponse(registrationID))
      case _ => None
    }
  }
}
