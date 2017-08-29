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
import auth.Crypto
import org.joda.time.DateTime
import play.api.libs.json.{Format, Reads, Writes}

object MongoValidation extends BaseJsonFormatting {
  val defaultStringFormat = Format(Reads.StringReads, Writes.StringWrites)

  val emailBooleanFormat: Format[Boolean] = Format(Reads.BooleanReads, Writes.BooleanWrites)

  val lastSignedInDateTimeFormat: Format[DateTime] = Format(Reads.DefaultJodaDateReads, Writes.DefaultJodaDateWrites)

  override val cryptoFormat: Format[String] = Format(Crypto.rds, Crypto.wts)

  val nameValidator = defaultStringFormat
  val phoneValidator = defaultStringFormat
  val emailValidator = defaultStringFormat
}
