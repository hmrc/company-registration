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

package controllers

import auth.{Authenticated, LoggedIn, NotLoggedIn}
import connectors.AuthConnector
import play.api.mvc.Action
import services.UserAccessService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object UserAccessController extends UserAccessController {
  override val auth = AuthConnector
  val userAccessService = UserAccessService
}

trait UserAccessController extends BaseController with Authenticated{

  val userAccessService : UserAccessService

  def checkUserAccess = Action.async {
    implicit request => authenticated{
      case NotLoggedIn => Future.successful(Forbidden)
      case LoggedIn(context) => userAccessService.checkUserAccess(context.oid) map {
          case Right(res) => Ok(res)
          case Left(_) => TooManyRequest
      }
    }
  }
}
