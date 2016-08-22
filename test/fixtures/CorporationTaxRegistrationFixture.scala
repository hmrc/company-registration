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

trait CorporationTaxRegistrationFixture extends CompanyDetailsFixture with ContactDetailsFixture {

	lazy val validCorporationTaxRegistrationRequest = CorporationTaxRegistrationRequest("en")

	lazy val validCorporationTaxRegistration = CorporationTaxRegistration(
		OID = "9876543210",
		registrationID = "0123456789",
		formCreationTimestamp = "2001-12-31T12:00:00Z",
		language = "en",
		companyDetails = Some(validCompanyDetails),
    tradingDetails = Some(TradingDetails()),
		contactDetails = Some(contactDetails)
	)

	lazy val validCorporationTaxRegistrationResponse = CorporationTaxRegistrationResponse(
		registrationID = "0123456789",
		formCreationTimestamp = "2001-12-31T12:00:00Z",
		Links(Some("/corporation-tax-registration/0123456789"))
  )
}