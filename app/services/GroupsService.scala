/*
 * Copyright 2022 HM Revenue & Customs
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
import models.Groups
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}

import scala.concurrent.{ExecutionContext, Future}

class GroupsServiceImpl @Inject()(val repositories: Repositories)(implicit val ec: ExecutionContext) extends GroupsService {
  lazy val cTRegistrationRepository: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait GroupsService {

  implicit val ec: ExecutionContext

  val cTRegistrationRepository: CorporationTaxRegistrationMongoRepository

  def returnGroups(registrationID: String): Future[Option[Groups]] = {
    cTRegistrationRepository.returnGroupsBlock(registrationID)
  }

  def deleteGroups(registrationID: String): Future[Boolean] = {
    cTRegistrationRepository.deleteGroupsBlock(registrationID).flatMap {
      deleted =>
        if (!deleted) {
          Future.failed(new Exception(s"Groups block failed to delete successfully for regId $registrationID"))
        } else {
          Future.successful(deleted)
        }
    }
  }

  def updateGroups(registrationID: String, groups: Groups): Future[Groups] = {
    cTRegistrationRepository.updateGroups(registrationID, groups)
  }

}