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

package models.admin

import play.api.libs.json.{Format, JsValue, Json, Writes}

case class AdminCTReferenceDetails(previousUtr: Option[String], newUtr : String, previousStatus: String, newStatus: String)

object AdminCTReferenceDetails {
  val format: Format[AdminCTReferenceDetails] = Json.format[AdminCTReferenceDetails]

  val adminAuditWrites = new Writes[AdminCTReferenceDetails]{
    override def writes(o: AdminCTReferenceDetails): JsValue = {
      val prevUtr = o.previousUtr.getOrElse("NO-UTR")

      Json.obj("utrChanges" -> Json.obj(
        "previousUtr" -> prevUtr,
        "newUtr" -> o.newUtr
      ),
      "statusChanges" -> Json.obj(
        "previousStatus" -> o.previousStatus,
        "newStatus" -> o.newStatus
      ))
    }
  }
}