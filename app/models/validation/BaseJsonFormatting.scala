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

import models.AcknowledgementReferences
import models.Validation.{digitLength, length, readToFmt}
import org.joda.time.DateTime
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{maxLength, minLength, pattern}
import play.api.libs.json.{Format, JsError, JsSuccess, JsValue, Reads, Writes}

trait BaseJsonFormatting {
  def length(maxLen: Int, minLen: Int = 1): Reads[String] = maxLength[String](maxLen) keepAnd minLength[String](minLen)

  def readToFmt(rds: Reads[String])(implicit wts: Writes[String]): Format[String] = Format(rds, wts)

  def digitLength(minLength: Int, maxLength: Int)(implicit wts: Writes[String]):Format[String] = {
    val reads: Reads[String] = new Reads[String] {
      override def reads(json: JsValue) = {
        val str = json.as[String]
        if(str.replaceAll(" ", "").matches(s"[0-9]{$minLength,$maxLength}")) {
          JsSuccess(str)
        } else {
          JsError(s"field must contain between $minLength and $maxLength numbers")
        }
      }
    }

    Format(reads, wts)
  }

  def lengthFmt(maxLen: Int, minLen: Int = 1): Format[String] = readToFmt(length(maxLen, minLen))

  def withFilter[A](fmt: Format[A], error: ValidationError)(f: (A) => Boolean): Format[A] = {
    Format(fmt.filter(error)(f), fmt)
  }

  def standardRead = Reads.StringReads

  val emailBooleanFormat: Format[Boolean]

  val emailBooleanRead: Reads[Boolean] = Reads.pure(true)

  val lastSignedInDateTimeFormat: Format[DateTime]

  //Acknowledgement Reference
  val cryptoFormat: Format[String] = Format(Reads.StringReads, Writes.StringWrites)

  //Contact Details
  val nameValidator: Format[String]
  val phoneValidator: Format[String]
  val emailValidator: Format[String]
}
