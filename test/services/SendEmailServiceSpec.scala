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

import connectors._

import models._
import connectors.SendEmailConnector
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}


class SendEmailServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication with BeforeAndAfterEach  {

  implicit val hc = HeaderCarrier()
  implicit val req = FakeRequest("GET", "/test-path")

  override def beforeEach() {
    resetMocks()
  }

  val mockAuthConnector = mock[AuthConnector]
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

  "SendEmailService" should {
    "use the correct Auth connector" in {
      SendEmailService.microserviceAuthConnector shouldBe AuthConnector
    }
    "use the correct Email connector" in {
      SendEmailService.emailConnector shouldBe SendEmailConnector
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
