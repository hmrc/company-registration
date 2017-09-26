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
import repositories._
import helpers.DateHelper
import models.{ConfirmationReferences, CorporationTaxRegistration}
import models._
import models.admin.Admin
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.http.HeaderCarrier
import cats.implicits._
import utils.SCRSFeatureSwitches
import RegistrationStatus._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object CorporationTaxRegistrationService extends CorporationTaxRegistrationService {
  val cTRegistrationRepository: CorporationTaxRegistrationMongoRepository = Repositories.cTRepository
  val sequenceRepository: SequenceMongoRepository = Repositories.sequenceRepository
  val stateDataRepository: StateDataMongoRepository = Repositories.stateDataRepository
  val heldSubmissionRepository: HeldSubmissionMongoRepository = Repositories.heldSubmissionRepository

  val microserviceAuthConnector = AuthConnector
  val brConnector = BusinessRegistrationConnector
  val auditConnector = MicroserviceAuditConnector
  val submissionCheckAPIConnector = IncorporationCheckAPIConnector
  val desConnector = DesConnector
  lazy val incorpInfoConnector = IncorporationInformationConnector

  def currentDateTime: DateTime = DateTime.now(DateTimeZone.UTC)
}

sealed trait RegistrationProgress
case object RegistrationProgressUpdated extends RegistrationProgress
case object CompanyRegistrationDoesNotExist extends RegistrationProgress

trait CorporationTaxRegistrationService extends DateHelper {

  val cTRegistrationRepository: CorporationTaxRegistrationRepository
  val sequenceRepository: SequenceRepository
  val stateDataRepository: StateDataRepository
  val microserviceAuthConnector: AuthConnector
  val brConnector: BusinessRegistrationConnector
  val heldSubmissionRepository: HeldSubmissionRepository
  val auditConnector: AuditConnector
  val incorpInfoConnector :IncorporationInformationConnector
  val desConnector: DesConnector
  val submissionCheckAPIConnector: IncorporationCheckAPIConnector

  def currentDateTime: DateTime

  def fetchStatus(regId: String): OptionT[Future, String] = cTRegistrationRepository.fetchDocumentStatus(regId)

  def updateRegistrationProgress(regID: String, progress: String): Future[RegistrationProgress] = {
    cTRegistrationRepository.updateRegistrationProgress(regID, progress).map{
      case Some(_) => RegistrationProgressUpdated
      case None => CompanyRegistrationDoesNotExist
    }
  }

  def updateCTRecordWithAckRefs(ackRef: String, refPayload: AcknowledgementReferences): Future[Option[CorporationTaxRegistration]] = {
    cTRegistrationRepository.retrieveByAckRef(ackRef) flatMap {
      case Some(record) =>
        record.acknowledgementReferences match {
          case Some(_) =>
            Logger.info(s"[CorporationTaxRegistrationService] - [updateCTRecordWithAckRefs] : Record previously updated")
            Future.successful(Some(record))
          case None =>
            cTRegistrationRepository.updateCTRecordWithAcknowledgments(ackRef, record.copy(acknowledgementReferences = Some(refPayload), status = "acknowledged")) map {
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

    cTRegistrationRepository.createCorporationTaxRegistration(record)
  }

  def retrieveCorporationTaxRegistrationRecord(rID: String, lastSignedIn: Option[DateTime] = None): Future[Option[CorporationTaxRegistration]] = {
    val repo = cTRegistrationRepository
    repo.retrieveCorporationTaxRegistration(rID) map {
      doc =>
        lastSignedIn map ( repo.updateLastSignedIn(rID, _))
        doc
    }
  }

  def handleSubmission(rID: String, refs: ConfirmationReferences, admin: Option[Admin] = None)
                      (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[ConfirmationReferences] = {
    isRegistrationHeld(rID).ifM(
      ifTrue = {
          Logger.info(s"[CorporationTaxRegistrationService] [updateConfirmationReferences] - Confirmation refs for Reg ID: $rID already exist")
          cTRegistrationRepository.retrieveConfirmationReferences(rID) flatMap {
            case Some(existingRefs) =>
              Future.successful(existingRefs)
            case _ =>
              Logger.error(s"[CorporationTaxRegistrationService] [updateConfirmationReferences] - Registration status is held for regId: $rID but confirmation refs not found")
              throw new RuntimeException(s"Registration status is held for regId: $rID but confirmation refs not found")
          }
        },
      ifFalse = submitPartial(rID, refs, admin)
    )
  }

  def submitPartial(rID: String, refs: ConfirmationReferences, admin: Option[Admin] = None)
                   (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[ConfirmationReferences] = {
    cTRegistrationRepository.retrieveConfirmationReferences(rID) flatMap {
      case Some(cr) => Future.successful(cr)
      case _ =>
        for {
          ackRef             <- generateAcknowledgementReference(rID)
          updatedRefs        <- storeConfirmationReferences(rID, refs.copy(acknowledgementReference = ackRef))
        } yield {
          updatedRefs
        }
    } flatMap { cr =>
        sendPartialSubmission(rID, cr, admin) map {if(_) cr else throw new RuntimeException("Document did not update successfully")}
    }
  }

  private def generateAcknowledgementReference(regId: String): Future[String] = {
    val sequenceID = "AcknowledgementID"
    sequenceRepository.getNext(sequenceID)
      .map(ref => f"BRCT$ref%011d")
  }

  def retrieveConfirmationReferences(rID: String): Future[Option[ConfirmationReferences]] = {
    cTRegistrationRepository.retrieveConfirmationReferences(rID)
  }

  private[services] def isRegistrationHeld(regId: String): Future[Boolean] = {
    fetchStatus(regId).fold(
      throw new RuntimeException(s"Registration status not found for regId : $regId")
    )(_ == HELD)
  }

  private[services] def sendPartialSubmission(regId: String, confRefs: ConfirmationReferences, admin: Option[Admin] = None)
                           (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Boolean] = {
    for{
      partialSubmission         <- buildPartialDesSubmission(regId, confRefs.acknowledgementReference, admin)
      _                         <- registerInterest(regId, confRefs.transactionId)
      _                         <- storePartial(regId, confRefs.acknowledgementReference, partialSubmission, admin)
      partialSubmissionAsJson    = Json.toJson(partialSubmission).as[JsObject]
      _                         <- auditUserPartialSubmission(regId, partialSubmissionAsJson, admin)
      success                   <- cTRegistrationRepository.updateRegistrationToHeld(regId, confRefs) map (_.isDefined)
    } yield success
  }

  private[services] def storeConfirmationReferences(regId: String, refs: ConfirmationReferences): Future[ConfirmationReferences] = {
    cTRegistrationRepository.updateConfirmationReferences(regId, refs) map {
      case Some(_) => refs
      case None =>
        Logger.error(s"[CorporationTaxRegistrationService] [HO6] [updateConfirmationRefs] - Could not find a registration document for regId : $regId")
        throw new RuntimeException(s"[HO6] Could not update confirmation refs for regId: $regId - registration document not found")
    }
  }

  private[services] def registerInterest(regId: String, transactionId: String)
                                        (implicit hc: HeaderCarrier, req: Request[_]): Future[Boolean] = {
    if(registerInterestRequired()) incorpInfoConnector.registerInterest(regId, transactionId) else Future.successful(false)
  }

  private[services] def storePartial(rID: String, ackRef: String, heldSubmission: InterimDesRegistration, admin: Option[Admin] = None)
                                    (implicit hc: HeaderCarrier, req: Request[AnyContent]) = {
    val submissionAsJson = Json.toJson(heldSubmission).as[JsObject]
    storePartialSubmission(rID, ackRef, submissionAsJson)
  }

  private[services] def auditUserPartialSubmission(regId: String, partialSubmission: JsObject, admin: Option[Admin] = None)
                                                  (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[AuditResult] = {
    for{
      ctRegistration     <- cTRegistrationRepository.retrieveCompanyDetails(regId)
      credId             <- admin.fold(retrieveAuthProviderId)(_.credId.pure[Future])
      auditResult        <- auditUserSubmission(regId, ctRegistration.get.ppob, credId, partialSubmission)
    } yield auditResult
  }

  private[services] def storePartialSubmission(regId: String, ackRef: String, partialSubmission: JsObject)
                                              (implicit hc: HeaderCarrier): Future[HeldSubmissionData] = {
    if(toETMPHoldingPen){
      desConnector.ctSubmission(ackRef, partialSubmission, regId) map {
        _ => HeldSubmissionData(regId, ackRef, partialSubmission.toString)
      }
    } else {
      heldSubmissionRepository.retrieveSubmissionByAckRef(ackRef) flatMap {
        case Some(hs) => HeldSubmissionData(hs.regId, hs.ackRef, hs.submission.toString).pure[Future]
        case None =>
          heldSubmissionRepository.storePartialSubmission(regId, ackRef, partialSubmission) map {
            case Some(heldSubmission) => heldSubmission
            case None => throw new RuntimeException(s"[HO6] [storePartialSubmission] Held submission failed to store for regId: $regId")
          }
      }
    }
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
    cTRegistrationRepository.retrieveCorporationTaxRegistration(regId) flatMap {
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
      cTRegistrationRepository.retrieveCorporationTaxRegistration(regId) map {
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

  private[services] def toETMPHoldingPen: Boolean = SCRSFeatureSwitches.etmpHoldingPen.enabled

  def locateOldHeldSubmissions(implicit hc : HeaderCarrier): Future[String] = {
    cTRegistrationRepository.retrieveAllWeekOldHeldSubmissions().map {submissions =>
      if (submissions.nonEmpty) {
        Logger.error("ALERT_missing_incorporations")
        submissions.map {submission =>
          val txID = submission.confirmationReferences.fold("")(cr => cr.transactionId)
          val heldTimestamp = submission.heldTimestamp.fold("")(ht => ht.toString())

          Logger.warn(s"Held submission older than one week of regID: ${submission.registrationID} txID: $txID heldDate: $heldTimestamp)")
          true
        }
        "Week old held submissions found"
      } else {
        "No week old held submissions found"
      }
    }
  }
}
