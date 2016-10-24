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

case class CorporationTaxRegistrationResponse(registrationID: String,
																							status: String,
																							formCreationTimestamp: String,
																							links: Links)

object CorporationTaxRegistrationResponse {
	implicit val linksFormats = Json.format[Links]
	implicit val formats = Json.format[CorporationTaxRegistrationResponse]
}

trait CorporationTaxRegistrationFixture extends CompanyDetailsFixture with AccountingDetailsFixture with ContactDetailsFixture {

	import RegistrationStatus._

	val validCorporationTaxRegistrationRequest = CorporationTaxRegistrationRequest("en")

	val validDraftCorporationTaxRegistration = CorporationTaxRegistration(
		OID = "9876543210",
		registrationID = "0123456789",
		status = DRAFT,
		formCreationTimestamp = "2001-12-31T12:00:00Z",
		language = "en",
		confirmationReferences = None,
		companyDetails = Some(validCompanyDetails),
		accountingDetails = Some(validAccountingDetails),
		tradingDetails = Some(TradingDetails()),
		contactDetails = Some(contactDetails)
	)

	val validConfirmationReferences = ConfirmationReferences(
		acknowledgementReference = "BRCT12345678910",
		transactionId = "TX1",
		paymentReference = "PY1",
		paymentAmount = "12.00"
	)

	val validHeldCorporationTaxRegistration = CorporationTaxRegistration(
		OID = "9876543210",
		registrationID = "0123456789",
		status = HELD,
		formCreationTimestamp = "2001-12-31T12:00:00Z",
		language = "en",
		confirmationReferences = Some(validConfirmationReferences),
		companyDetails = None,
		accountingDetails = None,
		tradingDetails = None,
		contactDetails = None
	)

	val validCorporationTaxRegistrationResponse = CorporationTaxRegistrationResponse(
		registrationID = "0123456789",
		status = DRAFT,
		formCreationTimestamp = "2001-12-31T12:00:00Z",
		Links(Some("/company-registration/corporation-tax-registration/0123456789"))
  )
}