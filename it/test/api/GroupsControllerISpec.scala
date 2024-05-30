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

import auth.CryptoSCRS
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository
import test.itutil.WiremockHelper.stubAuthorise
import test.itutil.{IntegrationSpecBase, LoginStub, MongoIntegrationSpec, WiremockHelper}
import uk.gov.hmrc.http.{HeaderNames => GovHeaderNames}

import scala.concurrent.ExecutionContext.Implicits.global

class GroupsControllerISpec extends IntegrationSpecBase with MongoIntegrationSpec with LoginStub {
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val regId: String = "123"
  val internalId: String = "456"
  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: Int = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration: Map[String, String] = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  private def client(path: String) =
    ws.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path")
      .withFollowRedirects(false)
      .withHttpHeaders(
        "Content-Type"->"application/json",
        GovHeaderNames.authorisation -> "Bearer123"
      )

  class Setup {
    val crypto: CryptoSCRS = app.injector.instanceOf[CryptoSCRS]
    val repository: CorporationTaxRegistrationMongoRepository = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    repository.deleteAll
    await(repository.ensureIndexes)
  }

  val jsonDoc: String => JsObject = (encryptedUTR: String) => Json.parse(
    s"""
       |{
       |"internalId":"$internalId","registrationID":"$regId","status":"draft","formCreationTimestamp":"testDateTime","language":"en",
       |"createdTime":1488304097470,"lastSignedIn":1488304097486,
       |"groups" : {
       |   "groupRelief": true,
       |   "nameOfCompany": {
       |     "name": "testCompanyName",
       |     "nameType" : "Other"
       |   },
       |   "addressAndType" : {
       |     "addressType" : "ALF",
       |       "address" : {
       |         "line1": "1 abc",
       |         "line2" : "2 abc",
       |         "line3" : "3 abc",
       |         "line4" : "4 abc",
       |         "country" : "country A",
       |         "postcode" : "ZZ1 1ZZ"
       |     }
       |   },
       |   "groupUTR" : {
       |     "UTR" : $encryptedUTR
       |   }
       |}
       |}
      """.stripMargin).as[JsObject]

  "returnGroupsBlock" must {
    "return groups json and a 200" in new Setup {
      stubAuthorise(200, "internalId" -> internalId)
      repository.insertRaw(jsonDoc(crypto.wts.writes("1234567890").toString()))
      val res: WSResponse = await(client(s"/$regId/groups").get())
      res.status mustBe 200
      res.json mustBe Json.parse("""{
                                     |   "groupRelief": true,
                                     |   "nameOfCompany": {
                                     |     "name": "testCompanyName",
                                     |     "nameType" : "Other"
                                     |   },
                                     |   "addressAndType" : {
                                     |     "addressType" : "ALF",
                                     |       "address" : {
                                     |         "line1": "1 abc",
                                     |         "line2" : "2 abc",
                                     |         "line3" : "3 abc",
                                     |         "line4" : "4 abc",
                                     |         "country" : "country A",
                                     |         "postcode" : "ZZ1 1ZZ"
                                     |     }
                                     |   },
                                     |   "groupUTR" : {
                                     |     "UTR" : "1234567890"
                                     |   }
                                     |}""".stripMargin)
    }
    "return 403 when user not authorised" in new Setup {
      stubAuthorise(401, "internalId" -> internalId)
      repository.insertRaw(jsonDoc(crypto.wts.writes("1234567890").toString()))
      val res: WSResponse = await(client(s"/$regId/groups").get())
      res.status mustBe 403
    }
  }
  "dropGroupsBlock" must {
    "drop groups json" in new Setup {
      stubAuthorise(200, "internalId" -> internalId)
      repository.insertRaw(jsonDoc(crypto.wts.writes("1234567890").toString()))
      await(repository.returnGroupsBlock(regId)).isEmpty mustBe false
      val res: WSResponse = await(client(s"/$regId/groups").delete())
      res.status mustBe 204
      await(repository.returnGroupsBlock(regId)).isEmpty mustBe true
    }
    "return 403 when user not authorised" in new Setup {
      stubAuthorise(401, "internalId" -> internalId)
      repository.insertRaw(jsonDoc(crypto.wts.writes("1234567890").toString()))
      val res: WSResponse = await(client(s"/$regId/groups").delete())
      res.status mustBe 403
    }
  }
  "updateGroupsBlock" must {
    "return groups updated" in new Setup {
      stubAuthorise(200, "internalId" -> internalId)
      repository.insertRaw(jsonDoc(crypto.wts.writes("1234567890").toString()))
      await(repository.returnGroupsBlock(regId)).isEmpty mustBe false
      val res: WSResponse = await(client(s"/$regId/groups").put(
        """
          |{
          | "groupRelief": false
          |}
        """.stripMargin))
      res.status mustBe 200
      res.json mustBe Json.parse( """
                                      |{
                                      | "groupRelief": false
                                      |}
                                    """.stripMargin)

    }
    "return 403 when user not authorised" in new Setup {
      stubAuthorise(401, "internalId" -> internalId)
      repository.insertRaw(jsonDoc(crypto.wts.writes("1234567890").toString()))
      val res: WSResponse = await(client(s"/$regId/groups").put( """
                                                       |{
                                                       | "groupRelief": false
                                                       |}
                                                     """.stripMargin))
      res.status mustBe 403
    }
  }
}
