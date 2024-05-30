/*
 * Copyright 2024 HM Revenue & Customs
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

class SendEmailService @Inject()(val emailConnector: SendEmailConnector,
                                 val thresholdService: ThresholdService
                                )(implicit val ec: ExecutionContext) extends Logging {

  private val template: String = "register_your_company_register_vat_email_v2"

  private[services] def generateVATEmailRequest(emailAddress: Seq[String], vatThresholdValue: String): SendEmailRequest =
    SendEmailRequest(
      to = emailAddress,
      templateId = template,
      parameters = Map("vatThreshold" -> vatThresholdValue),
      force = true
    )

  def sendVATEmail(emailAddress: String, regId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
        val formattedVatThreshold = thresholdService.formattedVatThreshold
        emailConnector.requestEmail(generateVATEmailRequest(Seq(emailAddress),formattedVatThreshold)).map {
          res =>
            logger.info(s"[sendVATEmail] VAT email sent with template name: '$template' with threshold value: '$formattedVatThreshold'  journey id " + regId)
            res
        }
  }
}