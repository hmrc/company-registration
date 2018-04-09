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

package jobs

import java.util.UUID

import com.google.inject.name.Names
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.{JsObject, Json, OWrites}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.commands.WriteResult
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.play.scheduling.ScheduledJob

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveStaleDocumentsJobISpec extends IntegrationSpecBase {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$mockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort",
    "microservice.services.business-registration.host" -> s"$mockHost",
    "microservice.services.business-registration.port" -> s"$mockPort",
    "microservice.services.des-service.host" -> s"$mockHost",
    "microservice.services.des-service.port" -> s"$mockPort",
    "staleDocumentAmount" -> 2
  )

  class Setup extends MongoDbConnection {
    val repository = new CorporationTaxRegistrationMongoRepository(db)
    await(repository.drop)
    await(repository.ensureIndexes)

    implicit val jsObjWts: OWrites[JsObject] = OWrites(identity)

    def insert(reg: CorporationTaxRegistration): WriteResult = await(repository.insert(reg))
    def count: Int = await(repository.count)
    def retrieve(regId: String): List[JsObject] = await(repository.collection.find(Json.obj()).cursor[JsObject]().collect[List]())
  }

  val txID = "txId"
  val txID2 = "txId2"
  val txID3 = "txId3"

  val regId = "regID"
  val regId2 = "regID2"
  val regId3 = "regID3"

  def corporationTaxRegistration(status: String = "draft",
                                 lastSignedIn: DateTime = DateTime.now(DateTimeZone.UTC),
                                 regId: String = UUID.randomUUID().toString,
                                 txID: String = txID) = CorporationTaxRegistration(
    status = status,
    internalId = "testID",
    registrationID = regId,
    formCreationTimestamp = "testDateTime",
    language = "en",
    companyDetails = Some(CompanyDetails(
      "testCompanyName",
      CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
      PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
      "testJurisdiction"
    )),
    contactDetails = Some(ContactDetails(
      "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
    )),
    tradingDetails = Some(TradingDetails("true")),
    confirmationReferences = Some(ConfirmationReferences("ackRef", txID, None, None)),
    acknowledgementReferences = None,
    createdTime = DateTime.parse("2017-09-04T14:49:48.261"),
    lastSignedIn = lastSignedIn
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  def lookupJob(name: String): ScheduledJob = {
    val qualifier = Some(QualifierInstance(Names.named(name)))
    val key = BindingKey[ScheduledJob](classOf[ScheduledJob], qualifier)
    app.injector.instanceOf[ScheduledJob](key)
  }

  "Remove Stale Documents Job" should {
    "take no action" when {
      "job is disabled" in new Setup {
        System.setProperty("feature.removeStaleDocuments", "false")

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.execute)
        res shouldBe job.Result("Feature remove-stale-documents-job is turned off")
      }
    }

    "delete 2 documents" when {
      "there are two stale document and 1 non-stale" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/incorporation-information/$txID2/incorporation-update", 200, s"""{}""")
        stubGet(s"/incorporation-information/$txID3/incorporation-update", 200, s"""{}""")

        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId2", 200, """{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId3", 200, """{}""")

        stubDelete(s"/incorporation-information/subscribe/$txID3/regime/CT/subscriber/SCRS?force=true", 200, s"""""")
        stubDelete(s"/incorporation-information/subscribe/$txID2/regime/CT/subscriber/SCRS?force=true", 200, s"""""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(84), regId = regId, txID = txID))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(92), regId = regId2, txID = txID2))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId3, txID = txID3))

        count shouldBe 3

        System.setProperty("feature.removeStaleDocuments", "true")
        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.execute)

        res shouldBe job.Result("[remove-stale-documents-job] Successfully deleted 2 stale documents")

        count shouldBe 1
      }
    }

    "delete 1 documents" when {
      "there is one stale document" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")
        stubDelete(s"/incorporation-information/subscribe/$txID/regime/CT/subscriber/SCRS?force=true", 200, s"""""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        System.setProperty("feature.removeStaleDocuments", "true")
        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.execute)

        res shouldBe job.Result("[remove-stale-documents-job] Successfully deleted 1 stale documents")

        count shouldBe 0
      }

      "there is one stale document and 2 non-stale" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/incorporation-information/$txID2/incorporation-update", 200, s"""{}""")
        stubGet(s"/incorporation-information/$txID3/incorporation-update", 200, s"""{}""")

        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId3", 200, """{}""")
        stubDelete(s"/incorporation-information/subscribe/$txID3/regime/CT/subscriber/SCRS?force=true", 200, s"""""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(84), regId = regId, txID = txID))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(85), regId = regId2, txID = txID2))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId3, txID = txID3))

        count shouldBe 3

        System.setProperty("feature.removeStaleDocuments", "true")
        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.execute)

        res shouldBe job.Result("[remove-stale-documents-job] Successfully deleted 1 stale documents")

        count shouldBe 2
      }
    }

    "delete 0 documents" when {
      "there is one stale document with a CRN" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{"crn" : "crn"}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        System.setProperty("feature.removeStaleDocuments", "true")
        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.execute)

        res shouldBe job.Result("[remove-stale-documents-job] Successfully deleted 0 stale documents")

        count shouldBe 1
      }

      "there is one stale document of which it's BR document failed to delete" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 400, """{}""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        System.setProperty("feature.removeStaleDocuments", "true")
        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.execute)

        res shouldBe job.Result("[remove-stale-documents-job] Successfully deleted 0 stale documents")

        count shouldBe 1
      }

      "there are no stale documents" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(85), regId = regId))

        count shouldBe 1

        System.setProperty("feature.removeStaleDocuments", "true")
        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.execute)

        res shouldBe job.Result("[remove-stale-documents-job] Successfully deleted 0 stale documents")

        count shouldBe 1
      }

      "there is an old submitted document" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")

        insert(corporationTaxRegistration(status = "submitted", lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(100), regId = regId))

        count shouldBe 1

        System.setProperty("feature.removeStaleDocuments", "true")
        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.execute)

        res shouldBe job.Result("[remove-stale-documents-job] Successfully deleted 0 stale documents")

        count shouldBe 1
      }
    }
  }

}
