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

package utils

import java.time.format.{DateTimeFormatter, TextStyle}
import java.time.{LocalDate, LocalTime, ZoneOffset}
import java.util.Locale


object DateCalculators {

  def getCurrentDay: String = getTheDay(LocalDate.now(ZoneOffset.UTC))

  def getCurrentTime: LocalTime = LocalTime.now

  def getTheDay(nowDateTime: LocalDate): String =
    nowDateTime.getDayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK).toUpperCase

  def loggingDay(validLoggingDays: String, todaysDate: String): Boolean =
    validLoggingDays.split(",").contains(todaysDate)

  def loggingTime(validLoggingTimes: String, now: LocalTime): Boolean = {
    implicit val frmt: String => LocalTime = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val validTimes = validLoggingTimes.split("_")

    (validTimes.head isBefore now) && (now isBefore validTimes.last)
  }

}
