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

package fixtures

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
      self = Some(s"/company-registration/corporation-tax-registration/$registrationID/accounting-details"),
      registration = Some(s"/company-registration/corporation-tax-registration/$registrationID")
    )
  }
}


trait AccountingDetailsFixture {

  val validAccountingDetails = AccountingDetails( "futureDate", Some("22-08-2016") )

  val accountingDetailsNoStartDateOfBusiness = AccountingDetails( "whenRegistered", None )

  import AccountingDetailsResponse.buildLinks
  val validAccountingDetailsResponse = AccountingDetailsResponse(
    accountingDateStatus = validAccountingDetails.accountingDateStatus,
    startDateOfBusiness = validAccountingDetails.startDateOfBusiness,
    buildLinks("12345")
    )
}

