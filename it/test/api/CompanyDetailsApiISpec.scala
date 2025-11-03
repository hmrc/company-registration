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

package test.api

import config.LangConstants
import controllers.routes
import models._
import org.mongodb.scala.result.InsertOneResult
import play.api.Application
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, SequenceMongoRepository}
import test.itutil.WiremockHelper.stubAuthorise
import test.itutil.{IntegrationSpecBase, LoginStub, MongoIntegrationSpec, WiremockHelper}
import uk.gov.hmrc.http.{HeaderNames => GovHeaderNames}

import scala.concurrent.ExecutionContext.Implicits.global

class CompanyDetailsApiISpec extends IntegrationSpecBase with MongoIntegrationSpec with LoginStub {
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]
  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: Int = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration: Map[String, Any] = Map(
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
  val regId = "reg-id-12345"
  val internalId = "int-id-12345"
  val transId = "trans-id-2345"
  val defaultCHROAddress: CHROAddress = CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region"))
  val defaulPPOBAddress: PPOB = PPOB("MANUAL", Some(PPOBAddress("10 tæst beet", "test tØwn", Some("tæst area"), Some("tæst coûnty"), Some("XX1 1ØZ"), Some("test coûntry"), None, "txid")))
  val nonNormalisedAddressMaxLengthCheck: PPOB = PPOB("MANUAL", Some(PPOBAddress("ææ ææææ æææææ", "abcdcgasfgfags fgafsggafgææ", Some("æææææææ æææææ"), Some("tæst coûnty"), Some("XX1 1ØZ"), Some("test coûntry"), None, "txid")))
  val normalisedAddressMaxLengthCheck: PPOB = PPOB("MANUAL", Some(PPOBAddress("aeae aeaeaeae aeaeaeaeae", "abcdcgasfgfags fgafsggafgae", Some("aeaeaeaeaeaeae aeaeaeaeae"), Some("taest county"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val normalisedDefaultPPOBAddress: PPOB = PPOB("MANUAL", Some(PPOBAddress("10 taest beet", "test tOwn", Some("taest area"), Some("taest county"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val validPPOBAddress: PPOB = PPOB("MANUAL", Some(PPOBAddress("10 test beet", "test tOwn", Some("test area"), Some("test county"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val ctDoc: CorporationTaxRegistration = CorporationTaxRegistration(internalId, regId, RegistrationStatus.DRAFT, formCreationTimestamp = "testTimestamp", language = LangConstants.english)
  val validCompanyDetails: CompanyDetails = CompanyDetails("testCompanyName", defaultCHROAddress, defaulPPOBAddress, "testJurisdiction")
  val ctDocWithCompDetails: CorporationTaxRegistration = ctDoc.copy(companyDetails = Some(validCompanyDetails))
  val nonNormalisedSpecialCharCheck: PPOB = PPOB("MANUAL", Some(PPOBAddress("<123> {ABC} !*^%$£", "BDT & CFD /|@", Some("A¥€ 1 «»"), Some("B~ ¬` 2^ -+=_"), Some("XX1 1ØZ"), Some("test coûntry"), None, "txid")))
  val normalisedSpecialCharCheck1: PPOB = PPOB("MANUAL", Some(PPOBAddress("123 ABC", "BDT & CFD", Some("A 1"), Some("B  2 -"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val normalisedSpecialCharCheck2: PPOB = PPOB("MANUAL", Some(PPOBAddress("123 ABC ", "BDT & CFD /", Some("A 1 "), Some("B  2 -"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val defaultSpecialCharCheck: PPOB = PPOB("MANUAL", Some(PPOBAddress("123 ABC ", "BDT & CFD ", Some("A 1 "), Some("B  2 -"), Some("XX1 1OZ"), Some("test country"), None, "txid")))


  val validCompanyDetailsResponse: CompanyDetails => JsObject = {
    Json.toJson(_).as[JsObject] ++ Json.obj(
      "tradingDetails" -> TradingDetails(),
      "links" -> Json.obj(
        "self" -> routes.CompanyDetailsController.retrieveCompanyDetails(regId).url,
        "registration" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(regId).url
      ))
  }
  val validPostData: PPOB => JsObject = address => Json.obj(
    "companyName" -> "testCompanyName",
    "cHROAddress" -> Json.toJson(defaultCHROAddress),
    "pPOBAddress" -> Json.toJson(address),
    "jurisdiction" -> "testJurisdiction"
  )

  private def client(path: String) = ws.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path")
    .withFollowRedirects(false)
    .withHttpHeaders(
      "Content-Type" -> "application/json",
      HeaderNames.SET_COOKIE -> getSessionCookie(),
      GovHeaderNames.xSessionId -> SessionId,
      GovHeaderNames.authorisation -> "Bearer123"
    )

  class Setup {
    val ctRepository: CorporationTaxRegistrationMongoRepository = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    val seqRepo: SequenceMongoRepository = app.injector.instanceOf[SequenceMongoRepository]

    ctRepository.deleteAll()
    await(ctRepository.ensureIndexes())

    seqRepo.deleteAll()
    await(seqRepo.ensureIndexes())

    System.clearProperty("feature.registerInterest")

    def setupCTRegistration(reg: CorporationTaxRegistration): InsertOneResult = ctRepository.insert(reg)

  }

  s"GET ${controllers.routes.CompanyDetailsController.retrieveCompanyDetails(regId).url}" must {

    "return 404 when company details does not exist" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDoc)
      val response: WSResponse = await(client(s"/$regId/company-details").get())
      response.status mustBe 404
    }

    "return 200 when company details exists" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDocWithCompDetails)
      val response: WSResponse = await(client(s"/$regId/company-details").get())
      response.status mustBe 200
      response.json mustBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get)
    }
  }

  s"POST ${controllers.routes.CompanyDetailsController.updateCompanyDetails(regId).url}" must {

    "return 200 and normalise ppob with invalid characters" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDoc)

      val response: WSResponse = await(client(s"/$regId/company-details").put(validPostData(defaulPPOBAddress).toString()))
      response.status mustBe 200
      response.json mustBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = normalisedDefaultPPOBAddress))
    }

    "return 200 with no normalising needed with an address type of manual" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDocWithCompDetails)
      val response: WSResponse = await(client(s"/$regId/company-details").put(validPostData(validPPOBAddress).toString()))
      response.status mustBe 200
      response.json mustBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = validPPOBAddress))
    }
    "return 200 with unnormalisable ppob because characters are trimmed to max length" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDocWithCompDetails)
      val response: WSResponse = await(client(s"/$regId/company-details").put(validPostData(nonNormalisedAddressMaxLengthCheck).toString()))
      response.status mustBe 200
      response.json mustBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = normalisedAddressMaxLengthCheck))
    }

    "return 200 and normalise ppob with special characters" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDoc)

      val response: WSResponse = await(client(s"/$regId/company-details").put(validPostData(defaultSpecialCharCheck).toString()))
      response.status mustBe 200
      response.json mustBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = normalisedSpecialCharCheck1))
    }

    "return 200 with normalised ppob special char test" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDocWithCompDetails)
      val response: WSResponse = await(client(s"/$regId/company-details").put(validPostData(nonNormalisedSpecialCharCheck).toString()))
      response.status mustBe 200
      response.json mustBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = normalisedSpecialCharCheck2))
    }
  }

  s"PUT ${controllers.routes.CompanyDetailsController.saveHandOff2ReferenceAndGenerateAckRef("").url}" must {
    "return 200 with json body containing ackref if conf refs dont exist in ct" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDoc)
      ctRepository.count mustBe 1
      seqRepo.count mustBe 0
      val response: WSResponse = await(client(s"/$regId/handOff2Reference-ackRef-save")
        .put(Json.obj("transaction_id" -> "testTransactionId").toString()))
      response.status mustBe 200
      response.json.as[JsObject] mustBe Json.obj("acknowledgement-reference" -> "BRCT00000000001")
      seqRepo.count mustBe 1
      val res: ConfirmationReferences = await(ctRepository.getExistingRegistration(regId)).confirmationReferences.get
      res mustBe ConfirmationReferences("BRCT00000000001", "testTransactionId", None, None)
    }
    "return 200 with json body containing existing ackref in db but updated txid" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDoc.copy(confirmationReferences = Some(ConfirmationReferences("testAckRef", "testTransactionId", None, None))))
      ctRepository.count mustBe 1
      seqRepo.count mustBe 0
      val response: WSResponse = await(client(s"/$regId/handOff2Reference-ackRef-save")
        .put(Json.obj("transaction_id" -> "testTxId").toString()))
      response.status mustBe 200
      seqRepo.count mustBe 0
      response.json.as[JsObject] mustBe Json.obj("acknowledgement-reference" -> "testAckRef")
      val res: ConfirmationReferences = await(ctRepository.getExistingRegistration(regId)).confirmationReferences.get
      res mustBe ConfirmationReferences("testAckRef", "testTxId", None, None)
    }
  }
}