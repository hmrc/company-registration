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

import models.RegistrationStatus._
import models._
import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.libs.json.{Json, JsObject}
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONString, BSONDocument}
import reactivemongo.json.ImplicitBSONHandlers
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CTUTRMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  class Setup {
    val repository = new CorporationTaxRegistrationMongoRepository(mongo)
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): Future[WriteResult] = {
    repo.insert(ctRegistration)
  }

  "CT UTR Encryption" should {

    val ackRef = "BRCT12345678910"

    val validConfirmationReferences = ConfirmationReferences(
      acknowledgementReference = "BRCT12345678910",
      transactionId = "TX1",
      paymentReference = "PY1",
      paymentAmount = "12.00"
    )

    val validAckRefs = AcknowledgementReferences(
      ctUtr = "CTUTR123456789",
      timestamp = "856412556487",
      status = "success"
    )

    val validHeldCorporationTaxRegistration = CorporationTaxRegistration(
      internalId = "9876543210",
      registrationID = "0123456789",
      status = HELD,
      formCreationTimestamp = "2001-12-31T12:00:00Z",
      language = "en",
      acknowledgementReferences = Some(validAckRefs),
      confirmationReferences = Some(validConfirmationReferences),
      companyDetails = None,
      accountingDetails = None,
      tradingDetails = None,
      contactDetails = None
    )

    "store the plain UTR in encrypted form" in new Setup {
      import reactivemongo.bson.{BSONDocument, BSONInteger, BSONString}
      import ImplicitBSONHandlers._

      val ctUtr = validHeldCorporationTaxRegistration.acknowledgementReferences.get.ctUtr
      await(setupCollection(repository, validHeldCorporationTaxRegistration))
      val result = await(repository.updateCTRecordWithAcknowledgments(ackRef, validHeldCorporationTaxRegistration))
      result.hasErrors shouldBe false

      // check the value isn't the UTR when fetched direct from the DB
      val query = BSONDocument("confirmationReferences.acknowledgement-reference" -> BSONString(ackRef))
      val project = BSONDocument("acknowledgementReferences.ct-utr" -> BSONInteger(1), "_id" -> BSONInteger(0))
      val stored: Option[JsObject] = await(repository.collection.find(query, project).one[JsObject])
      stored shouldBe defined
      (stored.get \ "acknowledgementReferences" \ "ct-utr").as[String] shouldNot be(ctUtr)

      // check that it is the UTR when fetched properly
      val fetched: CorporationTaxRegistration = await(repository.retrieveByAckRef(ackRef)).get
      fetched.acknowledgementReferences.map(_.ctUtr) shouldBe Some(ctUtr)
    }
  }
}
