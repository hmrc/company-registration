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

import javax.inject.{Inject, Singleton}

import org.joda.time.Duration
import play.api.{Logger, Play}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DefaultDB
import services.MetricsService
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.SCRSFeatureSwitches

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class MetricsJobImpl @Inject()(val metricsService: MetricsService) extends MetricsJob {
  val name = "metrics-job"
  lazy val db: () => DefaultDB = new MongoDbConnection{}.db
  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    private implicit val mongo = new MongoDbConnection {}.db
    override val repo = new LockRepository
  }
}

object MetricsJob extends MetricsJob {
  val name = "metrics-job"

  lazy val app = Play.current
  override lazy val metricsService: MetricsService = app.injector.instanceOf[MetricsService]
  lazy val db: () => DefaultDB = new MongoDbConnection{}.db
  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    private implicit val mongo = new MongoDbConnection {}.db
    override val repo = new LockRepository
  }
}


trait MetricsJob extends ExclusiveScheduledJob with JobConfig with JobHelper {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val lock: LockKeeper
  val metricsService: MetricsService

  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    ifFeatureEnabled(SCRSFeatureSwitches.graphiteMetrics) {
      whenLockAcquired {
        metricsService.updateDocumentMetrics() map { result =>
          val message = s"Feature is turned on - result = Updated document stats - $result"
          Logger.info(message)
          Result(message)
        }
      }
    }
  }
}