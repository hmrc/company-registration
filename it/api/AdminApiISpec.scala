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
import models._
import models.RegistrationStatus._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.modules.reactivemongo.MongoDbConnection
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.mongo.MongoSpecSupport

class AdminApiISpec extends IntegrationSpecBase with MongoSpecSupport {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig())
    .build

  implicit val ec = app.actorSystem.dispatcher.prepare()

  val regId = "reg-id-12345"
  val internalId = "int-id-12345"

  class Setup extends MongoDbConnection {
    val repository = new CorporationTaxRegistrationMongoRepository(db)
    await(repository.drop)
    await(repository.ensureIndexes)

    def count = await(repository.count)
    def insert(doc: CorporationTaxRegistration) = await(repository.insert(doc))

    count shouldBe 0
  }

  def client(path: String) = WS.url(s"http://localhost:$port/company-registration/admin$path").
    withFollowRedirects(false)

  def setupSimpleAuthMocks() = {
    stubPost("/write/audit", 200, """{"x":2}""")
    stubGet("/auth/authority", 200, """{"uri":"xxx","credentials":{"gatewayId":"xxx2"},"userDetailsLink":"xxx3","ids":"/auth/ids"}""")
    stubGet("/auth/ids", 200, s"""{"internalId":"$internalId","externalId":"Ext-xxx"}""")
  }

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
    verifiedEmail = None,
    registrationProgress = Some("ho5"),
    acknowledgementReferences = None,
    accountsPreparation = None
  )

  "GET /admin/fetch-ho6-registration-information" should {

    val url = "/fetch-ho6-registration-information"

    "return a 200 and ho6 registration information as json" in new Setup {

      val expected = Json.parse(
        """
          |{
          |  "status":"draft",
          |  "companyName":"testCompanyName",
          |  "registrationProgress":"ho5"
          |}
        """.stripMargin)

      setupSimpleAuthMocks()

      insert(draftRegistration)

      count shouldBe 1

      val result = await(client(s"$url/$regId").get())

      result.status shouldBe 200
      result.json shouldBe expected
    }

    "return a 404 when a company registration document does not exist for the supplied regId" in new Setup {

      setupSimpleAuthMocks()

      count shouldBe 0

      val result = await(client(s"$url/$regId").get())

      result.status shouldBe 404
    }
  }
}
