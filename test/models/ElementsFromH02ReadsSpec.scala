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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, Json}

class ElementsFromH02ReadsSpec extends PlaySpec {

  val validJson: JsObject = Json.obj("transaction_id" -> "testTransactionId")

  "reads should return a string when jsObject provided containing transaction_id" in {
    validJson.as[String](ElementsFromH02Reads.reads) mustBe "testTransactionId"
  }
  "reads should return jsError if element transaction_id not in obj" in {
    Json.obj().validate[String](ElementsFromH02Reads.reads).isError mustBe true
  }

}
