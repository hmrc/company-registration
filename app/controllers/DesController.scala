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

import config.WSHttp
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HttpGet, HttpPost}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object DesController extends DesController with ServicesConfig {
  //$COVERAGE-OFF$
  val desUrl = getConfString("des-service.des-submission", throw new Exception("could not find des-service.des-submission"))
  //$COVERAGE-ON$
  val http = WSHttp
}

case class HttpResponse(status: Int, msg: String)

object HttpResponse {
  implicit val format = Json.format[HttpResponse]
}

trait DesController extends BaseController {

  val desUrl : String
  val http : HttpGet with HttpPost

  val submit = Action.async(parse.json) { implicit request =>
    withJsonBody[JsValue] {
      json => http.POST[JsValue, HttpResponse](desUrl, json).map{
        res => res.status match {
          case ACCEPTED => Accepted
          case BAD_REQUEST => BadRequest
        }
      }
    }
  }

}
