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

package repositories

import models._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CorporationTaxRegistrationMongoRepositoryISpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

	class Setup {
		val repository = new CorporationTaxRegistrationMongoRepository()
		await(repository.drop)
		await(repository.ensureIndexes)
	}

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): Future[WriteResult] = {
    repo.insert(ctRegistration)
  }

  "updateSubmissionStatus" should {

    val registrationId = "testRegId"

    val corporationTaxRegistration = CorporationTaxRegistration(
      OID = "testOID",
      registrationID = registrationId,
      formCreationTimestamp = "testDateTime",
      language = "en"
    )

    "return an updated held status" in new Setup {
      val status = "held"

      await(setupCollection(repository, corporationTaxRegistration))

      val response = repository.updateSubmissionStatus(registrationId, status)
      await(response) shouldBe "held"
    }
  }

  "removeTaxRegistrationInformation" should {

    val registrationId = "testRegId"

    val corporationTaxRegistration = CorporationTaxRegistration(
      OID = "testOID",
      registrationID = registrationId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        ROAddress("10", "test street", "test town", "test area", "test county", "XX1 1ZZ", "test country"),
        PPOBAddress("10", "test street", Some("test town"), Some("test area"), Some("test county"), "XX1 1ZZ", "test country"),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        Some("testFirstName"), Some("testMiddleName"), Some("testSurname"), Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails(true))
    )

    "remove companyDetails, contactDetails and tradingDetails objects from the collection" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration))

      val response = repository.removeTaxRegistrationInformation(registrationId)
      await(response) shouldBe true
    }
  }
}
