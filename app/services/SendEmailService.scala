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

import connectors.SendEmailConnector
import javax.inject.Inject
import models.SendEmailRequest
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SendEmailServiceImpl @Inject()(
        val emailConnector: SendEmailConnector
      ) extends SendEmailService

trait SendEmailService {

    val RegisterForVATTemplate = "register_your_company_register_vat_email"
    val emailConnector: SendEmailConnector

    private[services] def generateVATEmailRequest(emailAddress: Seq[String]): SendEmailRequest = {
      SendEmailRequest(
        to = emailAddress,
        templateId = RegisterForVATTemplate,
        parameters = Map(),
        force = true
      )
    }

    def sendVATEmail(emailAddress :String, regId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
      emailConnector.requestEmail(generateVATEmailRequest(Seq(emailAddress))).map {
        res => Logger.info("VAT email sent for journey id " + regId)
          res
      }
    }
  }
