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

import config.MicroserviceAppConfig
import utils.DateCalculators.{getCurrentDay, getCurrentTime}

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}

object PagerDutyKeys extends Enumeration {
  val CT_REJECTED: PagerDutyKeys.Value = Value
  val CT_ACCEPTED_MISSING_UTR: PagerDutyKeys.Value = Value
  val STALE_DOCUMENTS_DELETE_WARNING_CRN_FOUND: PagerDutyKeys.Value = Value
  val CT_ACCEPTED_NO_REG_DOC_II_SUBS_DELETED: PagerDutyKeys.Value = Value
  val TXID_IN_CR_DOESNT_MATCH_HANDOFF_TXID: PagerDutyKeys.Value = Value
}

@Singleton
class AlertLoggingImpl @Inject()(microserviceAppConfig: MicroserviceAppConfig) extends AlertLogging {
  override protected val loggingDays: String = microserviceAppConfig.getConfigString("alert-config.logging-day")
  override protected val loggingTimes: String = microserviceAppConfig.getConfigString("alert-config.logging-time")
}

trait AlertLogging extends Logging {

  protected val loggingDays: String = "MON,TUE,WED,THU,FRI"
  protected val loggingTimes: String = "08:00:00_17:00:00"

  def pagerduty(key: PagerDutyKeys.Value, message: Option[String] = None): Unit = {
    val log = s"${key.toString}${message.fold("")(msg => s" - $msg")}"
    if (inWorkingHours) logger.error(log) else logger.info(log)
  }

  def inWorkingHours: Boolean = isLoggingDay && isBetweenLoggingTimes

  private[utils] def today: String = getCurrentDay

  private[utils] def now: LocalTime = getCurrentTime

  private[utils] def ifInWorkingHours(alert: => Unit): Unit = if (inWorkingHours) alert else ()

  private[utils] def isLoggingDay = loggingDays.split(",").contains(today)

  private[utils] def isBetweenLoggingTimes: Boolean = {
    val stringToDate = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val Array(start, end) = loggingTimes.split("_") map stringToDate
    ((start isBefore now) || (now equals start)) && (now isBefore end)
  }
}