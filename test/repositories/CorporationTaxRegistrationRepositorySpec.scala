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

import fixtures.CorporationTaxRegistrationFixture
import helpers.MongoMocks
import models.CorporationTaxRegistration
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.BeforeAndAfter
import reactivemongo.bson.{BSONDocument, BSONString}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CorporationTaxRegistrationRepositorySpec extends UnitSpec with MongoSpecSupport with MongoMocks with MockitoSugar with BeforeAndAfter with CorporationTaxRegistrationFixture {

	class MockedCorporationTaxRegistrationRepository extends CorporationTaxRegistrationMongoRepository {
		override lazy val collection = mockCollection()
	}

	val repository = new MockedCorporationTaxRegistrationRepository()

	before {
		reset(repository.collection)
	}

	"MetadataMongoRepository create metadata" should {
		val randomRegid = UUID.randomUUID().toString

		"Store a document " in {

			val captor = ArgumentCaptor.forClass(classOf[CorporationTaxRegistration])

			val ctData = CorporationTaxRegistration.empty.copy(registrationID = randomRegid)

			setupAnyInsertOn(repository.collection, fails = false)

			val ctDataResult = await(repository.createCorporationTaxRegistrationData(ctData))

			verifyInsertOn(repository.collection, captor)

			captor.getValue.registrationID shouldBe randomRegid
			ctDataResult.registrationID shouldBe randomRegid
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
}
