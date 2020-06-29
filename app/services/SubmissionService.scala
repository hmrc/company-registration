/*
 * Copyright 2020 HM Revenue & Customs
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
import cats.implicits._
import connectors.{BusinessRegistrationConnector, BusinessRegistrationSuccessResponse, DesConnector, IncorporationInformationConnector}
import helpers.DateHelper
import javax.inject.Inject
import models.RegistrationStatus.{ACKNOWLEDGED, DRAFT, LOCKED, SUBMITTED}
import models._
import models.des._
import models.validation.APIValidation
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AnyContent, Request}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories, SequenceRepository}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import utils.{PagerDutyKeys, StringNormaliser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmissionServiceImpl @Inject()(val repositories: Repositories,
                                      val incorpInfoConnector: IncorporationInformationConnector,
                                      val desConnector: DesConnector,
                                      val brConnector: BusinessRegistrationConnector,
                                      val corpTaxRegService: CorporationTaxRegistrationService,
                                      val auditConnector: AuditConnector) extends SubmissionService {
  lazy val cTRegistrationRepository: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
  lazy val sequenceRepository: SequenceRepository = repositories.sequenceRepository

  def currentDateTime: DateTime = DateTime.now(DateTimeZone.UTC)
}

trait SubmissionService extends DateHelper {

  val cTRegistrationRepository: CorporationTaxRegistrationMongoRepository
  val sequenceRepository: SequenceRepository
  val incorpInfoConnector: IncorporationInformationConnector
  val desConnector: DesConnector
  val auditConnector: AuditConnector
  val brConnector: BusinessRegistrationConnector
  val corpTaxRegService: CorporationTaxRegistrationService

  def currentDateTime: DateTime

  def handleSubmission(rID: String, authProvId: String, handOffRefs: ConfirmationReferences, isAdmin: Boolean)
                      (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[ConfirmationReferences] = {
    cTRegistrationRepository.findBySelector(cTRegistrationRepository.regIDSelector(rID)) flatMap {
      case Some(doc) =>
        throwPagerDutyIfTxIDInDBDoesntMatchHandOffTxID(doc.confirmationReferences, handOffRefs.transactionId)
        if (doc.status == DRAFT || doc.status == LOCKED) {
          prepareDocumentForSubmission(rID, authProvId, handOffRefs, doc) flatMap { confRefs =>
            processPartialSubmission(rID, authProvId, confRefs, doc, isAdmin).ifM(
              ifTrue = Future.successful(confRefs),
              ifFalse = throw new RuntimeException(s"[handleSubmission] Failed to submit for regid: $rID")
            )
          }
        } else {
          doc.confirmationReferences match {
            case Some(existingRefs) if existingRefs != handOffRefs && confirmationRefsAndPaymentRefsAreEmpty(existingRefs) =>
              storeConfirmationReferencesAndUpdateStatus(rID, existingRefs.copy(paymentReference = handOffRefs.paymentReference, paymentAmount = handOffRefs.paymentAmount), None)

            case Some(existingRefs) =>
              Logger.info(s"[SubmissionService] [handleSubmission] - Confirmation refs for Reg ID: $rID already exist")
              Future.successful(existingRefs)

            case _ =>
              Logger.error(s"[SubmissionService] [handleSubmission] - Registration status is ${doc.status} for regId: $rID but confirmation refs not found")
              throw new RuntimeException(s"Registration status is held for regId: $rID but confirmation refs not found")
          }
        }
      case None => throw new RuntimeException(s"[handleSubmission] Registration Document not found for regId: $rID")
    }
  }

  private[services] def throwPagerDutyIfTxIDInDBDoesntMatchHandOffTxID(crConfRefs: Option[ConfirmationReferences], hOffTxID: String): Boolean = {
    crConfRefs.fold(false) {
      confRef =>
        if (confRef.transactionId != hOffTxID) {
          Logger.error(s"${PagerDutyKeys.TXID_IN_CR_DOESNT_MATCH_HANDOFF_TXID} - CR txId: ${confRef.transactionId} hand off txId: $hOffTxID ")
          true
        } else {
          false
        }
    }
  }

  def updateCTRecordWithAckRefs(ackRef: String, etmpNotification: AcknowledgementReferences): Future[Option[CorporationTaxRegistration]] = {
    cTRegistrationRepository.findBySelector(cTRegistrationRepository.ackRefSelector(ackRef)) flatMap {
      case Some(record) =>
        cTRegistrationRepository.updateCTRecordWithAcknowledgments(ackRef, record.copy(acknowledgementReferences = Some(etmpNotification), status = RegistrationStatus.ACKNOWLEDGED)) map {
          _ => Some(record)
        }
      case None =>
        Logger.info(s"[SubmissionService] - [updateCTRecordWithAckRefs] : No record could not be found using this ackref")
        Future.successful(None)
    }
  }

  def generateAckRef: Future[String] = sequenceRepository.getNext("AcknowledgementID").map(ref => f"BRCT$ref%011d")

  def prepareDocumentForSubmission(rID: String, authProvId: String, refs: ConfirmationReferences, doc: CorporationTaxRegistration)
                                  (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[ConfirmationReferences] = {
    doc.confirmationReferences match {
      case None =>
        for {
          newlyGeneratedAckRef <- generateAckRef
          updatedRefs <- storeConfirmationReferencesAndUpdateStatus(rID, refs.copy(acknowledgementReference = newlyGeneratedAckRef), Some(LOCKED))
        } yield updatedRefs

      case Some(cr) if confirmationRefsAndPaymentRefsAreEmpty(cr) =>
        storeConfirmationReferencesAndUpdateStatus(rID, cr.copy(paymentReference = refs.paymentReference, paymentAmount = refs.paymentAmount), Some(LOCKED))

      case Some(cr) =>
        Future.successful(cr)

    }
  }

  private[services] def storeConfirmationReferencesAndUpdateStatus(regId: String, refs: ConfirmationReferences, status: Option[String]): Future[ConfirmationReferences] = {
    status.fold(cTRegistrationRepository.updateConfirmationReferences(regId, refs))(cTRegistrationRepository.updateConfirmationReferencesAndUpdateStatus(regId, refs, _)) map {
      case Some(_) => refs
      case None =>
        Logger.error(s"[SubmissionService] [HO6] [storeConfirmationReferencesAndUpdateStatus] - Could not find a registration document for regId : $regId")
        throw new RuntimeException(s"[HO6] Could not update confirmation refs for regId: $regId - registration document not found")
    }
  }

  private[services] def processPartialSubmission(regId: String, authProvId: String, confRefs: ConfirmationReferences, doc: CorporationTaxRegistration, isAdmin: Boolean)
                                                (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Boolean] = {
    for {
      brMetadata <- retrieveBRMetadata(regId, isAdmin)
      partialSubmission = buildPartialDesSubmission(regId, confRefs.acknowledgementReference, authProvId, brMetadata, doc)
      partialSubmissionAsJson = Json.toJson(partialSubmission).as[JsObject]
      _ <- incorpInfoConnector.registerInterest(regId, confRefs.transactionId)
      _ <- submitPartialToDES(regId, confRefs.acknowledgementReference, partialSubmissionAsJson, authProvId)
      _ = auditUserPartialSubmission(regId, authProvId, partialSubmissionAsJson, doc)
      success <- cTRegistrationRepository.updateRegistrationToHeld(regId, confRefs) map (_.isDefined)
    } yield success
  }


  private[services] def buildPartialDesSubmission(regId: String, ackRef: String, authProvId: String, brMetadata: BusinessRegistration, ctData: CorporationTaxRegistration)
                                                 (implicit hc: HeaderCarrier): InterimDesRegistration = {
    val (sessionID, credID): (String, String) = hc.headers.toMap.get("X-Session-ID") match {
      case Some(sesID) => (sesID, authProvId)
      case None => ctData.sessionIdentifiers match {
        case Some(sessionIdentifiers) => (sessionIdentifiers.sessionId, sessionIdentifiers.credId)
        case None => throw new RuntimeException(s"[buildPartialDesSubmission] No session identifiers available for DES submission")
      }
    }

    val companyDetails = ctData.companyDetails.getOrElse(throw new RuntimeException("[buildPartialDesSubmission] no company details found in ct doc when building partial des submission"))
    val contactDetails = ctData.contactDetails.getOrElse(throw new RuntimeException("[buildPartialDesSubmission] no contact details found in ct doc when building partial des submission"))
    val tradingDetails = ctData.tradingDetails.getOrElse(throw new RuntimeException("[buildPartialDesSubmission] no trading details found in ct doc when building partial des submission"))
    val completionCapacity = CompletionCapacity(
      brMetadata.completionCapacity.getOrElse(throw new RuntimeException("[buildPartialDesSubmission] no completion Capacity found in br when building partial des submission"))
    )

    val optPPOBAddress: Option[PPOBAddress] = companyDetails.ppob match {
      case PPOB(PPOB.RO, _) => corpTaxRegService.convertROToPPOBAddress(companyDetails.registeredOffice)
      case PPOB(_, address) => address
    }

    val businessAddress: Option[BusinessAddress] = optPPOBAddress map {
      address =>
        BusinessAddress(
          line1 = StringNormaliser.removeIllegalCharacters(address.line1),
          line2 = StringNormaliser.removeIllegalCharacters(address.line2),
          line3 = address.line3.map{ line3 => StringNormaliser.removeIllegalCharacters(line3) },
          line4 = address.line4.map{ line4 => StringNormaliser.removeIllegalCharacters(line4) },
          postcode = address.postcode,
          country = address.country
        )
    }

    def formatGroupsForSubmission: Option[Groups] = ctData.groups.map {
      og =>
        if (og.groupRelief) {
          val nameOfComp = og.nameOfCompany
            .getOrElse(throw new RuntimeException(s"formatGroupsForSubmission groups exists but name does not: $regId"))
          val address = og.addressAndType
            .getOrElse(throw new RuntimeException(s"formatGroupsForSubmission groups exists but address does not: $regId"))
          val utr = og.groupUTR.getOrElse(throw new RuntimeException(s"formatGroupsForSubmission groups exists but utr block does not: $regId"))
          val nameFormatted = APIValidation.parentGroupNameValidator.reads(JsString(nameOfComp.name))
            .getOrElse(throw new RuntimeException(s"Parent group name saved does not pass des validation: $regId"))
          Groups(
            og.groupRelief,
            Some(nameOfComp.copy(name = nameFormatted)),
            Some(address),
            Some(utr))
        } else Groups(groupRelief = false, None, None, None)
    }

    val businessContactDetails = BusinessContactDetails(contactDetails.phone, contactDetails.mobile, contactDetails.email)

    InterimDesRegistration(
      ackRef = ackRef,
      metadata = Metadata(
        sessionId = sessionID,
        credId = credID,
        language = brMetadata.language,
        submissionTs = DateTime.parse(formatTimestamp(currentDateTime)),
        completionCapacity = completionCapacity
      ),
      interimCorporationTax = InterimCorporationTax(
        companyName = companyDetails.companyName,
        returnsOnCT61 = tradingDetails.regularPayments.toBoolean,
        businessAddress = businessAddress,
        businessContactDetails = businessContactDetails,
        groups = formatGroupsForSubmission,
        takeOver = ctData.takeoverDetails
      )
    )
  }

  private[services] def submitPartialToDES(regId: String, ackRef: String, partialSubmission: JsObject, authProvId: String)
                                          (implicit hc: HeaderCarrier): Future[HttpResponse] = {
    desConnector.ctSubmission(ackRef, partialSubmission, regId) recoverWith {
      case e =>
        hc.headers.toMap.get("X-Session-ID") match {
          case Some(xSesID) =>
            Logger.warn(s"[storePartialSubmission] Saved session identifiers for regId: $regId")
            cTRegistrationRepository.storeSessionIdentifiers(regId, xSesID, authProvId) map (throw e)
          case _ =>
            Logger.warn(s"[storePartialSubmission] No session identifiers to save for regID: $regId")
            throw e
        }
    }
  }

  private[services] def auditUserPartialSubmission(regId: String, authProvId: String, partialSubmission: JsObject, doc: CorporationTaxRegistration)
                                                  (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[AuditResult] = {
    import PPOB.RO

    val ppob = doc.companyDetails.getOrElse(throw new RuntimeException(s"Could not retrieve Company Registration after DES Submission for $regId")).ppob
    val (txID, uprn) = (ppob.addressType, ppob.address) match {
      case (RO, _) => (None, None)
      case (_, Some(address)) => (Some(address.txid), address.uprn)
      case (_, None) => (None, None)
    }

    val event = new UserRegistrationSubmissionEvent(SubmissionEventDetail(regId, authProvId, txID, uprn, ppob.addressType, partialSubmission))(hc, req)
    auditConnector.sendExtendedEvent(event)
  }

  private[services] def retrieveBRMetadata(regId: String, isAdmin: Boolean = false)(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    (if (isAdmin) brConnector.adminRetrieveMetadata(regId) else brConnector.retrieveMetadata(regId)) flatMap {
      case BusinessRegistrationSuccessResponse(metadata) if metadata.registrationID == regId => Future.successful(metadata)
      case BusinessRegistrationSuccessResponse(metadata) if metadata.registrationID != regId =>
        Future.failed(new RuntimeException(s"[retrieveBRMetadata] ${metadata.registrationID} does not match $regId with isAdmin $isAdmin"))
      case _ => Future.failed(new RuntimeException("[retrieveBRMetadata] Could not find BR Metadata"))
    }
  }

  private def confirmationRefsAndPaymentRefsAreEmpty(refs: ConfirmationReferences): Boolean = refs.paymentReference.isEmpty && refs.paymentAmount.isEmpty

  def setupPartialForTopupOnLocked(transID: String)(implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Boolean] = {
    Logger.info(s"[setupPartialForTopup] Trying to update locked document of txId: $transID to held for topup with incorp update")

    cTRegistrationRepository.findBySelector(cTRegistrationRepository.transIdSelector(transID)) flatMap {
      case Some(reg) =>
        (reg.sessionIdentifiers, reg.confirmationReferences) match {
          case _ if reg.status == SUBMITTED || reg.status == ACKNOWLEDGED =>
            Logger.info(s"[setupPartialForTopup] Accepting incorporation update, registration already submitted for txID: $transID")
            Future.successful(true)
          case _ if reg.status != RegistrationStatus.LOCKED =>
            throw new RuntimeException(s"[setupPartialForTopup] Document status of txID: $transID was not locked, was ${reg.status}")
          case (Some(sIds), Some(confRefs)) =>
            processPartialSubmission(reg.registrationID, sIds.credId, confRefs, reg, isAdmin = true)
          case _ =>
            Logger.warn(s"[setupPartialForTopup] No session identifiers or conf refs for registration with txID: $transID")
            throw NoSessionIdentifiersInDocument
        }
      case _ => throw new RuntimeException(s"[setupPartialForTopup] Could not find registration by txID: $transID")
    }
  }
}