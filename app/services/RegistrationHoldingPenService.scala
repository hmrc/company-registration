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

import connectors.IncorporationCheckAPIConnector
import models.{CorporationTaxRegistration, SubmissionDates}
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object RegistrationHoldingPenService extends RegistrationHoldingPenService {
  override val stateDataRepository = Repositories.stateDataRepository
  override val ctRepository = Repositories.cTRepository
  override val incorporationCheckAPIConnector = IncorporationCheckAPIConnector
  override val heldRepo = Repositories.heldSubmissionRepository
  override val accountingService = AccountingDetailsService
}

trait RegistrationHoldingPenService {

  val stateDataRepository: StateDataRepository
  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector
  val ctRepository : CorporationTaxRegistrationRepository
  val heldRepo : HeldSubmissionRepository
  val accountingService : AccountingDetailsService

//  private[services] def retrieveSubmissionStatus(rID: String): Future[String] = {
//    corporationTaxRegistrationRepository.retrieveRegistrationByTransactionID(rID) map {
//      case Some(reg) => reg.status
//    }
//  }

  private[services] def appendDataToSubmission(crn: String, dates: SubmissionDates, partialSubmission: JsObject) : JsObject = {
    partialSubmission deepMerge
      Json.obj("registration" ->
        Json.obj("corporationTax" ->
          Json.obj(
            "crn" -> crn,
            "companyActiveDate" -> formatDate(dates.companyActiveDate),
            "startDateOfFirstAccountingPeriod" -> formatDate(dates.startDateOfFirstAccountingPeriod),
            "intendedAccountsPreparationDate" -> formatDate(dates.intendedAccountsPreparationDate)
          )
        )
      )
  }

  private[services] def fetchIncorpUpdate(implicit hc: HeaderCarrier) = {
    for {
      timepoint <- stateDataRepository.retrieveTimePoint
      submission <- incorporationCheckAPIConnector.checkSubmission(timepoint)
    } yield {
      submission.items.head
    }
  }

  private[services] class FailedToRetrieveByTxId  extends NoStackTrace

  private[services] def fetchRegistrationByTxId(transId : String): Future[CorporationTaxRegistration] = {
    ctRepository.retrieveRegistrationByTransactionID(transId) flatMap {
      case Some(s) => Future.successful(s)
      case None => Future.failed(new FailedToRetrieveByTxId)
    }
  }

  private[services] class FailedToRetrieveByAckRef extends NoStackTrace

  private[services] def fetchHeldData(ackRef : String) = {
    heldRepo.retrieveSubmissionByAckRef(ackRef) flatMap {
      case Some(held) => Future.successful(held)
      case None => Future.failed(new FailedToRetrieveByAckRef)
    }
  }

  private[services] def updateSubmission(implicit hc: HeaderCarrier): Future[JsObject] = {
    fetchIncorpUpdate map {
      item => fetchRegistrationByTxId(item.transactionId) map {
        i => {
          val refs = i.confirmationReferences
//          appendDataToSubmission(
//            item.crn,
//            //TODO Need a function to generate values for calculateSubmissionDates from stored data
//            accountingService.calculateSubmissionDates(DateTime.parse(item.incorpDate), i.accountingDetails, i.accountsPreparation),
//            fetchHeldData(refs.getOrElse("acknowledgementReference"))
//
//          )

        }
      }
    }
    ???
  }

  def checkAndProcessSubmission(implicit hc: HeaderCarrier) = {
    val heldData : JsObject = ???
    val incorporationStatus = fetchIncorpUpdate

    incorporationStatus map {
      response => appendDataToSubmission("", ???, heldData)
    }
  }

  private[services] def formatDate(date: DateTime): String = {
    date.toString("yyyy-MM-dd")
  }
}
