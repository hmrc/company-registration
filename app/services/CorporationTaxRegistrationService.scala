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
import java.util.{Date, TimeZone}

import config.MicroserviceAuthConnector
import connectors.{AuthConnector, BusinessRegistrationConnector, BusinessRegistrationSuccessResponse}
import models.des._
import models.{BusinessRegistration, ConfirmationReferences, CorporationTaxRegistration}
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}
import repositories.{HeldSubmissionRepository, CorporationTaxRegistrationRepository, Repositories, SequenceRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object CorporationTaxRegistrationService extends CorporationTaxRegistrationService {
  override val corporationTaxRegistrationRepository = Repositories.cTRepository
  override val sequenceRepository = Repositories.sequenceRepository
  override val microserviceAuthConnector = AuthConnector
  override val brConnector = BusinessRegistrationConnector
  val heldSubmissionRepository = Repositories.heldSubmissionRepository
}

trait CorporationTaxRegistrationService {

  val corporationTaxRegistrationRepository: CorporationTaxRegistrationRepository
  val sequenceRepository: SequenceRepository
  val microserviceAuthConnector : AuthConnector
  val brConnector : BusinessRegistrationConnector
  val heldSubmissionRepository: HeldSubmissionRepository

  def createCorporationTaxRegistrationRecord(OID: String, registrationId: String, language: String): Future[CorporationTaxRegistration] = {
    val record = CorporationTaxRegistration(
      OID = OID,
      registrationID = registrationId,
      formCreationTimestamp = generateTimestamp(new DateTime()),
      language = language)

    corporationTaxRegistrationRepository.createCorporationTaxRegistration(record)
  }

  def retrieveCorporationTaxRegistrationRecord(rID: String): Future[Option[CorporationTaxRegistration]] = {
    corporationTaxRegistrationRepository.retrieveCorporationTaxRegistration(rID)
  }

  def updateConfirmationReferences(rID: String, refs : ConfirmationReferences)(implicit hc: HeaderCarrier) : Future[Option[ConfirmationReferences]] = {
    for{
      ackRef <- generateAcknowledgementReference
      updatedRef <- corporationTaxRegistrationRepository.updateConfirmationReferences(rID, refs.copy(acknowledgementReference = ackRef))
      heldSubmission <- buildPartialDesSubmission(rID, ackRef)
    } yield {
      //todo need to save submission into CR after build SCRS-2283
      heldSubmissionRepository.storePartialSubmission(rID, ackRef, Json.toJson(heldSubmission).as[JsObject])
      updatedRef
    }
  }

  def retrieveConfirmationReference(rID: String): Future[Option[ConfirmationReferences]] = {
    corporationTaxRegistrationRepository.retrieveConfirmationReference(rID)
  }

  private[services] def generateTimestamp(timeStamp: DateTime) : String = {
    val timeStampFormat = "yyyy-MM-dd'T'HH:mm:ssXXX"
    val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    val format: SimpleDateFormat = new SimpleDateFormat(timeStampFormat)
    format.setTimeZone(UTC)
    format.format(new Date(timeStamp.getMillis))
  }

  private def generateAcknowledgementReference: Future[String] = {
    val sequenceID = "AcknowledgementID"
    sequenceRepository.getNext(sequenceID)
      .map(ref => f"BRCT$ref%011d")
  }

  def buildPartialDesSubmission(regId: String, ackRef : String)(implicit hc: HeaderCarrier) : Future[InterimDesRegistration] = {

    // TODO - check behaviour if session header is missing
    val sessionId = hc.headers.collect{ case ("X-Session-ID", x) => x }.head

    for {
      credId <- retrieveCredId
      brMetadata <- retrieveBRMetadata(regId)
      ctData <- retrieveCTData(regId)
    } yield {
      buildInterimSubmission(ackRef, sessionId, credId, brMetadata, ctData, DateTime.now())
    }
  }

  private[services] class FailedToGetCredId extends NoStackTrace

  private[services] def retrieveCredId(implicit hc: HeaderCarrier) : Future[String] = {
    microserviceAuthConnector.getCurrentAuthority flatMap {
      case Some(a) => Future.successful(a.gatewayId)
      case _ => Future.failed(new FailedToGetCredId)
    }
  }

  private[services] class FailedToGetBRMetadata extends NoStackTrace

  private[services] def retrieveBRMetadata(regId: String)(implicit hc: HeaderCarrier) : Future[BusinessRegistration] = {
    brConnector.retrieveMetadata flatMap {
      case BusinessRegistrationSuccessResponse(metadata) if metadata.registrationID == regId => Future.successful(metadata)
      case _ => Future.failed(new FailedToGetBRMetadata)
    }
  }

  private[services] class FailedToGetCTData extends NoStackTrace

  private[services] def retrieveCTData(regId: String) : Future[CorporationTaxRegistration] = {
    corporationTaxRegistrationRepository.retrieveCorporationTaxRegistration(regId) flatMap {
      case Some(ct) => Future.successful(ct)
      case _ => Future.failed(new FailedToGetCTData)
    }
  }

  private[services] def buildInterimSubmission(ackRef: String, sessionId: String, credId: String,
                                               brMetadata: BusinessRegistration, ctData: CorporationTaxRegistration, currentDateTime: DateTime): InterimDesRegistration = {
    InterimDesRegistration(
      ackRef = ackRef,
      metadata = Metadata(
        sessionId = sessionId,
        credId = credId,
        language = brMetadata.language,
        submissionTs = DateTime.parse(generateTimestamp(currentDateTime)),
        completionCapacity = CompletionCapacity(brMetadata.completionCapacity)
      ),
      interimCorporationTax = InterimCorporationTax(
        companyName = ctData.companyDetails.get.companyName,
        returnsOnCT61 = false,
        businessAddress = BusinessAddress( //todo check if optional SCRS-2283
          line1 = "",
          line2 = "",
          line3 = None,
          line4 = None,
          postcode = None,
          country = None
        ),
        businessContactName = BusinessContactName(
          firstName = ctData.contactDetails.get.contactFirstName.get,
          middleNames = ctData.contactDetails.get.contactMiddleName,
          lastName = ctData.contactDetails.get.contactSurname
        ),
        businessContactDetails = BusinessContactDetails(
          phoneNumber =  ctData.contactDetails.get.contactDaytimeTelephoneNumber,
          mobileNumber = ctData.contactDetails.get.contactMobileNumber,
          email = ctData.contactDetails.get.contactEmail
        )
      )
    )
  }
}
