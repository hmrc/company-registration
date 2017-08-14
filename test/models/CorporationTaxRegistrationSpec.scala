/*
 * Copyright 2017 HM Revenue & Customs
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

package models

import fixtures.CorporationTaxRegistrationFixture
import org.joda.time.{DateTimeZone, DateTime}
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class CorporationTaxRegistrationSpec extends UnitSpec with CorporationTaxRegistrationFixture {

  def now = DateTime.now(DateTimeZone.UTC)

  "CorporationTaxRegistration" should {

    val fullHeldJson = Json.parse(
      """
        |{
        | "internalId":"tiid",
        | "registrationID":"0123456789",
        | "status":"held",
        | "formCreationTimestamp":"2001-12-31T12:00:00Z",
        | "language":"en",
        | "confirmationReferences":{
        |  "acknowledgement-reference":"BRCT12345678910",
        |  "transaction-id":"TX1",
        |  "payment-reference":"PY1",
        |  "payment-amount":"12.00"
        | },
        | "accountingDetails":{
        |  "accountingDateStatus":"FUTURE_DATE",
        |  "startDateOfBusiness":"2019-12-31"
        | },
        | "createdTime":1485859623928
        |}
      """.stripMargin)

    "using a custom read on the held json document without a lastSignedIn value will default it to the current time" in {
      val before = now.getMillis
      val ct = Json.fromJson[CorporationTaxRegistration](fullHeldJson)(CorporationTaxRegistration.cTReads(CorporationTaxRegistration.formatAck)).get
      val after = now.getMillis

      ct.lastSignedIn.getMillis >= before && ct.lastSignedIn.getMillis <= after shouldBe true
    }

    "using a custom read on the held json document without a lastSignedIn value will not change the rest of the document" in {
      val ct = Json.fromJson[CorporationTaxRegistration](fullHeldJson)(CorporationTaxRegistration.cTReads(CorporationTaxRegistration.formatAck))
      validHeldCorporationTaxRegistration.copy(createdTime = ct.get.createdTime, lastSignedIn = ct.get.lastSignedIn) shouldBe ct.get
    }
  }
}
