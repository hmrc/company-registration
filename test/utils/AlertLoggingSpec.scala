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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import org.scalatest.concurrent.Eventually
import play.api.Logger
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}

class AlertLoggingSpec extends UnitSpec with LogCapturing with Eventually {

  val defaultLoggingDays = "MON,TUE,WED,THU,FRI"
  val defaultLoggingTime = "08:00:00_17:00:00"

  val monday = "MON"
  val friday = "FRI"
  val saturday = "SAT"
  val sunday = "SUN"
  val _2pm: LocalTime = LocalTime.parse("14:00:00")
  val _5pm: LocalTime = LocalTime.parse("17:00:00")
  val _9pm: LocalTime = LocalTime.parse("21:00:00")
  val _4_59pm: LocalTime = LocalTime.parse("16:59:59")
  val _8am: LocalTime = LocalTime.parse("08:00:00")
  val _7_59am: LocalTime = LocalTime.parse("07:59:59")
  val _8_01am: LocalTime = LocalTime.parse("08:01:00")

  class Setup(todayForTest: String,
              nowForTest: LocalTime,
              logDays: String = defaultLoggingDays,
              logTimes: String = defaultLoggingTime) {

    val alertLogging: AlertLogging = new AlertLogging {
      override protected val loggingTimes: String = logTimes
      override protected val loggingDays: String = logDays

      override private[utils] def today = todayForTest
      override private[utils] def now = nowForTest
    }
  }

  class SetupInWorkingHours extends Setup(monday, _2pm)
  class SetupNotInWorkingHours extends Setup(saturday, _9pm)

  "isLoggingDay" should {

    "return true when today is the logging day" in new SetupInWorkingHours {
      alertLogging.isLoggingDay shouldBe true
    }

    "return false when today is not the logging day" in new Setup(saturday, _2pm) {
      alertLogging.isLoggingDay shouldBe false
    }
  }

  "isBetweenLoggingTimes" should {

    "return true when now is between the logging times" in new SetupInWorkingHours {
      alertLogging.isBetweenLoggingTimes shouldBe true
    }

    "return false when now is not between the logging times" in new Setup(monday, _9pm) {
      alertLogging.isBetweenLoggingTimes shouldBe false
    }
  }

  "inWorkingHours" should {

    "return true" when {

      "the current time is 14:00 on a Monday" in new SetupInWorkingHours {
        alertLogging.inWorkingHours shouldBe true
      }

      "the current time is 08:00 on a Monday" in new Setup(monday, _8am) {
        alertLogging.inWorkingHours shouldBe true
      }

      "the current time is 08:01 on a Monday" in new Setup(monday, _8_01am) {
        alertLogging.inWorkingHours shouldBe true
      }

      "the current time is 16:59 on a Friday" in new Setup(friday, _4_59pm) {
        alertLogging.inWorkingHours shouldBe true
      }
    }

    "return false" when {

      "the current time is 07:59:59 on a Monday" in new Setup(monday, _7_59am) {
        alertLogging.inWorkingHours shouldBe false
      }

      "the current time is 17:00 on a Monday" in new Setup(monday, _5pm) {
        alertLogging.inWorkingHours shouldBe false
      }

      "the current time is 21:00 on a Monday" in new Setup(monday, _9pm) {
        alertLogging.inWorkingHours shouldBe false
      }

      "the current time is 14:00 on a Saturday" in new Setup(saturday, _2pm) {
        alertLogging.inWorkingHours shouldBe false
      }

      "the current time is 14:00 on a Sunday" in new Setup(sunday, _2pm) {
        alertLogging.inWorkingHours shouldBe false
      }

    }
  }

  "pager duty" should {

    def found(logs: List[ILoggingEvent])(count: Int, msg: String, level: Level) = {
      logs.size shouldBe count
      logs.head.getMessage shouldBe msg
      logs.head.getLevel shouldBe level
    }

    "accept any Pager Duty key" in new Setup(monday, _8am){
      val validKeys = List(
        PagerDutyKeys.CT_REJECTED,
        PagerDutyKeys.CT_ACCEPTED_MISSING_UTR
      )

      validKeys foreach { key =>
        withCaptureOfLoggingFrom(Logger) { logs =>
          alertLogging.pagerduty(key)
          logs.head.getMessage shouldBe key.toString
        }
      }
    }

    "change error level based on working" when {
      "within working hours" in new Setup(monday, _8am) {
        withCaptureOfLoggingFrom(Logger) { logs =>
          alertLogging.pagerduty(PagerDutyKeys.CT_REJECTED, message = Some("Extra Information"))
          found(logs)(1, "CT_REJECTED - Extra Information", Level.ERROR)
        }
      }
      "out of working hours" in new Setup(sunday, _9pm) {
        withCaptureOfLoggingFrom(Logger) { logs =>
          alertLogging.pagerduty(PagerDutyKeys.CT_REJECTED, message = Some("Extra Information"))
          found(logs)(1, "CT_REJECTED - Extra Information", Level.INFO)
        }
      }
    }
  }
}