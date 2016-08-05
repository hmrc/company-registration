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

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import models.{CorporationTaxRegistration, Language, Links}
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{Created, Ok, NotFound}
import repositories.{CorporationTaxRegistrationRepository, Repositories}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CorporationTaxRegistrationService extends CorporationTaxRegistrationService {
  override val CorporationTaxRegistrationRepository = Repositories.ctDataRepository
}

trait CorporationTaxRegistrationService {

  val CorporationTaxRegistrationRepository: CorporationTaxRegistrationRepository

  def createCorporationTaxRegistrationRecord(OID: String, registrationId: String, givenLanguage: Language): Future[Result] = {
    val newCTdata = CorporationTaxRegistration(OID,
      registrationId,
      generateTimestamp(new DateTime()),
      givenLanguage.language,
      Links(s"/business-tax-registration/$registrationId")
    )
    CorporationTaxRegistrationRepository.createCorporationTaxRegistrationData(newCTdata).map(res => Created(Json.toJson(res)))
  }

  private def generateTimestamp(timeStamp: DateTime) : String = {
    val timeStampFormat = "yyyy-MM-dd'T'HH:mm:ssXXX"
    val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    val format: SimpleDateFormat = new SimpleDateFormat(timeStampFormat)
    format.setTimeZone(UTC)
    format.format(new Date(timeStamp.getMillis))
  }

  def retrieveCTDataRecord(rID: String): Future[Result] = {
    CorporationTaxRegistrationRepository.retrieveCTData(rID).map{
      case Some(data) => Ok(Json.toJson(data))
      case _ => NotFound
    }
  }
}
