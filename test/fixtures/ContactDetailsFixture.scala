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

package fixtures

import models.{ContactDetails, Links}
import play.api.libs.json.Json

case class ContactDetailsResponse(contactFirstName: String,
                                  contactMiddleName: Option[String],
                                  contactSurname: String,
                                  contactDaytimeTelephoneNumber: Option[String],
                                  contactMobileNumber: Option[String],
                                  contactEmail: Option[String],
                                  links: Links)

object ContactDetailsResponse {
  implicit val formatsLinks = Json.format[Links]
  implicit val formats = Json.format[ContactDetailsResponse]
}

trait ContactDetailsFixture {

  lazy val contactDetails = ContactDetails(
    "testContactFirstName",
    Some("testContactMiddleName"),
    "testContactLastName",
    Some("02072899066"),
    Some("07567293726"),
    Some("test@email.co.uk")
  )

  lazy val contactDetailsResponse = ContactDetailsResponse(
    contactDetails.firstName,
    contactDetails.middleName,
    contactDetails.surname,
    contactDetails.phone,
    contactDetails.mobile,
    contactDetails.email,
    Links(Some(s"/company-registration/corporation-tax-registration/12345/contact-details"),
      Some(s"/company-registration/corporation-tax-registration/12345"))
  )
}
