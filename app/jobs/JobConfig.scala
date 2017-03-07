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

package jobs

import javax.inject.{Singleton, Inject}

import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}
import org.joda.time.{Duration => JodaDuration}
import uk.gov.hmrc.play.config.inject.ServicesConfig

@Singleton
class JobConfig @Inject()(config: ServicesConfig) {

  val name: String = "check-submission-job"

  lazy val INITIAL_DELAY       = s"$name.schedule.initialDelay"
  lazy val INTERVAL            = s"$name.schedule.interval"
  lazy val LOCK_TIMEOUT        = s"$name.schedule.lockTimeout"

  lazy val initialDelay = {
    val dur = ScalaDuration.create(config.getConfString(INITIAL_DELAY,
      throw new RuntimeException(s"Could not find config $INITIAL_DELAY")))
    FiniteDuration(dur.length, dur.unit)
  }

  lazy val interval = {
    val dur = ScalaDuration.create(config.getConfString(INTERVAL,
      //$COVERAGE-OFF$
      throw new RuntimeException(s"Could not find config $INTERVAL")))
      //$COVERAGE-ON$
    FiniteDuration(dur.length, dur.unit)
  }

  lazy val lockTimeout : JodaDuration = {
    val dur = ScalaDuration.create(config.getConfString(LOCK_TIMEOUT,
      throw new RuntimeException(s"Could not find config $LOCK_TIMEOUT")))
    JodaDuration.standardSeconds( FiniteDuration(dur.length, dur.unit).toSeconds )
  }
}
