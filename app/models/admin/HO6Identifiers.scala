/*
 * Copyright 2021 HM Revenue & Customs
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

case class HO6Identifiers(strideUser: String,
                          sessionId: String,
                          credId: String,
                          registrationId: String,
                          transactionId: String,
                          paymentReference: String,
                          paymentAmount: String)

object HO6Identifiers {
  val format: Format[HO6Identifiers] = Json.format[HO6Identifiers]

  val adminAuditWrites = new Writes[HO6Identifiers] {
    override def writes(o: HO6Identifiers): JsValue = {
      Json.obj("submittedDetails" -> Json.obj(
        "sessionId" -> o.sessionId,
        "credId" -> o.credId,
        "journeyId" -> o.registrationId,
        "transactionId" -> o.transactionId,
        "paymentReference" -> o.paymentReference,
        "paymentAmount" -> o.paymentAmount
      ))
    }
  }
}
