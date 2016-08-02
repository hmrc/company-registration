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

package models

import play.api.libs.json.Json

case class Metadata(OID: String,
                    registrationID: String,
                    formCreationTimestamp: String,
                    openIDContentId: String,
                    language: String,
                    submissionResponseEmail: String,
                    completionCapacity: String,
                    completionCapacityOther: String,
                    declareAccurateAndComplete: String)

object Metadata {
  implicit val formats = Json.format[Metadata]

  def empty: Metadata = {
    Metadata("", "", "", "", "", "", "", "", "")
  }
}

case class CTRequest(language: String)

object CTRequest {
  implicit val formats = Json.format[CTRequest]
}

case class CTResponse(registrationId: String,
                            formCreationTimestamp: String,
                            links: Links)

case class Links(self: String)

object CTResponse {
  implicit val formats = Json.format[Metadata]
  implicit val formatLinks = Json.format[Links]
}
