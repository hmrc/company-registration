/*
 * Copyright 2024 HM Revenue & Customs
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
import models.Email
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}

import scala.concurrent.Future


class EmailServiceImpl @Inject()(repositories: Repositories) extends EmailService {
  lazy val ctRepository: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait EmailService {

  val ctRepository: CorporationTaxRegistrationMongoRepository

  def updateEmail(registrationId: String, email: Email): Future[Option[Email]] = {
    ctRepository.updateEmail(registrationId, email)
  }

  def retrieveEmail(registrationId: String): Future[Option[Email]] = {
    ctRepository.retrieveEmail(registrationId)
  }
}
