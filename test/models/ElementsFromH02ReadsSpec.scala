/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json

class ElementsFromH02ReadsSpec extends WordSpec with Matchers {

  val jsonParsedTxidInObj = Json.parse(
    """{
      | "transaction_id" : "foo"
      | }
    """.stripMargin)

  val transactionIdDoesntExist = Json.parse(
    """{
      | "transaction_foo" : "bar"
      |}
    """.stripMargin
  )
  "reads should return a string when jsObject provided containing transaction_id" in {
    jsonParsedTxidInObj.as[String](ElementsFromH02Reads.reads) shouldBe "foo"
  }
  "reads should return jsError if element transaction_id not in obj" in {
    transactionIdDoesntExist.validate[String](ElementsFromH02Reads.reads).isError shouldBe true
  }

}
