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

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class Email(address: String,
                 emailType: String,
                 linkSent: Boolean,
                 verified: Boolean,
                 returnLinkEmailSent : Boolean)

object Email {

  implicit val formats = (
      (__ \ "address").format[String] and
      (__ \ "type").format[String] and
      (__ \ "link-sent").format[Boolean] and
      (__ \ "verified").format[Boolean] and
      (__ \ "return-link-email-sent").format[Boolean]
    )(Email.apply, unlift(Email.unapply))
}
