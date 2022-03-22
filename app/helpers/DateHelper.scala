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

package helpers

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsString, Writes}

trait DateFormatter extends DateHelper {
  val zonedDateTimeWrites: Writes[ZonedDateTime] = new Writes[ZonedDateTime] {
    def writes(z: ZonedDateTime) = JsString(formatTimestamp(z))
  }
}

trait DateHelper {
  val dtFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")

  def now: DateTime = {
    DateTime.now(DateTimeZone.UTC)
  }

  def nowAsZonedDateTime: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"))

  def getCurrentDay: String = {
    now.toString("yyyy-MM-dd")
  }

  def yyyymmdd(s: String): DateTime = {
    DateTime.parse(s, DateTimeFormat.forPattern("yyyy-MM-dd"))
  }

  def asDate(s: String): DateTime = {
    DateTime.parse(s)
  }

  def formatDate(date: DateTime): String = {
    date.toString("yyyy-MM-dd")
  }

  def formatTimestamp(timeStamp: DateTime): String = {
    val timeStampFormat = "yyyy-MM-dd'T'HH:mm:ssXXX"
    val format: SimpleDateFormat = new SimpleDateFormat(timeStampFormat)
    format.format(new Date(timeStamp.getMillis))
  }

  def formatTimestamp(timeStamp: ZonedDateTime): String = {
    val utcTimeStamp = timeStamp.withZoneSameInstant(ZoneId.of("Z"))
    dtFormat.format(utcTimeStamp)
  }
}

object DateHelper extends DateHelper
