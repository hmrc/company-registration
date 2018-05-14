/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import auth.AuthorisationResource
import cats.data.OptionT
import cats.implicits._
import models._
import models.validation.MongoValidation
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson.{BSONDocument, _}
import reactivemongo.play.json.BSONFormats
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

@Singleton
class CorpTaxRegistrationRepo @Inject()(mongo: ReactiveMongoComponent) {
  Logger.info("Creating CorporationTaxRegistrationMongoRepository")
  val repo = new CorporationTaxRegistrationMongoRepository(mongo.mongoConnector.db)
}

object CorporationTaxRegistrationMongo extends ReactiveMongoFormats {
  implicit val format = CorporationTaxRegistration.format(MongoValidation)
  implicit val oFormat = CorporationTaxRegistration.oFormat(format)
}

trait CorporationTaxRegistrationRepository extends Repository[CorporationTaxRegistration, BSONObjectID]{
  def createCorporationTaxRegistration(metadata: CorporationTaxRegistration): Future[CorporationTaxRegistration]
  def retrieveCorporationTaxRegistration(regID: String): Future[Option[CorporationTaxRegistration]]
  def retrieveStaleDocuments(count: Int, storageThreshold: Int): Future[List[CorporationTaxRegistration]]
  def retrieveMultipleCorporationTaxRegistration(regID: String): Future[List[CorporationTaxRegistration]]
  def retrieveRegistrationByTransactionID(regID: String): Future[Option[CorporationTaxRegistration]]
  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]]
  def retrieveAccountingDetails(registrationID: String): Future[Option[AccountingDetails]]
  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]]
  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetails): Future[Option[AccountingDetails]]
  def retrieveTradingDetails(registrationID : String) : Future[Option[TradingDetails]]
  def updateTradingDetails(registrationID : String, tradingDetails: TradingDetails) : Future[Option[TradingDetails]]
  def updateContactDetails(registrationID: String, contactDetails: ContactDetails): Future[Option[ContactDetails]]
  def retrieveConfirmationReferences(registrationID: String) : Future[Option[ConfirmationReferences]]
  def updateConfirmationReferences(registrationID: String, confirmationReferences: ConfirmationReferences) : Future[Option[ConfirmationReferences]]
  def updateConfirmationReferencesAndUpdateStatus(registrationID: String, confirmationReferences: ConfirmationReferences, status: String) : Future[Option[ConfirmationReferences]]
  def retrieveContactDetails(registrationID: String): Future[Option[ContactDetails]]
  def updateCompanyEndDate(registrationID: String, model: AccountPrepDetails): Future[Option[AccountPrepDetails]]
  def updateSubmissionStatus(registrationID: String, status: String): Future[String]
  def removeTaxRegistrationInformation(registrationId: String): Future[Boolean]
  def updateCTRecordWithAcknowledgments(ackRef : String, ctRecord : CorporationTaxRegistration) : Future[WriteResult]
  def retrieveByAckRef(ackRef : String) : Future[Option[CorporationTaxRegistration]]
  def updateHeldToSubmitted(registrationId: String, crn: String, submissionTS: String): Future[Boolean]
  def removeTaxRegistrationById(registrationId: String): Future[Boolean]
  def updateEmail(registrationId: String, email: Email): Future[Option[Email]]
  def retrieveEmail(registrationId: String): Future[Option[Email]]
  def updateLastSignedIn(regId: String, dateTime: DateTime): Future[DateTime]
  def updateRegistrationProgress(regId: String, progress: String): Future[Option[String]]
  def getRegistrationStats(): Future[Map[String, Int]]
  def fetchHO6Information(regId: String): Future[Option[HO6RegistrationInformation]]
  def fetchDocumentStatus(regId: String): OptionT[Future, String]
  def updateRegistrationToHeld(regId: String, confRefs: ConfirmationReferences): Future[Option[CorporationTaxRegistration]]
  def retrieveAllWeekOldHeldSubmissions() : Future[List[CorporationTaxRegistration]]
  def retrieveLockedRegIDs() : Future[List[String]]
  def retrieveStatusAndExistenceOfCTUTR(ackRef: String): Future[Option[(String, Boolean)]]
  def updateRegistrationWithAdminCTReference(ackRef : String, ctUtr : String) : Future[Option[CorporationTaxRegistration]]
  def storeSessionIdentifiers(regId: String, sessionId: String, credId: String) : Future[Boolean]
  def retrieveSessionIdentifiers(regId: String) : Future[Option[SessionIds]]
  def updateTransactionId(updateFrom: String, updateTo: String): Future[String]
}

private[repositories] class MissingCTDocument(regId: String) extends NoStackTrace

class CorporationTaxRegistrationMongoRepository(mongo: () => DB)
  extends ReactiveRepository[CorporationTaxRegistration, BSONObjectID]("corporation-tax-registration-information",
    mongo,
    CorporationTaxRegistrationMongo.format,
    ReactiveMongoFormats.objectIdFormats)
  with CorporationTaxRegistrationRepository
  with AuthorisationResource[String] {

  val cTRMongo = CorporationTaxRegistrationMongo
  implicit val format = cTRMongo.oFormat
  super.indexes

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("registrationID" -> IndexType.Ascending),
      name = Some("RegIdIndex"),
      unique = false,
      sparse = false
    ),
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
    ),
    Index(
      key = Seq("status" -> IndexType.Ascending, "heldTimestamp" -> IndexType.Ascending),
      name = Some("StatusHeldTimeIndex"),
      unique = false,
      sparse = false
    ),
    Index(
      key = Seq("lastSignedIn" -> IndexType.Ascending),
      name = Some("LastSignedInIndex"),
      unique = false,
      sparse = false
    )
  )

  override def updateLastSignedIn(regId: String, dateTime: DateTime): Future[DateTime] = {
    val selector = registrationIDSelector(regId)
    val update = BSONDocument("$set" -> BSONDocument("lastSignedIn" -> dateTime.getMillis))
    collection.update(selector, update).map(_ => dateTime)
  }

  override def updateCTRecordWithAcknowledgments(ackRef : String, ctRecord: CorporationTaxRegistration): Future[WriteResult] = {
    val updateSelector = BSONDocument("confirmationReferences.acknowledgement-reference" -> BSONString(ackRef))
    collection.update(updateSelector, ctRecord, upsert = false)
  }

  override def retrieveByAckRef(ackRef: String) : Future[Option[CorporationTaxRegistration]] = {
    val query = BSONDocument("confirmationReferences.acknowledgement-reference" -> BSONString(ackRef))
    collection.find(query).one[CorporationTaxRegistration]
  }

  override def retrieveRegistrationByTransactionID(transactionID: String): Future[Option[CorporationTaxRegistration]] = {
    val selector = BSONDocument("confirmationReferences.transaction-id" -> BSONString(transactionID))
    collection.find(selector).one[CorporationTaxRegistration]
  }


  private[repositories] def registrationIDSelector(registrationID: String): BSONDocument = BSONDocument(
    "registrationID" -> BSONString(registrationID)
  )

  private[repositories] def transactionIdSelector(transactionId: String): BSONDocument = BSONDocument(
    "confirmationReferences.transaction-id" -> BSONString(transactionId)
  )

  override def updateTransactionId(updateFrom: String, updateTo: String): Future[String] = {
    val selector = transactionIdSelector(updateFrom)
    val modifier = BSONDocument("$set" -> BSONDocument("confirmationReferences.transaction-id" -> updateTo))
    collection.update(selector, modifier) map { res =>
      if (res.nModified == 0) {
        Logger.error(s"[CorporationTaxRegistrationMongoRepository] [updateTransactionId] No document with transId: $updateFrom was found")
        throw new RuntimeException("Did not update transaction ID")
      } else {
        updateTo
      }
    } recover {
      case e =>
        Logger.error(s"[CorporationTaxRegistrationMongoRepository] [updateTransactionId] Unable to update transId: $updateFrom to $updateTo", e)
        throw e
    }
  }

  override def createCorporationTaxRegistration(ctReg: CorporationTaxRegistration): Future[CorporationTaxRegistration] = {
    collection.insert(ctReg) map (_ => ctReg)
  }

  override def retrieveCorporationTaxRegistration(registrationID: String): Future[Option[CorporationTaxRegistration]] = {
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).one[CorporationTaxRegistration]
  }

  override def retrieveMultipleCorporationTaxRegistration(registrationID: String): Future[List[CorporationTaxRegistration]] = {
    val selector = registrationIDSelector(registrationID)
    collection.find(selector).cursor[CorporationTaxRegistration]().collect[List]()
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

  override def retrieveConfirmationReferences(registrationID: String) : Future[Option[ConfirmationReferences]] = {
    retrieveCorporationTaxRegistration(registrationID) map { oreg => { oreg flatMap { _.confirmationReferences } } }
  }

  override def updateConfirmationReferences(registrationID: String, confirmationReferences: ConfirmationReferences) : Future[Option[ConfirmationReferences]] = {
    retrieveCorporationTaxRegistration(registrationID) flatMap {
      case Some(registration) => collection.update(registrationIDSelector(registrationID), registration.copy(confirmationReferences = Some(confirmationReferences)), upsert = false)
        .map(_ => Some(confirmationReferences))
      case None => Future.successful(None)
    }
  }

  override def updateConfirmationReferencesAndUpdateStatus(registrationID: String, confirmationReferences: ConfirmationReferences, status: String) : Future[Option[ConfirmationReferences]] = {
    retrieveCorporationTaxRegistration(registrationID) flatMap {
      case Some(registration) => collection.update(registrationIDSelector(registrationID), registration.copy(confirmationReferences = Some(confirmationReferences), status = status), upsert = false)
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

  override def getInternalId(id: String): Future[Option[(String, String)]] = {
    retrieveCorporationTaxRegistration(id) map {
      case None => None
      case Some(m) => Some(m.registrationID -> m.internalId)
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

  override def updateRegistrationProgress(regId: String, progress: String): Future[Option[String]] = {
    retrieveCorporationTaxRegistration(regId) flatMap {
      case Some(registration) =>
        collection.update(
          registrationIDSelector(regId),
          registration.copy(registrationProgress = Some(progress)),
          upsert = false
        ).map(_ => Some(progress))
      case _ => Future.successful(None)
    }
  }

  override def getRegistrationStats(): Future[Map[String, Int]] = {
    import collection.BatchCommands.AggregationFramework._

    val matchQuery = Match(Json.obj())
    val project = Project(Json.obj(
      "status" -> 1,
      "_id" -> 0
    ))
    val group = Group(JsString("$status"))("count" -> SumValue(1))

    val metrics = collection.aggregate(matchQuery, List(project, group)) map {
      _.documents map {
        d => {
          val regime = (d \ "_id").as[String]
          val count = (d \ "count").as[Int]
          regime -> count
        }
      }
    }

    metrics map {
      _.toMap
    }
  }

  override def fetchHO6Information(regId: String): Future[Option[HO6RegistrationInformation]] = {
    retrieveCorporationTaxRegistration(regId) map (_.map{ reg =>
      HO6RegistrationInformation(reg.status, reg.companyDetails.map(_.companyName), reg.registrationProgress)
    })
  }

  override def fetchDocumentStatus(regId: String): OptionT[Future, String] = {
    OptionT(retrieveCorporationTaxRegistration(regId)) map (_.status)
  }

  override def updateRegistrationToHeld(regId: String, confRefs: ConfirmationReferences): Future[Option[CorporationTaxRegistration]] = {
    val jsonobj = BSONFormats.readAsBSONValue(Json.obj(
      "status" -> RegistrationStatus.HELD,
      "confirmationReferences" -> Json.toJson(confRefs),
      "heldTimestamp" -> Json.toJson(CorporationTaxRegistration.now)
    )).get

    val modifier = BSONDocument(
      "$set" -> jsonobj,
      "$unset" -> BSONDocument("tradingDetails" -> 1, "contactDetails" -> 1, "companyDetails" -> 1)
    )

    collection.findAndUpdate[BSONDocument, BSONDocument](registrationIDSelector(regId), modifier, fetchNewObject = true, upsert = false) map {
      _.result[CorporationTaxRegistration] flatMap {
        reg =>
          (reg.status, reg.confirmationReferences, reg.tradingDetails, reg.contactDetails, reg.companyDetails, reg.heldTimestamp) match {
            case (RegistrationStatus.HELD, Some(cRefs), None, None, None, Some(_)) if cRefs == confRefs => Some(reg)
            case _ => None
          }
      }
    }
  }

  def retrieveAllWeekOldHeldSubmissions() : Future[List[CorporationTaxRegistration]] = {
    val selector = BSONDocument(
      "status" -> RegistrationStatus.HELD,
      "heldTimestamp" -> BSONDocument("$lte" -> DateTime.now(DateTimeZone.UTC).minusWeeks(1).getMillis)
    )
    collection.find(selector).cursor[CorporationTaxRegistration]().collect[List]()
  }

  def retrieveLockedRegIDs() : Future[List[String]] = {
    val selector = BSONDocument("status" -> RegistrationStatus.LOCKED)
    val res = collection.find(selector).cursor[CorporationTaxRegistration]().collect[List]()
    res.map{ docs => docs.map(_.registrationID) }
  }

  override def retrieveStatusAndExistenceOfCTUTR(ackRef: String): Future[Option[(String, Boolean)]] = {
    for {
      maybeRegistration <- retrieveByAckRef(ackRef)
    } yield {
      for {
        document <- maybeRegistration
        etmpAckRefs <- document.acknowledgementReferences
      } yield {
        etmpAckRefs.status -> etmpAckRefs.ctUtr.isDefined
      }
    }
  }

  override def updateRegistrationWithAdminCTReference(ackRef: String, ctUtr: String): Future[Option[CorporationTaxRegistration]] = {
    val timestamp = CorporationTaxRegistration.now.toString()
    val ackRefs = AcknowledgementReferences(Some(ctUtr), timestamp, "04")

    val selector = BSONDocument("confirmationReferences.acknowledgement-reference" -> BSONString(ackRef))
    val modifier = BSONDocument("$set" -> BSONFormats.readAsBSONValue(Json.obj(
      "status" -> RegistrationStatus.ACKNOWLEDGED,
      "acknowledgementReferences" -> Json.toJson(ackRefs)(AcknowledgementReferences.format(MongoValidation))
    )).get)

    collection.findAndUpdate(selector, modifier) map {
      _.result[CorporationTaxRegistration]
    }
  }

  override def storeSessionIdentifiers(regId: String, sessionId: String, credId: String) : Future[Boolean] = {
    val selector = registrationIDSelector(regId)
    val modifier = BSONDocument(
      "$set" -> BSONFormats.readAsBSONValue(
        Json.obj("sessionIdentifiers" -> Json.toJson(SessionIds(sessionId, credId)))
      ).get
    )

    collection.update(selector, modifier) map { _.nModified == 1 }
  }

  override def retrieveSessionIdentifiers(regId: String) : Future[Option[SessionIds]] = {
    for {
      taxRegistration <- collection.find(registrationIDSelector(regId)).one[CorporationTaxRegistration]
    } yield taxRegistration flatMap (_.sessionIdentifiers)
  }

  def fetchIndexes(): Future[List[Index]] = collection.indexesManager.list()

  override def retrieveStaleDocuments(count: Int, storageThreshold: Int): Future[List[CorporationTaxRegistration]] = {
    val query = Json.obj(
      "status" -> Json.obj("$in" -> Json.arr("draft", "held", "locked")),
       "$or" -> Json.arr(
         Json.obj("lastSignedIn" -> Json.obj("$exists" -> false)),
         Json.obj("lastSignedIn" -> Json.obj("$lte" -> DateTime.now(DateTimeZone.UTC).minusDays(storageThreshold).getMillis))
       )
    )
    val ascending = Json.obj("lastSignedIn" -> 1)
    val logOnError = Cursor.ContOnError[List[CorporationTaxRegistration]]((_, ex) =>
      Logger.error(s"[retrieveStaleDocuments] Mongo failed, problem occured in collect - ex: ${ex.getMessage}")
    )

    collection.find(query)
      .sort(ascending)
      .cursor[CorporationTaxRegistration]()
      .collect[List](count, logOnError)
  }
}
