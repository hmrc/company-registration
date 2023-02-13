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

package models.admin

import play.api.libs.json.{Format, JsValue, Json, Writes}

case class HO6Response(success: Boolean,
                       statusBefore: String,
                       statusAfter: String)

object HO6Response {
  val format: Format[HO6Response] = Json.format[HO6Response]

  val adminAuditWrites = new Writes[HO6Response] {
    override def writes(o: HO6Response): JsValue = {
      Json.obj("receivedDetails" -> Json.obj(
        "handOffTrigger" -> (if (o.success) "Successful" else "Failed"),
        "documentStatusBefore" -> o.statusBefore,
        "documentStatusAfter" -> o.statusAfter,
        "registrationOfInterest" -> "Not implemented yet"
      ))
    }
  }
}
