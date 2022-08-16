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

package api

import itutil.{IntegrationSpecBase, LoginStub, RequestFinder, WiremockHelper}
import play.api.Application
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderNames => GovHeaderNames}


class CorporationTaxRegistrationControllerISpec extends IntegrationSpecBase with LoginStub with RequestFinder {
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.business-registration.host" -> s"$mockHost",
    "microservice.services.business-registration.port" -> s"$mockPort",
    "microservice.services.email.sendEmailURL" -> s"$mockUrl/hmrc/email",
    "microservice.services.address-line-4-fix.regId" -> s"999",
    "microservice.services.address-line-4-fix.address-line-4" -> s"dGVzdEFMNA==",
    "microservice.services.check-submission-job.schedule.blockage-logging-day" -> s"MON,TUE,WED,THU,FRI",
    "microservice.services.check-submission-job.schedule.blockage-logging-time" -> s"00:00:00_01:00:00",
    "microservice.services.des-service.host" -> s"$mockHost",
    "microservice.services.des-service.port" -> s"$mockPort",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort",
    "microservice.services.des-service.url" -> s"$mockUrl/business-registration/corporation-tax",
    "microservice.services.des-service.environment" -> "local",
    "microservice.services.des-service.authorization-token" -> "testAuthToken",
    "microservice.services.des-topup-service.host" -> mockHost,
    "microservice.services.des-topup-service.port" -> mockPort
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()
  val regId = "reg-id-12345"

  private def client(path: String) = ws.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path")
    .withFollowRedirects(false)
    .withHttpHeaders("Content-Type" -> "application/json",
      HeaderNames.SET_COOKIE -> getSessionCookie(),
      GovHeaderNames.xSessionId -> SessionId,
      GovHeaderNames.authorisation -> "Bearer123"
    )

  "roAddressValid" should {

    val validCHROAddressOne =
      """
        |{
        |   "premises": "testPremises",
        |   "address_line_1": "11 AAAAA",
        |   "address_line_2": "BBBBB",
        |   "country": "Eng",
        |   "locality": "CCC",
        |   "po_box": "E",
        |   "postal_code": "AA11 1AA",
        |   "region": "DDDDD"
        |}
      """.stripMargin

    val validCHROAddressTwo =
      """
        |{
        |   "premises": "123",
        |   "address_line_1": "11, AAAAA",
        |   "address_line_2": "5 BBBB",
        |   "country": "England",
        |   "locality": "DDDDD",
        |   "po_box": "E E",
        |   "postal_code": "AA1A 1AA",
        |   "region": "FF FF FF"
        |}
      """.stripMargin

    val expectedCHROAddressOne = Json.parse(
      """
        |{
        |"addressLine1":"testPremises 11 AAAAA",
        |"addressLine2":"BBBBB",
        |"addressLine3":"CCC",
        |"addressLine4":"DDDDD",
        |"postCode":"AA11 1AA",
        |"country":"Eng",
        |"txid":""
        |}
      """.stripMargin)

    val expectedCHROAddressTwo = Json.parse(
      """
        |{
        |"addressLine1":"123 11, AAAAA",
        |"addressLine2":"5 BBBB",
        |"addressLine3":"DDDDD",
        |"addressLine4":"FF FF FF",
        |"postCode":"AA1A 1AA",
        |"country":"England",
        |"txid":""
        |}
      """.stripMargin)

    Seq(
      (validCHROAddressOne, expectedCHROAddressOne, "valid CHRO Address with postCode format AA11 1AA"),
      (validCHROAddressTwo, expectedCHROAddressTwo, "valid CHRO Address with postCode format AA1A 1AA")
    ).foreach { tuple =>
      val (validCHRO, expectedCHRO, testCase) = tuple
      s"validCHROAddress should match expectedCHRO with $testCase" in {
        val response = await(client(s"/check-ro-address").post(validCHRO))
        response.json shouldBe expectedCHRO
        response.status shouldBe 200
      }
    }
  }

  "roAddressSpecialCharsValid" should {
    "return 200 with valid data when special chars are removed" in {

      val specialCharCHROAddress =
        """
          |{
          |   "premises": "aaa! & BBB,",
          |   "address_line_1": "11 & $£AAAAA;.",
          |   "address_line_2": "BBBBB^*:",
          |   "country": "Eng;.",
          |   "locality": "CCC*(~",
          |   "po_box": "E£|",
          |   "postal_code": "AA11 1AA@@@",
          |   "region": "DDDDD/?"
          |}
        """.stripMargin

      val expectedResult = Json.parse(
        """
          |{
          |"addressLine1":"aaa & BBB, 11 & AAAAA;.",
          |"addressLine2":"BBBBB:",
          |"addressLine3":"CCC(",
          |"addressLine4":"DDDDD/",
          |"postCode":"AA11 1AA",
          |"country":"Eng",
          |"txid":""
          |}
        """.stripMargin)

      val response = await(client(s"/check-ro-address").post(specialCharCHROAddress))
      response.json shouldBe expectedResult
      response.status shouldBe 200
    }

    "CHROAddressinValid" should {
      "return 400 with invalid data" in {

        val invalidCHROAddress =
          """
            |{
            |   "premises": "|",
            |   "address_line_1": "|",
            |   "address_line_2": "|",
            |   "country": "Eng",
            |   "locality": "CCC",
            |   "po_box": "E",
            |   "postal_code": "AA11 1AA",
            |   "region": "DDDDD"
            |}
          """.stripMargin

        val expectedResult = Json.parse(
          """
            |{
            |"addressLine1":" ",
            |"addressLine2":"BBBBB",
            |"addressLine3":"CCC",
            |"addressLine4":"DDDDD",
            |"postCode":"AA11 1AA",
            |"country":"Eng",
            |"txid":""
            |}
          """.stripMargin)

        val response = await(client(s"/check-ro-address").post(invalidCHROAddress))
        response.status shouldBe 400

      }

    }
  }
}

