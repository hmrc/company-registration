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

import javax.inject.{Inject, Named, Singleton}

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.{Application, Configuration, Logger, Play}
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionMongoRepository, Repositories}
import services.admin.AdminServiceImpl
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}
import uk.gov.hmrc.play.scheduling.{RunningOfScheduledJobs, ScheduledJob}

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

  override lazy val scheduledJobs = Play.current.injector.instanceOf[Jobs].lookupJobs()

  override def onStart(app : play.api.Application) : scala.Unit = {

    val startupJobs = app.injector.instanceOf[AppStartupJobs]

    val regid = app.configuration.getString("companyNameRegID").getOrElse("")
    app.injector.instanceOf[AppStartupJobs].getCTCompanyName(regid)
    import java.util.Base64
    val regIdConf = app.configuration.getString("registrationList").getOrElse("")
    val regIdList = new String(Base64.getDecoder.decode(regIdConf), "UTF-8")
    val removalList = new String(
      Base64.getDecoder.decode(
        app.configuration.getString("removalList").getOrElse("")
      ), "UTF-8"
    )

    val limboCases = new String(
      Base64.getDecoder.decode(
        app.configuration.getString("limboCase").getOrElse("")
      ), "UTF-8"
    )

    app.injector.instanceOf[AppStartupJobs].removeRegistrations(removalList.split(","))
    (1 to 5) foreach {
      _ => app.injector.instanceOf[AppStartupJobs].getHeldDocsInfoSecondary()
    }

    val updateTransFrom = new String(
      Base64.getDecoder.decode(
        app.configuration.getString("updateTransId.from").getOrElse("")
      ),
      "UTF8"
    )
    val updateTransTo = new String(
      Base64.getDecoder.decode(
        app.configuration.getString("updateTransId.to").getOrElse("")
      ),
      "UTF8"
    )

    startupJobs.fetchIndexes()

    super.onStart(app)
  }
}

trait JobsList {
  def lookupJobs(): Seq[ScheduledJob] = Seq()
}

@Singleton
class Jobs @Inject()(
                      @Named("remove-stale-documents-job") removeStaleDocsJob: ScheduledJob,
                      @Named("metrics-job") metricsJob: ScheduledJob
                    ) extends JobsList {
  override def lookupJobs(): Seq[ScheduledJob] =
    Seq(
      removeStaleDocsJob,
      metricsJob
    )
}

class AppStartupJobs @Inject()(val service: AdminServiceImpl,
                               val repositories: Repositories) {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val heldRepo: HeldSubmissionMongoRepository = repositories.heldSubmissionRepository
  lazy val ctRepo: CorporationTaxRegistrationMongoRepository = repositories.cTRepository

  ctRepo.getRegistrationStats() map {
    stats => Logger.info(s"[RegStats] $stats")
  }

  ctRepo.retrieveLockedRegIDs() map { regIds =>
    val message = regIds.map(rid => s" RegId: $rid")
    Logger.info(s"RegIds with locked status:$message")
  }

  def getCTCompanyName(rid: String) : Future[Unit] = {
    ctRepo.retrieveMultipleCorporationTaxRegistration(rid) map {
      _ foreach { ctDoc =>
        Logger.info(s"[CompanyName] " +
          s"status : ${ctDoc.status} - " +
          s"reg Id : ${ctDoc.registrationID} - " +
          s"Company Name : ${ctDoc.companyDetails.fold("")(companyDetails => companyDetails.companyName)} - " +
          s"Trans ID : ${ctDoc.confirmationReferences.fold("")(confRefs => confRefs.transactionId)}")
      }}
  }

  def removeRegistrations(regIds: Seq[String]): Unit = {
    for(id <- regIds) {
      Logger.info(s"Deleting registration with regId: $id")
      service.adminDeleteSubmission(id)
    }
  }

  def updateTransId(updateTransFrom: String, updateTransTo: String): Unit = {
    if(updateTransFrom.nonEmpty && updateTransTo.nonEmpty) {
      service.updateTransactionId(updateTransFrom, updateTransTo) map { result =>
        if(result) {
          Logger.info(s"Updated transaction id from $updateTransFrom to $updateTransTo")
        }
      }
    } else {
      Logger.info("[AppStartupJobs] [updateTransId] Config missing or empty to update a transaction id")
    }
  }

  def getHeldDocsInfoPrimary() : Future[Unit] = {
    heldRepo.getAllHeldDocsP map {
      _ foreach { held =>
        Logger.info(s"[HeldDocs Pri] " +
          s"regid : ${held.regId} - " +
          s"ack ref : ${held.ackRef} - ")
      }
    }
  }

  def getHeldDocsInfoSecondary() : Future[Unit] = {
    heldRepo.getAllHeldDocsS map {
      _ foreach { held =>
        Logger.info(s"[HeldDocs Sec] " +
          s"regid : ${held.regId} - " +
          s"ack ref : ${held.ackRef} - ")
      }
    }
  }

  def removeLimboCases(regIds: Seq[String]): Unit = {
    val limboRegex = """(.*)#(.*)""".r
    def splitter(s: String) = s match {
      case limboRegex(id, companyName) => Some(id, companyName)
      case _ => None
    }

    Future.sequence(
      regIds.flatMap(splitter) map { case (regId, companyName) =>
        service.deleteLimboCase(regId, companyName) recover {
          case ex : Exception => Logger.warn(s"[removeLimboCases] $ex")
        }
      }
    )
  }

  def fetchIndexes(): Future[Unit] = {
    ctRepo.fetchIndexes().map { list =>
      Logger.info(s"[Indexes] There are ${list.size} indexes")
      list.foreach{ index =>
        Logger.info(s"[Indexes]\n " +
          s"name : ${index.eventualName}\n " +
          s"""keys : ${index.key match {
            case Seq(s @ _*) => s"$s\n "
            case Nil => "None\n "}}""" +
          s"unique : ${index.unique}\n " +
          s"background : ${index.background}\n " +
          s"dropDups : ${index.dropDups}\n " +
          s"sparse : ${index.sparse}\n " +
          s"version : ${index.version}\n " +
          s"partialFilter : ${index.partialFilter.map(_.values)}\n " +
          s"options : ${index.options.values}")
      }
    }
  }
}
