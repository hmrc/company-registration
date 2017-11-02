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

package services

import java.time.LocalTime
import java.util.Base64

import audit._
import config.MicroserviceAuditConnector
import connectors._
import helpers.DateHelper
import models._
import uk.gov.hmrc.play.http.{HttpResponse => HmrcHttpResponse}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import repositories._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpErrorFunctions}

import utils.{DateCalculators, SCRSFeatureSwitches}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object RegistrationHoldingPenService
  extends RegistrationHoldingPenService with ServicesConfig {
  override val desConnector = DesConnector
  override val incorporationCheckAPIConnector = IncorporationCheckAPIConnector
  override val stateDataRepository = Repositories.stateDataRepository
  override val ctRepository = Repositories.cTRepository
  override val heldRepo = Repositories.heldSubmissionRepository
  override val accountingService = AccountingDetailsService
  override val brConnector = BusinessRegistrationConnector
  override val auditConnector = MicroserviceAuditConnector
  override val microserviceAuthConnector = AuthConnector
  val sendEmailService = SendEmailService

  val addressLine4FixRegID = getConfString("address-line-4-fix.regId", throw new Exception("could not find config key address-line-4-fix.regId"))
  val amendedAddressLine4 = getConfString("address-line-4-fix.address-line-4", throw new Exception("could not find config key address-line-4-fix.address-line-4"))
  val blockageLoggingDay = getConfString("check-submission-job.schedule.blockage-logging-day", throw new RuntimeException(s"Could not find config schedule.blockage-logging-day"))
  val blockageLoggingTime = getConfString("check-submission-job.schedule.blockage-logging-time", throw new RuntimeException(s"Could not find config schedule.blockage-logging-time"))
}

private[services] class InvalidSubmission(val message: String) extends NoStackTrace
private[services] case class DesError(message: String) extends NoStackTrace
private[services] class MissingAckRef(val message: String) extends NoStackTrace
private[services] class UnexpectedStatus(val status: String) extends NoStackTrace

private[services] object FailedToUpdateSubmissionWithAcceptedIncorp extends NoStackTrace
private[services] object FailedToUpdateSubmissionWithRejectedIncorp extends NoStackTrace
private[services] object FailedToDeleteSubmissionData extends NoStackTrace

trait RegistrationHoldingPenService extends DateHelper with HttpErrorFunctions {

  val desConnector: DesConnector
  val stateDataRepository: StateDataRepository
  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector
  val ctRepository: CorporationTaxRegistrationRepository
  val heldRepo: HeldSubmissionRepository
  val accountingService: AccountingDetailsService
  val brConnector: BusinessRegistrationConnector
  val auditConnector: AuditConnector
  val microserviceAuthConnector: AuthConnector
  val sendEmailService: SendEmailService

  val addressLine4FixRegID: String
  val amendedAddressLine4: String
  val blockageLoggingDay: String
  val blockageLoggingTime: String

  class FailedToRetrieveByTxId(val transId: String) extends NoStackTrace

  private[services] class FailedToRetrieveByAckRef extends NoStackTrace

  private[services] class MissingAccountingDates extends NoStackTrace

  def updateNextSubmissionByTimepoint(implicit hc: HeaderCarrier): Future[String] = {
    fetchIncorpUpdate flatMap { items =>
      val results = items map { item =>
        processIncorporationUpdate(item)
      }
      Future.sequence(results) flatMap { _ =>
        items.headOption match {
          case Some(head) => stateDataRepository.updateTimepoint(head.timepoint).map(tp => s"Incorporation ${head.status} - Timepoint updated to $tp")
          case None => Future.successful("No Incorporation updates were fetched")
        }
      }
    }
  }

  def updateIncorp(incorpStatus: IncorpStatus, isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val incorpUpdate = incorpStatus.toIncorpUpdate
    processIncorporationUpdate(incorpUpdate, isAdmin)
  }


  def deleteRejectedSubmissionData(regId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    for {
      ctDeleted <- ctRepository.removeTaxRegistrationById(regId)
      metadataDeleted <- brConnector.removeMetadata(regId)
    } yield {
      if (ctDeleted && metadataDeleted) true else throw FailedToDeleteSubmissionData
    }
  }

  private[services] def processIncorporationUpdate(item: IncorpUpdate, isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    item.status match {
      case "accepted" =>
        for {
          ctReg <- fetchRegistrationByTxId(item.transactionId)
          _ <- ctReg.verifiedEmail.fold(Future.successful(false)) { email => sendEmailService.sendVATEmail(email.address) }
          result <- updateSubmissionWithIncorporation(item, ctReg, isAdmin)
        } yield {
          if (result) result else throw FailedToUpdateSubmissionWithAcceptedIncorp
        }
      case "rejected" =>
        val reason = item.statusDescription.fold("No reason given")(f => " Reason given:" + f)
        Logger.info("Incorporation rejected for Transaction: " + item.transactionId + reason)
        for {
          ctReg <- fetchRegistrationByTxId(item.transactionId)
          _ <- auditFailedIncorporation(item, ctReg)
          _ <- processIncorporationRejectionSubmission(item, ctReg, ctReg.registrationID, isAdmin)
          crRejected <- ctRepository.updateSubmissionStatus(ctReg.registrationID, "rejected")
        } yield {
          if (crRejected == "rejected") true else throw FailedToUpdateSubmissionWithRejectedIncorp
        }
    }
  }

  private[services] def updateSubmissionWithIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration, isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    import RegistrationStatus.{HELD, SUBMITTED}
    ctReg.status match {
      case HELD => updateHeldSubmission(item, ctReg, ctReg.registrationID, isAdmin)
      case SUBMITTED => updateSubmittedSubmission(ctReg)
      case unknown => updateOtherSubmission(ctReg.registrationID, item.transactionId, unknown)
    }
  }

  private[services] def updateHeldSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, journeyId: String, isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    getAckRef(ctReg) match {
      case Some(ackRef) =>
        val fResponse = for {
          heldData <- fetchHeldData(ackRef)
          submission <- constructSubmission(item, ctReg, ackRef, heldData)
          _ <- processAcceptedSubmission(item, ackRef, ctReg, submission, heldData, isAdmin)
        } yield {
          submission
        }
        fResponse flatMap { auditDetail =>
          processSuccessDesResponse(item, ctReg, auditDetail, isAdmin)
        } recover {
          case e =>
            Logger.error(s"""Submission to DES failed for ack ref ${ackRef}. Corresponding RegID: $journeyId and Transaction ID: ${item.transactionId}""")
            throw e
        }
      case None => processMissingAckRefForTxID(item.transactionId)
    }
  }

  private[services] def processAcceptedSubmission(item: IncorpUpdate, ackRef: String, ctReg: CorporationTaxRegistration, submission: JsObject,
                                                  heldData: Option[HeldSubmission], isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    heldData match {
      case Some(_) => for {
        _ <- postSubmissionToDes(ackRef, submission, ctReg.registrationID, isAdmin, heldData, item, ctReg)
        _  = auditDesSubmission(ctReg.registrationID, submission, isAdmin)
        _ <- heldRepo.removeHeldDocument(ctReg.registrationID)
      } yield true
      case None => for {
        _ <- postSubmissionToDes(ackRef, submission, ctReg.registrationID, isAdmin, heldData, item, ctReg)
      } yield true
    }
  }

  private[services] def processRejectedSubmission(item: IncorpUpdate, ackRef: String, ctReg: CorporationTaxRegistration, submission: Option[JsObject],
                                                  heldData: Option[HeldSubmission], isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    submission match {
      case Some(rej_sub) =>
        postSubmissionToDes(ackRef, rej_sub, ctReg.registrationID, isAdmin, heldData, item, ctReg) map (_ => true)
      case None => heldRepo.removeHeldDocument(ctReg.registrationID)
    }
  }

  private[services] def processIncorporationRejectionSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration,
                                                                journeyId: String, isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    getAckRef(ctReg) match {
      case Some(ackRef) =>
        for {
          heldData <- fetchHeldData(ackRef)
          topUpRejectedSubmission <- constructRejectedSubmission(item, ctReg, ackRef, heldData)
          processed <- processRejectedSubmission(item, ackRef, ctReg, topUpRejectedSubmission, heldData, isAdmin)
        } yield {
          processed
        }
      case None => processMissingAckRefForTxID(item.transactionId)
    }
  }

  private[services] def processSuccessDesResponse(item: IncorpUpdate, ctReg: CorporationTaxRegistration, auditDetail: JsObject, isAdmin: Boolean = false)
                                                 (implicit hc: HeaderCarrier): Future[Boolean] = {
    for {
    //_ <- auditDesSubmission(ctReg.registrationID, auditDetail, isAdmin)
      updated <- ctRepository.updateHeldToSubmitted(ctReg.registrationID, item.crn.get, formatTimestamp(now))
    //deleted <- heldRepo.removeHeldDocument(ctReg.registrationID)
    } yield {
      updated //&& deleted
    }
  }

  private[services] def auditDesSubmission(rID: String, jsSubmission: JsObject, isAdmin: Boolean = false)(implicit hc: HeaderCarrier) = {
    val event = new DesSubmissionEvent(DesSubmissionAuditEventDetail(rID, jsSubmission), isAdmin)
    auditConnector.sendEvent(event)
  }

  private[services] def auditSuccessfulIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration)(implicit hc: HeaderCarrier) = {
    val event = new SuccessfulIncorporationAuditEvent(
      SuccessfulIncorporationAuditEventDetail(
        ctReg.registrationID,
        item.crn.get,
        item.incorpDate.get
      ),
      "successIncorpInformation",
      "successIncorpInformation"
    )
    auditConnector.sendEvent(event)
  }

  private[services] def auditSuccessfulTopUp(submission: JsObject, item: IncorpUpdate, ctReg: CorporationTaxRegistration)(implicit hc: HeaderCarrier) = {
    item.incorpDate match {
      case Some(date) => val submissionDates = calculateDates(item, ctReg.accountingDetails, ctReg.accountsPreparation)
                         submissionDates flatMap { dates =>
          val event = new DesTopUpSubmissionEvent(
          DesTopUpSubmissionEventDetail(
            ctReg.registrationID,
            ctReg.confirmationReferences.get.acknowledgementReference,
            "Accepted",
            Some(dates.intendedAccountsPreparationDate),
            Some(dates.startDateOfFirstAccountingPeriod),
            Some(dates.companyActiveDate),
            item.crn
          ),
          "ctRegistrationAdditionalData",
          "ctRegistrationAdditionalData"
        )
          auditConnector.sendEvent(event)
        }
      case None =>  val event = new DesTopUpSubmissionEvent(
        DesTopUpSubmissionEventDetail(
          ctReg.registrationID,
          ctReg.confirmationReferences.get.acknowledgementReference,
          "Rejected",
          None,
          None,
          None,
          None
        ),
        "ctRegistrationAdditionalData",
        "ctRegistrationAdditionalData"
      )
        auditConnector.sendEvent(event)
    }
    }




   private[services] def auditFailedIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration)(implicit hc: HeaderCarrier) = {
    val event = new FailedIncorporationAuditEvent(
      FailedIncorporationAuditEventDetail(
        ctReg.registrationID,
        item.statusDescription.getOrElse("No reason provided")
      ),
      "failedIncorpInformation",
      "failedIncorpInformation"
    )

    auditConnector.sendEvent(event)
  }

  private def processMissingAckRefForTxID(txID: String) = {
    val errMsg = s"""Held Registration doc is missing the ack ref for tx_id "$txID"."""
    Logger.error(errMsg)
    Future.failed(new MissingAckRef(errMsg))
  }

  private[services] def updateSubmittedSubmission(ctReg: CorporationTaxRegistration): Future[Boolean] = {
    heldRepo.removeHeldDocument(ctReg.registrationID)
  }

  private def updateOtherSubmission(regId: String, txId: String, status: String) = {
    Logger.error(s"""Tried to process a submission (${regId}/${txId}) with an unexpected status of "${status}" """)
    Future.failed(new UnexpectedStatus(status))
  }

  def constructSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, ackRef: String, heldData: Option[HeldSubmission]): Future[JsObject] = {
    heldData match {
      case Some(heldData) => constructFullSubmission(item, ctReg, ackRef, heldData)
      case None => constructTopUpSubmission(item, ctReg, ackRef)
    }
  }

  def constructRejectedSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, ackRef: String, heldData: Option[HeldSubmission]): Future[Option[JsObject]] = {

    heldData match {
      case Some(heldData) => Future.successful(None)
      case None => Future.successful(Some(buildTopUpRejectionSubmission(item.status, ackRef)))

    }
  }

  private def constructFullSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, ackRef: String, heldData: HeldSubmission): Future[JsObject] = {
    for {
      dates <- calculateDates(item, ctReg.accountingDetails, ctReg.accountsPreparation)
    } yield {
      appendDataToSubmission(item.crn.get, dates, heldData.submission)
    }
  }


  private def constructTopUpSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, ackRef: String): Future[JsObject] = {
    for {
      dates <- calculateDates(item, ctReg.accountingDetails, ctReg.accountsPreparation)
    } yield {
      buildTopUpSubmission(item.crn.get, item.status, dates, ackRef)
    }
  }

  private def getAckRef(reg: CorporationTaxRegistration): Option[String] = {
    reg.confirmationReferences map (_.acknowledgementReference)
  }

  private[services] def fetchIncorpUpdate(): Future[Seq[IncorpUpdate]] = {
    val hc = new HeaderCarrier()
    for {
      timepoint <- stateDataRepository.retrieveTimePoint
      submission <- incorporationCheckAPIConnector.checkSubmission(timepoint)(hc)
    } yield {
      submission.items
    }
  }

  private[services] def fetchRegistrationByTxId(transId: String): Future[CorporationTaxRegistration] = {
    Logger.debug(s"""Got tx_id "${transId}" """)
    ctRepository.retrieveRegistrationByTransactionID(transId) flatMap {
      case Some(s) => Future.successful(s)
      case None =>
        if (inWorkingHours) {
          Logger.error("INCORP_BLOCKAGE")
        }
        Logger.error(s"BLOCKAGE :  We have an incorp blockage with txid ${transId}")
        Future.failed(throw new FailedToRetrieveByTxId(transId))
    }
  }

  def inWorkingHours: Boolean = {
    DateCalculators.loggingDay(blockageLoggingDay, DateCalculators.getTheDay(DateTime.now)) &&
      DateCalculators.loggingTime(blockageLoggingTime, LocalTime.now)
  }

  private[services] def activeDate(date: AccountingDetails, incorpDate: DateTime) = {
    import AccountingDetails.WHEN_REGISTERED

    (date.status, date.activeDate) match {
      case (_, Some(givenDate)) if asDate(givenDate).isBefore(incorpDate) => ActiveOnIncorporation
      case (_, Some(givenDate)) => ActiveInFuture(asDate(givenDate))
      case (status, _) if status == WHEN_REGISTERED => ActiveOnIncorporation
      case _ => DoNotIntendToTrade
    }
  }

  private[services] def fetchHeldData(ackRef: String): Future[Option[HeldSubmission]] = {
    heldRepo.retrieveSubmissionByAckRef(ackRef) flatMap {
      case Some(held) => Future.successful(Some(held.copy(submission = addressLine4Fix(held.regId, held.submission))))
      case None => Future.successful(None)
    }
  }

  private[services] def addressLine4Fix(regId: String, held: JsObject): JsObject = {
    if (regId == addressLine4FixRegID) {
      val decodedAddressLine4 = new String(Base64.getDecoder.decode(amendedAddressLine4), "UTF-8")
      held.deepMerge(
        Json.obj("registration" ->
          Json.obj("corporationTax" ->
            Json.obj("businessAddress" ->
              Json.obj("line4" -> decodedAddressLine4.mkString)))))
    } else {
      held
    }
  }

  def fetchHeldDataTime(regId: String): Future[Option[DateTime]] = {
    heldRepo.retrieveHeldSubmissionTime(regId)
  }

  private[services] def calculateDates(item: IncorpUpdate,
                                       accountingDetails: Option[AccountingDetails],
                                       accountsPreparation: Option[AccountPrepDetails]): Future[SubmissionDates] = {

    item.incorpDate.fold(Logger.error(s"[IncorpUpdate] - Incorp update for transaction-id : ${item.transactionId} does not contain an incorp date - IncorpUpdate : $item")) (_ => ())

    accountingDetails map { details =>
      val prepDate = accountsPreparation flatMap (_.endDate)
      accountingService.calculateSubmissionDates(item.incorpDate.get, activeDate(details, item.incorpDate.get), prepDate)
    } match {
      case Some(dates) => Future.successful(dates)
      case None => Future.failed(new MissingAccountingDates)
    }
  }

  private[services] def appendDataToSubmission(crn: String, dates: SubmissionDates, partialSubmission: JsObject): JsObject = {
    partialSubmission deepMerge
      Json.obj("registration" ->
        Json.obj("corporationTax" ->
          Json.obj(
            "crn" -> crn,
            "companyActiveDate" ->
              formatDate(
                dates.companyActiveDate),
            "startDateOfFirstAccountingPeriod" -> formatDate(dates.startDateOfFirstAccountingPeriod),
            "intendedAccountsPreparationDate" -> formatDate(dates.intendedAccountsPreparationDate)
          )
        )
      )
  }

  private[services] def buildTopUpSubmission(crn: String, status: String, dates: SubmissionDates, ackRef: String): JsObject = {

    Json.obj(
      "status" -> "Accepted",
      "acknowledgementReference" -> ackRef) ++
      Json.obj("corporationTax" ->
        Json.obj(
          "crn" -> crn,
          "companyActiveDate" ->
            formatDate(
              dates.companyActiveDate),
          "startDateOfFirstAccountingPeriod" -> formatDate(dates.startDateOfFirstAccountingPeriod),
          "intendedAccountsPreparationDate" -> formatDate(dates.intendedAccountsPreparationDate)
        )
      )
  }

  private[services] def buildTopUpRejectionSubmission(status: String, ackRef: String): JsObject = {

    Json.obj(
      "status" -> "Rejected",
      "acknowledgementReference" -> ackRef)

  }

  private[services] def postSubmissionToDes(ackRef: String, submission: JsObject, journeyId: String, isAdmin: Boolean = false,
                                            hData: Option[HeldSubmission], item: IncorpUpdate, ctReg: CorporationTaxRegistration)
                                           (implicit hc: HeaderCarrier): Future[HmrcHttpResponse] = {

    hData match {
      case Some(_) => {
        for {
          response <- desConnector.ctSubmission(ackRef, submission, journeyId, isAdmin)
        } yield {
          auditSuccessfulIncorporation(item, ctReg)
          response
        }
      }
      case None => {
        for {
          response <- desConnector.topUpCTSubmission(ackRef, submission, journeyId, isAdmin)
          _ = auditSuccessfulTopUp(submission, item, ctReg)
        } yield {
          response
        }
      }
    }
  }
}
