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

package submissionModules

import javax.inject.{Inject, Singleton}

import org.joda.time.DateTime
import play.api.{Application, Configuration, Environment, Logger}
import play.api.inject.{Binding, Module}
import repositories.{HeldSubmissionMongoRepository, HeldSubmissionRepository, HeldSubmissionRepositoryImpl}

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.mongo.Repository

import scala.concurrent.Future

/**
  * Created by george on 11/08/17.
  */
class HeldSubmissionModule extends Module{
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[HeldSubmissionReporter].to[HeldSubmissionReporterImpl].eagerly()
    )
  }
}

@Singleton
class HeldSubmissionReporterImpl @Inject()(app: Application, configuration: Configuration, repository: HeldSubmissionRepositoryImpl) extends HeldSubmissionReporter {

  def apply(app: Application, configuration: Configuration): Future[Unit] = {
    repository.store.retrieveHeldSubmissionElapsedTimes(DateTime.now).map {
      a => Logger.info(s"Held submission elapsed days: $a")
    }
  }

  apply(app, configuration)
}

trait HeldSubmissionReporter
