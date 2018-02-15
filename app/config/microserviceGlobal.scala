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

package config

import javax.inject.{Inject, Singleton}

import com.typesafe.config.Config
import jobs.{CheckSubmissionJob, MetricsJob, MissingIncorporationJob}
import play.api.{Application, Configuration, Logger, Play}
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import net.ceedubs.ficus.Ficus._
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmission, HeldSubmissionMongoRepository, Repositories}
import services.CorporationTaxRegistrationService
import uk.gov.hmrc.play.scheduling.RunningOfScheduledJobs
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}

import scala.concurrent.Future

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with RunningOfScheduledJobs {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter: Option[AuthorisationFilter] = None

  override val scheduledJobs = Seq(CheckSubmissionJob, MissingIncorporationJob, MetricsJob)

  override def onStart(app : play.api.Application) : scala.Unit = {

    import scala.concurrent.ExecutionContext.Implicits.global
    Repositories.cTRepository.getRegistrationStats() map {
      stats => Logger.info(s"[RegStats] ${stats}")
    }

    Repositories.cTRepository.retrieveLockedRegIDs() map { regIds =>
      val message = regIds.map(rid => s" RegId: $rid")
      Logger.info(s"RegIds with locked status:$message")
    }

    app.injector.instanceOf[AppStartupJobs].getHeldDocsInfo

    import java.util.Base64
    val regIdConf = app.configuration.getString("registrationList").getOrElse("")
    val regIdList = new String(Base64.getDecoder.decode(regIdConf), "UTF-8")

    CorporationTaxRegistrationService.checkDocumentStatus(regIdList.split(","))

    super.onStart(app)
  }
}

@Singleton
class AppStartupJobs {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val heldRepo: HeldSubmissionMongoRepository = Repositories.heldSubmissionRepository
  lazy val ctRepo: CorporationTaxRegistrationMongoRepository = Repositories.cTRepository

  def getHeldDocsInfo : Future[Unit] = {
    heldRepo.getAllHeldDocs map {
      _ foreach { held =>
        ctRepo.retrieveCorporationTaxRegistration(held.regId) map {
          _ map { ctDoc =>
            Logger.info(s"[HeldDocs] " +
              s"status : ${ctDoc.status} - " +
              s"reg Id : ${ctDoc.registrationID} - " +
              s"""conf refs : ${ctDoc.confirmationReferences.fold("none"){ refs =>
                s"txId : ${refs.transactionId} - " +
                s"ack ref : ${refs.acknowledgementReference}"
              }}"""
            )
            Unit
          }
        }
      }
    }
  }
}
