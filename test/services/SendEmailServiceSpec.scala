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

import config.{LangConstants, MicroserviceAppConfig}
import connectors.SendEmailConnector
import helpers.BaseSpec
import mocks.AuthorisationMocks
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SendEmailServiceSpec extends BaseSpec with AuthorisationMocks {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/test-path")

  val mockSendEmailConnector: SendEmailConnector = mock[SendEmailConnector]
  val mockThresholdService: ThresholdService = mock[ThresholdService]
  val emailService = new SendEmailService(mockSendEmailConnector,mockThresholdService)(global)

  val regId = "reg1234"
  val templateName = "register_your_company_register_vat_email_v2"
  val testEmail = "myTestEmail@test.test"

  override def beforeEach() {
    reset(mockSendEmailConnector)
  }

  val testRequest = SendEmailRequest(
    to = Seq(testEmail),
    templateId = templateName,
    parameters = Map("vatThreshold" -> "85,000"),
    force = true
  )

  "calling .sendEmail" when {

    "call to connector is successful" must {

      "return the connector result" in {

        when(mockSendEmailConnector.requestEmail(ArgumentMatchers.eq(testRequest))(ArgumentMatchers.eq(hc)))
          .thenReturn(Future.successful(true))
        when(mockThresholdService.formattedVatThreshold).thenReturn("85,000")
        await(emailService.sendVATEmail(testEmail, regId)) mustBe true
      }
    }

    "call to connector is fails" must {

      "throw the Exception" in {

        when(mockSendEmailConnector.requestEmail(ArgumentMatchers.eq(testRequest))(ArgumentMatchers.eq(hc)))
          .thenReturn(Future.failed(new Exception("fooBarBang")))

        intercept[Exception](await(emailService.sendVATEmail(testEmail, regId))).getMessage mustBe "fooBarBang"
      }
    }
  }

  "calling .generateVATEmailRequest()" when {

    "return a EmailRequest with the correct email (EN)" in {
      emailService.generateVATEmailRequest(Seq(testEmail),vatThresholdValue = "85,000") mustBe testRequest
    }

    "construct the correct JSON" in {

      val result = emailService.generateVATEmailRequest(Seq("test@email.com"),vatThresholdValue = "85,000")

      val resultAsJson = Json.toJson(result)

      val expectedJson = Json.parse {
        s"""
           |{
           |  "to":["test@email.com"],
           |  "templateId":"${templateName}",
           |  "parameters":{"vatThreshold":"85,000"},
           |  "force":true
           |}
         """.stripMargin
      }

      resultAsJson mustBe expectedJson
    }
  }
}