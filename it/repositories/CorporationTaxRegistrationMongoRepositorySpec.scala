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

import models.CorporationTaxRegistration
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class CorporationTaxRegistrationMongoRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

	class Setup {
		val repository = new CorporationTaxRegistrationMongoRepository()
		await(repository.drop)
		await(repository.ensureIndexes)
	}

	"MetadataRepository" should {

		"be able to retrieve a document that has been created by registration id" in new Setup {

			val randomOid = UUID.randomUUID().toString
			val randomRegid = UUID.randomUUID().toString

			val ctData = CorporationTaxRegistration.empty.copy(OID = randomOid, registrationID = randomRegid)

			val ctDataResponse = await(repository.createCorporationTaxRegistration(ctData))

			ctDataResponse.registrationID shouldBe randomRegid

			val mdByRegId = await(repository.retrieveCorporationTaxRegistration(randomRegid))

			mdByRegId shouldBe defined
			mdByRegId.get.OID shouldBe randomOid
			mdByRegId.get.registrationID shouldBe randomRegid
		}

		"be able to use the authorisation call to check a document" in new Setup {

			val randomOid = UUID.randomUUID().toString
			val randomRegid = UUID.randomUUID().toString

			val ctData = CorporationTaxRegistration.empty.copy(OID = randomOid, registrationID = randomRegid)

			val ctDataResponse = await(repository.createCorporationTaxRegistration(ctData))

			ctDataResponse.registrationID shouldBe randomRegid

			val auth = await(repository.getOid(randomRegid))

			auth shouldBe defined
			auth shouldBe Some((randomRegid, randomOid))
		}

		"return None for the authorisation call when there's no document" in new Setup {
			val randomRegid = UUID.randomUUID().toString
			val auth = await(repository.getOid(randomRegid))
			auth shouldBe None
		}
	}
}
