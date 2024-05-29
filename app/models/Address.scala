/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.libs.json.{JsPath, JsonValidationError, Reads, Writes}
import utils.StringNormaliser

case class Address(line1: String,
                   line2: String,
                   line3: Option[String],
                   line4: Option[String],
                   postcode: Option[String],
                   country: Option[String]) {
  def sanitised: Address =
    Address(
      StringNormaliser.normaliseAndRemoveIllegalCharacters(line1),
      StringNormaliser.normaliseAndRemoveIllegalCharacters(line2),
      line3.map(StringNormaliser.normaliseAndRemoveIllegalCharacters),
      line4.map(StringNormaliser.normaliseAndRemoveIllegalCharacters),
      postcode.map(StringNormaliser.normaliseAndRemoveIllegalCharacters),
      country.map(StringNormaliser.normaliseAndRemoveIllegalCharacters)
    )
}

object Address {
  implicit val reads: Reads[Address] = (
    (JsPath \ "line1").read[String] and
      (JsPath \ "line2").read[String] and
      (JsPath \ "line3").readNullable[String] and
      (JsPath \ "line4").readNullable[String] and
      (JsPath \ "postcode").readNullable[String] and
      (JsPath \ "country").readNullable[String]
    ) (Address.apply _)
    .filter(JsonValidationError("Must have at least one of postcode and country"))(addr => addr.postcode.isDefined || addr.country.isDefined)

  implicit val writes: Writes[Address] = (
    (JsPath \ "line1").write[String] and
      (JsPath \ "line2").write[String] and
      (JsPath \ "line3").writeNullable[String] and
      (JsPath \ "line4").writeNullable[String] and
      (JsPath \ "postcode").writeNullable[String] and
      (JsPath \ "country").writeNullable[String]
    ) (unlift(Address.unapply))

}