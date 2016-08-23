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

import java.util.UUID

import fixtures.{ContactDetailsFixture, CorporationTaxRegistrationFixture}
import helpers.MongoMocks
import models.{CorporationTaxRegistration, TradingDetails}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.BeforeAndAfter
import reactivemongo.bson.{BSONDocument, BSONString}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class CorporationTaxRegistrationRepositorySpec extends UnitSpec with MongoSpecSupport with MongoMocks with MockitoSugar with BeforeAndAfter
  with CorporationTaxRegistrationFixture with ContactDetailsFixture {

	class MockedCorporationTaxRegistrationRepository extends CorporationTaxRegistrationMongoRepository {
		override lazy val collection = mockCollection()
	}

	val repository = new MockedCorporationTaxRegistrationRepository()

	before {
		reset(repository.collection)
	}

  val registrationID = "12345"

	"createCorporationTaxRegistration" should {
		val randomRegid = UUID.randomUUID().toString

		"Store a document " in {

			val captor = ArgumentCaptor.forClass(classOf[CorporationTaxRegistration])

			val ctData = CorporationTaxRegistration.empty.copy(registrationID = randomRegid)

			setupAnyInsertOn(repository.collection, fails = false)

			val ctDataResult = await(repository.createCorporationTaxRegistration(ctData))

			verifyInsertOn(repository.collection, captor)

			captor.getValue.registrationID shouldBe randomRegid
			ctDataResult.registrationID shouldBe randomRegid
		}

    "fail on insert" in {
      setupAnyInsertOn(repository.collection, fails = true)

      val result = await(repository.createCorporationTaxRegistration(validCorporationTaxRegistration))
      result shouldBe validCorporationTaxRegistration

    }


		"return None when no document exists" in {

			val ctDataModel = mock[CorporationTaxRegistration]

			when(ctDataModel.registrationID) thenReturn randomRegid

			val selector = BSONDocument("registrationID" -> BSONString(randomRegid))
			setupFindFor(repository.collection, selector, None)

			val result = await(repository.getOid(randomRegid))

			result should be(None)
		}
	}

  "retrieveCompanyDetails" should {

    "fetch a document by registrationID if it exists" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validCorporationTaxRegistration))

      val result = await(repository.retrieveCompanyDetails(registrationID))
      result shouldBe validCorporationTaxRegistration.companyDetails
    }

    "return None when the record to retrieve doesn't exists" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)

      val result = await(repository.retrieveCompanyDetails(registrationID))
      result shouldBe None
    }
  }

  "updateCompanyDetails" should {

    "retrieve a document by registration ID and update it with the new one" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateCompanyDetails(registrationID, validCompanyDetails))
      result shouldBe validCorporationTaxRegistration.companyDetails
    }

    "return a None when the document to update did not exist" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)

      val result = await(repository.updateCompanyDetails(registrationID, validCompanyDetails))
      result shouldBe None
    }
  }

  "retrieveContactDetails" should {

    "fetch a document by registration ID if one is found" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validCorporationTaxRegistration))

      val result = await(repository.retrieveContactDetails(registrationID))
      result shouldBe validCorporationTaxRegistration.contactDetails
    }

    "return None when a document cannot be found" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)

      val result = await(repository.retrieveContactDetails(registrationID))
      result shouldBe None
    }
  }

  "fetchContactDetails" should {

    "update a contact details on a document found by registrationID if one exists" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateContactDetails(registrationID, contactDetails))
      result shouldBe validCorporationTaxRegistration.contactDetails
    }

    "return None when a document cannot be found" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateContactDetails(registrationID, contactDetails))
      result shouldBe None
    }
  }

  "getOID" should {

    "return the reg ID and OID from a fetched document" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validCorporationTaxRegistration))

      val result = await(repository.getOid(registrationID))
      result shouldBe Some(("0123456789", "9876543210"))
    }
  }

  "retrieveTradingDetails" should {
    "return a TradingDetails" in  {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.retrieveTradingDetails(registrationID))
      result shouldBe validCorporationTaxRegistration.tradingDetails
    }
  }

  "updateTradingDetails" should {
    "return a TradingDetails" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateTradingDetails(registrationID, TradingDetails()))
      result shouldBe validCorporationTaxRegistration.tradingDetails
    }

    "return None" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateTradingDetails(registrationID, TradingDetails()))
      result shouldBe None
    }
  }
}
