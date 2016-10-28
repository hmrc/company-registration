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

case class CompanyDetailsResponse(companyName: String,
                                  cHROAddress: CHROAddress,
                                  rOAddress: ROAddress,
                                  pPOBAddress: PPOBAddress,
                                  jurisdiction: String,
                                  tradingDetails: TradingDetails,
                                  links: Links)

object CompanyDetailsResponse {
  implicit val formats = Json.format[CompanyDetailsResponse]

  def buildLinks(registrationID: String): Links = {
    Links(
      self = Some(s"/company-registration/corporation-tax-registration/$registrationID/company-details"),
      registration = Some(s"/company-registration/corporation-tax-registration/$registrationID")
    )
  }
}

trait CompanyDetailsFixture {

  lazy val validCompanyDetails = CompanyDetails(
    "testCompanyName",
    CHROAddress(
      "Premises",
      "Line 1",
      Some("Line 2"),
      "Country",
      "Locality",
      Some("PO box"),
      Some("Post code"),
      Some("Region")
    ),
    ROAddress(
      "10",
      "test street",
      "test town",
      "test area",
      "test county",
      "XX1 1ZZ",
      "test country"
    ),
    PPOBAddress(
      "10",
      "test street",
      Some("test town"),
      Some("test area"),
      Some("test county"),
      "XX1 1ZZ",
      "test country"
    ),
    "testJurisdiction"
  )

  import CompanyDetailsResponse.buildLinks
  lazy val validCompanyDetailsResponse = CompanyDetailsResponse(
    companyName = validCompanyDetails.companyName,
    cHROAddress = validCompanyDetails.cHROAddress,
    rOAddress = validCompanyDetails.rOAddress,
    pPOBAddress = validCompanyDetails.pPOBAddress,
    jurisdiction = validCompanyDetails.jurisdiction,
    TradingDetails(),
    buildLinks("12345")
    )
}
