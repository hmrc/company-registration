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

import models.CompanyDetails
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}

import scala.concurrent.Future

class CompanyDetailsServiceImpl @Inject()(val repositories: Repositories) extends CompanyDetailsService {
  lazy val corporationTaxRegistrationRepository = repositories.cTRepository
}

trait CompanyDetailsService {

  val corporationTaxRegistrationRepository : CorporationTaxRegistrationMongoRepository

  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    corporationTaxRegistrationRepository.retrieveCompanyDetails(registrationID)
  }

  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]] = {
    corporationTaxRegistrationRepository.updateCompanyDetails(registrationID, companyDetails)
  }
}
