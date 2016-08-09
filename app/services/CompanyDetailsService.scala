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

import models.{CompanyDetailsResponse, CompanyDetails}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CompanyDetailsService extends CompanyDetailsService {
  override val corporationTaxRegistrationRepository = Repositories.cTRepository
}

trait CompanyDetailsService extends BaseController {

  val corporationTaxRegistrationRepository : CorporationTaxRegistrationMongoRepository

  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetailsResponse]] = {
    corporationTaxRegistrationRepository.retrieveCompanyDetails(registrationID).map{
      case Some(companyDetails) => Some(companyDetails.toCompanyDetailsResponse(registrationID))
      case _ => None
    }
  }

  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetailsResponse]] = {
    corporationTaxRegistrationRepository.updateCompanyDetails(registrationID, companyDetails).map{
      case Some(details) => Some(details.toCompanyDetailsResponse(registrationID))
      case _ => None
    }
  }
}
