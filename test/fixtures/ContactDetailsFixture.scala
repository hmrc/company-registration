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
import models.{ContactDetails, Links}
import play.api.libs.json.Json

case class ContactDetailsResponse(contactDaytimeTelephoneNumber: Option[String],
                                  contactMobileNumber: Option[String],
                                  contactEmail: Option[String],
                                  links: Links)

object ContactDetailsResponse {
  implicit val formatsLinks = Json.format[Links]
  implicit val formats = Json.format[ContactDetailsResponse]
}

trait ContactDetailsFixture {

   val contactDetails = ContactDetails(
    Some("02072899066"),
    Some("07567293726"),
    Some("test@email.co.uk")
  )

  def contactDetailsResponse(regId: String) = ContactDetailsResponse(
    contactDetails.phone,
    contactDetails.mobile,
    contactDetails.email,
    Links(Some(routes.ContactDetailsController.retrieveContactDetails(regId).url),
      Some(routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(regId).url))
  )

   val contactDetailsResponse = ContactDetailsResponse(
    contactDetails.phone,
    contactDetails.mobile,
    contactDetails.email,
    Links(Some(routes.ContactDetailsController.retrieveContactDetails("12345").url),
      Some(routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration("12345").url))
  )
}