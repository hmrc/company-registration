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

import audit.{SubmissionEventDetail, UserRegistrationSubmissionEvent}
import config.MicroserviceAuditConnector
import connectors.{AuthConnector, BusinessRegistrationConnector, BusinessRegistrationSuccessResponse}
import models.des._
import models.{BusinessRegistration, RegistrationStatus}
import play.api.mvc.{Request, AnyContent}
import repositories.HeldSubmissionRepository
import connectors.IncorporationCheckAPIConnector
import helpers.DateHelper
import models.{ConfirmationReferences, CorporationTaxRegistration}
import repositories.{CorporationTaxRegistrationRepository, Repositories, SequenceRepository, StateDataRepository}
import models._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object CorporationTaxRegistrationService extends CorporationTaxRegistrationService {
  override val corporationTaxRegistrationRepository = Repositories.cTRepository
  override val sequenceRepository = Repositories.sequenceRepository
  override val stateDataRepository = Repositories.stateDataRepository
  override val microserviceAuthConnector = AuthConnector
  override val brConnector = BusinessRegistrationConnector
  val heldSubmissionRepository = Repositories.heldSubmissionRepository
  val auditConnector = MicroserviceAuditConnector

  def currentDateTime = DateTime.now(DateTimeZone.UTC)

  override val submissionCheckAPIConnector = IncorporationCheckAPIConnector
}

trait CorporationTaxRegistrationService extends DateHelper {

  val corporationTaxRegistrationRepository: CorporationTaxRegistrationRepository
  val sequenceRepository: SequenceRepository
  val stateDataRepository: StateDataRepository
  val microserviceAuthConnector: AuthConnector
  val brConnector: BusinessRegistrationConnector
  val heldSubmissionRepository: HeldSubmissionRepository
  val auditConnector: AuditConnector

  def currentDateTime: DateTime

  val submissionCheckAPIConnector: IncorporationCheckAPIConnector


  def updateCTRecordWithAckRefs(ackRef: String, refPayload: AcknowledgementReferences): Future[Option[CorporationTaxRegistration]] = {
    corporationTaxRegistrationRepository.retrieveByAckRef(ackRef) flatMap {
      case Some(record) =>
        record.acknowledgementReferences match {
          case Some(refs) =>
            Logger.info(s"[CorporationTaxRegistrationService] - [updateCTRecordWithAckRefs] : Record previously updated")
            Future.successful(Some(record))
          case None =>
            corporationTaxRegistrationRepository.updateCTRecordWithAcknowledgments(ackRef, record.copy(acknowledgementReferences = Some(refPayload), status = "acknowledged")) map {
              _ => Some(record)
            }
        }
      case None =>
        Logger.info(s"[CorporationTaxRegistrationService] - [updateCTRecordWithAckRefs] : No record could not be found using this ackref")
        Future.successful(None)
    }
  }

  def createCorporationTaxRegistrationRecord(internalID: String, registrationId: String, language: String): Future[CorporationTaxRegistration] = {
    val record = CorporationTaxRegistration(
      internalId = internalID,
      registrationID = registrationId,
      formCreationTimestamp = formatTimestamp(currentDateTime),
      language = language)

    corporationTaxRegistrationRepository.createCorporationTaxRegistration(record)
  }

  def retrieveCorporationTaxRegistrationRecord(rID: String, lastSignedIn: Option[DateTime] = None): Future[Option[CorporationTaxRegistration]] = {
    val repo = corporationTaxRegistrationRepository
    repo.retrieveCorporationTaxRegistration(rID) map {
      doc =>
        lastSignedIn map ( repo.updateLastSignedIn(rID, _))
        doc
    }
  }

  def updateConfirmationReferences(rID: String, refs: ConfirmationReferences)(implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[ConfirmationReferences]] = {
    corporationTaxRegistrationRepository.retrieveConfirmationReference(rID) flatMap {
      case existingRefs @ Some(confRefs) =>
        Logger.info(s"[CorporationTaxRegistrationService] [updateConfirmationReferences] - Confirmation refs for Reg ID: $rID already exist")
        Future.successful(existingRefs)
      case None =>
        for {
          ackRef <- generateAcknowledgementReference
          updatedRef <- corporationTaxRegistrationRepository.updateConfirmationReferences(rID, refs.copy(acknowledgementReference = ackRef))
          heldSubmission <- buildPartialDesSubmission(rID, ackRef)
          submissionStatus <- storeAndUpdateSubmission(rID, ackRef, heldSubmission)
          _ <- removeTaxRegistrationInformation(rID)
        } yield {
          updatedRef
        }
    }
  }

  private[services] def storeAndUpdateSubmission(rID: String, ackRef: String, heldSubmission: InterimDesRegistration)
                                                (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[String] = {
    val submissionAsJson = Json.toJson(heldSubmission).as[JsObject]
    for {
      heldSubmissionData <- heldSubmissionRepository.storePartialSubmission(rID, ackRef, submissionAsJson)
      submissionStatus <- updateSubmissionStatus(rID, RegistrationStatus.HELD)
      ctRegistration <- corporationTaxRegistrationRepository.retrieveCompanyDetails(rID)
      userDetails <- microserviceAuthConnector.getUserDetails
      authProviderId = userDetails.get.authProviderId
      ppob = ctRegistration.get.ppob
      _ <- auditUserSubmission(rID, ppob, authProviderId, submissionAsJson)
    } yield submissionStatus
  }

  private[services] def auditUserSubmission(rID: String, ppob: PPOB, authProviderId: String, jsSubmission: JsObject)(implicit hc: HeaderCarrier, req: Request[AnyContent]) = {
    import PPOB.RO

    val (txID, uprn) = (ppob.addressType, ppob.address) match {
      case (RO, _) => (None, None)
      case (_, Some(address)) => (Some(address.txid), address.uprn)
    }
    val event = new UserRegistrationSubmissionEvent(SubmissionEventDetail(rID, authProviderId, txID, uprn, ppob.addressType, jsSubmission))(hc, req)
    auditConnector.sendEvent(event)
  }

  private[services] class FailedToRemoveTaxRegistrationInformation extends NoStackTrace

  private[services] def removeTaxRegistrationInformation(registrationId: String): Future[Boolean] = {
    corporationTaxRegistrationRepository.removeTaxRegistrationInformation(registrationId) flatMap {
      case true => Future.successful(true)
      case false => Future.failed(new FailedToRemoveTaxRegistrationInformation)
    }
  }

  def updateSubmissionStatus(rID: String, status: String): Future[String] = {
    corporationTaxRegistrationRepository.updateSubmissionStatus(rID, status)
  }

  def retrieveConfirmationReference(rID: String): Future[Option[ConfirmationReferences]] = {
    corporationTaxRegistrationRepository.retrieveConfirmationReference(rID)
  }

  private def generateAcknowledgementReference: Future[String] = {
    val sequenceID = "AcknowledgementID"
    sequenceRepository.getNext(sequenceID)
      .map(ref => f"BRCT$ref%011d")
  }

  private[services] def buildPartialDesSubmission(regId: String, ackRef: String)(implicit hc: HeaderCarrier): Future[InterimDesRegistration] = {

    // TODO - check behaviour if session header is missing
    val sessionId = hc.headers.collect { case ("X-Session-ID", x) => x }.head

    for {
      credId <- retrieveCredId
      brMetadata <- retrieveBRMetadata(regId)
      ctData <- retrieveCTData(regId)
    } yield {
      buildInterimSubmission(ackRef, sessionId, credId, brMetadata, ctData, currentDateTime)
    }
  }

  private[services] class FailedToGetCredId extends NoStackTrace

  private[services] def retrieveCredId(implicit hc: HeaderCarrier): Future[String] = {
    microserviceAuthConnector.getCurrentAuthority flatMap {
      case Some(a) => Future.successful(a.gatewayId)
      case _ => Future.failed(new FailedToGetCredId)
    }
  }

  private[services] class FailedToGetBRMetadata extends NoStackTrace

  private[services] def retrieveBRMetadata(regId: String)(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    brConnector.retrieveMetadata flatMap {
      case BusinessRegistrationSuccessResponse(metadata) if metadata.registrationID == regId => Future.successful(metadata)
      case _ => Future.failed(new FailedToGetBRMetadata)
    }
  }

  private[services] class FailedToGetCTData extends NoStackTrace

  private[services] def retrieveCTData(regId: String): Future[CorporationTaxRegistration] = {
    corporationTaxRegistrationRepository.retrieveCorporationTaxRegistration(regId) flatMap {
      case Some(ct) => Future.successful(ct)
      case _ => Future.failed(new FailedToGetCTData)
    }
  }

  private[services] def buildInterimSubmission(ackRef: String, sessionId: String, credId: String,
                                               brMetadata: BusinessRegistration, ctData: CorporationTaxRegistration, currentDateTime: DateTime): InterimDesRegistration = {

    // It's an error if these aren't available at this point!
    val companyDetails = ctData.companyDetails.get
    val contactDetails = ctData.contactDetails.get
    val tradingDetails = ctData.tradingDetails.get
    val ppob = companyDetails.ppob
    val completionCapacity = CompletionCapacity(brMetadata.completionCapacity.get)

    // SCRS-3708 - should be an Option[BusinessAddress] and mapped from the optional ppob
    val businessAddress: Option[BusinessAddress] = ppob.address match {
      case Some(address) => Some(
        BusinessAddress(
          line1 = address.line1,
          line2 = address.line2,
          line3 = address.line3,
          line4 = address.line4,
          postcode = address.postcode,
          country = address.country
        ))
      case None => None
    }

    val businessContactName = BusinessContactName(
      firstName = contactDetails.firstName,
      middleNames = contactDetails.middleName,
      lastName = contactDetails.surname
    )

    val businessContactDetails = BusinessContactDetails(
      phoneNumber = contactDetails.phone,
      mobileNumber = contactDetails.mobile,
      email = contactDetails.email
    )

    InterimDesRegistration(
      ackRef = ackRef,
      metadata = Metadata(
        sessionId = sessionId,
        credId = credId,
        language = brMetadata.language,
        submissionTs = DateTime.parse(formatTimestamp(currentDateTime)),
        completionCapacity = completionCapacity
      ),
      interimCorporationTax = InterimCorporationTax(
        companyName = companyDetails.companyName,
        returnsOnCT61 = tradingDetails.regularPayments,
        businessAddress = businessAddress, //todo - SCRS-3708: make business address optional
        businessContactName = businessContactName,
        businessContactDetails = businessContactDetails
      )
    )
  }
}
