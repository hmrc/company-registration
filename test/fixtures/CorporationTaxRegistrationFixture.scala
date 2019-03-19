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

package fixtures

import controllers.routes
import models._
import play.api.libs.json.{JsObject, Json}

case class CorporationTaxRegistrationResponse(
  registrationID: String,
  status: String,
  formCreationTimestamp: String,
  links: Links
)

object CorporationTaxRegistrationResponse {
  implicit val linksFormats = Json.format[Links]
  implicit val formats = Json.format[CorporationTaxRegistrationResponse]
}

trait CorporationTaxRegistrationFixture extends CompanyDetailsFixture with AccountingDetailsFixture with ContactDetailsFixture {

  import RegistrationStatus._

  val validCorporationTaxRegistrationRequest = CorporationTaxRegistrationRequest("en")

  val validDraftCorporationTaxRegistration = draftCorporationTaxRegistration("0123456789")

  def draftCorporationTaxRegistration(regId: String, doneHO5: Boolean = false) = CorporationTaxRegistration(
    internalId = "tiid",
    registrationID = regId,
    status = DRAFT,
    formCreationTimestamp = "2001-12-31T12:00:00Z",
    language = "en",
    confirmationReferences = None,
    companyDetails = Some(validCompanyDetails),
    accountingDetails = Some(validAccountingDetails),
    tradingDetails = Some(TradingDetails()),
    contactDetails = Some(contactDetails),
    verifiedEmail = None,
    registrationProgress = if(doneHO5) Some("ho5") else None
  )

  def validConfRefsWithData(ackRef: Option[String] = None) = ConfirmationReferences(
    acknowledgementReference = ackRef.getOrElse("BRCT12345678910"),
    transactionId = "TX1",
    paymentReference = Some("PY1"),
    paymentAmount = Some("12.00")
  )

  val validConfirmationReferences = validConfRefsWithData()

  def validHeldCTRegWithData(regId: String = "0123456789", ackRef: Option[String] = None) = CorporationTaxRegistration(
    internalId = "tiid",
    registrationID = regId,
    status = HELD,
    formCreationTimestamp = "2001-12-31T12:00:00Z",
    language = "en",
    confirmationReferences = Some(validConfRefsWithData(ackRef)),
    companyDetails = None,
    accountingDetails = Some(AccountingDetails(AccountingDetails.FUTURE_DATE, Some("2019-12-31"))),
    tradingDetails = None,
    contactDetails = None
  )

  def validCTRegWithCompanyName(regId: String = "0123456789", companyName: String) = CorporationTaxRegistration(
    internalId = "tiid",
    registrationID = regId,
    status = HELD,
    formCreationTimestamp = "2001-12-31T12:00:00Z",
    language = "en",
    confirmationReferences = Some(validConfRefsWithData(Some("BRCT1234"))),
    companyDetails = Some(validCompanyDetails.copy(companyName = companyName)),
    accountingDetails = Some(AccountingDetails(AccountingDetails.FUTURE_DATE, Some("2019-12-31"))),
    tradingDetails = None,
    contactDetails = None
  )

  val validHeldCorporationTaxRegistration = validHeldCTRegWithData()

  val validCorporationTaxRegistrationResponse = CorporationTaxRegistrationResponse(
    registrationID = "0123456789",
    status = DRAFT,
    formCreationTimestamp = "2001-12-31T12:00:00Z",
    Links(Some(routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration("0123456789").url))
  )

  def buildCTRegistrationResponse(regId: String = "0123456789",
                                  status: String = DRAFT,
                                  timeStamp: String = "2001-12-31T12:00:00Z") = {
    CorporationTaxRegistrationResponse(
      registrationID = regId,
      status = status,
      formCreationTimestamp = timeStamp,
      Links(Some(routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(regId).url))
    )
  }

  val heldJson: JsObject = Json.parse(
    """
      |{
      |   "acknowledgementReference":"BRCT00000000001",
      |   "registration":{
      |      "metadata":{
      |         "businessType":"Limited company",
      |         "submissionFromAgent":false,
      |         "declareAccurateAndComplete":true,
      |         "sessionId":"session-152ebc2f-8e0d-4137-8236-058b295fe52f",
      |         "credentialId":"cred-id-254524311264",
      |         "language":"ENG",
      |         "formCreationTimestamp":"2017-07-03T13:19:54.000+01:00",
      |         "completionCapacity":"Director"
      |      },
      |      "corporationTax":{
      |         "companyOfficeNumber":"623",
      |         "hasCompanyTakenOverBusiness":false,
      |         "companyMemberOfGroup":false,
      |         "companiesHouseCompanyName":"Company Name Ltd",
      |         "returnsOnCT61":false,
      |         "companyACharity":false,
      |         "businessAddress":{
      |            "line1":"12 address line 1",
      |            "line2":"address line 2",
      |            "line3":"address line 3",
      |            "line4":"address line 4",
      |            "postcode":"ZZ11 1ZZ"
      |         },
      |         "businessContactDetails":{
      |            "phoneNumber":"1234",
      |            "mobileNumber":"123456",
      |            "email":"6email@whatever.com"
      |         }
      |      }
      |   }
      |}
    """.stripMargin).as[JsObject]
}
