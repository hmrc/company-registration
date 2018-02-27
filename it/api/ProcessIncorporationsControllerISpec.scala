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

package api

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil.{IntegrationSpecBase, LoginStub, MongoIntegrationSpec, WiremockHelper}
import models.RegistrationStatus.{DRAFT, HELD, LOCKED}
import models._
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.{WS, WSResponse}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.commands.WriteResult
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionData, HeldSubmissionMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class ProcessIncorporationsControllerISpec extends IntegrationSpecBase with MongoIntegrationSpec with LoginStub {
  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.email.sendEmailURL" -> s"$mockUrl/hmrc/email",
    "microservice.services.address-line-4-fix.regId" -> s"999",
    "microservice.services.address-line-4-fix.address-line-4" -> s"dGVzdEFMNA==",
    "microservice.services.check-submission-job.schedule.blockage-logging-day" -> s"MON,TUE,WED,THU,FRI",
    "microservice.services.check-submission-job.schedule.blockage-logging-time" -> s"00:00:00_01:00:00",
    "microservice.services.des-service.host" -> s"$mockHost",
    "microservice.services.des-service.port" -> s"$mockPort",
    "microservice.services.des-service.url" -> s"$mockUrl/business-registration/corporation-tax",
    "microservice.services.des-service.environment" -> "local",
    "microservice.services.des-service.authorization-token" -> "testAuthToken",
    "microservice.services.des-topup-service.host" -> mockHost,
    "microservice.services.des-topup-service.port" -> mockPort,
    "microservice.services.doNotIndendToTradeDefaultDate" -> "MTkwMC0wMS0wMQ==",
    "microservice.services.business-registration.host" -> s"$mockHost",
    "microservice.services.business-registration.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  private def client(path: String) = WS.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path").
    withFollowRedirects(false).
    withHeaders("Content-Type"->"application/json")

  class Setup extends MongoDbConnection {
    val ctRepository = new CorporationTaxRegistrationMongoRepository(db)
    val heldRepository = new HeldSubmissionMongoRepository(db)
    await(ctRepository.drop)
    await(ctRepository.ensureIndexes)
    await(heldRepository.drop)
    await(heldRepository.ensureIndexes)

    def setupCTRegistration(reg: CorporationTaxRegistration): WriteResult = ctRepository.insert(reg)
    def setupHeldSubmission(regId: String, ackRef: String, heldJson: JsObject): Option[HeldSubmissionData] =
      heldRepository.storePartialSubmission(regId, ackRef, heldJson)
  }

  val regId = "reg-id-12345"
  val internalId = "int-id-12345"
  val transId = "trans-id-2345"
  val payRef = "pay-ref-2345"
  val ackRef = "BRCT00000000001"
  val activeDate = "2017-07-23"

  val heldRegistration = CorporationTaxRegistration(
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
    accountingDetails = Some(AccountingDetails(
      status = AccountingDetails.FUTURE_DATE,
      activeDate = Some(activeDate))),
    verifiedEmail = Some(Email(
      address = "testEmail@address.com",
      emailType = "testType",
      linkSent = true,
      verified = true,
      returnLinkEmailSent = true
    )),
    companyDetails =  Some(CompanyDetails(
      companyName = "testCompanyName",
      CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
      PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
      jurisdiction = "testJurisdiction"
    )),
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
    registrationProgress = Some("ho5"),
    acknowledgementReferences = None,
    accountsPreparation = None
  )

  val heldJson: JsObject = Json.parse(
    s"""
      |{
      |   "acknowledgementReference":"$ackRef",
      |   "registration":{
      |      "metadata":{
      |         "businessType":"Limited company",
      |         "submissionFromAgent":false,
      |         "declareAccurateAndComplete":true,
      |         "sessionId":"session-152ebc2f-8e0d-4137-8236-058b295fe52f",
      |         "credentialId":"cred-id-254524311264",
      |         "language":"ENG",
      |         "formCreationTimestamp":"2017-07-03T13:19:54.000+01:00",
      |         "completionCapacity":"Director"
      |      },
      |      "corporationTax":{
      |         "companyOfficeNumber":"623",
      |         "hasCompanyTakenOverBusiness":false,
      |         "companyMemberOfGroup":false,
      |         "companiesHouseCompanyName":"Company Name Ltd",
      |         "returnsOnCT61":false,
      |         "companyACharity":false,
      |         "businessAddress":{
      |            "line1":"12 address line 1",
      |            "line2":"address line 2",
      |            "line3":"address line 3",
      |            "line4":"address line 4",
      |            "postcode":"ZZ11 1ZZ"
      |         },
      |         "businessContactName":{
      |            "firstName":"MGD",
      |            "middleNames":"GG Stub Test",
      |            "lastName":"Org"
      |         },
      |         "businessContactDetails":{
      |            "phoneNumber":"1234",
      |            "mobileNumber":"123456",
      |            "email":"6email@whatever.com"
      |         }
      |      }
      |   }
      |}
    """.stripMargin).as[JsObject]

  val subscriber = "SCRS"
  val regime = "CT"
  val crn = "012345"
  val prepDate = "2018-07-31"

  def jsonIncorpStatus(incorpDate: String): String =
    s"""
      |{
      |  "SCRSIncorpStatus": {
      |    "IncorpSubscriptionKey": {
      |      "subscriber":"$subscriber",
      |      "discriminator":"$regime",
      |      "transactionId": "$transId"
      |    },
      |    "SCRSIncorpSubscription":{
      |      "callbackUrl":"www.url.com"
      |    },
      |    "IncorpStatusEvent": {
      |      "status": "accepted",
      |      "crn": "$crn",
      |      "incorporationDate": ${DateTime.parse(incorpDate).getMillis}
      |    }
      |  }
      |}
      """.stripMargin

  def jsonIncorpStatusRejected: String =
    s"""
       |{
       |  "SCRSIncorpStatus": {
       |    "IncorpSubscriptionKey": {
       |      "subscriber":"$subscriber",
       |      "discriminator":"$regime",
       |      "transactionId": "$transId"
       |    },
       |    "SCRSIncorpSubscription":{
       |      "callbackUrl":"www.url.com"
       |    },
       |    "IncorpStatusEvent": {
       |      "status": "rejected"
       |    }
       |  }
       |}
      """.stripMargin

  def jsonAppendDataForSubmission(incorpDate: String): JsObject = Json.parse(
    s"""
       |{
       |   "registration": {
       |     "corporationTax": {
       |       "crn": "$crn",
       |       "companyActiveDate": "$incorpDate",
       |       "startDateOfFirstAccountingPeriod": "$incorpDate",
       |       "intendedAccountsPreparationDate": "$prepDate"
       |     }
       |   }
       |}
       """.stripMargin).as[JsObject]

  def stubDesPost(status: Int, submission: String): StubMapping = stubPost("/business-registration/corporation-tax", status, submission)
  def stubEmailPost(status: Int): StubMapping = stubPost("/hmrc/email", status, "")
  def stubDesTopUpPost(status: Int, submission: String): StubMapping = stubPost("/business-incorporation/corporation-tax", status, submission)

  "Process Incorporation" should {

    val path = "/process-incorp"
    val testIncorpDate = "2017-07-24"

    "send a full submission to DES with correct active date" when {

      "user has selected a Future Date for Accounting Dates before the incorporation date" in new Setup {
        val incorpDate = "2017-07-24"

        await(ctRepository.insert(heldRegistration))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 200, ctSubmission)

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200

        val crPosts = findAll(postRequestedFor(urlMatching(s"/business-registration/corporation-tax")))
        val captor = crPosts.get(0)
        val json = Json.parse(captor.getBodyAsString)
        (json \ "registration" \ "corporationTax" \ "companyActiveDate").as[String] shouldBe incorpDate
      }

      "user has selected a Future Date for Accounting Dates after the incorporation date" in new Setup {
        val incorpDate = "2017-07-22"

        await(ctRepository.insert(heldRegistration))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(activeDate)).toString
        stubPost("/business-registration/corporation-tax", 200, ctSubmission)

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200

        val crPosts = findAll(postRequestedFor(urlMatching(s"/business-registration/corporation-tax")))
        val captor = crPosts.get(0)
        val json = Json.parse(captor.getBodyAsString)
        (json \ "registration" \ "corporationTax" \ "companyActiveDate").as[String] shouldBe activeDate
      }

      "user has selected When Registered for Accounting Dates" in new Setup {
        import AccountingDetails.WHEN_REGISTERED

        val incorpDate = "2017-07-25"
        val heldRegistration2: CorporationTaxRegistration = heldRegistration.copy(
          accountingDetails = Some(heldRegistration.accountingDetails.get.copy(status = WHEN_REGISTERED, activeDate = None))
        )

        await(ctRepository.insert(heldRegistration2))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 200, ctSubmission)

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200

        val crPosts = findAll(postRequestedFor(urlMatching(s"/business-registration/corporation-tax")))
        val captor = crPosts.get(0)
        val json = Json.parse(captor.getBodyAsString)
        (json \ "registration" \ "corporationTax" \ "companyActiveDate").as[String] shouldBe incorpDate
      }

      "user has selected Not Intend to trade for Accounting Dates" in new Setup {
        import AccountingDetails.NOT_PLANNING_TO_YET

        val incorpDate = "2017-07-25"
        val activeDateToDES = "1900-01-01"
        val heldRegistration2: CorporationTaxRegistration = heldRegistration.copy(
          accountingDetails = Some(heldRegistration.accountingDetails.get.copy(status = NOT_PLANNING_TO_YET, activeDate = None))
        )

        await(ctRepository.insert(heldRegistration2))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 200, ctSubmission)

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200

        val crPosts = findAll(postRequestedFor(urlMatching(s"/business-registration/corporation-tax")))
        val captor = crPosts.get(0)
        val json = Json.parse(captor.getBodyAsString)
        (json \ "registration" \ "corporationTax" \ "companyActiveDate").as[String] shouldBe activeDateToDES
      }
    }

    "handle an error response" when {
      val incorpDate = "2017-07-24"

      "it is a 400" in new Setup {
        await(ctRepository.insert(heldRegistration))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 400, "")

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 400
      }
      "it is a 409" in new Setup {
        await(ctRepository.insert(heldRegistration))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 409, """{"wibble" : "bar"}""")

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200
      }
      "registration is Locked by returning 202 to postpone the II notification" in new Setup {
        val businessRegistrationResponse = s"""
                                              |{
                                              |  "registrationID":"$regId",
                                              |  "formCreationTimestamp":"2017-08-09T11:48:20+01:00",
                                              |  "language":"en",
                                              |  "completionCapacity":"director"
                                              |}
        """.stripMargin

        await(ctRepository.insert(heldRegistration.copy(
          status = LOCKED, sessionIdentifiers = Some(SessionIds("sessID", "credID"))
        )))

        setupSimpleAuthMocks()

        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 200, """{"a": "b"}""")

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 202

        val reg = await(ctRepository.findAll()).head
        reg.status shouldBe HELD
      }
      "registration is Locked, but the update to held fails" in new Setup {
        val businessRegistrationResponse = s"""
                                              |{
                                              |  "registrationID":"$regId",
                                              |  "formCreationTimestamp":"2017-08-09T11:48:20+01:00",
                                              |  "language":"en",
                                              |  "completionCapacity":"director"
                                              |}
        """.stripMargin

        await(ctRepository.insert(heldRegistration.copy(
          status = LOCKED, sessionIdentifiers = Some(SessionIds("sessID", "credID"))
        )))

        setupSimpleAuthMocks()

        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 400, """{"a": "b"}""")

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 400

        val reg = await(ctRepository.findAll()).head
        reg.status shouldBe LOCKED
      }
      "registration is Locked, but cannot submit on behalf due to no session identifiers" in new Setup {
        val businessRegistrationResponse = s"""
                                              |{
                                              |  "registrationID":"$regId",
                                              |  "formCreationTimestamp":"2017-08-09T11:48:20+01:00",
                                              |  "language":"en",
                                              |  "completionCapacity":"director"
                                              |}
        """.stripMargin

        await(ctRepository.insert(heldRegistration.copy(
          status = LOCKED
        )))

        setupSimpleAuthMocks()

        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse)

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200

        val reg = await(ctRepository.findAll()).head
        reg.status shouldBe LOCKED
      }
      "registration is Locked by returning 202 to postpone the II rejected notification" in new Setup {
        val businessRegistrationResponse = s"""
                                              |{
                                              |  "registrationID":"$regId",
                                              |  "formCreationTimestamp":"2017-08-09T11:48:20+01:00",
                                              |  "language":"en",
                                              |  "completionCapacity":"director"
                                              |}
        """.stripMargin

        await(ctRepository.insert(heldRegistration.copy(
          status = LOCKED, sessionIdentifiers = Some(SessionIds("sessID", "credID"))
        )))

        setupSimpleAuthMocks()

        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 200, """{"a": "b"}""")

        val response = await(client("/process-incorp").post(jsonIncorpStatusRejected))
        response.status shouldBe 202

        val reg = await(ctRepository.findAll()).head
        reg.status shouldBe HELD
      }
      "registration is Locked, but the update to held fails on a rejection" in new Setup {
        val businessRegistrationResponse = s"""
                                              |{
                                              |  "registrationID":"$regId",
                                              |  "formCreationTimestamp":"2017-08-09T11:48:20+01:00",
                                              |  "language":"en",
                                              |  "completionCapacity":"director"
                                              |}
        """.stripMargin

        await(ctRepository.insert(heldRegistration.copy(
          status = LOCKED, sessionIdentifiers = Some(SessionIds("sessID", "credID"))
        )))

        setupSimpleAuthMocks()

        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        stubGet(s"/business-registration/admin/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 400, """{"a": "b"}""")

        val response = await(client("/process-incorp").post(jsonIncorpStatusRejected))
        response.status shouldBe 400

        val reg = await(ctRepository.findAll()).head
        reg.status shouldBe LOCKED
      }
      "it is a 499" in new Setup {
        await(ctRepository.insert(heldRegistration))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 499, """{"wibble" : "bar"}""")

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 502
      }
      "it is a 501" in new Setup {
        await(ctRepository.insert(heldRegistration))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 501, """{"wibble" : "bar"}""")

        val response = await(client("/process-incorp").post(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 502
      }
    }

    "send a top-up submission to DES if a matching held registration exists and a held submission does not exist" in new Setup {

      val jsonBodyFromII: String = jsonIncorpStatus(testIncorpDate)

      setupSimpleAuthMocks()
      setupCTRegistration(heldRegistration)

      stubDesTopUpPost(200, """{"test":"json"}""")
      stubEmailPost(202)

      heldRepository.awaitCount shouldBe 0

      val response: WSResponse = client(path).post(jsonBodyFromII)

      response.status shouldBe 200

      val reg :: _ = await(ctRepository.findAll())

      reg.status shouldBe "submitted"
    }

    "NOT send a top-up submission to DES if a matching registration exists as not held status and a held submission does not exist" in new Setup {

      val jsonBodyFromII: String = jsonIncorpStatus(testIncorpDate)

      setupSimpleAuthMocks()
      setupCTRegistration(heldRegistration.copy(status = DRAFT))

      stubEmailPost(202)
      stubPost("/write/audit", 200, """{"x":2}""")

      heldRepository.awaitCount shouldBe 0

      val response: WSResponse = client(path).post(jsonBodyFromII)
      response.status shouldBe 500
    }

    "send a full submission to DES if a matching held registration exists and a held submission exists" in new Setup {

      val jsonBodyFromII: String = jsonIncorpStatus(testIncorpDate)

      setupSimpleAuthMocks()
      setupCTRegistration(heldRegistration)
      setupHeldSubmission(regId, ackRef, heldJson)

      heldRepository.awaitCount shouldBe 1

      stubDesPost(202, """{"test":"json"}""")
      stubEmailPost(202)

      val response: WSResponse = client(path).post(jsonBodyFromII)

      response.status shouldBe 200

      val reg :: _ = await(ctRepository.findAll())

      reg.status shouldBe "submitted"
      heldRepository.awaitCount shouldBe 0
    }

    "return a 502 if the top-up submission to DES fails, then return a 200 when retried and the submission to DES is successful" in new Setup {

      val jsonBodyFromII: String = jsonIncorpStatus(testIncorpDate)

      setupSimpleAuthMocks()
      setupCTRegistration(heldRegistration)

      stubDesTopUpPost(502, """{"test":"json for 502"}""")
      stubEmailPost(202)

      heldRepository.awaitCount shouldBe 0

      val response1: WSResponse = client(path).post(jsonBodyFromII)

      response1.status shouldBe 502

      val reg1 :: _ = await(ctRepository.findAll())
      reg1.status shouldBe "held"

      stubDesTopUpPost(200, """{"test":"json for 200"}""")

      val response2: WSResponse = client(path).post(jsonBodyFromII)

      response2.status shouldBe 200

      val reg2 :: _ = await(ctRepository.findAll())
      reg2.status shouldBe "submitted"
    }
  }
}
