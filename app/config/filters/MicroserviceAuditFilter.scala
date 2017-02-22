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

package config.filters

import javax.inject.Inject

import akka.stream.Materializer
import play.api.Configuration
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.RunMode

/**
  * Created by jackie on 21/02/17.
  */
class MicroserviceAuditFilter @Inject()(conf: Configuration,
  controllerConf: ControllerConfiguration
)(implicit val mat: Materializer) extends AuditFilter{

  override lazy val appName = conf.getString("appName").getOrElse("APP NAME NOT SET")

  override def auditConnector: AuditConnector = MicroserviceAuditConnector

  override def controllerNeedsAuditing(controllerName: String): Boolean = {
    controllerConf.paramsForController(controllerName).needsAuditing
  }
}

  object MicroserviceAuditConnector extends AuditConnector with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig(s"$env.auditing")
  }
