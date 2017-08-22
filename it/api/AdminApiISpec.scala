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

import itutil.IntegrationSpecBase
import itutil.WiremockHelper._
import models._
import models.RegistrationStatus._
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.commands.WriteResult
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionMongoRepository, SequenceMongoRepository}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext

class AdminApiISpec extends IntegrationSpecBase with MongoSpecSupport {

  val regime = "testRegime"
  val subscriber = "testSubcriber"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      fakeConfig(
        Map("microservice.services.incorporation-information.host" -> s"$wiremockHost",
            "microservice.services.incorporation-information.port" -> s"$wiremockPort",
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

  class Setup extends MongoDbConnection {
    val repository = new CorporationTaxRegistrationMongoRepository(db)
    val heldRepo = new HeldSubmissionMongoRepository(db)
    val seqRepo = new SequenceMongoRepository(db)

    await(repository.drop)
    await(heldRepo.drop)
    await(seqRepo.drop)

    await(repository.ensureIndexes)
    await(heldRepo.ensureIndexes)
    await(seqRepo.ensureIndexes)

    def ctRegCount: Int = await(repository.count)
    def heldCount: Int = await(heldRepo.count)

    def insert(doc: CorporationTaxRegistration): WriteResult = await(repository.insert(doc))

    ctRegCount shouldBe 0
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
      firstName = "testContactFirstName",
      middleName = Some("testContactMiddleName"),
      surname = "testContactLastName",
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
    accountsPreparation = None
  )

  val heldRegistration = CorporationTaxRegistration(
    internalId = internalId,
    registrationID = regId,
    status = HELD,
    formCreationTimestamp = "2001-12-31T12:00:00Z",
    language = "en",
    confirmationReferences = Some(ConfirmationReferences(
      acknowledgementReference = ackRef,
      transactionId = transId,
      paymentReference = payRef,
      paymentAmount = "12"
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

      insert(draftRegistration)

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

    val incorpInfoResponse = s"""
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
      """.stripMargin

    "update the confirmation refs for the record matching the supplied reg Id and create a held submission and return a 200" in new Setup {

      System.setProperty("feature.registerInterest", "true")

      setupSimpleAuthMocks()

      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())

      stubPost(s"/incorporation-information/subscribe/$transId/regime/$regime/subscriber/$subscriber\\?force=true", 202, incorpInfoResponse)

      insert(draftRegistration)

      ctRegCount shouldBe 1
      heldCount shouldBe 0

      val jsonBody: JsValue = Json.parse(
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

      val result: WSResponse = await(client(s"$url").post(jsonBody))

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

      heldCount shouldBe 1
    }

    "do not update the record matching the supplied reg Id when the record status is already held and do not create a held record and return a 200" in new Setup {

      setupSimpleAuthMocks()

      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())

      insert(heldRegistration)

      ctRegCount shouldBe 1
      heldCount shouldBe 0

      val jsonBody: JsValue = Json.parse(
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

      val result: WSResponse = await(client(s"$url").post(jsonBody))

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

      heldCount shouldBe 0
    }

    "return a 404 when a CT registration does not exist" in new Setup {

      setupSimpleAuthMocks()

      stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse.toString())

      ctRegCount shouldBe 0
      heldCount shouldBe 0

      val jsonBody: JsValue = Json.parse(
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

      val result: WSResponse = await(client(s"$url").post(jsonBody))

      result.status shouldBe 404
    }
  }
}
