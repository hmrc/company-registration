/*
 * Copyright 2019 HM Revenue & Customs
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

package services

import config.MicroserviceAppConfig
import javax.inject.Inject
import org.joda.time.DateTime
import repositories.{Repositories, ThrottleMongoRepository}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait ThrottleResponse
case class ThrottleSuccessResponse(registrationID: String) extends ThrottleResponse
case object ThrottleTooManyRequestsResponse extends ThrottleResponse

class ThrottleServiceImpl @Inject()(val repositories: Repositories,
                                    microserviceAppConfig: MicroserviceAppConfig) extends ThrottleService {
  lazy val throttleMongoRepository = repositories.throttleRepository
  def dateTime = DateTimeUtils.now
  lazy val threshold = microserviceAppConfig.getConfInt("throttle-threshold", throw new Exception("throttle-threshold not found in config"))
}

trait ThrottleService extends BaseController {
  val throttleMongoRepository : ThrottleMongoRepository
  def dateTime: DateTime
  val threshold: Int

  def checkUserAccess: Future[Boolean] = {
    val date = getCurrentDay
    throttleMongoRepository.update(date, threshold) map {
      case count if threshold < count =>
        compensateTransaction(date)
        false
      case count => true
    }
  }

  private[services] def compensateTransaction(date: String): Future[Int] = {
    throttleMongoRepository.compensate(date, threshold)
  }

  private[services] def getCurrentDay: String = {
    dateTime.toString("yyyy-MM-dd")
  }
}
