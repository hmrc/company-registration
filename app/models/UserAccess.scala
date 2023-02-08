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

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}

case class UserAccessSuccessResponse
(
  registrationId: String,
  created: Boolean,
  confRefs: Boolean,
  paymentRefs: Boolean,
  verifiedEmail: Option[Email] = None,
  registrationProgress: Option[String] = None
)

object UserAccessSuccessResponse {
  implicit val writes: Writes[UserAccessSuccessResponse] = (
    (JsPath \ "registration-id").write[String] and
      (JsPath \ "created").write[Boolean] and
      (JsPath \ "confirmation-reference").write[Boolean] and
      (JsPath \ "payment-reference").write[Boolean] and
      (JsPath \ "email").writeNullable[Email] and
      (JsPath \ "registration-progress").writeNullable[String]
    ) (unlift(UserAccessSuccessResponse.unapply))
}

case class UserAccessLimitReachedResponse(limitReached: Boolean)

object UserAccessLimitReachedResponse {
  implicit val formats = Json.format[UserAccessLimitReachedResponse]
}
