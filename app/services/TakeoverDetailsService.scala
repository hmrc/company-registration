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

import javax.inject.{Inject, Singleton}
import models.TakeoverDetails
import play.api.libs.json.Json
import repositories.CorporationTaxRegistrationMongoRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TakeoverDetailsService @Inject()(corporationTaxRegistrationMongoRepository: CorporationTaxRegistrationMongoRepository)(implicit ec: ExecutionContext) {


  def retrieveTakeoverDetailsBlock(registrationID: String): Future[Option[TakeoverDetails]] = {
    corporationTaxRegistrationMongoRepository
      .findOneBySelector(corporationTaxRegistrationMongoRepository.regIDSelector(registrationID)).map {
      document =>
        document.getOrElse(
          throw new Exception(s"[retrieveTakeoverDetails] failed to retrieve document with regId: '$registrationID' as it was not found")
        ).takeoverDetails
    }
  }

  def updateTakeoverDetailsBlock(registrationID: String, takeoverDetails: TakeoverDetails): Future[TakeoverDetails] = {
    val json = Json.toJson(takeoverDetails)
    val key = "takeoverDetails"

    corporationTaxRegistrationMongoRepository.update(
      selector = corporationTaxRegistrationMongoRepository.regIDSelector(registrationID),
      key = key,
      value = json
    ).map {
      _ => takeoverDetails
    }
  }

}
