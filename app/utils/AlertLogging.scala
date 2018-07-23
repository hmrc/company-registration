/*
 * Copyright 2018 HM Revenue & Customs
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

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import DateCalculators.{getCurrentDay, getCurrentTime}
import play.api.Logger
import uk.gov.hmrc.play.config.ServicesConfig

object PagerDutyKeys extends Enumeration {
  val CT_REJECTED = Value
  val CT_ACCEPTED_MISSING_UTR = Value
  val STALE_DOCUMENTS_DELETE_WARNING_CRN_FOUND = Value
  val CT_ACCEPTED_NO_REG_DOC_II_SUBS_DELETED = Value
}

object AlertLogging extends AlertLogging with ServicesConfig {
  override protected val loggingDays: String = getConfString("alert-config.logging-day", throw new Exception("Could not find config key: LoggingDay"))
  override protected val loggingTimes: String = getConfString("alert-config.logging-time", throw new Exception("Could not find config key: LoggingTime"))
}

trait AlertLogging {

  protected val loggingDays: String = "MON,TUE,WED,THU,FRI"
  protected val loggingTimes: String = "08:00:00_17:00:00"

  def pagerduty(key: PagerDutyKeys.Value, message: Option[String] = None) {
    val log = s"${key.toString}${message.fold("")(msg => s" - $msg")}"
    if(inWorkingHours) Logger.error(log) else Logger.info(log)
  }

  def inWorkingHours: Boolean = isLoggingDay && isBetweenLoggingTimes

  private[utils] def today: String = getCurrentDay

  private[utils] def now: LocalTime = getCurrentTime

  private[utils] def ifInWorkingHours(alert: => Unit): Unit = if(inWorkingHours) alert else ()

  private[utils] def isLoggingDay = loggingDays.split(",").contains(today)

  private[utils] def isBetweenLoggingTimes: Boolean = {
    val stringToDate = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val Array(start, end) = loggingTimes.split("_") map stringToDate
    ((start isBefore now) || (now equals start)) && (now isBefore end)
  }
}