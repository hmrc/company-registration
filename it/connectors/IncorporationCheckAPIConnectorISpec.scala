/*
 * Copyright 2022 HM Revenue & Customs
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

package connectors

import itutil.WiremockHelper._
import itutil.{IntegrationSpecBase, WiremockHelper}
import models._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier


class IncorporationCheckAPIConnectorISpec extends IntegrationSpecBase {
  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.company-registration-frontend.host" -> s"$mockHost",
    "microservice.services.company-registration-frontend.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  implicit val hc = HeaderCarrier()

  def asDate(date: String) = DateTime.parse(date, DateTimeFormat.forPattern("yyyy-MM-dd"))

  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector = app.injector.instanceOf[IncorporationCheckAPIConnector]

  "IncorporationCheckAPIConnector" must {
    "process a full response as expected" in {
      stubGet("/internal/check-submission.*", 200,
        s"""{
           |"items":[{
           |   "company_number":"c",
           |   "transaction_status":"s",
           |   "transaction_type":"incorporation",
           |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
           |   "transaction_id":"t",
           |   "incorporated_on":"2017-05-06",
           |   "timepoint":"tp"
           | }],"links":{"next":"testLink"}
           |}""".stripMargin
      )

      val expected = SubmissionCheckResponse(
        Seq(IncorpUpdate("t", "s", Some("c"), Some(asDate("2017-05-06")), "tp", None)),
        "testLink"
      )
      val actual = await(incorporationCheckAPIConnector.checkSubmission(None))

      actual mustBe expected
    }

    "process a minimal single response" in {
      stubGet("/internal/check-submission.*", 200,
        s"""{
           |"items":[
           |  {"transaction_status":"s", "transaction_id":"t", "timepoint":"tp"}
           | ],"links":{"next":"testLink"}
           |}""".stripMargin
      )

      val expected = SubmissionCheckResponse(
        Seq(IncorpUpdate("t", "s", None, None, "tp", None)),
        "testLink"
      )
      val actual = await(incorporationCheckAPIConnector.checkSubmission(None))

      actual mustBe expected
    }

    "process a couple of results" in {
      stubGet("/internal/check-submission.*", 200,
        s"""{
           |"items":[
           |  {"transaction_status":"s1", "transaction_id":"t1", "timepoint":"tp1"},
           |  {"transaction_status":"s2", "transaction_id":"t2", "timepoint":"tp2"}
           | ],"links":{"next":"testLink"}
           |}""".stripMargin
      )

      val expected = SubmissionCheckResponse(Seq(
        IncorpUpdate("t1", "s1", None, None, "tp1", None),
        IncorpUpdate("t2", "s2", None, None, "tp2", None)
      ), "testLink")

      val actual = await(incorporationCheckAPIConnector.checkSubmission(None))

      actual mustBe expected
    }

    "process with no results" in {
      stubGet("/internal/check-submission.*", 200, """{"items":[],"links":{"next":"testLink"}}""")

      val expected = SubmissionCheckResponse(Seq(), "testLink")
      val actual = await(incorporationCheckAPIConnector.checkSubmission(None))

      actual mustBe expected
    }

  }
}
