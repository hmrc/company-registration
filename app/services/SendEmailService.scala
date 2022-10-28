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

import connectors.SendEmailConnector
import models.SendEmailRequest
import uk.gov.hmrc.http.HeaderCarrier
import utils.Logging

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SendEmailService @Inject()(val emailConnector: SendEmailConnector
                                )(implicit val ec: ExecutionContext) extends Logging {

  val template: String = "register_your_company_register_vat_email"

  private[services] def generateVATEmailRequest(emailAddress: Seq[String]): SendEmailRequest =
    SendEmailRequest(
      to = emailAddress,
      templateId = template,
      parameters = Map(),
      force = true
    )

  def sendVATEmail(emailAddress: String, regId: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    emailConnector.requestEmail(generateVATEmailRequest(Seq(emailAddress))).map {
      res =>
        logger.info(s"[sendVATEmail] VAT email sent with template name: '$template' for journey id " + regId)
        res
    }
}
