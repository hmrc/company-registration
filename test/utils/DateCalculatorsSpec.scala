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

import java.time.{LocalDate, LocalTime}
import java.time.format.DateTimeFormatter
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec

class DateCalculatorsSpec extends PlaySpec with BeforeAndAfterEach {

  "getTheDay" must {
    "return todays day" in {
      val testDate = LocalDate.parse("2017-07-11")
      DateCalculators.getTheDay(testDate) mustBe "TUE"
    }
  }

  "loggingDate" must {

    def time(h: Int, m: Int, s: Int) = LocalTime.parse(f"$h%02d" + ":" + f"$m%02d" + ":" + f"$s%02d",  DateTimeFormatter.ofPattern("HH:mm:ss"))

    "return true if today is a logging day" in {
      val testDate = "TUE"
      val loggingDays = "MON,TUE,WED,THU,FRI"
      DateCalculators.loggingDay(loggingDays, testDate) mustBe true
    }
    "return false if today is not logging day" in {
      val testDate = "SAT"
      val loggingDays = "MON,TUE,WED,THU,FRI"
      DateCalculators.loggingDay(loggingDays, testDate) mustBe false
    }
    "return true if time now is in range" in {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(9,0,0)
      DateCalculators.loggingTime(blockageLoggingTime,theTimeNow) mustBe true
    }
    "return true if time now is a second within top range" in {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(15,59,59)
      DateCalculators.loggingTime(blockageLoggingTime,theTimeNow) mustBe true
    }
    "return true if time now is a second within bottom range" in {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(7,0,1)
      DateCalculators.loggingTime(blockageLoggingTime,theTimeNow) mustBe true
    }
    "return false if time now is outside the range" in {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(6,0,0)
      DateCalculators.loggingTime(blockageLoggingTime,theTimeNow) mustBe false
    }
    "return false if time now is on the bottom range" in {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(7,0,0)
      DateCalculators.loggingTime(blockageLoggingTime,theTimeNow) mustBe false
    }
    "return false if time now is on the top range" in {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(16,0,0)
      DateCalculators.loggingTime(blockageLoggingTime,theTimeNow) mustBe false
    }
  }
}
