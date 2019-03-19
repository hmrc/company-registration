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
import mocks.{AuthorisationMocks, SCRSMocks}
import models._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec


class SendEmailServiceSpec extends UnitSpec with SCRSMocks with AuthorisationMocks with MockitoSugar  with BeforeAndAfterEach  {

  implicit val hc = HeaderCarrier()
  implicit val req = FakeRequest("GET", "/test-path")

  override def beforeEach() {
    resetMocks()
  }

  val mockSendEmailConnector = mock[SendEmailConnector]

  def resetMocks() = {
    reset(mockAuthConnector)
    reset(mockSendEmailConnector)

  }

  trait Setup {

    val emailService = new SendEmailService {
      val microserviceAuthConnector = mockAuthConnector
      val emailConnector = mockSendEmailConnector

    }
  }

    "generateEmailRequest" should {

    val testEmail = "myTestEmail@test.test"
    val testRequest = SendEmailRequest(
      to = Seq(testEmail),
      templateId = "register_your_company_register_vat_email",
      parameters = Map(),
      force = true
    )

    "return a EmailRequest with the correct email " in new Setup {
      emailService.generateVATEmailRequest(Seq(testEmail)) shouldBe testRequest
    }
  }


  "Generating an email request" should {
    "construct the correct JSON" in new Setup {
      val result = emailService.generateVATEmailRequest(Seq("test@email.com"))

      val resultAsJson = Json.toJson(result)

      val expectedJson = Json.parse{
        s"""
           |{
           |  "to":["test@email.com"],
           |  "templateId":"register_your_company_register_vat_email",
           |  "parameters":{},
           |  "force":true
           |}
         """.stripMargin
      }
      resultAsJson shouldBe expectedJson
    }
  }
}