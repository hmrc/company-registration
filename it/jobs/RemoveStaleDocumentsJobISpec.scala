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

import auth.CryptoSCRS
import com.google.inject.name.Names
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.{Application, Logger}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.commands.WriteResult
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.play.test.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveStaleDocumentsJobISpec extends IntegrationSpecBase with LogCapturing {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
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
      val rmc = app.injector.instanceOf[ReactiveMongoComponent]
      val crypto = app.injector.instanceOf[CryptoSCRS]
     val repository = new CorporationTaxRegistrationMongoRepository(rmc,crypto)
    await(repository.drop)
    await(repository.count) shouldBe 0


    implicit val jsObjWts: OWrites[JsObject] = OWrites(identity)

    def insert(reg: CorporationTaxRegistration): WriteResult = await(repository.insert(reg))
    def count: Int = await(repository.count)
    def retrieve(regId: String): List[JsObject] = await(repository.collection.find(Json.obj()).cursor[JsObject]().collect[List](-1,Cursor.FailOnError()))
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
       Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
    )),
    tradingDetails = Some(TradingDetails("true")),
    confirmationReferences = Some(ConfirmationReferences("ackRef", txID, None, None)),
    acknowledgementReferences = None,
    createdTime = DateTime.parse("2017-09-04T14:49:48.261"),
    lastSignedIn = lastSignedIn
  )



  def lookupJob(name: String): ScheduledJob = {
    val qualifier = Some(QualifierInstance(Names.named(name)))
    val key = BindingKey[ScheduledJob](classOf[ScheduledJob], qualifier)
    app.injector.instanceOf[ScheduledJob](key)
  }

  "Remove Stale Documents Job" should {
    "delete 2 documents" when {
      "there are two stale document and 1 non-stale" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/incorporation-information/$txID2/incorporation-update", 200, s"""{}""")
        stubGet(s"/incorporation-information/$txID3/incorporation-update", 200, s"""{}""")

        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId2", 200, """{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId3", 200, """{}""")

        stubDelete(s"/incorporation-information/subscribe/$txID3/regime/ctax/subscriber/SCRS?force=true", 200, s"""""")
        stubDelete(s"/incorporation-information/subscribe/$txID2/regime/ctax/subscriber/SCRS?force=true", 200, s"""""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(84), regId = regId, txID = txID))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(92), regId = regId2, txID = txID2))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId3, txID = txID3))

        count shouldBe 3
        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]]))

        res.left.get shouldBe 2
        count shouldBe 1
      }
    }

    "delete 1 documents" when {
      "there is one stale document" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]]))

        res.left.get shouldBe 1

        count shouldBe 0
      }

      "there is one stale document using an old regime" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 204, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")
        stubDelete(s"/incorporation-information/subscribe/$txID/regime/ctax/subscriber/SCRS?force=true", 404, s"""""")
        stubDelete(s"/incorporation-information/subscribe/$txID/regime/ct/subscriber/SCRS?force=true", 200, s"""""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        val job = lookupJob("remove-stale-documents-job")
        val message = s"[processStaleDocument] Registration $regId - $txID does not have CTAX subscription. Now trying to delete CT sub."
        val delMess = "[remove-stale-documents-job] Successfully deleted 1 stale documents"
        withCaptureOfLoggingFrom(Logger) { logs =>
          val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]]))
          res.left.get shouldBe 1

          count shouldBe 0

          logs.find(event => event.getMessage == message).get.getMessage shouldBe message
          logs.find(event => event.getMessage == delMess).get.getMessage shouldBe delMess
        }
      }

      "there is one stale document with no subscriptions" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 204, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")
        stubDelete(s"/incorporation-information/subscribe/$txID/regime/ctax/subscriber/SCRS?force=true", 404, s"""""")
        stubDelete(s"/incorporation-information/subscribe/$txID/regime/ct/subscriber/SCRS?force=true", 404, s"""""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        val job = lookupJob("remove-stale-documents-job")
        val message = s"[processStaleDocument] Registration $regId - $txID does not have CTAX subscription. Now trying to delete CT sub."
        val finalMessage = s"[processStaleDocument] Registration $regId - $txID has no subscriptions."
        val delMess = "[remove-stale-documents-job] Successfully deleted 1 stale documents"

        withCaptureOfLoggingFrom(Logger) { logs =>
          val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]]))
          res.left.get shouldBe 1

          count shouldBe 0

          logs.find(event => event.getMessage.contains(message)).get.getMessage shouldBe message
          logs.find(event => event.getMessage.contains(finalMessage)).get.getMessage shouldBe finalMessage
          logs.find(event => event.getMessage.contains(delMess)).get.getMessage shouldBe delMess
        }
      }

      "there is one stale document and 3 on the whitelist" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")
        stubDelete(s"/incorporation-information/subscribe/$txID/regime/ctax/subscriber/SCRS?force=true", 200, s"""""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(100), regId = "2"))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(100), regId = "3"))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(100), regId = "1"))

        count shouldBe 4

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]])).left.get

        res shouldBe 1
        count shouldBe 3
      }

      "there is one stale document and 2 non-stale" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/incorporation-information/$txID2/incorporation-update", 200, s"""{}""")
        stubGet(s"/incorporation-information/$txID3/incorporation-update", 200, s"""{}""")

        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId3", 200, """{}""")
        stubDelete(s"/incorporation-information/subscribe/$txID3/regime/ctax/subscriber/SCRS?force=true", 200, s"""""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(84), regId = regId, txID = txID))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(85), regId = regId2, txID = txID2))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId3, txID = txID3))

        count shouldBe 3

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]])).left.get

        res shouldBe 1
        count shouldBe 2
      }

      "there is one stale document in held status" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubDelete(s"/incorporation-information/subscribe/txId/regime/ct/subscriber/SCRS?force=true", 200, "{}")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")
        stubPost("/business-incorporation/corporation-tax", 200, "{}")
        stubPost(s"/write/audit", 200, "{}")

        insert(corporationTaxRegistration(status = "held", lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]])).left.get

        res shouldBe 1
        count shouldBe 0

        val auditJson = Json.obj("journeyId" -> regId, "acknowledgementReference" -> "ackRef", "incorporationStatus" -> "Rejected", "rejectedAsNotPaid" -> true)
        verifyPOSTRequestBody("/write/audit", auditJson.toString) shouldBe true
      }
    }

    "delete 0 documents" when {
      "there is one stale document with a CRN" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{"crn" : "crn"}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]])).left.get

        res shouldBe 0
        count shouldBe 1
      }

      "there is one stale document of which it's BR document failed to delete" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 400, """{}""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(93), regId = regId))

        count shouldBe 1

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]])).left.get

        res shouldBe 0

        count shouldBe 1
      }

      "there are no stale documents" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")

        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(85), regId = regId))

        count shouldBe 1

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]])).left.get

        res shouldBe 0

        count shouldBe 1
      }

      "there is an old submitted document" in new Setup {
        stubGet(s"/incorporation-information/$txID/incorporation-update", 200, s"""{}""")
        stubGet(s"/business-registration/admin/business-tax-registration/remove/$regId", 200, """{}""")

        insert(corporationTaxRegistration(status = "submitted", lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(100), regId = regId))

        count shouldBe 1

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]])).left.get

        res shouldBe 0

        count shouldBe 1
      }

      "the documents are on the whitelist" in new Setup {
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(100), regId = "2"))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(100), regId = "3"))
        insert(corporationTaxRegistration(lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(100), regId = "1"))

        count shouldBe 3

        val job = lookupJob("remove-stale-documents-job")
        val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Int, LockResponse]])).left.get

        res shouldBe 0

        count shouldBe 3
      }
    }
  }

}
