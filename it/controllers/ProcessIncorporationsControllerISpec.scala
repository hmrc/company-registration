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

package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import itutil.{IntegrationSpecBase, WiremockHelper}
import models.{AccountingDetails, ConfirmationReferences, CorporationTaxRegistration, Email}
import models.RegistrationStatus.HELD
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WS
import play.modules.reactivemongo.MongoDbConnection
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class ProcessIncorporationsControllerISpec extends IntegrationSpecBase {
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
    "microservice.services.des-service.authorization-token" -> "testAuthToken"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  private def client(path: String) = WS.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path").
    withFollowRedirects(false).
    withHeaders("Content-Type"->"application/json")

  class Setup extends MongoDbConnection {
    val repository = new CorporationTaxRegistrationMongoRepository(db)
    val heldRepository = new HeldSubmissionMongoRepository(db)
    await(repository.drop)
    await(repository.ensureIndexes)
    await(heldRepository.drop)
    await(heldRepository.ensureIndexes)
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
      paymentReference = payRef,
      paymentAmount = "12"
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

  def jsonIncorpStatus(incorpDate: String) =
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

  def jsonAppendDataForSubmission(incorpDate: String) = Json.parse(
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

  "Process Incorporation" should {
    "send a full submission to DES with correct active date" when {
      "user has selected a Future Date for Accounting Dates before the incorporation date" in new Setup {
        val incorpDate = "2017-07-24"

        await(repository.insert(heldRegistration))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 200, ctSubmission)

        val response = await(client("/process-incorp").put(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200

        val crPosts = findAll(postRequestedFor(urlMatching(s"/business-registration/corporation-tax")))
        val captor = crPosts.get(0)
        val json = Json.parse(captor.getBodyAsString)
        (json \ "registration" \ "corporationTax" \ "companyActiveDate").as[String] shouldBe incorpDate
      }

      "user has selected a Future Date for Accounting Dates after the incorporation date" in new Setup {
        val incorpDate = "2017-07-22"

        await(repository.insert(heldRegistration))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(activeDate)).toString
        stubPost("/business-registration/corporation-tax", 200, ctSubmission)

        val response = await(client("/process-incorp").put(jsonIncorpStatus(incorpDate)))
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

        await(repository.insert(heldRegistration2))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 200, ctSubmission)

        val response = await(client("/process-incorp").put(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200

        val crPosts = findAll(postRequestedFor(urlMatching(s"/business-registration/corporation-tax")))
        val captor = crPosts.get(0)
        val json = Json.parse(captor.getBodyAsString)
        (json \ "registration" \ "corporationTax" \ "companyActiveDate").as[String] shouldBe incorpDate
      }

      "user has selected Not Intend to trade for Accounting Dates" in new Setup {
        import AccountingDetails.NOT_PLANNING_TO_YET

        val incorpDate = "2017-07-25"
        val activeDateToDES = "2022-07-25"
        val heldRegistration2: CorporationTaxRegistration = heldRegistration.copy(
          accountingDetails = Some(heldRegistration.accountingDetails.get.copy(status = NOT_PLANNING_TO_YET, activeDate = None))
        )

        await(repository.insert(heldRegistration2))
        await(heldRepository.storePartialSubmission(regId, ackRef, heldJson))

        setupSimpleAuthMocks()
        stubPost("/hmrc/email", 202, "")
        val ctSubmission = heldJson.deepMerge(jsonAppendDataForSubmission(incorpDate)).toString
        stubPost("/business-registration/corporation-tax", 200, ctSubmission)

        val response = await(client("/process-incorp").put(jsonIncorpStatus(incorpDate)))
        response.status shouldBe 200

        val crPosts = findAll(postRequestedFor(urlMatching(s"/business-registration/corporation-tax")))
        val captor = crPosts.get(0)
        val json = Json.parse(captor.getBodyAsString)
        (json \ "registration" \ "corporationTax" \ "companyActiveDate").as[String] shouldBe activeDateToDES
      }
    }
  }
}
