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
import models.{CTRequest, Metadata}
import play.api.libs.json.JsValue
import play.api.mvc.Action
import services.MetadataService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

object MetadataController extends MetadataController {
  val metadataService = MetadataService
  val auth = AuthConnector
}

trait MetadataController extends BaseController with Authenticated {

  val metadataService: MetadataService

  def createMetadata: Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          withJsonBody[CTRequest] {
            metadata => metadataService.createMetadataRecord(Metadata.empty.copy(
              OID = context.oid))
          }
        }
  }

  def retrieveMetadata = Action.async {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) => metadataService.retrieveMetadataRecord(context.oid)
      }
  }
}
