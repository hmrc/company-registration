/*
 * Copyright 2016 HM Revenue & Customs
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

import connectors._
import helpers.DateHelper
import models._
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object RegistrationHoldingPenService extends RegistrationHoldingPenService {

  //$COVERAGE-OFF$
  override val desConnector = DesConnector
  override val incorporationCheckAPIConnector = IncorporationCheckAPIConnector
  //$COVERAGE-ON$
  override val stateDataRepository = Repositories.stateDataRepository
  override val ctRepository = Repositories.cTRepository
  override val heldRepo = Repositories.heldSubmissionRepository
  override val accountingService = AccountingDetailsService
  val brConnector = BusinessRegistrationConnector

}

private[services] class InvalidSubmission(val message: String) extends NoStackTrace
private[services] class MissingAckRef(val message: String) extends NoStackTrace
private[services] class UnexpectedStatus(val status: String) extends NoStackTrace

trait RegistrationHoldingPenService extends DateHelper {

  val desConnector : DesConnector
  val stateDataRepository: StateDataRepository
  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector
  val ctRepository : CorporationTaxRegistrationRepository
  val heldRepo : HeldSubmissionRepository
  val accountingService : AccountingDetailsService
  val brConnector: BusinessRegistrationConnector

  private[services] class FailedToRetrieveByTxId(transId: String)  extends NoStackTrace
  private[services] class FailedToRetrieveByAckRef extends NoStackTrace
  private[services] class MissingAccountingDates extends NoStackTrace

  def updateNextSubmissionByTimepoint(implicit hc: HeaderCarrier): Future[String] = {
    fetchIncorpUpdate flatMap { items =>
      val results = items map { item =>
        //TODO see SCRS-3766
        processIncorporationUpdate(item)
      }
      Future.sequence(results) flatMap { r =>
        //TODO For day one, take the first timepoint - see SCRS-3766
        items.headOption match {
          case Some(head) => stateDataRepository.updateTimepoint(head.timepoint)
          case None => Future.successful("")
        }
      }
    }
  }

  private[services] def processIncorporationUpdate(item : IncorpUpdate)(implicit hc: HeaderCarrier): Future[Boolean] = {
    item.status match {
      case "accepted" => updateSubmission(item)
      case "rejected" =>
        val reason = item.statusDescription.fold("")(f => " Reason given:" + f)
        Logger.info("Incorporation rejected for Transaction: " + item.transactionId + reason)
        for{
          ctReg <- fetchRegistrationByTxId(item.transactionId)
          heldDeleted <- heldRepo.removeHeldDocument(ctReg.registrationID)
          ctDeleted <- ctRepository.removeTaxRegistrationById(ctReg.registrationID)
          metadataDeleted <- brConnector.removeMetadata(ctReg.registrationID)
        } yield {
          heldDeleted && ctDeleted && metadataDeleted
        }
    }
  }

  private[services] def updateSubmission(item: IncorpUpdate): Future[Boolean] = {
    Logger.debug(s"""Got tx_id "${item.transactionId}" """)
    fetchRegistrationByTxId(item.transactionId) flatMap { ctReg =>
      import RegistrationStatus.{HELD,SUBMITTED}
      ctReg.status match {
        case HELD => updateHeldSubmission(item, ctReg, ctReg.registrationID)
        case SUBMITTED => updateSubmittedSubmission(ctReg)
        case unknown => updateOtherSubmission(ctReg.registrationID, item.transactionId, unknown)
      }
    }
  }

  private[services] def updateHeldSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, journeyId : String): Future[Boolean] = {
    getAckRef(ctReg) match {
      case Some(ackRef) => {
        val fResponse = for {
          submission <- constructFullSubmission(item, ctReg, ackRef)
          response <- postSubmissionToDes(ackRef, submission, journeyId)
        } yield {
          response
        }
        fResponse flatMap {
          case SuccessDesResponse(response) => processSuccessDesResponse(item, ctReg)
          case InvalidDesRequest(message) => processInvalidDesRequest(ackRef, message)
          case NotFoundDesResponse => processNotFoundDesResponse(ackRef)
        }
      }
      case None => {
        val errMsg = s"""Held Registration doc is missing the ack ref for tx_id "${item.transactionId}"."""
        Logger.error(errMsg)
        Future.failed(new MissingAckRef(errMsg))
      }
    }
  }

  private def processSuccessDesResponse(item: IncorpUpdate, ctReg: CorporationTaxRegistration): Future[Boolean] = {
    for {
      updated <- ctRepository.updateHeldToSubmitted(ctReg.registrationID, item.crn, formatTimestamp(now))
      deleted <- heldRepo.removeHeldDocument(ctReg.registrationID)
    } yield {
      updated && deleted
    }
  }

  private def processInvalidDesRequest(ackRef: String, message: String) = {
    val errMsg = s"""Submission to DES failed for ack ref ${ackRef} - Reason: "${message}"."""
    Logger.error(errMsg)
    Future.failed(new InvalidSubmission(errMsg))
  }

  private def processNotFoundDesResponse(ackRef: String) = {
    val errMsg = s"""Request sent to DES for ack ref ${ackRef} not found" """
    Logger.error(errMsg)
    Future.failed(new InvalidSubmission(errMsg))
  }

  private def updateSubmittedSubmission(ctReg: CorporationTaxRegistration): Future[Boolean] = {
    heldRepo.removeHeldDocument(ctReg.registrationID)
  }

  private def updateOtherSubmission(regId: String, txId: String, status: String) = {
    Logger.error(s"""Tried to process a submission (${regId}/${txId}) with an unexpected status of "${status}" """)
    Future.failed(new UnexpectedStatus(status))
  }

  private def constructFullSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, ackRef: String): Future[JsObject] = {
    for {
      heldData <- fetchHeldData(ackRef)
      dates <- calculateDates(item, ctReg.accountingDetails, ctReg.accountsPreparation)
    } yield {
      appendDataToSubmission(item.crn, dates, heldData.submission)
    }
  }

  private def getAckRef(reg: CorporationTaxRegistration): Option[String] = {
    reg.confirmationReferences map (_.acknowledgementReference )
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

  private[services] def fetchRegistrationByTxId(transId : String): Future[CorporationTaxRegistration] = {
    Logger.debug(s"""Got tx_id "${transId}" """)
    ctRepository.retrieveRegistrationByTransactionID(transId) flatMap {
      case Some(s) => Future.successful(s)
      case None => Future.failed(new FailedToRetrieveByTxId(transId))
    }
  }

  private[services] def activeDate(date: AccountingDetails) = {
    import AccountingDetails.WHEN_REGISTERED
    (date.status, date.activeDate) match {
      case (_, Some(givenDate))  => ActiveInFuture(asDate(givenDate))
      case (status, _) if status == WHEN_REGISTERED => ActiveOnIncorporation
      case _ => DoNotIntendToTrade
    }
  }

  private[services] def fetchHeldData(ackRef: String) = {
    heldRepo.retrieveSubmissionByAckRef(ackRef) flatMap {
      case Some(held) => Future.successful(held)
      case None => Future.failed(new FailedToRetrieveByAckRef)
    }
  }

  private[services] def calculateDates(item: IncorpUpdate,
  accountingDetails: Option[AccountingDetails],
  accountsPreparation: Option[AccountPrepDetails]): Future[SubmissionDates] = {

    accountingDetails map { details =>
      val prepDate = accountsPreparation flatMap (_.endDate)
      accountingService.calculateSubmissionDates(item.incorpDate, activeDate(details), prepDate)
    } match {
      case Some(dates) => Future.successful(dates)
      case None => Future.failed(new MissingAccountingDates)
    }
  }

  private[services] def appendDataToSubmission(crn: String, dates: SubmissionDates, partialSubmission: JsObject) : JsObject = {
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

  private[services] def postSubmissionToDes(ackRef: String, submission: JsObject, journeyId : String) = {
    val hc = new HeaderCarrier()
    desConnector.ctSubmission(ackRef, submission, journeyId)(hc)
  }
}
