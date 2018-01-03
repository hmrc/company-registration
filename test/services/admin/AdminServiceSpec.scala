/*
 * Copyright 2018 HM Revenue & Customs
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

package services.admin

import connectors.IncorporationInformationConnector
import models.{ConfirmationReferences, IncorpStatus}
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionData, HeldSubmissionMongoRepository}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Matchers.any
import org.mockito.Mockito._
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class AdminServiceSpec extends UnitSpec with MockitoSugar {

  val mockHeldSubmissionRepo: HeldSubmissionMongoRepository = mock[HeldSubmissionMongoRepository]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockIncorpInfoConnector: IncorporationInformationConnector = mock[IncorporationInformationConnector]
  val mockCorpTaxRegistrationRepo: CorporationTaxRegistrationMongoRepository = mock[CorporationTaxRegistrationMongoRepository]

  class Setup {
    val service = new AdminService {
      val heldSubRepo: HeldSubmissionMongoRepository = mockHeldSubmissionRepo
      val auditConnector: AuditConnector = mockAuditConnector
      val incorpInfoConnector: IncorporationInformationConnector = mockIncorpInfoConnector
      val corpTaxRegRepo: CorporationTaxRegistrationMongoRepository = mockCorpTaxRegistrationRepo
    }
  }

  val regId = "reg-id-12345"
  val ackRef = "ack-ref-12345"
  val transId = "trans-12345"

  implicit val hc = HeaderCarrier()
  implicit val req: Request[AnyContent] = FakeRequest()

  "migrateHeldSubmissions" should {

    val incorpStatus = IncorpStatus(transId, "", None, None, None)

    "return a list of true's when all held submissions migrated successfully" in new Setup {

      val heldSub = HeldSubmissionData(regId, ackRef, "testPartial", DateTime.now())

      when(mockHeldSubmissionRepo.findAll(any())(any()))
        .thenReturn(Future.successful(List.fill(3)(heldSub)))

      when(mockCorpTaxRegistrationRepo.retrieveConfirmationReferences(any()))
        .thenReturn(Future.successful(Some(ConfirmationReferences("", transId, Some("payRef"), Some("12")))))

      when(mockIncorpInfoConnector.registerInterest(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      val result: List[Boolean] = await(service.migrateHeldSubmissions)

      result shouldBe List(true, true, true)
    }

    "return a list of false when a held submissions migrated unsuccessfully because the registration doesn't have confirmation refs" in new Setup {

      val heldSub = HeldSubmissionData(regId, ackRef, "testPartial", DateTime.now())

      when(mockHeldSubmissionRepo.findAll(any())(any()))
        .thenReturn(Future.successful(List.fill(3)(heldSub)))

      when(mockCorpTaxRegistrationRepo.retrieveConfirmationReferences(any()))
        .thenReturn(Future.successful(None))

      when(mockIncorpInfoConnector.registerInterest(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      val result: List[Boolean] = await(service.migrateHeldSubmissions)

      result shouldBe List(false, false, false)
    }

    "return a list of false when a held submissions migrated unsuccessfully because the forceSubscription call failed" in new Setup {

      val heldSub = HeldSubmissionData(regId, ackRef, "testPartial", DateTime.now())

      when(mockHeldSubmissionRepo.findAll(any())(any()))
        .thenReturn(Future.successful(List.fill(3)(heldSub)))

      when(mockCorpTaxRegistrationRepo.retrieveConfirmationReferences(any()))
        .thenReturn(Future.successful(Some(ConfirmationReferences("", transId, Some("payRef"), Some("12")))))

      when(mockIncorpInfoConnector.registerInterest(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(false))

      val result: List[Boolean] = await(service.migrateHeldSubmissions)

      result shouldBe List(false, false, false)
    }
  }
}
