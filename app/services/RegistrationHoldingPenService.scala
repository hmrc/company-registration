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
import play.api.libs.json.{JsObject, Json}
import repositories.{Repositories, StateDataRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

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
            "companyActiveDate" -> dates.companyActiveDate.toString.substring(0,10),
            "startDateOfFirstAccountingPeriod" -> dates.startDateOfFirstAccountingPeriod.toString.substring(0,10),
            "intendedAccountsPreparationDate" -> dates.intendedAccountsPreparationDate.toString.substring(0,10)
          )//TODO This needs to look cleaner - SubmissionDates used format yyyy-MM-dd
        )
      )
  }

  //TODO This needs tests
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
}
