/*
 * Copyright 2022 HM Revenue & Customs
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

import config.{LangConstants, MicroserviceAppConfig}
import connectors.SendEmailConnector
import models.SendEmailRequest
import utils.Logging
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SendEmailService @Inject()(val emailConnector: SendEmailConnector
                                )(implicit val ec: ExecutionContext, appConfig: MicroserviceAppConfig) extends Logging {

  private[services] def template(lang: String): String =
    (lang.toLowerCase, appConfig.welshVatEmailEnabled) match {
      case (LangConstants.welsh, true) => "register_your_company_register_vat_email_cy"
      case _ => "register_your_company_register_vat_email"
    }

  private[services] def generateVATEmailRequest(emailAddress: Seq[String], lang: String): SendEmailRequest = {
    SendEmailRequest(
      to = emailAddress,
      templateId = template(lang),
      parameters = Map(),
      force = true
    )
  }

  def sendVATEmail(emailAddress: String, regId: String, lang: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    emailConnector.requestEmail(generateVATEmailRequest(Seq(emailAddress), lang)).map {
      res =>
        logger.info(s"[sendVATEmail] VAT email sent with template name: '${template(lang)}' for journey id " + regId)
        res
    }
  }
}
