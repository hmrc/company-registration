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

import models.{ContactDetails, ContactDetailsResponse, Links}

trait ContactDetailsFixture {

  lazy val contactDetails = ContactDetails(
    Some("testContactFirstName"),
    Some("testContactMiddleName"),
    Some("testContactLastName"),
    Some("02072899066"),
    Some("07567293726"),
    Some("test@email.co.uk")
  )

  lazy val contactDetailsResponse = ContactDetailsResponse(
    contactDetails.contactFirstName,
    contactDetails.contactMiddleName,
    contactDetails.contactSurname,
    contactDetails.contactDaytimeTelephoneNumber,
    contactDetails.contactMobileNumber,
    contactDetails.contactEmail,
    Links(Some(s"/corporation-tax-registration/12345/trading-details"),
      Some(s"/corporation-tax-registration/12345/"))
  )
}
