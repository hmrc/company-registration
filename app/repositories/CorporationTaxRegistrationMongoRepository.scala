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

package repositories

import auth.AuthorisationResource
import models._
import play.api.libs.json.{JsObject, JsValue}
import reactivemongo.api.DB
import reactivemongo.bson.BSONDocument
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

trait CorporationTaxRegistrationRepository extends Repository[CorporationTaxRegistration, BSONObjectID]{
  def createCorporationTaxRegistration(metadata: CorporationTaxRegistration): Future[CorporationTaxRegistration]
  def retrieveCorporationTaxRegistration(regID: String): Future[Option[CorporationTaxRegistration]]
  def retrieveRegistrationByTransactionID(regID: String): Future[Option[CorporationTaxRegistration]]
  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]]
  def retrieveAccountingDetails(registrationID: String): Future[Option[AccountingDetails]]
  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]]
  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetails): Future[Option[AccountingDetails]]
  def retrieveTradingDetails(registrationID : String) : Future[Option[TradingDetails]]
  def updateTradingDetails(registrationID : String, tradingDetails: TradingDetails) : Future[Option[TradingDetails]]
  def updateContactDetails(registrationID: String, contactDetails: ContactDetails): Future[Option[ContactDetails]]
  def retrieveConfirmationReference(registrationID: String) : Future[Option[ConfirmationReferences]]
  def updateConfirmationReferences(registrationID: String, confirmationReferences: ConfirmationReferences) : Future[Option[ConfirmationReferences]]
  def retrieveContactDetails(registrationID: String): Future[Option[ContactDetails]]
  def updateCompanyEndDate(registrationID: String, model: AccountPrepDetails): Future[Option[AccountPrepDetails]]
  def updateSubmissionStatus(registrationID: String, status: String): Future[String]
  def removeTaxRegistrationInformation(registrationId: String): Future[Boolean]
  def updateCTRecordWithAcknowledgments(ackRef : String, ctRecord : CorporationTaxRegistration) : Future[WriteResult]
  def getHeldCTRecord(ackRef : String) : Future[Option[CorporationTaxRegistration]]
  def updateHeldToSubmitted(registrationId: String, crn: String, submissionTS: String): Future[Boolean]
  def removeTaxRegistrationById(registrationId: String): Future[Boolean]
  def updateEmail(registrationId: String, email: Email): Future[Option[Email]]
  def retrieveEmail(registrationId: String): Future[Option[Email]]
}

private[repositories] class MissingCTDocument(regId: String) extends NoStackTrace

class CorporationTaxRegistrationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CorporationTaxRegistration, BSONObjectID]("corporation-tax-registration-information", mongo, CorporationTaxRegistration.format, ReactiveMongoFormats.objectIdFormats)
  with CorporationTaxRegistrationRepository
  with AuthorisationResource[String] {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("confirmationReferences.acknowledgement-reference" -> IndexType.Ascending),
      name = Some("AckRefIndex"),
      unique = false,
      sparse = false
    ),
    Index(
      key = Seq("confirmationReferences.transaction-id" -> IndexType.Ascending),
      name = Some("TransIdIndex"),
      unique = false,
      sparse = false
    )
  )

  override def updateCTRecordWithAcknowledgments(ackRef : String, ctRecord: CorporationTaxRegistration): Future[WriteResult] = {
    val updateSelector = BSONDocument("confirmationReferences.acknowledgement-reference" -> BSONString(ackRef))
    collection.update(updateSelector, ctRecord, upsert = false)
  }

  override def getHeldCTRecord(ackRef: String) : Future[Option[CorporationTaxRegistration]] = {
    val query = BSONDocument("confirmationReferences.acknowledgement-reference" -> BSONString(ackRef))
    collection.find(query).one[CorporationTaxRegistration]
  }


  override def retrieveRegistrationByTransactionID(transactionID: String): Future[Option[CorporationTaxRegistration]] = {
    val selector = BSONDocument("confirmationReferences.transactionId" -> BSONString(transactionID))
    collection.find(selector).one[CorporationTaxRegistration]
  }


  private[repositories] def registrationIDSelector(registrationID: String): BSONDocument = BSONDocument(
    "registrationID" -> BSONString(registrationID)
  )

  override def createCorporationTaxRegistration(ctReg: CorporationTaxRegistration): Future[CorporationTaxRegistration] = {
    collection.insert(ctReg) map (_ => ctReg)
  }

  override def retrieveCorporationTaxRegistration(registrationID: String): Future[Option[CorporationTaxRegistration]] = {
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).one[CorporationTaxRegistration]
  }

  override def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]] = {
    retrieveCorporationTaxRegistration(registrationID).flatMap {
      case Some(data) => collection.update(registrationIDSelector(registrationID), data.copy(companyDetails = Some(companyDetails)), upsert = false)
        .map(_ => Some(companyDetails))
      case None => Future.successful(None)
    }
  }

  override def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    retrieveCorporationTaxRegistration(registrationID).map {
      case Some(cTRegistration) => cTRegistration.companyDetails
      case None => None
    }
  }

  override def retrieveAccountingDetails(registrationID: String): Future[Option[AccountingDetails]] = {
    retrieveCorporationTaxRegistration(registrationID).map {
      case Some(cTRegistration) => cTRegistration.accountingDetails
      case None => None
    }
  }

  override def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetails): Future[Option[AccountingDetails]] = {
    retrieveCorporationTaxRegistration(registrationID).flatMap {
      case Some(data) => collection.update(registrationIDSelector(registrationID), data.copy(accountingDetails = Some(accountingDetails))).map(
        _ => Some(accountingDetails)
      )
      case None => Future.successful(None)
    }
  }

  override def retrieveTradingDetails(registrationID: String): Future[Option[TradingDetails]] = {
    retrieveCorporationTaxRegistration(registrationID).map {
      case Some(ctRegistration) =>
        ctRegistration.tradingDetails
      case None => None
    }
  }

  override def retrieveContactDetails(registrationID: String): Future[Option[ContactDetails]] = {
    retrieveCorporationTaxRegistration(registrationID) map {
      case Some(registration) => registration.contactDetails
      case None => None
    }
  }

  override def updateTradingDetails(registrationID: String, tradingDetails: TradingDetails): Future[Option[TradingDetails]] = {
    retrieveCorporationTaxRegistration(registrationID).flatMap {
      case Some(data) => collection.update(registrationIDSelector(registrationID), data.copy(tradingDetails = Some(tradingDetails))).map(
        _ => Some(tradingDetails)
      )
      case None => Future.successful(None)
    }
  }

  override def updateContactDetails(registrationID: String, contactDetails: ContactDetails): Future[Option[ContactDetails]] = {
    retrieveCorporationTaxRegistration(registrationID) flatMap {
      case Some(registration) => collection.update(registrationIDSelector(registrationID), registration.copy(contactDetails = Some(contactDetails)), upsert = false)
        .map(_ => Some(contactDetails))
      case None => Future.successful(None)
    }
  }

  override def retrieveConfirmationReference(registrationID: String) : Future[Option[ConfirmationReferences]] = {
    retrieveCorporationTaxRegistration(registrationID) map { oreg => { oreg flatMap { _.confirmationReferences } } }
  }

  override def updateConfirmationReferences(registrationID: String, confirmationReferences: ConfirmationReferences) : Future[Option[ConfirmationReferences]] = {
    retrieveCorporationTaxRegistration(registrationID) flatMap {
      case Some(registration) => collection.update(registrationIDSelector(registrationID), registration.copy(confirmationReferences = Some(confirmationReferences)), upsert = false)
        .map(_ => Some(confirmationReferences))
      case None => Future.successful(None)
    }
  }

  override def updateCompanyEndDate(registrationID: String, model: AccountPrepDetails): Future[Option[AccountPrepDetails]] = {
    retrieveCorporationTaxRegistration(registrationID) flatMap {
      case Some(ct) =>
        collection.update(registrationIDSelector(registrationID), ct.copy(accountsPreparation = Some(model)), upsert = false)
          .map(_=>Some(model))
        case None => Future.successful(None)
    }
  }

  override def updateSubmissionStatus(registrationID: String, status: String): Future[String] = {
    val modifier = BSONDocument("$set" -> BSONDocument("status" -> status))
    collection.findAndUpdate(registrationIDSelector(registrationID), modifier, fetchNewObject = true, upsert = false) map { r =>
      (r.result[JsValue].get \ "status").as[String]
    }
  }

  override def removeTaxRegistrationInformation(registrationId: String): Future[Boolean] = {
    val modifier = BSONDocument("$unset" -> BSONDocument("tradingDetails" -> 1, "contactDetails" -> 1, "companyDetails" -> 1))
    collection.findAndUpdate(registrationIDSelector(registrationId), modifier, fetchNewObject = true, upsert = false) map { r =>
      val tradingDetails = (r.result[JsValue].get \ "tradingDetails").asOpt[JsObject].fold(true)(_ => false)
      val contactDetails = (r.result[JsValue].get \ "contactDetails").asOpt[JsObject].fold(true)(_ => false)
      val companyDetails = (r.result[JsValue].get \ "companyDetails").asOpt[JsObject].fold(true)(_ => false)

      (tradingDetails, contactDetails, companyDetails) match {
        case (true, true, true) => true
        case _ => false
      }
    }
  }

  override def updateHeldToSubmitted(registrationId: String, crn: String, submissionTS: String): Future[Boolean] = {
    retrieveCorporationTaxRegistration(registrationId) flatMap {
      case Some(ct) =>
        import RegistrationStatus.SUBMITTED
        val updatedDoc = ct.copy(
          status = SUBMITTED,
          crn = Some(crn),
          submissionTimestamp = Some(submissionTS),
          accountingDetails = None,
          accountsPreparation = None
        )
        collection.update(
          registrationIDSelector(registrationId),
          updatedDoc,
          upsert = false
        ).map( _ => true )
      case None => Future.failed(new MissingCTDocument(registrationId))
    }
  }

  override def getOid(id: String): Future[Option[(String, String)]] = {
    retrieveCorporationTaxRegistration(id) map {
      case None => None
      case Some(m) => Some(m.registrationID -> m.OID)
    }
  }

  override def removeTaxRegistrationById(registrationId: String): Future[Boolean] = {
    retrieveCorporationTaxRegistration(registrationId) flatMap {
      case Some(ct) => collection.remove(registrationIDSelector(registrationId)) map { _ => true }
      case None => Future.failed(new MissingCTDocument(registrationId))
    }
  }

  override def updateEmail(registrationId: String, email: Email): Future[Option[Email]] = {
    retrieveCorporationTaxRegistration(registrationId) flatMap {
      case Some(registration) =>
        collection.update(
        registrationIDSelector(registrationId),
        registration.copy(verifiedEmail = Some(email)),
        upsert = false
      ).map(_ => Some(email))
      case None => Future.successful(None)
    }
  }

  override def retrieveEmail(registrationId: String): Future[Option[Email]] = {
    retrieveCorporationTaxRegistration(registrationId) map {
      case Some(registration) => registration.verifiedEmail
      case None => None
    }
  }

  def dropCollection = {
    collection.drop()
  }
}
