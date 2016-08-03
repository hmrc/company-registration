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

import models.{Links, CorporationTaxRegistration}
import play.api.libs.json.{Json, JsValue}

trait CorporationTaxRegistrationFixture {

	lazy val validCTData = CorporationTaxRegistration(
		registrationID = "0123456789",
		formCreationTimestamp = "2001-12-31T12:00:00Z",
		language = "en",
		Links("/corporation-tax-registration/4815162342"))

//	lazy val initialCTData = CorporationTaxRegistration(
//		"0123456789",
//		"2001-12-31T12:00:00Z",
//		"en",
//		Links("/corporation-tax-registration/4815162342")
//	)

	lazy val validCTDataJson: JsValue = Json.toJson(validCTData)

	//todo: something here is wrong - use validMetadataJson instead
//	lazy val validMetadataJsValue: JsValue =
//		Json.toJson("""{
//					  |    "OID": "0123456789",
//					  |    "registrationID": "0123456789",
//					  |    "formCreationTimestamp": "test",
//					  |    "openIDContentId": "0123456789",
//					  |    "language": "en",
//					  |    "submissionResponseEmail": "test@email.co.uk",
//					  |    "completionCapacity": "test",
//					  |    "completionCapacityOther": "test",
//					  |    "declareAccurateAndComplete": "test"
//					  |}""".stripMargin)
}