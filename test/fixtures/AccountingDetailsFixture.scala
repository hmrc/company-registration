/*
 * Copyright 2023 HM Revenue & Customs
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

package fixtures

import controllers.routes
import models._
import play.api.libs.json.Json

case class AccountingDetailsResponse(accountingDateStatus : String,
                                     startDateOfBusiness : Option[String],
                                     links : Links){
}

object AccountingDetailsResponse {
  implicit val formats = Json.format[AccountingDetailsResponse]
  def buildLinks(registrationID: String): Links = {
    Links(
      self = Some(routes.AccountingDetailsController.retrieveAccountingDetails(registrationID).url),
      registration = Some(routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url)
    )
  }
}


trait AccountingDetailsFixture {

  val validAccountingDetails = AccountingDetails( AccountingDetails.FUTURE_DATE, Some("2016-08-16") )

  val accountingDetailsNoStartDateOfBusiness = AccountingDetails( AccountingDetails.WHEN_REGISTERED, None )

  import AccountingDetailsResponse.buildLinks
  val validAccountingDetailsResponse = AccountingDetailsResponse(
    accountingDateStatus = validAccountingDetails.status,
    startDateOfBusiness = validAccountingDetails.activeDate,
    buildLinks("12345")
    )
}
