/*
 * Copyright 2021 HM Revenue & Customs
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
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.jul
import javax.inject.{Inject, Singleton}
import models.TakeoverDetails
import play.api
import play.api.{Configuration, Logger}
import reactivemongo.api.indexes.IndexType
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.TakeoverDetailsService
import services.admin.AdminService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration



class Startup @Inject()(appStartupJobs: AppStartupJobs, actorSystem: ActorSystem) {
  actorSystem.scheduler.scheduleOnce(FiniteDuration(1,TimeUnit.MINUTES))(appStartupJobs.runEverythingOnStartUp)
}

class AppStartupJobsImpl @Inject()(val config: Configuration,
                                val service: AdminService,
                                   val takeoverDetailsService: TakeoverDetailsService,
val ctRepo: CorporationTaxRegistrationMongoRepository) extends AppStartupJobs {

}
  trait AppStartupJobs {

    val config: Configuration
    val service: AdminService
    val takeoverDetailsService: TakeoverDetailsService
    val ctRepo: CorporationTaxRegistrationMongoRepository

    def startupStats: Future[Unit] = {
      ctRepo.getRegistrationStats map {
        stats => Logger.info(s"[RegStats] $stats")
      }
    }

 def lockedRegIds: Future[Unit] = {
   ctRepo.retrieveLockedRegIDs() map { regIds =>
     val message = regIds.map(rid => s" RegId: $rid")
     Logger.info(s"RegIds with locked status:$message")
   }
 }

  def getCTCompanyName(rid: String) : Future[Unit] = {
    ctRepo.retrieveMultipleCorporationTaxRegistration(rid) map {
      list => list foreach { ctDoc =>
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
            case s:Seq[(String, IndexType)] if s.nonEmpty => s"$s\n "
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
      ctRepo.findBySelector(ctRepo.regIDSelector(regId)).map {
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
      ctRepo.findBySelector(ctRepo.ackRefSelector(ackRef)).map {
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


    def updateTakeoverData(regIds: List[String]): Future[Seq[TakeoverDetails]] = {
      Future.traverse(regIds){
        regId =>
          Logger.info(s" $regId has had its takeover section rectified to false")
          takeoverDetailsService.updateTakeoverDetailsBlock(regId, TakeoverDetails(replacingAnotherBusiness = false, None, None, None, None))
      }
    }


    def runEverythingOnStartUp = {
      Logger.info("Running Startup Jobs")
      lazy val regid = config.getString("companyNameRegID").getOrElse("")
      getCTCompanyName(regid)

      lazy val base64RegIds = config.getString("list-of-regids").getOrElse("")
      lazy val listOftxIDs = new String(Base64.getDecoder.decode(base64RegIds), "UTF-8").split(",").toList
      fetchDocInfoByRegId(listOftxIDs)

      lazy val base64ackRefs = config.getString("list-of-ackrefs").getOrElse("")
      lazy val listOfackRefs = new String(Base64.getDecoder.decode(base64ackRefs), "UTF-8").split(",").toList
      fetchByAckRef(listOfackRefs)

      lazy val base64TakeoverRegIds = config.getString("list-of-takeover-regids").getOrElse("")
      lazy val listOfTakeoverRegIds = new String(Base64.getDecoder.decode(base64TakeoverRegIds), "UTF-8").split(",").toList
      updateTakeoverData(listOfTakeoverRegIds)

      fetchIndexes()
      startupStats
      lockedRegIds

    }


}