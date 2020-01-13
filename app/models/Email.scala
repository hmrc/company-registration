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

package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Email(address: String,
                 emailType: String,
                 linkSent: Boolean,
                 verified: Boolean,
                 returnLinkEmailSent : Boolean)

object Email {
  implicit val format: Format[Email] = {
    val reads = (
      (__ \ "address").read[String] and
      (__ \ "type").read[String] and
      (__ \ "link-sent").read[Boolean] and
      (__ \ "verified").read[Boolean] and
      (__ \ "return-link-email-sent").read[Boolean].orElse(Reads.pure(true))
    )(Email.apply _)

    val writes = (
      (__ \ "address").write[String] and
      (__ \ "type").write[String] and
      (__ \ "link-sent").write[Boolean] and
      (__ \ "verified").write[Boolean] and
      (__ \ "return-link-email-sent").write[Boolean]
    )(unlift(Email.unapply))

    Format(reads, writes)
  }
}
