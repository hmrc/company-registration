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
import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionData, HeldSubmissionMongoRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

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

  "adminCTUTRCheck" should {
    "return the Status and presence of CTUTR as valid JSON" when {
       "using a valid AckRef" in new Setup {
         val id = "BRCT09876543210"
         val expected = Json.parse(
           """
             |{
             | "status": "04",
             | "ctutr": true
             |}
           """.stripMargin
         )

         when(mockCorpTaxRegistrationRepo.retrieveStatusAndExistenceOfCTUTR(any()))
           .thenReturn(Future.successful(Option("04" -> true)))

         val result = await(service.ctutrCheck(id))

         result shouldBe expected
      }
      "the valid AckRef retrieves no stored document" in new Setup {
        val id = "BRCT09876543210"
        when(mockCorpTaxRegistrationRepo.retrieveStatusAndExistenceOfCTUTR(any()))
          .thenReturn(Future.successful(None))

        val result = await(service.ctutrCheck(id))
        result shouldBe Json.parse("{}")
      }
    }
  }

  "updateRegistrationWithAdminCTReference" should {
    "update a registration with the new admin ct reference" in new Setup {
      val ctUtr = "ctUtr"
      val timestamp = "timestamp"
      val expected = Some(
        CorporationTaxRegistration(
          internalId = "testID",
          registrationID = "registrationId",
          formCreationTimestamp = "dd-mm-yyyy",
          language = "en",
          status = RegistrationStatus.HELD,
          companyDetails = Some(CompanyDetails(
            "testCompanyName",
            CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
            PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
            "testJurisdiction"
          )),
          contactDetails = Some(ContactDetails(
            "testFirstName",
            Some("testMiddleName"),
            "testSurname",
            Some("0123456789"),
            Some("0123456789"),
            Some("test@email.co.uk")
          )),
          tradingDetails = Some(TradingDetails("false")),
          heldTimestamp = None,
          confirmationReferences = Some(ConfirmationReferences(
            acknowledgementReference = "ackRef",
            transactionId = "tID",
            paymentReference = Some("payref"),
            paymentAmount = Some("12")
          )),
          acknowledgementReferences = Some(AcknowledgementReferences(
            Some(ctUtr), timestamp, "04"
          ))
        )
      )

      when(mockCorpTaxRegistrationRepo.updateRegistrationWithAdminCTReference(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(expected))

      val result = await(service.updateRegistrationWithCTReference("ackRef", "ctUtr"))
      result shouldBe Some(Json.parse(
        s"""{
           |"status": "04",
           |"ctutr" : true
        }""".stripMargin))
    }

    "do not update a registration with the new admin ct reference that does not exist" in new Setup {
      when(mockCorpTaxRegistrationRepo.updateRegistrationWithAdminCTReference(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val result = await(service.updateRegistrationWithCTReference("ackRef", "ctUtr"))
      result shouldBe None
    }
  }
}
