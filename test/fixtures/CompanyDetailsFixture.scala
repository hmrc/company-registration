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

case class CompanyDetailsResponse(companyName: String,
                                  cHROAddress: CHROAddress,
                                  pPOBAddress: PPOB,
                                  jurisdiction: String,
                                  tradingDetails: TradingDetails,
                                  links: Links)

object CompanyDetailsResponse {
  implicit val formats = Json.format[CompanyDetailsResponse]

  def buildLinks(registrationID: String): Links = {
    Links(
      self = Some(routes.CompanyDetailsController.retrieveCompanyDetails(registrationID).url),
      registration = Some(routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url)
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
    PPOB(
      "MANUAL",
      Some(PPOBAddress(
      "10 test street",
      "test town",
      Some("test area"),
      Some("test county"),
      Some("XX1 1ZZ"),
      Some("test country"),
      None,
      "txid"
    ))),
    "testJurisdiction"
  )

  lazy val validCompanyDetailsNormalisableAddress = CompanyDetails(
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
    PPOB(
      "MANUAL",
      Some(PPOBAddress(
        "10 test æet",
        "test tØwn",
        Some("tæst area"),
        Some("tæst coûnty"),
        Some("XX1 1ØZ"),
        Some("test country"),
        None,
        "txid"
      ))),
    "testJurisdiction"
  )

  import CompanyDetailsResponse.buildLinks
  lazy val validCompanyDetailsResponse = CompanyDetailsResponse(
    companyName = validCompanyDetails.companyName,
    cHROAddress = validCompanyDetails.registeredOffice,
    pPOBAddress = validCompanyDetails.ppob,
    jurisdiction = validCompanyDetails.jurisdiction,
    TradingDetails(),
    buildLinks("12345")
    )

  def validCompanyDetailsResponse(regId: String) = CompanyDetailsResponse(
    companyName = validCompanyDetails.companyName,
    cHROAddress = validCompanyDetails.registeredOffice,
    pPOBAddress = validCompanyDetails.ppob,
    jurisdiction = validCompanyDetails.jurisdiction,
    TradingDetails(),
    buildLinks(regId)
  )
}
