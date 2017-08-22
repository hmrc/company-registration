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
import cats.data.OptionT
import config.MicroserviceAuditConnector
import connectors._
import models.des._
import models.{BusinessRegistration, RegistrationStatus}
import play.api.mvc.{AnyContent, Request}
import repositories.HeldSubmissionRepository
import helpers.DateHelper
import models.{ConfirmationReferences, CorporationTaxRegistration}
import repositories.{CorporationTaxRegistrationRepository, Repositories, SequenceRepository, StateDataRepository}
import models._
import models.admin.Admin
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import cats.implicits._
import utils.SCRSFeatureSwitches

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
  lazy val incorpInfoConnector = IncorporationInformationConnector

  def currentDateTime = DateTime.now(DateTimeZone.UTC)

  override val submissionCheckAPIConnector = IncorporationCheckAPIConnector
}

sealed trait RegistrationProgress
case object RegistrationProgressUpdated extends RegistrationProgress
case object CompanyRegistrationDoesNotExist extends RegistrationProgress

trait CorporationTaxRegistrationService extends DateHelper {

  val corporationTaxRegistrationRepository: CorporationTaxRegistrationRepository
  val sequenceRepository: SequenceRepository
  val stateDataRepository: StateDataRepository
  val microserviceAuthConnector: AuthConnector
  val brConnector: BusinessRegistrationConnector
  val heldSubmissionRepository: HeldSubmissionRepository
  val auditConnector: AuditConnector
  val incorpInfoConnector :IncorporationInformationConnector

  def currentDateTime: DateTime

  val submissionCheckAPIConnector: IncorporationCheckAPIConnector

  def updateRegistrationProgress(regID: String, progress: String): Future[RegistrationProgress] = {
    corporationTaxRegistrationRepository.updateRegistrationProgress(regID, progress).map{
      case Some(_) => RegistrationProgressUpdated
      case None => CompanyRegistrationDoesNotExist
    }
  }

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

  def updateConfirmationReferences(rID: String, refs: ConfirmationReferences, admin: Option[Admin] = None)
                                  (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[ConfirmationReferences]] = {
    corporationTaxRegistrationRepository.retrieveConfirmationReference(rID) flatMap {
      case existingRefs @ Some(_) =>
        Logger.info(s"[CorporationTaxRegistrationService] [updateConfirmationReferences] - Confirmation refs for Reg ID: $rID already exist")
        Future.successful(existingRefs)
      case None =>
        for {
          ackRef         <- generateAcknowledgementReference
          heldSubmission <- buildPartialDesSubmission(rID, ackRef, admin)
          _              <- storeAndUpdateSubmission(rID, ackRef, heldSubmission, admin)
          updatedRef     <- corporationTaxRegistrationRepository.updateConfirmationReferences(rID, refs.copy(acknowledgementReference = ackRef))
          _              <- if(registerInterestRequired()) incorpInfoConnector.registerInterest(rID, refs.transactionId) else Future.successful(None)
          _              <- removeTaxRegistrationInformation(rID)
        } yield {
          updatedRef
        }
    }
  }

  def fetchStatus(regId: String): OptionT[Future, String] = corporationTaxRegistrationRepository.fetchDocumentStatus(regId)

  private[services] def storeAndUpdateSubmission(rID: String, ackRef: String, heldSubmission: InterimDesRegistration, admin: Option[Admin] = None)
                                                (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[String] = {
    val submissionAsJson = Json.toJson(heldSubmission).as[JsObject]
    for {
      _                  <- heldSubmissionRepository.storePartialSubmission(rID, ackRef, submissionAsJson)
      submissionStatus   <- updateSubmissionStatus(rID, RegistrationStatus.HELD)
      ctRegistration     <- corporationTaxRegistrationRepository.retrieveCompanyDetails(rID)
      credId             <- admin.fold(retrieveAuthProviderId)(_.credId.pure[Future])
      _                  <- auditUserSubmission(rID, ctRegistration.get.ppob, credId, submissionAsJson)
    } yield submissionStatus
  }

  private[services] def retrieveAuthProviderId(implicit hc: HeaderCarrier) = microserviceAuthConnector.getUserDetails map (_.get.authProviderId)

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

  private[services] def buildPartialDesSubmission(regId: String, ackRef: String, admin: Option[Admin] = None)(implicit hc: HeaderCarrier): Future[InterimDesRegistration] = {

    // TODO - check behaviour if session header is missing
    val sessionId = hc.headers.collect { case ("X-Session-ID", x) => x }.head

    for {
      credId <- admin.fold(retrieveCredId)(a => Future.successful(a.credId))
      brMetadata <- retrieveBRMetadata(regId, admin.isDefined)
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

  private[services] def retrieveBRMetadata(regId: String, isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    (if(isAdmin) brConnector.adminRetrieveMetadata(regId) else brConnector.retrieveMetadata(regId)) flatMap {
      case BusinessRegistrationSuccessResponse(metadata) if metadata.registrationID == regId => Future.successful(metadata)
      case _ => Future.failed(new FailedToGetBRMetadata)
    }
  }

  final class FailedToGetCTData extends NoStackTrace

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

    val businessContactName = BusinessContactName(contactDetails.firstName, contactDetails.middleName, contactDetails.surname)
    val businessContactDetails = BusinessContactDetails(contactDetails.phone, contactDetails.mobile, contactDetails.email)

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
        returnsOnCT61 = tradingDetails.regularPayments.toBoolean,
        businessAddress = businessAddress, //todo - SCRS-3708: make business address optional
        businessContactName = businessContactName,
        businessContactDetails = businessContactDetails
      )
    )
  }


  def checkDocumentStatus(regIds: Seq[String]): Future[Seq[Boolean]] = {
    def check(regId: String) = {
      corporationTaxRegistrationRepository.retrieveCorporationTaxRegistration(regId) map {
        document =>
          val doc = document.get
          val status = doc.status
          val ho5 = doc.registrationProgress.fold("HO5 NOT reached")(_ => "HO5 reached")
          val txId = doc.confirmationReferences.fold("")(cr => s" TxId is = ${cr.transactionId}.")

          for {
            held <- heldSubmissionRepository.retrieveSubmissionByRegId(regId).recover {
              case _ =>
                Logger.error("Error fetching Held document")
                None
            }
          } yield Logger.warn(s"Current status of regId: $regId is $status.$txId $ho5 and ${held.fold("no held document")(_ => "document is in held")}.")
          true
      } recover {
        case e =>
          Logger.error(s"Data check was unsuccessful for regId: $regId")
          false
      }
    }
    Future.sequence(regIds.map(check))
  }

  private[services] def registerInterestRequired(): Boolean = SCRSFeatureSwitches.registerInterest.enabled
}
