/*
 * Copyright 2017 HM Revenue & Customs
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

import connectors.{AuthConnector, SendEmailConnector}
import models.SendEmailRequest
import play.api.mvc.{AnyContent, Request}
import sun.security.krb5.internal.AuthContext
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier


object SendEmailService extends SendEmailService with ServicesConfig {
  val microserviceAuthConnector = AuthConnector
  val emailConnector = SendEmailConnector

}
  trait SendEmailService {

    val RegisterForVATTemplate = "register_your_company_register_vat_email"


    val microserviceAuthConnector: AuthConnector
    val emailConnector: SendEmailConnector

      private[services] def generateVATEmailRequest(emailAddress: Seq[String]): SendEmailRequest = {
        SendEmailRequest(
          to = emailAddress,
          templateId = RegisterForVATTemplate,
          parameters = Map(),
          force = true
        )
    }

    def sendVATEmail(emailAddress :String)(implicit hc: HeaderCarrier): Future[Boolean] = {
      SendEmailConnector.requestEmail(generateVATEmailRequest(Seq(emailAddress)))
    }
  }
