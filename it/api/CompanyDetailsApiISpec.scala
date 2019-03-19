
package api

import auth.CryptoSCRS
import controllers.routes
import itutil.{IntegrationSpecBase, LoginStub, WiremockHelper}
import models._
import play.api.Application
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WS
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import repositories.{CorporationTaxRegistrationMongoRepository, SequenceMongoRepo}
import uk.gov.hmrc.http.{HeaderNames => GovHeaderNames}

import scala.concurrent.ExecutionContext.Implicits.global

class CompanyDetailsApiISpec extends IntegrationSpecBase with LoginStub {
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
  val regId = "reg-id-12345"
  val internalId = "int-id-12345"
  val transId = "trans-id-2345"
  val defaultCHROAddress = CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region"))
  val defaulPPOBAddress = PPOB("MANUAL", Some(PPOBAddress("10 tæst beet", "test tØwn", Some("tæst area"), Some("tæst coûnty"), Some("XX1 1ØZ"), Some("test coûntry"), None, "txid")))
  val nonNormalisedAddressMaxLengthCheck = PPOB("MANUAL", Some(PPOBAddress("ææ ææææ æææææ", "abcdcgasfgfags fgafsggafgææ", Some("æææææææ æææææ"), Some("tæst coûnty"), Some("XX1 1ØZ"), Some("test coûntry"), None, "txid")))
  val normalisedAddressMaxLengthCheck = PPOB("MANUAL", Some(PPOBAddress("aeae aeaeaeae aeaeaeaeae", "abcdcgasfgfags fgafsggafgae", Some("aeaeaeaeaeaeae aeaeaeaeae"), Some("taest county"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val normalisedDefaultPPOBAddress = PPOB("MANUAL", Some(PPOBAddress("10 taest beet", "test tOwn", Some("taest area"), Some("taest county"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val validPPOBAddress = PPOB("MANUAL", Some(PPOBAddress("10 test beet", "test tOwn", Some("test area"), Some("test county"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val ctDoc = CorporationTaxRegistration(internalId, regId, RegistrationStatus.DRAFT, formCreationTimestamp = "foo", language = "bar")
  val validCompanyDetails = CompanyDetails("testCompanyName", defaultCHROAddress, defaulPPOBAddress, "testJurisdiction")
  val ctDocWithCompDetails: CorporationTaxRegistration = ctDoc.copy(companyDetails = Some(validCompanyDetails))
  val nonNormalisedSpecialCharCheck = PPOB("MANUAL", Some(PPOBAddress("<123> {ABC} !*^%$£", "BDT & CFD /|@", Some("A¥€ 1 «»"), Some("B~ ¬` 2^ -+=_:;"), Some("XX1 1ØZ"), Some("test coûntry"), None, "txid")))
  val normalisedSpecialCharCheck1 = PPOB("MANUAL", Some(PPOBAddress("123 ABC", "BDT & CFD", Some("A 1"), Some("B  2 -"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val normalisedSpecialCharCheck2 = PPOB("MANUAL", Some(PPOBAddress("123 ABC ", "BDT & CFD /", Some("A 1 "), Some("B  2 -"), Some("XX1 1OZ"), Some("test country"), None, "txid")))
  val defaultSpecialCharCheck = PPOB("MANUAL", Some(PPOBAddress("123 ABC ", "BDT & CFD ", Some("A 1 "), Some("B  2 -"), Some("XX1 1OZ"), Some("test country"), None, "txid")))


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

  private def client(path: String) = WS.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path")
    .withFollowRedirects(false)
    .withHeaders("Content-Type" -> "application/json")
    .withHeaders(HeaderNames.SET_COOKIE -> getSessionCookie())
    .withHeaders(GovHeaderNames.xSessionId -> SessionId)

  class Setup {
    val rmComp = app.injector.instanceOf[ReactiveMongoComponent]
    val crypto = app.injector.instanceOf[CryptoSCRS]
    val ctRepository = new CorporationTaxRegistrationMongoRepository(
      rmComp,crypto)
    val seqRepo = app.injector.instanceOf[SequenceMongoRepo].repo

    await(ctRepository.drop)
    await(ctRepository.ensureIndexes)

    await(seqRepo.drop)
    await(seqRepo.ensureIndexes)

    System.clearProperty("feature.registerInterest")

    def setupCTRegistration(reg: CorporationTaxRegistration): WriteResult = ctRepository.insert(reg)

  }

  s"GET ${controllers.routes.CompanyDetailsController.retrieveCompanyDetails(regId).url}" should {

    "return 404 when company details does not exist" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDoc)
      val response = await(client(s"/$regId/company-details").get())
      response.status shouldBe 404
    }

    "return 200 when company details exists" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDocWithCompDetails)
      val response = await(client(s"/$regId/company-details").get())
      response.status shouldBe 200
      response.json shouldBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get)
    }
  }

  s"POST ${controllers.routes.CompanyDetailsController.updateCompanyDetails(regId).url}" should {

    "return 200 and normalise ppob with invalid characters" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDoc)

      val response = await(client(s"/$regId/company-details").put(validPostData(defaulPPOBAddress).toString()))
      response.status shouldBe 200
      response.json shouldBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = normalisedDefaultPPOBAddress))
    }

    "return 200 with no normalising needed with an address type of manual" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDocWithCompDetails)
      val response = await(client(s"/$regId/company-details").put(validPostData(validPPOBAddress).toString()))
      response.status shouldBe 200
      response.json shouldBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = validPPOBAddress))
    }
    "return 200 with unnormalisable ppob because characters are trimmed to max length" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDocWithCompDetails)
      val response = await(client(s"/$regId/company-details").put(validPostData(nonNormalisedAddressMaxLengthCheck).toString()))
      response.status shouldBe 200
      response.json shouldBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = normalisedAddressMaxLengthCheck))
    }

    "return 200 and normalise ppob with special characters" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDoc)

      val response = await(client(s"/$regId/company-details").put(validPostData(defaultSpecialCharCheck).toString()))
      response.status shouldBe 200
      response.json shouldBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = normalisedSpecialCharCheck1))
    }

    "return 200 with normalised ppob special char test" in new Setup {
      stubAuthorise(internalId)
      setupCTRegistration(ctDocWithCompDetails)
      val response = await(client(s"/$regId/company-details").put(validPostData(nonNormalisedSpecialCharCheck).toString()))
      response.status shouldBe 200
      response.json shouldBe validCompanyDetailsResponse(ctDocWithCompDetails.companyDetails.get.copy(ppob = normalisedSpecialCharCheck2))
    }
  }
}