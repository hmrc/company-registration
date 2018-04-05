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

package jobs

import javax.inject.{Inject, Singleton}

import org.joda.time.Duration
import play.api.Logger
import repositories.Repositories
import services.admin.AdminService
import uk.gov.hmrc.lock.LockKeeper
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.SCRSFeatureSwitches

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RemoveStaleDocumentsJobImpl @Inject()(
                                           val repositories: Repositories,
                                           val adminService : AdminService
                                         ) extends RemoveStaleDocumentsJob {
  val name = "remove-stale-documents-job"

  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    override val repo = repositories.lockRepository
  }
}

trait RemoveStaleDocumentsJob extends ExclusiveScheduledJob with JobConfig {

  val lock: LockKeeper
  val adminService : AdminService

  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    if (SCRSFeatureSwitches.removeStaleDocuments.enabled) {
      lock.tryLock {
        Logger.info(s"Triggered $name")
        adminService.deleteStaleDocuments() map {deletions =>
          Result(s"[remove-stale-documents-job] Successfully deleted $deletions stale documents")
        }
      } map {
        case Some(x) =>
          Logger.info(s"successfully acquired lock for $name")
          x
        case None =>
          Logger.info(s"failed to acquire lock for $name")
          Result(s"$name failed")
      } recover {
        case _: Exception => Result(s"$name failed")
      }
    } else {
      Future.successful(Result(s"Feature remove-stale-documents-job is turned off"))
    }
  }
}
