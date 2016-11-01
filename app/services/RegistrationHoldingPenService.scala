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
import models.{SubmissionCheckResponse, SubmissionDates}
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}
import repositories.{Repositories, StateDataRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object RegistrationHoldingPenService extends RegistrationHoldingPenService {
  override val stateDataRepository = Repositories.stateDataRepository
  override val incorporationCheckAPIConnector = IncorporationCheckAPIConnector
}

trait RegistrationHoldingPenService {

  val stateDataRepository: StateDataRepository
  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector

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

  private[services] def fetchSubmission(implicit hc: HeaderCarrier) = {
    for {
      timepoint <- stateDataRepository.retrieveTimePoint
      submission <- incorporationCheckAPIConnector.checkSubmission(timepoint)
    } yield {
      submission
    }
  }

  //TODO This needs tests - use fetchSubmission to retrieve the submission
  private[services] def checkSubmission(implicit hc: HeaderCarrier) = {
//    stateDataRepository.retrieveTimePoint
//      .flatMap {
//        timepoint => submissionCheckAPIConnector.checkSubmission(timepoint)
//      }
  }

//  //TODO This needs tests
//  private[services] def processSubmission(submission : SubmissionCheckResponse) = {
//    retrieveSubmissionStatus(submission.transactionId)
//
//    //TODO This should construct the full submission and send it to DES
//    //TODO This needs to delete submission from holding pen and update status to 'Submitted'
//  }

  //TODO This needs tests
  def checkAndProcessSubmission(implicit hc: HeaderCarrier) = {
    val status = checkSubmission

//    status map {
//      response => processSubmission(response)
//    }
  }

  private[services] def formatDate(date: DateTime): String = {
    date.toString("yyyy-MM-dd")
  }
}
