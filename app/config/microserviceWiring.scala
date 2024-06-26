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

package config

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem

import javax.inject.Inject
import play.api.Configuration
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.{HttpHook, HttpHooks}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.http.ws._

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks: Seq[HttpHook with AnyRef] = Seq(AuditingHook)

}

trait WSHttpSCRS extends
  HttpGet with WSGet with
  HttpPut with WSPut with
  HttpPost with WSPost with
  HttpDelete with WSDelete with
  HttpPatch with WSPatch

abstract class WSHttpSCRSImpl @Inject()(val actorSystem: ActorSystem, val appNameConfiguration: Configuration, val auditConnector: AuditConnector) extends WSHttpSCRS with HttpClient {
  override val hooks: Seq[Nothing] = NoneRequired

  override protected def configuration: Config = appNameConfiguration.underlying
}