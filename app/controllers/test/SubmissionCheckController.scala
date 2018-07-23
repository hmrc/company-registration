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

package controllers.test

import javax.inject.Inject
import jobs.CheckSubmissionJob
import org.joda.time.Duration
import play.api.Logger
import play.api.mvc.Action
import repositories.Repositories
import services.{FailedToRetrieveByTxId, RegistrationHoldingPenService}
import uk.gov.hmrc.lock.LockKeeper
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global


class SubmissionCheckControllerImpl @Inject()(
       val service: RegistrationHoldingPenService,
       val checkSubmissionJob: CheckSubmissionJob,
       val repositories: Repositories
     ) extends SubmissionCheckController {
  val name = "check-submission-test-endpoint"
  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = checkSubmissionJob.lockTimeout
    override val repo = repositories.lockRepository
  }
}

trait SubmissionCheckController extends BaseController {

  val service: RegistrationHoldingPenService
  val name: String
  val lock: LockKeeper

  def triggerSubmissionCheck = Action.async {
    implicit request =>
      lock.tryLock[String] {
        Logger.info(s"[Test] [SubmissionCheckController] Triggered $name")
        service.updateNextSubmissionByTimepoint recover {
          case ex: FailedToRetrieveByTxId =>
            ex.getClass.toString
          case ex =>
            ex.getClass.toString
        }
      } map {
        case Some(x) =>
          Logger.info(s"successfully acquired lock for $name")
          Ok(x)
        case None =>
          Logger.info(s"failed to acquire lock for $name")
          Ok(s"$name failed - could not acquire lock")
      } recover {
        case ex: Exception =>
          InternalServerError(s"$name failed - An error has occurred during the submission - ${ex.getMessage}")
      }
  }
}
