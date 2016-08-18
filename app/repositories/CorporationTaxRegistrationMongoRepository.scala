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
import models.{ContactDetails, CompanyDetails, CorporationTaxRegistration}
import play.api.Logger
import play.mvc.Result
import reactivemongo.api.DB
import reactivemongo.bson._
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
// $COVERAGE-OFF$
trait CorporationTaxRegistrationRepository extends Repository[CorporationTaxRegistration, BSONObjectID]{
  def createCorporationTaxRegistration(metadata: CorporationTaxRegistration): Future[CorporationTaxRegistration]
  def retrieveCorporationTaxRegistration(regI: String): Future[Option[CorporationTaxRegistration]]
  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]]
  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]]
}

class CorporationTaxRegistrationMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CorporationTaxRegistration, BSONObjectID]("corporation-tax-registration-information", mongo, CorporationTaxRegistration.formats, ReactiveMongoFormats.objectIdFormats)
  with CorporationTaxRegistrationRepository
  with AuthorisationResource[String] {

    private def registrationIDSelector(registrationID: String): BSONDocument = BSONDocument(
      "registrationID" -> BSONString(registrationID)
    )

    override def createCorporationTaxRegistration(ctReg: CorporationTaxRegistration): Future[CorporationTaxRegistration] = {
      collection.insert(ctReg).map { res =>
        if (res.hasErrors) {
          Logger.error(s"Failed to store company registration data. Error: ${res.errmsg.getOrElse("")} for registration id ${ctReg.registrationID}")
        }
        ctReg
      }
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

    def retrieveContactDetails(registrationID: String): Future[Option[ContactDetails]] = {
      retrieveCorporationTaxRegistration(registrationID) map {
        case Some(registration) => registration.contactDetails
        case None => None
      }
    }

    override def getOid(id: String): Future[Option[(String, String)]] = {
      retrieveCorporationTaxRegistration(id) map {
        case None => None
        case Some(m) => Some(m.registrationID -> m.OID)
      }
    }
}
// $COVERAGE-ON$
