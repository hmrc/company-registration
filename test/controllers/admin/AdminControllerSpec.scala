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

package controllers.admin

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import models._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsResultException, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import repositories.CorporationTaxRegistrationRepository
import services.SubmissionService
import services.admin.AdminService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AdminControllerSpec extends UnitSpec with MockitoSugar {

  implicit val act = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val hc = HeaderCarrier()

  val mockAdminService: AdminService = mock[AdminService]
  val mockSubmissionService: SubmissionService = mock[SubmissionService]
  val mockCorporationTaxMongo: CorporationTaxRegistrationRepository = mock[CorporationTaxRegistrationRepository]

  trait Setup {
    val controller = new AdminController {
      override val adminService: AdminService = mockAdminService
      override val submissionService: SubmissionService = mockSubmissionService
      override val cTRegistrationRepository: CorporationTaxRegistrationRepository = mockCorporationTaxMongo
    }
  }

  val regId = "reg-12345"
  val ackRef = "BRCT000000001"

  "fetchHO6RegistrationInformation" should {

    "return a 200 and HO6 registration information as json" in new Setup {
      val regInfo = HO6RegistrationInformation("draft" , Some("testCompanyName"), Some("ho5"))


      val expectedJson = Json.parse(
        """
          |{
          |  "status":"draft",
          |  "companyName":"testCompanyName",
          |  "registrationProgress":"ho5"
          |}
        """.stripMargin)

      when(mockAdminService.fetchHO6RegistrationInformation(eqTo(regId)))
        .thenReturn(Future.successful(Some(regInfo)))

      val result = await(controller.fetchHO6RegistrationInformation(regId)(FakeRequest()))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe expectedJson
    }

    "return a 404 if a company registration document can't be found for the supplied reg id" in new Setup {
      when(mockAdminService.fetchHO6RegistrationInformation(eqTo(regId)))
        .thenReturn(Future.successful(None))

      val result = await(controller.fetchHO6RegistrationInformation(regId)(FakeRequest()))

      status(result) shouldBe 404
    }
  }

  "fetchSessionIDData" should {

    "return a 200 and HO6 registration information as json" in new Setup {
      val sessionId = "session-id"
      val credId = "cred-id"
      val companyName = "Fake Company Name"
      val ackRef = "fakeAckRef"

      val regInfo = SessionIdData(Some(sessionId), Some(credId), Some(companyName), Some(ackRef))

      val expectedJson = Json.parse(
        s"""
          |{
          |  "sessionId":"$sessionId",
          |  "credId":"$credId",
          |  "companyName":"$companyName",
          |  "ackRef":"$ackRef"
          |}
        """.stripMargin)

      when(mockAdminService.fetchSessionIdData(eqTo(regId)))
        .thenReturn(Future.successful(Some(regInfo)))

      val result = await(controller.fetchSessionIDData(regId)(FakeRequest()))

      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe expectedJson
    }

    "return a 204 if a company registration document can't be found for the supplied reg id" in new Setup {
      when(mockAdminService.fetchSessionIdData(eqTo(regId)))
        .thenReturn(Future.successful(None))

      val result = await(controller.fetchSessionIDData(regId)(FakeRequest()))

      status(result) shouldBe 204
    }
  }

  "ctutrCheck" should {
    "return valid JSON responses" in new Setup {
      val outputs = List(
        """{}""",
        """{"status": "06", "ctutr": false}""",
        """{"status": "04", "ctutr": true}"""
      ).map{js => Json.parse(js).as[JsObject]}

      val mocks = outputs map Future.successful

      when(mockAdminService.ctutrCheck(any()))
        .thenReturn(mocks.head, mocks(1), mocks(2))

      outputs foreach { expected =>
        val result: Result = await(controller.ctutrCheck(ackRef)(FakeRequest()))

        status(result) shouldBe 200
        jsonBodyOf(result) shouldBe expected
      }
    }
  }

  val expected = Some(
    CorporationTaxRegistration(
      internalId = "testID",
      registrationID = "registrationId",
      formCreationTimestamp = "dd-mm-yyyy",
      language = "en",
      status = RegistrationStatus.HELD,
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        Some("0123456789"),
        Some("0123456789"),
        Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("false")),
      heldTimestamp = None,
      confirmationReferences = Some(ConfirmationReferences(
        acknowledgementReference = "ackRef",
        transactionId = "tID",
        paymentReference = Some("payref"),
        paymentAmount = Some("12")
      ))
    )
  )

  "ctUtrUpdate" should {
    val jsrequest = FakeRequest().withBody(Json.parse("""
        |{
        | "ctutr" : "test",
        | "username" : "user"
        |}""".stripMargin))

    "return a success" in new Setup {
      val json = """{"status": "04", "ctutr": true}"""
      val emptyMock = Future.successful(Option(Json.parse(json).as[JsObject]))

      when(mockAdminService.updateRegistrationWithCTReference(any(), any(), any())(any()))
        .thenReturn(emptyMock)

      val result: Result = await(controller.ctutrUpdate(ackRef)(jsrequest))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse(json).as[JsObject]
    }

    "return no content if nothing was updated" in new Setup {
      when(mockAdminService.updateRegistrationWithCTReference(any(), any(), any())(any()))
        .thenReturn(Future.successful(None))

      val result: Result = await(controller.ctutrUpdate(ackRef)(jsrequest))

      status(result) shouldBe NO_CONTENT
    }
  }

  "updateSessionId" should {
    val jsrequest = FakeRequest().withBody(Json.parse("""
        |{
        | "sessionId" : "new-session-id",
        | "credId" : "new-cred-id",
        | "username" : "username"
        |}""".stripMargin))

    val invalidJsRequest = FakeRequest().withBody(Json.parse("""
        |{
        | "badKey" : "new-session-id"
        |}""".stripMargin))

    "return a success when provided a session id to update with" in new Setup {
      val sessionIdData = SessionIdData(Some("new-session-id"), Some("new-cred-id"), None, None)

      when(mockAdminService.updateDocSessionID(any(), any(), any(), any())(any()))
        .thenReturn(sessionIdData)

      val result: Result = await(controller.updateSessionId(regId)(jsrequest))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.toJson(sessionIdData)(SessionIdData.writes)
    }

    "fail if passed in invalid json" in new Setup {
      intercept[JsResultException](controller.updateSessionId(regId)(invalidJsRequest))
    }
  }
}
