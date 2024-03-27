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

package jobs

import config.AppStartupJobsImpl
import itutil.{IntegrationSpecBase, LogCapturingHelper, MongoIntegrationSpec, WiremockHelper}
import models.RegistrationStatus.DRAFT
import models._
import org.mongodb.scala.result.InsertOneResult
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, OWrites}
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class TakeoverJobISpec extends IntegrationSpecBase with MongoIntegrationSpec with LogCapturingHelper {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration: Map[String, Any] = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort",
    "microservice.services.business-registration.host" -> s"$mockHost",
    "microservice.services.business-registration.port" -> s"$mockPort",
    "microservice.services.des-service.host" -> s"$mockHost",
    "microservice.services.des-service.port" -> s"$mockPort",
    "staleDocumentAmount" -> 4,
    "microservice.services.skipStaleDocs" -> "MSwyLDM="
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  class Setup {
    val repository = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    repository.deleteAll
    await(repository.ensureIndexes)
    repository.count mustBe 0


    implicit val jsObjWts: OWrites[JsObject] = OWrites(identity)

    def insert(reg: CorporationTaxRegistration): InsertOneResult = repository.insert(reg)

    def count: Int = repository.count

    def retrieve(regId: String): Option[CorporationTaxRegistration] = await(repository.findOneBySelector(repository.regIDSelector(regId)))
  }

  val regId2 = "regID2"
  val regId3 = "regID3"
  val regId4 = "regID4"

  val dateTimeNow: Instant = LocalDateTime.now().withNano(0).toInstant(ZoneOffset.UTC)

  def takeOverCorporationTaxRegistration(regId: String,
                                         incompleteTakeoverBlock: Boolean): CorporationTaxRegistration = {

    def takeoverBlock: Option[TakeoverDetails] = {
      if (incompleteTakeoverBlock) {
        Some(TakeoverDetails(replacingAnotherBusiness = true, None, None, None, None))
      }
      else {
        Some(TakeoverDetails(replacingAnotherBusiness = true,
          Some("Takeover company name ltd"),
          Some(Address("Line1", "line2", Some("line3"), Some("line4"), Some("ZZ1 1ZZ"), None)),
          Some("Takeover name"),
          Some(Address("Line1", "line2", Some("line3"), Some("line4"), Some("ZZ1 1ZZ"), None))
        ))
      }
    }

    CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = "testDateTime",
      submissionTimestamp = Some("testDateTime"),
      createdTime = dateTimeNow,
      heldTimestamp = Some(dateTimeNow),
      lastSignedIn = dateTimeNow,
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), None, None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        Some("0123456789"),
        Some("0123456789"),
        Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("false")),
      status = DRAFT,
      confirmationReferences = Some(ConfirmationReferences(s"ACKFOR-$regId", s"transid-$regId", None, None)),
      takeoverDetails = takeoverBlock

    )
  }

  def takeOverCorporationTaxRegistrationResult(regId: String): CorporationTaxRegistration = {
    CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = "testDateTime",
      submissionTimestamp = Some("testDateTime"),
      createdTime = dateTimeNow,
      heldTimestamp = Some(dateTimeNow),
      lastSignedIn = dateTimeNow,
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), None, None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        Some("0123456789"),
        Some("0123456789"),
        Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("false")),
      status = DRAFT,
      confirmationReferences = Some(ConfirmationReferences(s"ACKFOR-$regId", s"transid-$regId", None, None)),
      takeoverDetails = Some(TakeoverDetails(replacingAnotherBusiness = false, None, None, None, None))
    )
  }

  lazy val runStartupJob: AppStartupJobsImpl = app.injector.instanceOf[AppStartupJobsImpl]

  "update takeover" should {
    "modify a record" when {
      "there is an entry with incomplete takeover blocks" in new Setup {
        insert(takeOverCorporationTaxRegistration(regId = regId2, incompleteTakeoverBlock = true))
        insert(takeOverCorporationTaxRegistration(regId = regId3, incompleteTakeoverBlock = true))
        insert(takeOverCorporationTaxRegistration(regId = regId4, incompleteTakeoverBlock = false))

        count mustBe 3
        runStartupJob.runEverythingOnStartUp
        retrieve("regID2") mustBe Some(takeOverCorporationTaxRegistrationResult(regId = regId2))
        retrieve("regID3") mustBe Some(takeOverCorporationTaxRegistrationResult(regId = regId3))
        retrieve("regID4") mustBe Some(takeOverCorporationTaxRegistration(regId = regId4, incompleteTakeoverBlock = false))
      }
    }
  }
}