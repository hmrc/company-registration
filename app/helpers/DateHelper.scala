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

package helpers

import play.api.libs.json.{JsString, Writes}

import java.time._
import java.time.format.DateTimeFormatter

trait DateFormatter extends DateHelper {
  val zonedDateTimeWrites: Writes[ZonedDateTime] = new Writes[ZonedDateTime] {
    def writes(z: ZonedDateTime) = JsString(formatTimestamp(z))
  }
}

trait DateHelper {
  val dtFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")

  def nowAsZonedDateTime: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)

  def getCurrentDay: String = formatDate(LocalDate.now(ZoneOffset.UTC))

  def asDate(s: String): LocalDate = LocalDate.parse(s)

  def formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

  def formatTimestamp(timeStamp: Instant): String = timeStamp.toString

  def formatTimestamp(timeStamp: ZonedDateTime): String = {
    val utcTimeStamp = timeStamp.withZoneSameInstant(ZoneId.of("Z"))
    dtFormat.format(utcTimeStamp)
  }
}

object DateHelper extends DateHelper
