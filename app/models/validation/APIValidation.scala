/*
 * Copyright 2017 HM Revenue & Customs
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

package models.validation
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Reads.pattern
import play.api.libs.json.{Format, JsObject, JsResult, JsSuccess, JsValue, OFormat, Reads, Writes}
import play.api.libs.functional.syntax._

object APIValidation extends BaseJsonFormatting {
  val emailBooleanFormat: Format[Boolean] = new Format[Boolean] {
    override def reads(json: JsValue): JsResult[Boolean] = JsSuccess(json.asOpt[Boolean].getOrElse(true))

    override def writes(o: Boolean): JsValue = Writes.BooleanWrites.writes(o)
  }

  val lastSignedInDateTimeFormat: Format[DateTime] = new Format[DateTime] {
    override def reads(json: JsValue): JsResult[DateTime] = {
      println("USING FORMATTER API")
      JsSuccess(json.asOpt[DateTime].getOrElse(DateTime.now(DateTimeZone.UTC)))
    }

    override def writes(o: DateTime): JsValue = Writes.DefaultJodaDateWrites.writes(o)
  }

  val nameValidator = readToFmt(length(100) keepAnd pattern("^[A-Za-z 0-9'\\\\-]{1,100}$".r))
  val phoneValidator = readToFmt(length(20) keepAnd pattern("^[0-9 ]{1,20}$".r) keepAnd digitLength(10, 20))
  val emailValidator = readToFmt(length(70) keepAnd pattern("^[A-Za-z0-9\\-_.@]{1,70}$".r))
}
