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
import models._
import org.joda.time.DateTime
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
    override def indexes = Seq()
	}

	val repository = new MockedCorporationTaxRegistrationRepository()

	before {
		reset(repository.collection)
	}

  val registrationID = "12345"
  val txID = s"tx${registrationID}"

	"createCorporationTaxRegistration" should {
		val randomRegid = UUID.randomUUID().toString
    val emptyReg: CorporationTaxRegistration = CorporationTaxRegistration(
      OID = "",
      registrationID = "",
      formCreationTimestamp = "",
      language = "")

    "Store a document " in {

			val captor = ArgumentCaptor.forClass(classOf[CorporationTaxRegistration])

			val ctData = emptyReg.copy(registrationID = randomRegid)

			setupAnyInsertOn(repository.collection, fails = false)

			val ctDataResult = await(repository.createCorporationTaxRegistration(ctData))

			verifyInsertOn(repository.collection, captor)

			captor.getValue.registrationID shouldBe randomRegid
			ctDataResult.registrationID shouldBe randomRegid
		}

    "fail on insert" in {
      setupAnyInsertOn(repository.collection, fails = true)

      val result = await(repository.createCorporationTaxRegistration(validDraftCorporationTaxRegistration))
      result shouldBe validDraftCorporationTaxRegistration

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
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))

      val result = await(repository.retrieveCompanyDetails(registrationID))
      result shouldBe validDraftCorporationTaxRegistration.companyDetails
    }

    "fetch a document by transactionID if it exists" in {
      val selector = BSONDocument("confirmationReferences.transactionId" -> BSONString(txID))
      setupFindFor(repository.collection, selector, Some(validHeldCorporationTaxRegistration))

      val result = await(repository.retrieveRegistrationByTransactionID(txID))
      result shouldBe Some(validHeldCorporationTaxRegistration)
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
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateCompanyDetails(registrationID, validCompanyDetails))
      result shouldBe validDraftCorporationTaxRegistration.companyDetails
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
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))

      val result = await(repository.retrieveContactDetails(registrationID))
      result shouldBe validDraftCorporationTaxRegistration.contactDetails
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
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateContactDetails(registrationID, contactDetails))
      result shouldBe validDraftCorporationTaxRegistration.contactDetails
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
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))

      val result = await(repository.getOid(registrationID))
      result shouldBe Some(("0123456789", "9876543210"))
    }
  }

  "retrieveTradingDetails" should {
    "return a TradingDetails" in  {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.retrieveTradingDetails(registrationID))
      result shouldBe validDraftCorporationTaxRegistration.tradingDetails
    }

    "return an empty option when a document does'nt exist" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.retrieveTradingDetails(registrationID))
      result shouldBe None
    }
  }

  "updateTradingDetails" should {
    "return a TradingDetails" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateTradingDetails(registrationID, TradingDetails()))
      result shouldBe validDraftCorporationTaxRegistration.tradingDetails
    }

    "return None" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateTradingDetails(registrationID, TradingDetails()))
      result shouldBe None
    }
  }

  "retrieveAccountingDetails" should {
    "return an AccountingDetails model" in  {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.retrieveAccountingDetails(registrationID))
      result shouldBe validDraftCorporationTaxRegistration.accountingDetails
    }
    "return none" in  {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.retrieveAccountingDetails(registrationID))
      result shouldBe None
    }
  }

  "updateAccountingDetails" should {
    "return an AccountingDetails model" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateAccountingDetails(registrationID, validAccountingDetails))
      result shouldBe validDraftCorporationTaxRegistration.accountingDetails
    }

    "return None" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateAccountingDetails(registrationID, validAccountingDetails))
      result shouldBe None
    }

    "return an accountingDetails model if the start date of business is not defined" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateAccountingDetails(registrationID, accountingDetailsNoStartDateOfBusiness))
      result shouldBe validDraftCorporationTaxRegistration.copy(accountingDetails = Some(accountingDetailsNoStartDateOfBusiness)).accountingDetails
    }
  }

  "retrieve confirmation references" should {

    "not return references for a draft CT registration" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))

      val result = await(repository.retrieveConfirmationReference(registrationID))
      result shouldBe None
    }

    "return an references if a held CT registration exists" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validHeldCorporationTaxRegistration))

      val result = await(repository.retrieveConfirmationReference(registrationID))
      result shouldBe Some(validConfirmationReferences)
    }

    "return an empty option if a CT registration doesn't exists" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)

      val result = await(repository.retrieveConfirmationReference(registrationID))
      result shouldBe None
    }
  }

  "update confirmation references" should {
    "return the correct references when stored" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validHeldCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = await(repository.updateConfirmationReferences(registrationID, validConfirmationReferences))
      result shouldBe validHeldCorporationTaxRegistration.confirmationReferences
    }
  }

  "updateCompanyEndDate" should {

    val accountsPreparationDateModel = PrepareAccountModel("HMRCEndDate", Some(DateTime.parse("1980-12-12")))

    "return an AccountsPreparationModel on a successful update" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = repository.updateCompanyEndDate(registrationID, accountsPreparationDateModel)
      await(result) shouldBe Some(accountsPreparationDateModel)
    }

    "return None when a corporation tax cannot be found" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, None)

      val result = repository.updateCompanyEndDate(registrationID, accountsPreparationDateModel)
      await(result) shouldBe None
    }
  }

  "updateEmail" should {

    val email = Email("testAddress", "GG", linkSent = true, verified = true)

    "insert a new email block into an existing CT registration" in new {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))
      setupAnyUpdateOn(repository.collection)

      val result = repository.updateEmail(registrationID, email)
      await(result) shouldBe Some(email)
    }
  }

  "retrieveEmail" should {

    "return None if no email information is on the registration" in {
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(validDraftCorporationTaxRegistration))

      val result = repository.retrieveEmail(registrationID)
      await(result) shouldBe None
    }

    "return email information if it exists" in {
      val expectedEmail = Email("testAddress", "GG", linkSent = true, verified = true)
      val expectedDoc = validDraftCorporationTaxRegistration.copy(verifiedEmail = Some(expectedEmail))
      val selector = BSONDocument("registrationID" -> BSONString(registrationID))
      setupFindFor(repository.collection, selector, Some(expectedDoc))

      val result = repository.retrieveEmail(registrationID)
      await(result) shouldBe Some(expectedEmail)
    }
  }
}
