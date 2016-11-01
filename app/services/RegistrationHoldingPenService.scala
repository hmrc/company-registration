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

import java.text.SimpleDateFormat
import java.util.Date

import connectors.{AuthConnector, BusinessRegistrationConnector, BusinessRegistrationSuccessResponse}
import models.des._
import models.{BusinessRegistration, RegistrationStatus}
import play.api.libs.json.{JsObject, Json}
import repositories.HeldSubmissionRepository
import connectors.IncorporationCheckAPIConnector
import models.{ConfirmationReferences, CorporationTaxRegistration, SubmissionCheckResponse}
import org.joda.time.{DateTime, DateTimeZone}
import repositories.{CorporationTaxRegistrationRepository, Repositories, SequenceRepository, StateDataRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object RegistrationHoldingPenService extends RegistrationHoldingPenService {
  override val corporationTaxRegistrationRepository = Repositories.cTRepository
  override val sequenceRepository = Repositories.sequenceRepository
  override val stateDataRepository = Repositories.stateDataRepository
  override val microserviceAuthConnector = AuthConnector
  override val brConnector = BusinessRegistrationConnector
  val heldSubmissionRepository = Repositories.heldSubmissionRepository
  def currentDateTime = DateTime.now(DateTimeZone.UTC)
  override val submissionCheckAPIConnector = IncorporationCheckAPIConnector
}

trait RegistrationHoldingPenService {

  val corporationTaxRegistrationRepository: CorporationTaxRegistrationRepository
  val sequenceRepository: SequenceRepository
  val stateDataRepository: StateDataRepository
  val microserviceAuthConnector : AuthConnector
  val brConnector : BusinessRegistrationConnector
  val heldSubmissionRepository: HeldSubmissionRepository
  def currentDateTime: DateTime
  val submissionCheckAPIConnector: IncorporationCheckAPIConnector

  private[services] def retrieveSubmissionStatus(rID: String): Future[String] = {
    corporationTaxRegistrationRepository.retrieveRegistrationByTransactionID(rID) map {
      case Some(reg) => reg.status
    }
  }

  //TODO This needs tests
  private[services] def checkSubmission(implicit hc: HeaderCarrier) : Future[SubmissionCheckResponse] = {
    stateDataRepository.retrieveTimePoint
      .flatMap {
        timepoint => submissionCheckAPIConnector.checkSubmission(timepoint)
      }
  }

  //TODO This needs tests
  private[services] def processSubmission(submission : SubmissionCheckResponse) = {
    retrieveSubmissionStatus(submission.transactionId)



    //TODO This should construct the full submission and send it to DES
    //TODO This needs to delete submission from holding pen and update status to 'Submitted'
  }

  //TODO This needs tests
  def checkAndProcessSubmission(implicit hc: HeaderCarrier) = {
    val status = checkSubmission

    status map {
      response => processSubmission(response)
    }
  }
}
