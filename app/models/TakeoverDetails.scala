/*
 * Copyright 2023 HM Revenue & Customs
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

import models.validation.APIValidation
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.StringNormaliser

case class TakeoverDetails(replacingAnotherBusiness: Boolean,
                           businessName: Option[String],
                           businessTakeoverAddress: Option[Address],
                           prevOwnersName: Option[String],
                           prevOwnersAddress: Option[Address]) {
  def withSanitisedFields: TakeoverDetails = copy(
    businessName = businessName.map(StringNormaliser.normaliseAndRemoveIllegalNameCharacters),
    businessTakeoverAddress = businessTakeoverAddress.map(_.sanitised),
    prevOwnersName = prevOwnersName.map(StringNormaliser.normaliseAndRemoveIllegalNameCharacters),
    prevOwnersAddress = prevOwnersAddress.map(_.sanitised)
  )
}

object TakeoverDetails {
  implicit val format: Format[TakeoverDetails] = (
    (JsPath \ "replacingAnotherBusiness").format[Boolean] and
      (JsPath \ "businessName").formatNullable[String] and
      (JsPath \ "businessTakeoverAddress").formatNullable[Address] and
      (JsPath \ "prevOwnersName").formatNullable[String] and
      (JsPath \ "prevOwnersAddress").formatNullable[Address]
    ) (TakeoverDetails.apply, unlift(TakeoverDetails.unapply))
}
