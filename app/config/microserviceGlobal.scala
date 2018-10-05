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

import java.util.Base64

import com.typesafe.config.Config
import javax.inject.{Inject, Named, Singleton}
import net.ceedubs.ficus.Ficus._
import play.api.{Application, Configuration, Logger, Play}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
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
    startupJobs.getCTCompanyName(regid)

    val base64RegIds = app.configuration.getString("list-of-regids").getOrElse("")
    val listOftxIDs = new String(Base64.getDecoder.decode(base64RegIds), "UTF-8").split(",").toList
    startupJobs.fetchDocInfoByRegId(listOftxIDs)

    val base64ackRefs = app.configuration.getString("list-of-ackrefs").getOrElse("")
    val listOfackRefs = new String(Base64.getDecoder.decode(base64ackRefs), "UTF-8").split(",").toList
    startupJobs.fetchByAckRef(listOfackRefs)

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

class AppStartupJobs @Inject()(config: Configuration,
                                val service: AdminServiceImpl,
                                val repositories: Repositories) {

  import scala.concurrent.ExecutionContext.Implicits.global

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

  def fetchDocInfoByRegId(regIds: Seq[String]): Future[Seq[Unit]] = {

    Future.sequence(regIds.map{ regId =>
      ctRepo.retrieveCorporationTaxRegistration(regId).map {
        case Some(doc) =>
          Logger.info(
            s"""
              |[StartUp] [fetchByRegID] regId: $regId,
              | status: ${doc.status},
              | lastSignedIn: ${doc.lastSignedIn},
              | confRefs: ${doc.confirmationReferences},
              | tradingDetails: ${doc.tradingDetails.isDefined},
              | contactDetails: ${doc.contactDetails.isDefined},
              | companyDetails: ${doc.companyDetails.isDefined},
              | accountingDetails: ${doc.accountingDetails.isDefined},
              | accountsPreparation: ${doc.accountsPreparation.isDefined},
              | crn: ${doc.crn.isDefined},
              | verifiedEmail: ${doc.verifiedEmail.isDefined}
            """.stripMargin
          )
        case _ => Logger.info(s"[StartUp] [fetchByRegID] No registration document found for $regId")
      }
    })
  }

  def fetchByAckRef(ackRefs: Seq[String]): Unit = {

    for (ackRef <- ackRefs) {
      ctRepo.retrieveByAckRef(ackRef).map {
        case Some(doc) =>
          Logger.info(
            s"""
               |[StartUp] [fetchByAckRef] Ack Ref: $ackRef, RegId: ${doc.registrationID}, Status: ${doc.status}, LastSignedIn: ${doc.lastSignedIn}, ConfRefs: ${doc.confirmationReferences}
            """.stripMargin
          )
        case _ => Logger.info(s"[StartUp] [fetchByAckRef] No registration document found for $ackRef")
      }
    }
  }
}