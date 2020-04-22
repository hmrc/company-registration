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

import javax.inject.Inject
import models.{CompanyDetails, ConfirmationReferences}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class CompanyDetailsServiceImpl @Inject()(val repositories: Repositories,
                                          val submissionService: SubmissionService) extends CompanyDetailsService {
  lazy val corporationTaxRegistrationMongoRepository = repositories.cTRepository
}
trait CompanyDetailsService {

  val submissionService: SubmissionService

  val corporationTaxRegistrationMongoRepository : CorporationTaxRegistrationMongoRepository

  private[services] def convertAckRefToJsObject(ref: String): JsObject = Json.obj("acknowledgement-reference" -> ref)

  def saveTxIdAndAckRef(registrationId:String, txid:String): Future[JsObject] = {
    ( for {
    optConfRefs <- corporationTaxRegistrationMongoRepository.retrieveConfirmationReferences(registrationId)
     updated <- optConfRefs.fold(submissionService.generateAckRef
       .map{ackRef => ConfirmationReferences(ackRef,txid,None,None)}){ alreadyHasConfRefs => Future.successful(alreadyHasConfRefs.copy(transactionId =  txid))
     }
    confRefs <- corporationTaxRegistrationMongoRepository.updateConfirmationReferences(registrationId, updated)
   } yield convertAckRefToJsObject(updated.acknowledgementReference) )
      .recoverWith{
      case e: Exception =>
        Logger.error(s"[CompanyDetailsService] saveTxIdAndAckRef threw an exception ${e.getMessage}  for txid: $txid, regId: $registrationId")
       throw e
    }
  }

  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    corporationTaxRegistrationMongoRepository.retrieveCompanyDetails(registrationID)
  }

  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]] = {
    corporationTaxRegistrationMongoRepository.updateCompanyDetails(registrationID, companyDetails)
  }
}