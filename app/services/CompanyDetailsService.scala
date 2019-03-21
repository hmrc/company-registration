/*
 * Copyright 2019 HM Revenue & Customs
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
  lazy val corporationTaxRegistrationRepository = repositories.cTRepository
}
trait CompanyDetailsService {

  val submissionService: SubmissionService

  val corporationTaxRegistrationRepository : CorporationTaxRegistrationMongoRepository

  private[services] def convertAckRefToJsObject(ref: String): JsObject = Json.obj("acknowledgement-reference" -> ref)

  def saveTxIdAndAckRef(registrationId:String, txid:String): Future[SaveTxIdRes] = {
    corporationTaxRegistrationRepository.retrieveConfirmationReferences(registrationId).flatMap {
      optConfRefs => optConfRefs.fold[Future[SaveTxIdRes]] {
        submissionService.generateAckRef.flatMap { ackRef =>
          val confirmationRefsToBeAddedToCTRecord = ConfirmationReferences(ackRef, txid, None, None)
          corporationTaxRegistrationRepository.updateConfirmationReferences(registrationId, confirmationRefsToBeAddedToCTRecord)
            .map { _ =>
              DidNotExistInCRNowSaved(convertAckRefToJsObject(ackRef))
            }
        }
      }(confReferences => Future.successful(ExistedInCRAlready(convertAckRefToJsObject(confReferences.acknowledgementReference))))
    }.recoverWith {
      case e: Exception => Future.successful(SomethingWentWrongWhenSaving(e,registrationId,txid))
    }
  }

  def retrieveCompanyDetails(registrationID: String): Future[Option[CompanyDetails]] = {
    corporationTaxRegistrationRepository.retrieveCompanyDetails(registrationID)
  }

  def updateCompanyDetails(registrationID: String, companyDetails: CompanyDetails): Future[Option[CompanyDetails]] = {
    corporationTaxRegistrationRepository.updateCompanyDetails(registrationID, companyDetails)
  }
}

sealed trait SaveTxIdRes
case class DidNotExistInCRNowSaved(js:JsObject) extends SaveTxIdRes {
  Logger.info("Saved TxId after H02")
}
case class ExistedInCRAlready(js:JsObject) extends SaveTxIdRes
case class SomethingWentWrongWhenSaving(ex:Exception, regId:String, txId:String) extends SaveTxIdRes {
  Logger.warn(s"Something went wrong when calling the function that saves txid and gens ackreg with ex: ${ex.getMessage} for regId: $regId and txId: $txId")
}
