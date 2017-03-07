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

package config

import javax.inject.Inject

import jobs.CheckSubmissionJob
import play.api.{Application, Logger}

/**
  * Created by jackie on 16/02/17.
  */
trait AppStartup {

  protected def app: Application
  protected def graphiteConfig: GraphiteConfig
  protected def appName: String

}

class DefaultAppStartup @Inject()(
                                   val app: Application,
                                   jobExecutor: JobExecutor,
                                   job: CheckSubmissionJob
                                 ) extends AppStartup {

  override lazy val graphiteConfig: GraphiteConfig = new GraphiteConfig(app)

  override lazy val appName: String = app.configuration.getString("appName").getOrElse("APP NAME NOT SET")

  Logger.info(s"Starting microservice : $appName : in mode : ${app.mode}")
  if(graphiteConfig.enabled) graphiteConfig.startGraphite()

  jobExecutor.scheduleJobs(Seq(job))

}
