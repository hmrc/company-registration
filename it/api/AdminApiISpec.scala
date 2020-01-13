/*
* Copyright 2016 HM Revenue & Customs
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

import auth.CryptoSCRS
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil.WiremockHelper._
import itutil.{IntegrationSpecBase, LoginStub}
import models.RegistrationStatus._
import models._
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import repositories.{CorporationTaxRegistrationMongoRepository, SequenceMongoRepo}

import scala.concurrent.ExecutionContext

class AdminApiISpec extends IntegrationSpecBase with LoginStub {

  val regime = "testRegime"
  val subscriber = "testSubcriber"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      fakeConfig(
        Map("microservice.services.incorporation-information.host" -> s"$wiremockHost",
            "microservice.services.incorporation-information.port" -> s"$wiremockPort",
            "microservice.services.des-service.host" -> s"$wiremockHost",
            "microservice.services.des-service.port" -> s"$wiremockPort",
            "microservice.services.regime" -> s"$regime",
            "microservice.services.subscriber" -> s"$subscriber")
      )
    ).build

  implicit val ec: ExecutionContext = app.actorSystem.dispatcher.prepare()

  val regId = "reg-id-12345"
  val internalId = "int-id-12345"
  val sessionId = "session-id-12345"
  val credId = "cred-id-12345"
  val transId = "trans-id-2345"
  val payRef = "pay-ref-2345"
  val ackRef = "BRCT00000000001"
  val strideUser = "stride-12345"

  class Setup {
    val rmComp = app.injector.instanceOf[ReactiveMongoComponent]
    val crypto = app.injector.instanceOf[CryptoSCRS]
    val corpTaxRepo = new CorporationTaxRegistrationMongoRepository(
      rmComp,crypto)
    val seqRepo = app.injector.instanceOf[SequenceMongoRepo].repo

    await(corpTaxRepo.drop)
    await(seqRepo.drop)

    await(corpTaxRepo.ensureIndexes)
    await(seqRepo.ensureIndexes)

    def ctRegCount: Int = await(corpTaxRepo.count)
    def insertCorpTax(doc: CorporationTaxRegistration): WriteResult = await(corpTaxRepo.insert(doc))

    ctRegCount shouldBe 0

    System.clearProperty("feature.registerInterest")
  }

  val ws: WSClient = app.injector.instanceOf(classOf[WSClient])

  def client(path: String): WSRequest = ws.url(s"http://localhost:$port/company-registration/admin$path").
    withFollowRedirects(false)

  val draftRegistration = CorporationTaxRegistration(
    internalId = internalId,
    registrationID = regId,
    status = DRAFT,
    formCreationTimestamp = "2001-12-31T12:00:00Z",
    language = "en",
    confirmationReferences = None,
    companyDetails =  Some(CompanyDetails(
      companyName = "testCompanyName",
      CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
      PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
      jurisdiction = "testJurisdiction"
    )),
    accountingDetails = Some(AccountingDetails(
      status = AccountingDetails.FUTURE_DATE,
      activeDate = Some("2019-12-31"))),
    tradingDetails = Some(TradingDetails(
      regularPayments = "true"
    )),
    contactDetails = Some(ContactDetails(
      phone = Some("02072899066"),
      mobile = Some("07567293726"),
      email = Some("test@email.co.uk")
    )),
    verifiedEmail = Some(Email(
      address = "testEmail@address.com",
      emailType = "testType",
      linkSent = true,
      verified = true,
      returnLinkEmailSent = true
    )),
    registrationProgress = Some("ho5"),
    acknowledgementReferences = None,
    accountsPreparation = None,
    sessionIdentifiers = Some(SessionIds(
      sessionId = "oldSessionId",
      credId = "credId"
    ))
  )

  val lockedRegistration = CorporationTaxRegistration(
    internalId = internalId,
    registrationID = regId,
    status = LOCKED,
    formCreationTimestamp = "2001-12-31T12:00:00Z",
    language = "en",
    confirmationReferences = Some(ConfirmationReferences(
      acknowledgementReference = ackRef,
      transactionId = transId,
      paymentReference = Some(payRef),
      paymentAmount = Some("12")
    )),
    companyDetails =  Some(CompanyDetails(
      companyName = "testCompanyName",
      CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
      PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
      jurisdiction = "testJurisdiction"
    )),
    accountingDetails = Some(AccountingDetails(
      status = AccountingDetails.FUTURE_DATE,
      activeDate = Some("2019-12-31"))),
    tradingDetails = Some(TradingDetails(
      regularPayments = "true"
    )),
    contactDetails = Some(ContactDetails(
      phone = Some("02072899066"),
      mobile = Some("07567293726"),
      email = Some("test@email.co.uk")
    )),
    verifiedEmail = Some(Email(
      address = "testEmail@address.com",
      emailType = "testType",
      linkSent = true,
      verified = true,
      returnLinkEmailSent = true
    )),
    registrationProgress = Some("ho5"),
    acknowledgementReferences = None,
    accountsPreparation = None,
    sessionIdentifiers = Some(SessionIds(
      sessionId = "oldSessionId",
      credId = "oldCredId"
    ))
  )

  def heldRegistration(regId: String = regId) = CorporationTaxRegistration(
    internalId = internalId,
    registrationID = regId,
    status = HELD,
    formCreationTimestamp = "2001-12-31T12:00:00Z",
    language = "en",
    confirmationReferences = Some(ConfirmationReferences(
      acknowledgementReference = ackRef,
      transactionId = transId,
      paymentReference = Some(payRef),
      paymentAmount = Some("12")
    )),
    companyDetails =  None,
    accountingDetails = Some(AccountingDetails(
      status = AccountingDetails.FUTURE_DATE,
      activeDate = Some("2019-12-31"))),
    tradingDetails = None,
    contactDetails = None,
    verifiedEmail = Some(Email(
      address = "testEmail@address.com",
      emailType = "testType",
      linkSent = true,
      verified = true,
      returnLinkEmailSent = true
    )),
    registrationProgress = Some("ho5"),
    acknowledgementReferences = None,
    accountsPreparation = None
  )

  def stubIISubscribe(status: Int, response: JsObject): StubMapping = {
    stubPost(s"/incorporation-information/subscribe/$transId/regime/$regime/subscriber/$subscriber\\?force=true", status, response.toString())
  }

  def stubDESSubmission(status: Int): StubMapping = stubPost("/business-registration/corporation-tax", status, """{"x":"y"}""")

  "GET /admin/fetch-ho6-registration-information" should {

    val url = "/fetch-ho6-registration-information"

    "return a 200 and ho6 registration information as json" in new Setup {

      val expected: JsValue = Json.parse(
        """
          |{
          |  "status":"draft",
          |  "companyName":"testCompanyName",
          |  "registrationProgress":"ho5"
          |}
        """.stripMargin)

      setupSimpleAuthMocks()

      insertCorpTax(draftRegistration)

      ctRegCount shouldBe 1

      val result: WSResponse  = await(client(s"$url/$regId").get())

      result.status shouldBe 200
      result.json shouldBe expected
    }

    "return a 404 when a company registration document does not exist for the supplied regId" in new Setup {

      setupSimpleAuthMocks()

      ctRegCount shouldBe 0

      val result: WSResponse = await(client(s"$url/$regId").get())

      result.status shouldBe 404
    }
  }

  "GET /admin/session-id" should {

    val url = s"/session-id"

    "return a 200 and session id info" in new Setup {

      val expected: JsValue = Json.parse(
        s"""
          |{
          |  "sessionId":"oldSessionId",
          |  "credId":"oldCredId",
          |  "companyName":"testCompanyName",
          |  "ackRef":"$ackRef"
          |}
        """.stripMargin)

      setupSimpleAuthMocks()

      insertCorpTax(lockedRegistration)

      ctRegCount shouldBe 1

      val result: WSResponse  = await(client(s"$url/$regId").get())

      result.status shouldBe 200
      result.json shouldBe expected
    }

    "return a 204 when a company registration document does not exist for the supplied regId" in new Setup {

      setupSimpleAuthMocks()

      ctRegCount shouldBe 0

      val result: WSResponse = await(client(s"$url/$regId").get())

      result.status shouldBe 204
    }
  }

  "POST /admin/update-confirmation-references" should {

    val url = "/update-confirmation-references"

    val businessRegistrationResponse = Json.parse(
      s"""
        |{
        |  "registrationID":"$regId",
        |  "formCreationTimestamp":"2017-08-09T11:48:20+01:00",
        |  "language":"en",
        |  "completionCapacity":"director"
        |}
      """.stripMargin)

    val incorpInfoResponse = Json.parse(s"""
         |{
         |  "SCRSIncorpStatus":{
         |    "IncorpSubscriptionKey":{
         |      "subscriber":"SCRS",
         |      "discriminator":"CT",
         |      "transactionId":"$transId"
         |    },
         |    "SCRSIncorpSubscription":{
         |      "callbackUrl":"/callBackUrl"
         |    },
         |    "IncorpStatusEvent":{
         |      "status":"accepted",
         |      "crn":"123456789",
         |      "incorporationDate":"2017-04-25",
         |      "description":"Some description",
         |      "timestamp":"${DateTime.parse("2017-04-25").getMillis}"
         |    }
         |  }
         |}
      """.stripMargin).as[JsObject]

    val adminJsonBody: JsValue = Json.parse(
      s"""
         |{
         |  "strideUser":"$strideUser",
         |  "sessionId":"$sessionId",
         |  "credId":"$credId",
         |  "registrationId":"$regId",
         |  "transactionId":"$transId",
         |  "paymentReference":"$payRef",
         |  "paymentAmount":"12"
         |}
        """.stripMargin)

    "update the confirmation refs for the record matching the supplied reg Id and create a held submission and return a 200" in new Setup {
      setupSimpleAuthMocks()

      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())
      stubPost("/business-registration/corporation-tax", 202, """{"x":"y"}""")

      stubIISubscribe(202, incorpInfoResponse)

      insertCorpTax(draftRegistration)

      ctRegCount shouldBe 1

      val result: WSResponse = await(client(s"$url").post(adminJsonBody))

      val expected: JsValue = Json.parse(
        s"""
          |{
          |  "success":true,
          |  "statusBefore":"draft",
          |  "statusAfter":"held"
          |}
        """.stripMargin)

      result.status shouldBe 200
      result.json shouldBe expected
    }

    "do not update the record matching the supplied reg Id when the record status is already held and do not create a held record and return a 200" in new Setup {

      setupSimpleAuthMocks()

      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())

      insertCorpTax(heldRegistration())

      ctRegCount shouldBe 1

      val result: WSResponse = await(client(s"$url").post(adminJsonBody))

      val expected: JsValue = Json.parse(
        s"""
           |{
           |  "success":true,
           |  "statusBefore":"held",
           |  "statusAfter":"held"
           |}
        """.stripMargin)

      result.status shouldBe 200
      result.json shouldBe expected
    }

    "return a 404 when a CT registration does not exist" in new Setup {

      setupSimpleAuthMocks()

      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())

      ctRegCount shouldBe 0

      val result: WSResponse = await(client(s"$url").post(adminJsonBody))

      result.status shouldBe 404
    }

    "update confirmation ref, register an interest to incorporation-information and send the partial submission to ETMP" in new Setup {
      System.setProperty("feature.registerInterest", "true")

      setupSimpleAuthMocks()

      stubIISubscribe(202, incorpInfoResponse)
      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())
      stubPost("/business-registration/corporation-tax", 202, """{"x":"y"}""")

      insertCorpTax(draftRegistration)

      ctRegCount shouldBe 1

      val result: WSResponse = await(client(s"$url").post(adminJsonBody))

      result.status shouldBe 200
    }

    "use existing conf refs when called again after registration of interest fails" in new Setup {
      System.setProperty("feature.registerInterest", "true")

      setupSimpleAuthMocks()
      stubIISubscribe(500, Json.obj())

      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())
      stubDESSubmission(202)

      insertCorpTax(draftRegistration)

      val expectedConfRefs = ConfirmationReferences("BRCT00000000001", transId, Some(payRef), Some("12"))

      val firstCall: WSResponse = await(client(s"$url").post(adminJsonBody))
      firstCall.status shouldBe 404

      await(corpTaxRepo.findAll()).head.confirmationReferences shouldBe Some(expectedConfRefs)

      stubIISubscribe(202, incorpInfoResponse)

      val otherJsonBody: JsValue = Json.parse(
        s"""
           |{
           |  "strideUser":"$strideUser",
           |  "sessionId":"$sessionId",
           |  "credId":"$credId",
           |  "registrationId":"$regId",
           |  "transactionId":"otherTransId",
           |  "paymentReference":"otherPayRef",
           |  "paymentAmount":"12"
           |}
        """.stripMargin)

      val secondCall: WSResponse = await(client(s"$url").post(otherJsonBody))
      secondCall.status shouldBe 200

      await(corpTaxRepo.findAll()).head.confirmationReferences shouldBe Some(expectedConfRefs)
    }

    "be able to retry a submission when the submission to DES fails" in new Setup {
      System.setProperty("feature.registerInterest", "true")

      setupSimpleAuthMocks()
      stubIISubscribe(202, Json.obj())

      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())
      stubDESSubmission(500)

      insertCorpTax(draftRegistration)

      val firstCall: WSResponse = await(client(s"$url").post(adminJsonBody))
      firstCall.status shouldBe 404

      stubDESSubmission(202)

      val secondCall: WSResponse = await(client(s"$url").post(adminJsonBody))
      secondCall.status shouldBe 200
    }
  }

  "GET /admin/:id/ctutr" should {

    val testRegistrationID = "12345678"
    val testAckowledgementReference = "BRCT01234567890"
    def path(id: String) = s"/$id/ctutr"

    "successfully return a 200 with valid JSON" when {
      "fetched by AckRef" in new Setup {
        insertCorpTax(heldRegistration(testRegistrationID).copy(
          confirmationReferences = Option(
            ConfirmationReferences(
              acknowledgementReference = testAckowledgementReference, "transID", None, None
            )),
            acknowledgementReferences = Option(
              AcknowledgementReferences(
              Option("ctutr"), "timestamp", "04"
            ))
          )
        )
        ctRegCount shouldBe 1

        val result: WSResponse = await(client(path(testAckowledgementReference)).get())
        val expectedJson: JsValue = Json.parse(
          """
          |{
          |  "status": "04",
          |  "ctutr": true
          |}
        """.
            stripMargin)

        result.status shouldBe 200
        result.json shouldBe expectedJson
      }
      "fetched by AckRef without a CT-UTR" in new Setup {
        insertCorpTax(heldRegistration(testRegistrationID).copy(
          confirmationReferences = Option(
            ConfirmationReferences(
              acknowledgementReference = testAckowledgementReference, "transID", None, None
            )),
          acknowledgementReferences = Option(
            AcknowledgementReferences(
              None, "timestamp", "06"
            ))
        )
        )
        ctRegCount shouldBe 1

        val result: WSResponse = await(client(path(testAckowledgementReference)).get())
        val expectedJson: JsValue = Json.parse(
          """
            |{
            |  "status": "06",
            |  "ctutr": false
            |}
          """.
            stripMargin)

        result.status shouldBe 200
        result.json shouldBe expectedJson
      }
      "not fetched by something other than an ack ref" in new Setup {
        insertCorpTax(draftRegistration)
        ctRegCount shouldBe 1

        val result: WSResponse = await(client(path(testRegistrationID)).get())
        val expectedJson: JsValue = Json.parse(
          """
            |{
            |}
          """.
            stripMargin)
        result.status shouldBe 200
        result.json shouldBe expectedJson
      }
    }
  }

  "POST /admin/:id/ctutr" should {

    val testRegistrationID = "12345678"
    val testAckowledgementReference = "BRCT01234567890"
    def path(id: String) = s"/$id/ctutr"

    "successfully return a 200 with valid JSON" when {
      "updating an ackref with a CT-UTR" in new Setup {
        insertCorpTax(heldRegistration(testRegistrationID).copy(
          confirmationReferences = Option(
            ConfirmationReferences(
              acknowledgementReference = testAckowledgementReference, "transID", None, None
            )),
          acknowledgementReferences = Option(
            AcknowledgementReferences(
              None, "timestamp", "06"
            ))
        ))
        ctRegCount shouldBe 1

        val result: WSResponse = await(client(path(testAckowledgementReference)).post({
          Json.parse("""{"ctutr" : "test", "username" : "user"}""")
        }))
        val expectedJson: JsValue = Json.parse(
          """
            |{
            |  "status": "04",
            |  "ctutr": true
            |}
          """.
            stripMargin)

        result.status shouldBe 200
        result.json shouldBe expectedJson
      }

      "trying to update non-existent ackref with a CT-UTR" in new Setup {
        val result: WSResponse = await(client(path(testAckowledgementReference)).post({
          Json.parse("""{"ctutr" : "test", "username" : "user"}""")
        }))

        result.status shouldBe 204
      }
    }
  }

  "POST /admin/session-id" should {

    val adminJsonBody: JsValue = Json.parse(
      s"""
         |{
         |  "sessionId":"cleanNewShinySessionId",
         |  "credId":"cleanNewShinyCredId",
         |  "username" :"username"
         |}
        """.stripMargin)

    val url = s"/session-id"

    "return a 200 and session id info" in new Setup {

      val expected: JsValue = Json.parse(
        s"""
           |{
           |  "sessionId":"cleanNewShinySessionId",
           |  "credId":"cleanNewShinyCredId",
           |  "companyName":"testCompanyName",
           |  "ackRef":"$ackRef"
           |}
        """.stripMargin)

      setupSimpleAuthMocks()

      insertCorpTax(lockedRegistration)

      ctRegCount shouldBe 1

      val result: WSResponse  = await(client(s"$url/$regId").post(adminJsonBody))

      result.status shouldBe 200
      result.json shouldBe expected

      await(corpTaxRepo.findAll()).head.sessionIdentifiers.get.sessionId shouldBe "cleanNewShinySessionId"
    }

    "return a 500 when a company registration document does not exist for the supplied regId" in new Setup {

      val expected: JsValue = Json.parse(
        s"""
           |{
           |  "sessionId":"oldSessionId",
           |  "credId":"oldCredId",
           |  "companyName":"testCompanyName",
           |  "ackRef":"$ackRef"
           |}
        """.stripMargin)

      setupSimpleAuthMocks()

      ctRegCount shouldBe 0

      val result: WSResponse = await(client(s"$url/$regId").post(adminJsonBody))

      result.status shouldBe 500
    }
  }
}
