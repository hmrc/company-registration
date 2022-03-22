/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{JsValue, Json, Writes}

case class ErrorResponse(statusCode: String, message: String) {
  def toJson(implicit writes: Writes[ErrorResponse]): JsValue = {
    Json.toJson(this)
  }
}

object ErrorResponse {
  implicit val formats = Json.format[ErrorResponse]

  lazy val MetadataNotFound = ErrorResponse("404", "Could not find metadata record").toJson
  lazy val companyDetailsNotFound = ErrorResponse("404", "Could not find company details record").toJson
  lazy val chHandoffDetailsNotFound = ErrorResponse("404", "Could not find CH handoff data").toJson
  lazy val chHandoffDetailsNotStored = ErrorResponse("400", "Could not store the CH handoff data").toJson
  lazy val accountingDetailsNotFound = ErrorResponse("404", "Could not find accounting details record").toJson
  lazy val tradingDetailsNotFound = ErrorResponse("404", "Could not find trading details record").toJson
  lazy val contactDetailsNotFound = ErrorResponse("404", "Could not find company details record").toJson
}
