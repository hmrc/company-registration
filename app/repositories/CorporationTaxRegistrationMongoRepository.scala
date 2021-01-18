/*
 * Copyright 2021 HM Revenue & Customs
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

import auth.{AuthorisationResource, CryptoSCRS}
import cats.data.OptionT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import models._
import models.validation.MongoValidation
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.JodaWrites._
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.{FindAndModifyCommand, UpdateWriteResult, WriteResult}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, WriteConcern}
import reactivemongo.bson.{BSONDocument, _}
import reactivemongo.play.json.BSONFormats
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class MissingCTDocument(regId: String) extends NoStackTrace

@Singleton
class CorporationTaxRegistrationMongoRepository @Inject()(mongo: ReactiveMongoComponent,
                                                          crypto: CryptoSCRS
                                                         )(implicit val executionContext: ExecutionContext)
  extends ReactiveRepository[CorporationTaxRegistration, BSONObjectID](
    collectionName = "corporation-tax-registration-information",
    mongo = mongo.mongoConnector.db,
    domainFormat = CorporationTaxRegistration.format(MongoValidation, crypto),
    idFormat = ReactiveMongoFormats.objectIdFormats)
    with AuthorisationResource[String] {

  implicit val formats: OFormat[CorporationTaxRegistration] = CorporationTaxRegistration.oFormat(CorporationTaxRegistration.format(MongoValidation, crypto))
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

  def regIDSelector(registrationID: String): BSONDocument = BSONDocument(
    "registrationID" -> BSONString(registrationID)
  )

  def transIdSelector(transactionId: String): BSONDocument = BSONDocument(
    "confirmationReferences.transaction-id" -> BSONString(transactionId)
  )

  def ackRefSelector(ackRef: String): BSONDocument = BSONDocument(
    "confirmationReferences.acknowledgement-reference" -> BSONString(ackRef)
  )

  def findAndUpdate(selector: BSONDocument, modifier: BSONDocument, fetchNewObject: Boolean): Future[FindAndModifyCommand.Result[collection.pack.type]] = {
    collection.findAndUpdate[BSONDocument, BSONDocument](
      selector = selector,
      update = modifier,
      fetchNewObject = fetchNewObject,
      upsert = false,
      sort = None,
      fields = None,
      bypassDocumentValidation = false,
      writeConcern = WriteConcern.Acknowledged,
      maxTime = None,
      collation = None,
      arrayFilters = Seq()
    )
  }

  def update(selector: BSONDocument, key: String, value: JsValue): Future[UpdateWriteResult] = {
    collection.update.one(
      q = selector,
      u = Json.obj(fields = "$set" -> Json.obj(key -> value)),
      upsert = false
    ) filter (_.n == 1)
  }

  def unsetFields(selector: BSONDocument, fields: JsObject): Future[UpdateWriteResult] = {
    collection.update.one(
      q = selector,
      u = Json.obj(fields = "$unset" -> fields),
      upsert = false
    ) filter (_.n == 1)
  }

  def findBySelector(selector: BSONDocument): Future[Option[CorporationTaxRegistration]] = {
    collection.find[BSONDocument, CorporationTaxRegistration](
      selector = selector,
      projection = None
    ).one[CorporationTaxRegistration]
  }

  def retrieveMultipleCorporationTaxRegistration(registrationID: String): Future[List[CorporationTaxRegistration]] = {
    collection.find[BSONDocument, CorporationTaxRegistration](
      selector = regIDSelector(registrationID),
      projection = None
    ).cursor[CorporationTaxRegistration]().collect[List](Int.MaxValue, Cursor.FailOnError())
  }

  def returnGroupsBlock(registrationID: String): Future[Option[Groups]] = {
    findBySelector(regIDSelector(registrationID))
      .map(ctDoc => ctDoc.getOrElse {
        throw new Exception("[returnGroupsBlock] ctDoc does not exist")
      }.groups)
  }

  def deleteGroupsBlock(registrationID: String): Future[Boolean] = {
    unsetFields(regIDSelector(registrationID), Json.obj("groups" -> 1))
      .map {
        result =>
          if (result.ok) {
            true
          }
          else {
            throw new Exception(s"[deleteGroupsBlock] no Delete occurred Document was not found for regId: $registrationID")
          }
      }
  }

  def updateGroups(registrationID: String, groups: Groups): Future[Groups] = {
    val json = Json.toJson(groups)(Groups.formats(MongoValidation, crypto))
    update(regIDSelector(registrationID), "groups", json).map {
      updateWriteResult =>
        if (updateWriteResult.n == 1) {
          groups
        } else {
          throw new Exception(s"[updateGroups] failed for regId: $registrationID because a record was not found")
        }
    }
  }

  def updateLastSignedIn(regId: String, dateTime: DateTime): Future[DateTime] = {
    val json = Json.toJson(dateTime.getMillis)
    update(regIDSelector(regId), "lastSignedIn", json).map(_ => dateTime)
  }

  def updateCTRecordWithAcknowledgments(ackRef: String, ctRecord: CorporationTaxRegistration): Future[WriteResult] = {
    collection.update.one(ackRefSelector(ackRef), ctRecord, upsert = false)
  }

  def updateTransactionId(updateFrom: String, updateTo: String): Future[String] = {
    update(
      transIdSelector(updateFrom),
      "confirmationReferences.transaction-id",
      Json.toJson(updateTo)
    ) map { res =>
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

  def createCorporationTaxRegistration(ctReg: CorporationTaxRegistration): Future[CorporationTaxRegistration] = {
    collection.insert.one(ctReg) map (_ => ctReg)
  }

  def getExistingRegistration(registrationID: String): Future[CorporationTaxRegistration] = {
    findBySelector(regIDSelector(registrationID)).map {
      _.getOrElse {
        Logger.warn(s"[getExistingRegistration] No Document Found for RegId: $registrationID")
        throw new MissingCTDocument(registrationID)
      }
    }
  }

  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).semiflatMap {
      data =>
        collection.update.one(regIDSelector(registrationID), data.copy(companyDetails = Some(companyDetails)), upsert = false)
          .map(_ => companyDetails)
    }.value
  }

  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).subflatMap {
      data => data.companyDetails
    }.value
  }

  def retrieveAccountingDetails(registrationID: String): Future[Option[AccountingDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).subflatMap {
      data => data.accountingDetails
    }.value
  }

  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetails): Future[Option[AccountingDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).semiflatMap {
      data =>
        collection.update.one(regIDSelector(registrationID), data.copy(accountingDetails = Some(accountingDetails)), upsert = false)
          .map(_ => accountingDetails)
    }.value
  }

  def retrieveTradingDetails(registrationID: String): Future[Option[TradingDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).subflatMap {
      data => data.tradingDetails
    }.value
  }

  def updateTradingDetails(registrationID: String, tradingDetails: TradingDetails): Future[Option[TradingDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).semiflatMap {
      data =>
        collection.update.one(regIDSelector(registrationID), data.copy(tradingDetails = Some(tradingDetails)), upsert = false)
          .map(_ => tradingDetails)
    }.value
  }

  def retrieveContactDetails(registrationID: String): Future[Option[ContactDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).subflatMap {
      data => data.contactDetails
    }.value
  }

  def updateContactDetails(registrationID: String, contactDetails: ContactDetails): Future[Option[ContactDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).semiflatMap {
      data =>
        collection.update.one(regIDSelector(registrationID), data.copy(contactDetails = Some(contactDetails)), upsert = false)
          .map(_ => contactDetails)
    }.value
  }

  def retrieveConfirmationReferences(registrationID: String): Future[Option[ConfirmationReferences]] = {
    findBySelector(regIDSelector(registrationID)) map { oreg =>
      oreg flatMap {
        _.confirmationReferences
      }
    }
  }

  def updateConfirmationReferences(registrationID: String, confirmationReferences: ConfirmationReferences): Future[Option[ConfirmationReferences]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).semiflatMap {
      data =>
        collection.update.one(regIDSelector(registrationID), data.copy(confirmationReferences = Some(confirmationReferences)), upsert = false)
          .map(_ => confirmationReferences)
    }.value
  }

  def updateConfirmationReferencesAndUpdateStatus(registrationID: String, confirmationReferences: ConfirmationReferences, status: String): Future[Option[ConfirmationReferences]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).semiflatMap {
      data =>
        collection.update.one(regIDSelector(registrationID), data.copy(confirmationReferences = Some(confirmationReferences), status = status), upsert = false)
          .map(_ => confirmationReferences)
    }.value
  }

  def updateCompanyEndDate(registrationID: String, model: AccountPrepDetails): Future[Option[AccountPrepDetails]] = {
    OptionT(findBySelector(regIDSelector(registrationID))).semiflatMap {
      data =>
        collection.update.one(regIDSelector(registrationID), data.copy(accountsPreparation = Some(model)), upsert = false)
          .map(_ => model)
    }.value
  }

  def updateSubmissionStatus(registrationID: String, status: String): Future[String] = {
    val modifier = BSONDocument("$set" -> BSONDocument("status" -> status))
    findAndUpdate(regIDSelector(registrationID), modifier, fetchNewObject = true) map { r =>
      (r.result[JsValue].get \ "status").as[String]
    }
  }

  def removeTaxRegistrationInformation(registrationId: String): Future[Boolean] = {
    val modifier = BSONDocument("$unset" -> BSONDocument("tradingDetails" -> 1, "contactDetails" -> 1, "companyDetails" -> 1))
    findAndUpdate(regIDSelector(registrationId), modifier, fetchNewObject = true) map { r =>
      val corpTaxModel = r.result[CorporationTaxRegistration].getOrElse(throw new MissingCTDocument(registrationId))
      List(corpTaxModel.tradingDetails, corpTaxModel.contactDetails, corpTaxModel.companyDetails).forall(_.isEmpty)
    }
  }

  def removeUnnecessaryRegistrationInformation(registrationId: String): Future[Boolean] = {
    val modifier = BSONDocument("$unset" -> BSONDocument("confirmationReferences" -> 1, "accountingDetails" -> 1,
      "accountsPreparation" -> 1, "verifiedEmail" -> 1, "companyDetails" -> 1, "tradingDetails" -> 1, "contactDetails" -> 1))
    findAndUpdate(regIDSelector(registrationId), modifier, fetchNewObject = false) map { res =>
      res.lastError.flatMap {
        _.err.map { e =>
          Logger.warn(s"[removeUnnecessaryInformation] - an error occurred for regId: $registrationId with error: $e")
          false
        }
      }.getOrElse {
        if (res.value.isDefined) {
          true
        } else {
          Logger.warn(s"[removeUnnecessaryInformation] - attempted to remove keys but no doc was found for regId: $registrationId")
          true
        }
      }
    }
  }

  def updateHeldToSubmitted(registrationId: String, crn: String, submissionTS: String): Future[Boolean] = {
    getExistingRegistration(registrationId) flatMap {
      ct =>
        import RegistrationStatus.SUBMITTED
        val updatedDoc = ct.copy(
          status = SUBMITTED,
          crn = Some(crn),
          submissionTimestamp = Some(submissionTS),
          accountingDetails = None,
          accountsPreparation = None
        )
        collection.update.one(
          regIDSelector(registrationId),
          updatedDoc,
          upsert = false
        ).map(_ => true)
    }
  }

  def getInternalId(id: String): Future[(String, String)] =
    getExistingRegistration(id).map { c => c.registrationID -> c.internalId }


  def removeTaxRegistrationById(registrationId: String): Future[Boolean] = {
    getExistingRegistration(registrationId) flatMap {
      _ => collection.delete(ordered = true).one(regIDSelector(registrationId), None, None) map { _ => true }

    }
  }

  def updateEmail(registrationId: String, email: Email): Future[Option[Email]] = {
    OptionT(findBySelector(regIDSelector(registrationId))).semiflatMap {
      registration =>
        collection.update.one(
          regIDSelector(registrationId),
          registration.copy(verifiedEmail = Some(email)),
          upsert = false
        ).map(_ => email)
    }.value
  }

  def retrieveEmail(registrationId: String): Future[Option[Email]] = {
    OptionT(findBySelector(regIDSelector(registrationId))).subflatMap {
      registration => registration.verifiedEmail
    }.value
  }

  def updateRegistrationProgress(regId: String, progress: String): Future[Option[String]] = {
    OptionT(findBySelector(regIDSelector(regId))).semiflatMap {
      registration =>
        collection.update.one(
          regIDSelector(regId),
          registration.copy(registrationProgress = Some(progress)),
          upsert = false
        ).map(_ => progress)
    }.value
  }

  def getRegistrationStats: Future[Map[String, Int]] = {
    // needed to make it pick up the index
    val matchQuery: collection.PipelineOperator = collection.BatchCommands.AggregationFramework.Match(Json.obj())
    val project = collection.BatchCommands.AggregationFramework.Project(Json.obj(
      "status" -> 1,
      "_id" -> 0
    ))
    // calculate the regime counts
    val group = collection.BatchCommands.AggregationFramework.Group(JsString("$status"))("count" -> collection.BatchCommands.AggregationFramework.SumAll)

    val query = collection.aggregateWith[JsObject]()(_ => (matchQuery, List(project, group)))
    val fList = query.collect(Int.MaxValue, Cursor.FailOnError[List[JsObject]]())
    fList.map {
      _.map {
        statusDoc => {
          val regime = (statusDoc \ "_id").as[String]
          val count = (statusDoc \ "count").as[Int]
          regime -> count
        }
      }.toMap
    }
  }

  def fetchHO6Information(regId: String): Future[Option[HO6RegistrationInformation]] = {
    findBySelector(regIDSelector(regId)) map (_.map { reg =>
      HO6RegistrationInformation(reg.status, reg.companyDetails.map(_.companyName), reg.registrationProgress)
    })
  }

  def fetchDocumentStatus(regId: String): OptionT[Future, String] = for {
    status <- OptionT(findBySelector(regIDSelector(regId))).map(_.status)
    _ = Logger.info(s"[FetchDocumentStatus] status for reg id $regId is $status ")
  } yield status

  def updateRegistrationToHeld(regId: String, confRefs: ConfirmationReferences): Future[Option[CorporationTaxRegistration]] = {

    val jsonobj = BSONFormats.readAsBSONValue(Json.obj(
      "status" -> RegistrationStatus.HELD,
      "confirmationReferences" -> Json.toJson(confRefs),
      "heldTimestamp" -> Json.toJson(CorporationTaxRegistration.now)
    )).get

    val modifier = BSONDocument(
      "$set" -> jsonobj,
      "$unset" -> BSONDocument("tradingDetails" -> 1, "contactDetails" -> 1, "companyDetails" -> 1, "groups" -> 1)
    )

    findAndUpdate(regIDSelector(regId), modifier, fetchNewObject = true) map {
      _.result[CorporationTaxRegistration] flatMap {
        reg =>
          (reg.status, reg.confirmationReferences, reg.tradingDetails, reg.contactDetails, reg.companyDetails, reg.heldTimestamp) match {
            case (RegistrationStatus.HELD, Some(cRefs), None, None, None, Some(_)) if cRefs == confRefs => Some(reg)
            case _ => None
          }
      }
    }
  }

  def retrieveAllWeekOldHeldSubmissions(): Future[List[CorporationTaxRegistration]] = {
    val selector = BSONDocument(
      "status" -> RegistrationStatus.HELD,
      "heldTimestamp" -> BSONDocument("$lte" -> DateTime.now(DateTimeZone.UTC).minusWeeks(1).getMillis)
    )
    collection.find[BSONDocument, CorporationTaxRegistration](
      selector = selector,
      projection = None
    ).cursor[CorporationTaxRegistration]().collect[List](Int.MaxValue, Cursor.FailOnError())
  }

  def retrieveLockedRegIDs(): Future[List[String]] = {
    val selector = BSONDocument("status" -> RegistrationStatus.LOCKED)
    val res = collection.find[BSONDocument, CorporationTaxRegistration](
      selector = selector,
      projection = None
    ).cursor[CorporationTaxRegistration]().collect[List](Int.MaxValue, Cursor.FailOnError())
    res.map { docs => docs.map(_.registrationID) }
  }

  def retrieveStatusAndExistenceOfCTUTR(ackRef: String): Future[Option[(String, Boolean)]] = {
    for {
      maybeRegistration <- findBySelector(ackRefSelector(ackRef))
    } yield {
      for {
        document <- maybeRegistration
        etmpAckRefs <- document.acknowledgementReferences
      } yield {
        etmpAckRefs.status -> etmpAckRefs.ctUtr.isDefined
      }
    }
  }

  def updateRegistrationWithAdminCTReference(ackRef: String, ctUtr: String): Future[Option[CorporationTaxRegistration]] = {
    val timestamp = CorporationTaxRegistration.now.toString()
    val ackRefs = AcknowledgementReferences(Some(ctUtr), timestamp, "04")

    val selector = BSONDocument("confirmationReferences.acknowledgement-reference" -> BSONString(ackRef))
    val modifier = BSONDocument("$set" -> BSONFormats.readAsBSONValue(Json.obj(
      "status" -> RegistrationStatus.ACKNOWLEDGED,
      "acknowledgementReferences" -> Json.toJson(ackRefs)(AcknowledgementReferences.format(MongoValidation, crypto))
    )).get)

    findAndUpdate(selector, modifier, fetchNewObject = false) map {
      _.result[CorporationTaxRegistration]
    }
  }

  def storeSessionIdentifiers(regId: String, sessionId: String, credId: String): Future[Boolean] = {
    val json = Json.toJson(
      SessionIds(sessionId.format(crypto), credId))(SessionIds.format(crypto)
    )

    update(regIDSelector(regId), "sessionIdentifiers", json) map {
      _.nModified == 1
    }
  }

  def retrieveSessionIdentifiers(regId: String): Future[Option[SessionIds]] = {
    for {
      taxRegistration <- findBySelector(regIDSelector(regId))
    } yield taxRegistration flatMap (_.sessionIdentifiers)
  }

  def fetchIndexes(): Future[List[Index]] = collection.indexesManager.list()

  def retrieveStaleDocuments(count: Int, storageThreshold: Int): Future[List[CorporationTaxRegistration]] = {
    val query = Json.obj(
      "status" -> Json.obj("$in" -> Json.arr("draft", "held", "locked")),
      "confirmationReferences.payment-reference" -> Json.obj("$exists" -> false),
      "lastSignedIn" -> Json.obj("$lt" -> DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(storageThreshold).getMillis),
      "$or" -> Json.arr(
        Json.obj("heldTimestamp" -> Json.obj("$exists" -> false)),
        Json.obj("heldTimestamp" -> Json.obj("$lt" -> DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(storageThreshold).getMillis))
      )
    )
    val ascending = Json.obj("lastSignedIn" -> 1)
    val logOnError = Cursor.ContOnError[List[CorporationTaxRegistration]]((_, ex) =>
      Logger.error(s"[retrieveStaleDocuments] Mongo failed, problem occured in collect - ex: ${ex.getMessage}")
    )

    collection.find[JsObject, CorporationTaxRegistration](
      selector = query,
      projection = None
    ).sort(ascending)
      .batchSize(count)
      .cursor[CorporationTaxRegistration]()
      .collect[List](count, logOnError)
  }
}