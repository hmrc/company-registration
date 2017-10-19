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

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock.{findAll, postRequestedFor, urlMatching}
import itutil.{IntegrationSpecBase, LoginStub, WiremockHelper}
import models.RegistrationStatus._
import models.{AccountingDetails, CHROAddress, CompanyDetails, ConfirmationReferences, ContactDetails, CorporationTaxRegistration, Email, PPOB, PPOBAddress, TradingDetails}
import play.api.Application
import play.api.http.HeaderNames
import uk.gov.hmrc.play.http.{HeaderNames => GovHeaderNames}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WS
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.commands.WriteResult
import repositories.{CorporationTaxRegistrationMongoRepository, SequenceMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class CorporationTaxRegistrationControllerISpec extends IntegrationSpecBase with LoginStub {
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
    "microservice.services.des-service.url" -> s"$mockUrl/business-registration/corporation-tax",
    "microservice.services.des-service.environment" -> "local",
    "microservice.services.des-service.authorization-token" -> "testAuthToken",
    "microservice.services.des-topup-service.host" -> mockHost,
    "microservice.services.des-topup-service.port" -> mockPort
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  private def client(path: String) = WS.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path")
    .withFollowRedirects(false)
    .withHeaders("Content-Type"->"application/json")
    .withHeaders(HeaderNames.COOKIE -> getSessionCookie())
    .withHeaders(GovHeaderNames.xSessionId -> SessionId)

  class Setup extends MongoDbConnection {
    val ctRepository = new CorporationTaxRegistrationMongoRepository(db)
    val seqRepo = new SequenceMongoRepository(db)

    await(ctRepository.drop)
    await(ctRepository.ensureIndexes)

    await(seqRepo.drop)
    await(seqRepo.ensureIndexes)

    System.clearProperty("feature.registerInterest")
    System.clearProperty("feature.etmpHoldingPen")

    def setupCTRegistration(reg: CorporationTaxRegistration): WriteResult = ctRepository.insert(reg)
  }

  val regId = "reg-id-12345"
  val internalId = "int-id-12345"
  val transId = "trans-id-2345"
  val ackRef = "BRCT00000000001"
  val activeDate = "2017-07-23"
  val payRef = "pay-ref-2345"
  val payAmount = "12"

  val confRefsWithPayment = ConfirmationReferences(
    acknowledgementReference = ackRef,
    transactionId = transId,
    paymentReference = Some(payRef),
    paymentAmount = Some(payAmount)
  )

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
      paymentReference = None,
      paymentAmount = None
    )),
    companyDetails =  None,
    accountingDetails = Some(AccountingDetails(
      status = AccountingDetails.FUTURE_DATE,
      activeDate = Some(activeDate))),
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

  def jsonConfirmationRefs(ackReference: String = "", paymentRef: Option[String] = None, paymentAmount: Option[String] = None): String = {
    val confRefs = Json.parse(s"""
       |{
       |  "acknowledgement-reference": "$ackReference",
       |  "transaction-id": "$transId"
       |}
     """.stripMargin).as[JsObject]

    val json = confRefs ++
               paymentRef.fold(Json.obj())(ref => Json.obj("payment-reference" -> ref)) ++
               paymentAmount.fold(Json.obj())(tot => Json.obj("payment-amount" -> tot))

    json.toString
  }

  "handleSubmission" should {
    "return Confirmation References when registration is in Held status" in new Setup {
      await(ctRepository.insert(heldRegistration.copy(confirmationReferences = Some(confRefsWithPayment))))

      setupSimpleAuthMocks(internalId)

      val response = await(client(s"/$regId/confirmation-references").put(jsonConfirmationRefs(ackRef, Some(payRef), Some(payAmount))))
      response.status shouldBe 200
      response.json shouldBe Json.toJson(confRefsWithPayment)
    }

    "update Confirmation References when registration is in Held status (new HO6)" in new Setup {
      await(ctRepository.insert(heldRegistration))

      setupSimpleAuthMocks(internalId)

      val response = await(client(s"/$regId/confirmation-references").put(jsonConfirmationRefs(ackRef, Some(payRef), Some(payAmount))))
      response.status shouldBe 200
      response.json shouldBe Json.toJson(confRefsWithPayment)

      await(ctRepository.findAll()).head.confirmationReferences shouldBe Some(confRefsWithPayment)
    }

    "submit to DES" when {
      val businessRegistrationResponse = s"""
           |{
           |  "registrationID":"$regId",
           |  "formCreationTimestamp":"2017-08-09T11:48:20+01:00",
           |  "language":"en",
           |  "completionCapacity":"director"
           |}
      """.stripMargin

      "registration is in Draft status and update Confirmation References with Ack Ref and Payment infos (old HO6)" in new Setup {
        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        await(ctRepository.insert(draftRegistration))

        setupSimpleAuthMocks(internalId)

        stubGet(s"/business-registration/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 200, """{"a": "b"}""")

        val response = await(client(s"/$regId/confirmation-references").put(jsonConfirmationRefs("", Some(payRef), Some(payAmount))))
        response.status shouldBe 200
        response.json shouldBe Json.toJson(confRefsWithPayment)

        val reg = await(ctRepository.findAll()).head
        reg.confirmationReferences shouldBe Some(confRefsWithPayment)
        reg.status shouldBe HELD
      }

      "registration is in Draft status and update Confirmation References but DES submission failed (old HO6)" in new Setup {
        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        await(ctRepository.insert(draftRegistration))

        setupSimpleAuthMocks(internalId)

        stubGet(s"/business-registration/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 403, "")

        val response = await(client(s"/$regId/confirmation-references").put(jsonConfirmationRefs("", Some(payRef), Some(payAmount))))
        response.status shouldBe 400

        val reg = await(ctRepository.findAll()).head
        reg.confirmationReferences shouldBe Some(confRefsWithPayment)
        reg.status shouldBe LOCKED
      }

      "registration is in Locked status (old HO6)" in new Setup {
        val confRefsWithPayment = ConfirmationReferences(
          acknowledgementReference = ackRef,
          transactionId = transId,
          paymentReference = Some(payRef),
          paymentAmount = Some(payAmount)
        )
        val lockedRegistration = draftRegistration.copy(status = LOCKED, confirmationReferences = Some(confRefsWithPayment))

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        await(ctRepository.insert(lockedRegistration))

        setupSimpleAuthMocks(internalId)

        stubGet(s"/business-registration/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 200, """{"a": "b"}""")

        val response = await(client(s"/$regId/confirmation-references").put(jsonConfirmationRefs(ackRef, Some(payRef), Some(payAmount))))
        response.status shouldBe 200
        response.json shouldBe Json.toJson(confRefsWithPayment)

        val reg = await(ctRepository.findAll()).head
        reg.confirmationReferences shouldBe Some(confRefsWithPayment)
        reg.status shouldBe HELD
      }

      "registration is in Draft status and update Confirmation References with Ack Ref (new HO5-1)" in new Setup {
        val confRefsWithoutPayment = ConfirmationReferences(
          acknowledgementReference = ackRef,
          transactionId = transId,
          paymentReference = None,
          paymentAmount = None
        )

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        await(ctRepository.insert(draftRegistration))

        setupSimpleAuthMocks(internalId)

        stubGet(s"/business-registration/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 200, """{"a": "b"}""")

        val response = await(client(s"/$regId/confirmation-references").put(jsonConfirmationRefs()))
        response.status shouldBe 200
        response.json shouldBe Json.toJson(confRefsWithoutPayment)

        val reg = await(ctRepository.findAll()).head
        reg.confirmationReferences shouldBe Some(confRefsWithoutPayment)
        reg.status shouldBe HELD
      }

      "registration is in Draft status and update Confirmation References with Ack Ref but DES submission FAILED (new HO5-1)" in new Setup {
        val confRefsWithoutPayment = ConfirmationReferences(
          acknowledgementReference = ackRef,
          transactionId = transId,
          paymentReference = None,
          paymentAmount = None
        )

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        await(ctRepository.insert(draftRegistration))

        setupSimpleAuthMocks(internalId)

        stubGet(s"/business-registration/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 403, "")

        val response = await(client(s"/$regId/confirmation-references").put(jsonConfirmationRefs()))
        response.status shouldBe 400

        val reg = await(ctRepository.findAll()).head
        reg.confirmationReferences shouldBe Some(confRefsWithoutPayment)
        reg.status shouldBe LOCKED
      }

      "registration is in Locked status and update Confirmation References with Payment infos (new HO6)" in new Setup {
        val confRefsWithoutPayment = ConfirmationReferences(
          acknowledgementReference = ackRef,
          transactionId = transId,
          paymentReference = None,
          paymentAmount = None
        )
        val lockedRegistration = draftRegistration.copy(status = LOCKED, confirmationReferences = Some(confRefsWithoutPayment))

        System.setProperty("feature.registerInterest", "false")
        System.setProperty("feature.etmpHoldingPen", "true")

        await(ctRepository.insert(lockedRegistration))

        setupSimpleAuthMocks(internalId)

        stubGet(s"/business-registration/business-tax-registration/$regId", 200, businessRegistrationResponse)
        stubPost(s"/business-registration/corporation-tax", 200, """{"a": "b"}""")

        val response = await(client(s"/$regId/confirmation-references").put(jsonConfirmationRefs(ackRef, Some(payRef), Some(payAmount))))
        response.status shouldBe 200
        response.json shouldBe Json.toJson(confRefsWithPayment)

        val reg = await(ctRepository.findAll()).head
        reg.confirmationReferences shouldBe Some(confRefsWithPayment)
        reg.status shouldBe HELD
      }
    }
  }
}
