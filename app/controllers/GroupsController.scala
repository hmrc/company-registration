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

package controllers

import auth._
import javax.inject.{Inject, Singleton}
import models.validation.APIValidation
import models.{GroupNameListValidator, Groups}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.GroupsService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GroupsController @Inject()(val authConnector: AuthConnector,
                                 val groupsService: GroupsService,
                                 val cryptoSCRS: CryptoSCRS,
                                 val repositories: Repositories,
                                 controllerComponents: ControllerComponents
                                )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with AuthorisedActions {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository


  private[controllers] def groupsBlockValidation(groups: Groups): Either[Groups, Throwable] = {
    groups match {
      case Groups(_, None, address@Some(_), _) => Right(new Exception("[groupsBlockValidation] name of company skipped"))
      case Groups(_, nameOfCompany@Some(_), None, Some(_)) => Right(new Exception("[groupsBlockValidation] address skipped"))
      case Groups(_, None, None, Some(_)) => Right(new Exception("[groupsBlockValidation] name of the company and address skipped"))
      case _ =>
        Logger.info("Groups block in correct state to use")
        Left(groups)
    }
  }

  def deleteBlock(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    implicit request =>
      groupsService.deleteGroups(registrationID).map { deleted =>
        if (deleted) {
          NoContent
        } else {
          Logger.warn("[deleteBlock] groups did return true indicating deletion of block, returning 500")
          InternalServerError
        }
      }
  }

  def saveBlock(registrationID: String): Action[JsValue] = AuthorisedAction(registrationID).async[JsValue](parse.json) {
    implicit request =>
      withJsonBody[Groups] { groups =>
        groupsBlockValidation(groups).fold(
          validatedBlock => groupsService.updateGroups(registrationID, groups).map { updatedGroups =>
            Ok(Json.toJson(updatedGroups)(Groups.formats(APIValidation, cryptoSCRS)))
          }, exceptionOccurredValidatingBlock => throw exceptionOccurredValidatingBlock
        )
      }(implicitly, implicitly, Groups.formats(APIValidation, cryptoSCRS))
  }

  def getBlock(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    implicit request =>
      groupsService.returnGroups(registrationID).map {
        optGroupsReturned =>
          optGroupsReturned
            .fold[Result](NoContent)(groups =>
              Ok(Json.toJson(groups)(Groups.formats(APIValidation, cryptoSCRS))))
      }
  }


  def validateListOfNamesAgainstGroupNameValidation: Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[Seq[String]] { listOfNames =>
        if (listOfNames.isEmpty) {
          Future.successful(NoContent)
        } else {
          Future.successful(Ok(Json.toJson(listOfNames)(GroupNameListValidator.formats)))
        }
      }(implicitly, implicitly, GroupNameListValidator.formats)
  }
}