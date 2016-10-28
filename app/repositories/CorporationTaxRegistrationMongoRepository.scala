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
import reactivemongo.bson._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait CorporationTaxRegistrationRepository extends Repository[CorporationTaxRegistration, BSONObjectID]{
  def createCorporationTaxRegistration(metadata: CorporationTaxRegistration): Future[CorporationTaxRegistration]
  def retrieveCorporationTaxRegistration(regI: String): Future[Option[CorporationTaxRegistration]]
  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]]
  def retrieveAccountingDetails(registrationID: String): Future[Option[AccountingDetails]]
  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]]
  def updateAccountingDetails(registrationID: String, accountingDetails: AccountingDetails): Future[Option[AccountingDetails]]
  def retrieveTradingDetails(registrationID : String) : Future[Option[TradingDetails]]
  def updateTradingDetails(registrationID : String, tradingDetails: TradingDetails) : Future[Option[TradingDetails]]
  def updateAcknowledgementRef(registrationID: String, acknowledgementRef: String): Future[Option[String]]
  def updateContactDetails(registrationID: String, contactDetails: ContactDetails): Future[Option[ContactDetails]]
  def retrieveConfirmationReference(registrationID: String) : Future[Option[ConfirmationReferences]]
  def updateConfirmationReferences(registrationID: String, confirmationReferences: ConfirmationReferences) : Future[Option[ConfirmationReferences]]
  def retrieveContactDetails(registrationID: String): Future[Option[ContactDetails]]
  def retrieveAcknowledgementRef(registrationID: String): Future[Option[String]]
  def updateCompanyEndDate(registrationID: String, model: AccountsPreparationDate): Future[Option[AccountsPreparationDate]]
  def updateSubmissionStatus(registrationID: String, status: String): Future[String]
  def removeTaxRegistrationInformation(registrationId: String): Future[Boolean]
}

class CorporationTaxRegistrationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CorporationTaxRegistration, BSONObjectID]("corporation-tax-registration-information", mongo, CorporationTaxRegistration.formats, ReactiveMongoFormats.objectIdFormats)
  with CorporationTaxRegistrationRepository
  with AuthorisationResource[String] {

    private def registrationIDSelector(registrationID: String): BSONDocument = BSONDocument(
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
      val doc = accountingDetails.startDateOfBusiness.isDefined match {
        case true => BSONDocument(
          "$set" -> BSONDocument(
            "accountingDetails.accountingDateStatus" -> accountingDetails.accountingDateStatus,
            "accountingDetails.startDateOfBusiness" -> accountingDetails.startDateOfBusiness.get))
        case false =>
          BSONDocument(
          "$set" -> BSONDocument("accountingDetails.accountingDateStatus" -> accountingDetails.accountingDateStatus),
          "$unset" -> BSONDocument("accountingDetails.startDateOfBusiness" -> 1))
      }

      retrieveCorporationTaxRegistration(registrationID).flatMap {
        case Some(data) => collection.update(registrationIDSelector(registrationID), doc, upsert = false).map(_ => Some(accountingDetails))
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

  // TODO remove this
    override def updateAcknowledgementRef(registrationID: String, acknowledgementRef: String): Future[Option[String]] = {
      retrieveCorporationTaxRegistration(registrationID) flatMap {
        case Some(reg) => collection.update(
                                             registrationIDSelector(registrationID),
                                             reg.copy(confirmationReferences = Some(ConfirmationReferences(acknowledgementRef,"","",""))),
                                             upsert = false)
            .map(_ => Some(acknowledgementRef))
        case None => Future.successful(None)
      }
    }

  // TODO remove this
  override def retrieveAcknowledgementRef(registrationID: String): Future[Option[String]] = {

    retrieveCorporationTaxRegistration(registrationID) map { oreg => {
        oreg flatMap {
          reg => reg.confirmationReferences map { _.acknowledgementReference }
        }
      }
    }
  }
  override def updateCompanyEndDate(registrationID: String, model: AccountsPreparationDate): Future[Option[AccountsPreparationDate]] = {
    retrieveCorporationTaxRegistration(registrationID) flatMap {
      case Some(ct) =>
        collection.update(registrationIDSelector(registrationID), ct.copy(accountsPreparation = Some(model)), upsert = false)
          .map(_=>Some(model))
        case None => Future.successful(None)
    }
  }

  override def updateSubmissionStatus(registrationID: String, status: String): Future[String] = {
    val modifier = BSONDocument("$set" -> BSONDocument("status" -> status))
    collection.findAndUpdate(registrationIDSelector(registrationID), modifier, fetchNewObject = false, upsert = false) map { r =>
      (r.result[JsValue].get \ "status").as[String]
    }
  }

  override def removeTaxRegistrationInformation(registrationId: String): Future[Boolean] = {
    val modifier = BSONDocument("$unset" -> BSONDocument("tradingDetails" -> 1))
    collection.findAndUpdate(registrationIDSelector(registrationId), modifier, fetchNewObject = false, upsert = false) map { r =>
      (r.result[JsValue].get \ "tradingDetails").asOpt[JsObject].fold(true)(_ => false)
    }
  }


  override def getOid(id: String): Future[Option[(String, String)]] = {
    retrieveCorporationTaxRegistration(id) map {
      case None => None
      case Some(m) => Some(m.registrationID -> m.OID)
    }
  }

  def dropCollection = {
    collection.drop()
  }
}
