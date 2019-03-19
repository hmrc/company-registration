/*
 * Copyright 2019 HM Revenue & Customs
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

import audit.{AdminCTReferenceEvent, AdminSessionIDEvent}
import connectors.{BusinessRegistrationConnector, DesConnector, IncorporationInformationConnector}
import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{when, _}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import repositories.CorporationTaxRegistrationMongoRepository
import services.FailedToDeleteSubmissionData
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException, Upstream4xxResponse}
import uk.gov.hmrc.lock.LockKeeper
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AdminServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with Eventually {

  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockIncorpInfoConnector: IncorporationInformationConnector = mock[IncorporationInformationConnector]
  val mockCorpTaxRegistrationRepo: CorporationTaxRegistrationMongoRepository = mock[CorporationTaxRegistrationMongoRepository]
  val mockBusRegConnector = mock[BusinessRegistrationConnector]
  val mockDesConnector = mock[DesConnector]
  val mockLockKeeper = mock[LockKeeper]

  class Setup {
    val service = new AdminService {
      val auditConnector: AuditConnector = mockAuditConnector
      val incorpInfoConnector: IncorporationInformationConnector = mockIncorpInfoConnector
      val corpTaxRegRepo: CorporationTaxRegistrationMongoRepository = mockCorpTaxRegistrationRepo
      val brConnector: BusinessRegistrationConnector = mockBusRegConnector
      val desConnector: DesConnector = mockDesConnector
      override val lockKeeper: LockKeeper = mockLockKeeper

      override val staleAmount: Int = 10
      override val clearAfterXDays: Int = 90
      override val ignoredDocs: Set[String] = Set("1", "2", "3", "4", "5")
    }
    val docInfo = service.DocumentInfo(regId, "draft", DateTime.now)
  }

  override def beforeEach() {
    reset(mockAuditConnector)
    reset(mockBusRegConnector)
    reset(mockCorpTaxRegistrationRepo)
    reset(mockDesConnector)
    reset(mockIncorpInfoConnector)
    reset(mockLockKeeper)
  }

  val regId = "reg-id-12345"
  val ackRef = "ack-ref-12345"
  val transId = "trans-12345"

  implicit val hc = HeaderCarrier()
  implicit val req: Request[AnyContent] = FakeRequest()

  val sessionIdData = SessionIdData(Some("session-id"), Some("credId"), Some("fakeCompanyName"), Some(ackRef))

  def makeSessionReg(sessionIds : Option[SessionIdData]) = CorporationTaxRegistration(
    internalId = "testID",
    registrationID = "registrationId",
    formCreationTimestamp = "dd-mm-yyyy",
    language = "en",
    status = RegistrationStatus.HELD,
    companyDetails = Some(CompanyDetails(
      sessionIds.flatMap(ids => sessionIds.flatMap(_.companyName)).getOrElse("companyName"),
      CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
      PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
      "testJurisdiction"
    )),
    contactDetails = Some(ContactDetails(
      Some("0123456789"),
      Some("0123456789"),
      Some("test@email.co.uk")
    )),
    tradingDetails = Some(TradingDetails("false")),
    heldTimestamp = None,
    confirmationReferences = Some(ConfirmationReferences(
      acknowledgementReference = sessionIds.flatMap(ids => sessionIds.flatMap(_.ackRef)).getOrElse("ackRef"),
      transactionId = "tID",
      paymentReference = Some("payref"),
      paymentAmount = Some("12")
    )),
    sessionIdentifiers = sessionIds.map(ids => SessionIds(ids.sessionId.getOrElse(""), ids.credId.getOrElse("")))
  )

  "fetchSessionIdData" should {
    "fetch session id data if it exists" in new Setup {
      val data = Some(sessionIdData)
      val reg = makeSessionReg(data)

      when(mockCorpTaxRegistrationRepo.retrieveCorporationTaxRegistration(any()))
        .thenReturn(Future.successful(Some(reg)))

      await(service.fetchSessionIdData(regId)) shouldBe data
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
    val timestamp = "timestamp"
    def makeReg(utr : Option[String]) = CorporationTaxRegistration(
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
        utr, timestamp, utr.map(_ => "04").getOrElse("06")
      ))
    )

    "update a registration with the new admin ct reference" in new Setup {
      val ctUtr = "oldIncorrectUtr"
      val expected = Some(makeReg(Some(ctUtr)))

      when(mockCorpTaxRegistrationRepo.updateRegistrationWithAdminCTReference(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(expected))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any[AdminCTReferenceEvent]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      val newUtr = "newFreshUtr"
      val result = await(service.updateRegistrationWithCTReference("ackRef", newUtr, "test"))
      result shouldBe Some(Json.parse(
        s"""{
           |"status": "04",
           |"ctutr" : true
        }""".stripMargin))

      val captor = ArgumentCaptor.forClass(classOf[AdminCTReferenceEvent])

      eventually {
        verify(mockAuditConnector, times(1)).sendExtendedEvent(captor.capture())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]())
      }
      val audit = captor.getAllValues

      audit.get(0).auditType shouldBe "adminCtReference"
      (audit.get(0).detail \ "utrChanges" \ "previousUtr").as[String] shouldBe ctUtr
      (audit.get(0).detail \ "utrChanges" \ "newUtr"     ).as[String] shouldBe newUtr
    }

    "update a previously rejected registration with the new admin ct reference" in new Setup {
      val ctUtr = "oldIncorrectUtr"
      val expected = Some(makeReg(None))

      when(mockCorpTaxRegistrationRepo.updateRegistrationWithAdminCTReference(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(expected))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any[AdminCTReferenceEvent]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      val newUtr = "newFreshUtr"
      val result = await(service.updateRegistrationWithCTReference("ackRef", newUtr, "test"))
      result shouldBe Some(Json.parse(
        s"""{
           |"status": "04",
           |"ctutr" : true
        }""".stripMargin))

      val captor = ArgumentCaptor.forClass(classOf[AdminCTReferenceEvent])

      eventually {
        verify(mockAuditConnector, times(1)).sendExtendedEvent(captor.capture())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]())
      }
      val audit = captor.getAllValues

      audit.get(0).auditType shouldBe "adminCtReference"
      (audit.get(0).detail \ "utrChanges" \ "previousUtr").as[String] shouldBe "NO-UTR"
      (audit.get(0).detail \ "utrChanges" \ "newUtr"     ).as[String] shouldBe newUtr
    }

    "do not update a registration with the new admin ct reference that does not exist" in new Setup {
      when(mockCorpTaxRegistrationRepo.updateRegistrationWithAdminCTReference(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val result = await(service.updateRegistrationWithCTReference("ackRef", "ctUtr", "test"))
      result shouldBe None
    }
  }

  "deleteRejectedSubmissionData" should {
    "delete a registration" when {
      "it exists" in new Setup {
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockIncorpInfoConnector.cancelSubscription(any(), any(), any())(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))

        await(service.adminDeleteSubmission(docInfo, Some(transId))) shouldBe true
      }
    }
    "not delete a registration" when {
      "it does not exist any of the databases" in new Setup {
        val inputs = List((true, false), (false, true), (false, false))

        for((brResult, crResult) <- inputs) {
          when(mockBusRegConnector.adminRemoveMetadata(any()))
            .thenReturn(
              Future.successful(brResult)
            )
          when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
            .thenReturn(
              Future.successful(crResult)
            )
          when(mockIncorpInfoConnector.cancelSubscription(any(), any(), any())(any()))
            .thenReturn(
              Future.successful(true)
            )

          intercept[FailedToDeleteSubmissionData.type](await(service.adminDeleteSubmission(docInfo, Some(transId))))
        }
      }
      "an error is thrown" in new Setup {
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.failed(new RuntimeException))

        intercept[RuntimeException](await(service.adminDeleteSubmission(docInfo, Some(transId))))
      }
    }
  }

  "updateTransactionId" should {

    val updateFrom = "TRANS-123456789"
    val updateTo = "TRANS-0123456789"

    "update the transaction id" when {
      "the transaction id exists in the database" in new Setup {
        when(mockCorpTaxRegistrationRepo.updateTransactionId(any(), any()))
          .thenReturn(Future.successful(updateTo))

        await(service.updateTransactionId(updateFrom, updateTo)) shouldBe true
      }
    }

    "not update a transaction id" when {
      "the transaction id does not exist in the database" in new Setup {
        when(mockCorpTaxRegistrationRepo.updateTransactionId(any(), any()))
          .thenReturn(Future.failed(new RuntimeException("")))

        await(service.updateTransactionId(updateFrom, updateTo)) shouldBe false
      }
    }
  }

  "deleteLimboCase" should {

    val regId = "myRegID"
    val companyName = "dtl ynapmoc"

    def makeReg(status: String, compName: String, ackRefExists: Boolean, companyDetailsExists: Boolean = true) = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = "dd-mm-yyyy",
      language = "en",
      status = status,
      companyDetails = if (companyDetailsExists) {
        Some(CompanyDetails(
          compName,
          CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
          PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
          "testJurisdiction"
        ))
      } else {
        None
      },
      contactDetails = Some(ContactDetails(
        Some("0123456789"),
        Some("0123456789"),
        Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("false")),
      heldTimestamp = None,
      confirmationReferences = if (ackRefExists) {
        Some(ConfirmationReferences(
          acknowledgementReference = "ackRef",
          transactionId = "tID",
          paymentReference = Some("payref"),
          paymentAmount = Some("12")
        ))
      } else {
        None
      },
      acknowledgementReferences = None
    )
  }

  "deleteStaleDocuments" should {
    val exampleDoc = CorporationTaxRegistration("id", "regid", "draft", "timestamp", "lang")
    "return a count of documents retrieved and deleted" when {
      "only one is older than 90 days" in new Setup {
        when(mockCorpTaxRegistrationRepo.retrieveStaleDocuments(any(), any()))
          .thenReturn(Future.successful(List(exampleDoc)))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))
        await(service.deleteStaleDocuments()) shouldBe 1
      }
      "three are older than 90 days" in new Setup {
        when(mockCorpTaxRegistrationRepo.retrieveStaleDocuments(any(), any()))
          .thenReturn(Future.successful(List.fill(3)(exampleDoc)))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))
        await(service.deleteStaleDocuments()) shouldBe 3
      }
      "three are older than 90 days, and one fails to delete BR data" in new Setup {
        when(mockCorpTaxRegistrationRepo.retrieveStaleDocuments(any(), any()))
          .thenReturn(Future.successful(List.fill(3)(exampleDoc)))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true), Future.successful(false), Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true), Future.successful(true), Future.successful(true))
        await(service.deleteStaleDocuments()) shouldBe 2
      }
      "three are older than 90 days, and one has a status of submitted" in new Setup {
        when(mockCorpTaxRegistrationRepo.retrieveStaleDocuments(any(), any()))
          .thenReturn(Future.successful(List.fill(2)(exampleDoc).::(exampleDoc.copy(status="submitted"))))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))
        await(service.deleteStaleDocuments()) shouldBe 2
      }
      "three are older than 90 days, and one is on the whitelist" in new Setup {
        when(mockCorpTaxRegistrationRepo.retrieveStaleDocuments(any(), any()))
          .thenReturn(Future.successful(List.fill(2)(exampleDoc).::(exampleDoc.copy(registrationID="2"))))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))
        await(service.deleteStaleDocuments()) shouldBe 2
      }
    }
  }

  "processStaleDocument" should {
    val exampleDoc = CorporationTaxRegistration("id", "regid", "draft", "timestamp", "lang")
    "return true" when {
      "the document is draft, has no confirmation references and successfully deletes BR and CR document" in new Setup {

        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))

        await(service.processStaleDocument(exampleDoc)) shouldBe true
      }

      "the document is draft, has confirmation references and successfully deletes BR and CR document" in new Setup {
        val confRefExampleDoc = exampleDoc.copy(confirmationReferences = Some(ConfirmationReferences("", "", None, None)))

        when(mockIncorpInfoConnector.checkCompanyIncorporated(any())(any()))
          .thenReturn(Future.successful(None))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockIncorpInfoConnector.cancelSubscription(any(), any(), any())(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))

        await(service.processStaleDocument(confRefExampleDoc)) shouldBe true

        verify(mockIncorpInfoConnector, times(1)).cancelSubscription(any(), any(), any())(any())
      }

      "the document is draft, has confirmation references and successfully deletes BR and CR document but the subscription has an old regime" in new Setup {
        val confRefExampleDoc = exampleDoc.copy(confirmationReferences = Some(ConfirmationReferences("", "", None, None)))

        when(mockIncorpInfoConnector.checkCompanyIncorporated(any())(any()))
          .thenReturn(Future.successful(None))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockIncorpInfoConnector.cancelSubscription(any(), any(), any())(any()))
          .thenReturn(
            Future.failed(new NotFoundException("failed to cancel sub")),
            Future.successful(true)
          )
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))

        await(service.processStaleDocument(confRefExampleDoc)) shouldBe true

        verify(mockIncorpInfoConnector, times(1)).cancelSubscription(any(), any(), ArgumentMatchers.eq(false))(any())
        verify(mockIncorpInfoConnector, times(1)).cancelSubscription(any(), any(), ArgumentMatchers.eq(true))(any())
      }

      "the document is draft, has confirmation references and successfully deletes BR and CR document, but no subs" in new Setup {
        val confRefExampleDoc = exampleDoc.copy(confirmationReferences = Some(ConfirmationReferences("", "", None, None)))

        when(mockIncorpInfoConnector.checkCompanyIncorporated(any())(any()))
          .thenReturn(Future.successful(None))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockIncorpInfoConnector.cancelSubscription(any(), any(), any())(any()))
          .thenReturn(
            Future.failed(new NotFoundException("failed to cancel sub")),
            Future.failed(new NotFoundException("failed to cancel another sub"))
          )
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))

        await(service.processStaleDocument(confRefExampleDoc)) shouldBe true

        verify(mockIncorpInfoConnector, times(1)).cancelSubscription(any(), any(), ArgumentMatchers.eq(false))(any())
        verify(mockIncorpInfoConnector, times(1)).cancelSubscription(any(), any(), ArgumentMatchers.eq(true))(any())
      }

      "the document is held or locked, has confirmation references and successfully deletes BR and CR document" in new Setup {
        val confRefExampleDoc = exampleDoc.copy(status= "held", confirmationReferences = Some(ConfirmationReferences("", "", None, None)))

        when(mockIncorpInfoConnector.checkCompanyIncorporated(any())(any()))
          .thenReturn(Future.successful(None))
        when(mockDesConnector.topUpCTSubmission(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockIncorpInfoConnector.cancelSubscription(any(), any(), any())(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.successful(true))
        when(mockAuditConnector.sendExtendedEvent(any())(any(), any()))
            .thenReturn(Future.successful(AuditResult.Success))

        await(service.processStaleDocument(confRefExampleDoc)) shouldBe true

        verify(mockIncorpInfoConnector, times(1)).cancelSubscription(any(), any(), any())(any())
      }
    }

    "return false" when {
      "the document is held or locked with a CRN" in new Setup {
        val confRefWithCRNExampleDoc = exampleDoc.copy(status= "held", confirmationReferences = Some(ConfirmationReferences("", "transID", None, None)))

        when(mockIncorpInfoConnector.checkCompanyIncorporated(any())(any()))
          .thenReturn(Future.successful(Some("CRN found")))

        await(service.processStaleDocument(confRefWithCRNExampleDoc)) shouldBe false
      }

      "the document is held or locked with failure to send a rejection topup" in new Setup {
        val confRefWithCRNExampleDoc = exampleDoc.copy(status= "locked", confirmationReferences = Some(ConfirmationReferences("", "transID", None, None)))

        when(mockIncorpInfoConnector.checkCompanyIncorporated(any())(any()))
          .thenReturn(Future.successful(None))
        when(mockDesConnector.topUpCTSubmission(any(),any(),any(),any())(any()))
          .thenReturn(Future.failed(new Upstream4xxResponse("test", 400, 400, Map())))

        await(service.processStaleDocument(confRefWithCRNExampleDoc)) shouldBe false
      }
      "the document is held or locked with failure to delete the BR document" in new Setup {
        val confRefWithCRNExampleDoc = exampleDoc.copy(status= "held", confirmationReferences = Some(ConfirmationReferences("", "transID", None, None)))

        when(mockIncorpInfoConnector.checkCompanyIncorporated(any())(any()))
          .thenReturn(Future.successful(None))
        when(mockDesConnector.topUpCTSubmission(any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.failed(new RuntimeException("failed to delete BR doc")))

        await(service.processStaleDocument(confRefWithCRNExampleDoc)) shouldBe false
      }
      "the document is held or locked with failure to delete the CR document" in new Setup {
        val confRefWithCRNExampleDoc = exampleDoc.copy(status= "held", confirmationReferences = Some(ConfirmationReferences("", "transID", None, None)))

        when(mockIncorpInfoConnector.checkCompanyIncorporated(any())(any()))
          .thenReturn(Future.successful(None))
        when(mockDesConnector.topUpCTSubmission(any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))
        when(mockBusRegConnector.adminRemoveMetadata(any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.removeTaxRegistrationById(any()))
          .thenReturn(Future.failed(new RuntimeException("failed to delete CR doc")))

        await(service.processStaleDocument(confRefWithCRNExampleDoc)) shouldBe false
      }
    }
  }

  "updateDocSessionID" should {
    val newSessionId = "newSessionId"
    val newCredId = "newCredId"
    val userName = "admin"

    "update a document's session id" when {
      "provided a session id" in new Setup {
        val data = Some(sessionIdData.copy(sessionId = Some(newSessionId)))
        val reg = makeSessionReg(data)

        when(mockCorpTaxRegistrationRepo.retrieveCorporationTaxRegistration(any()))
          .thenReturn(Future.successful(Some(reg)))
        when(mockCorpTaxRegistrationRepo.retrieveSessionIdentifiers(any()))
          .thenReturn(Future.successful(Some(SessionIds(sessionIdData.sessionId.get, "credId"))))
        when(mockCorpTaxRegistrationRepo.storeSessionIdentifiers(any(), any(), any()))
          .thenReturn(Future.successful(true))
        when(mockAuditConnector.sendExtendedEvent(any())(any(), any()))
          .thenReturn(Future.successful(Success))

        await(service.updateDocSessionID(regId, newSessionId, newCredId, userName)) shouldBe sessionIdData.copy(sessionId = Some(newSessionId))

        val captor = ArgumentCaptor.forClass(classOf[AdminSessionIDEvent])
        eventually {
          verify(mockAuditConnector, times(1)).sendExtendedEvent(captor.capture())(any[HeaderCarrier](), any[ExecutionContext]())
        }
        val audit = captor.getAllValues

        audit.get(0).auditType shouldBe "adminSessionID"
        (audit.get(0).detail \ "oldSessionId").as[String] shouldBe sessionIdData.sessionId.get
        (audit.get(0).detail \ "sessionId").as[String] shouldBe newSessionId
      }
      "audit event fails" in new Setup {
        val data = Some(sessionIdData.copy(sessionId = Some(newSessionId), credId = Some(newCredId)))
        val reg = makeSessionReg(data)

        when(mockCorpTaxRegistrationRepo.retrieveSessionIdentifiers(any()))
          .thenReturn(Future.successful(Some(SessionIds(sessionIdData.sessionId.get, sessionIdData.credId.get))))
        when(mockCorpTaxRegistrationRepo.storeSessionIdentifiers(any(), any(), any()))
          .thenReturn(Future.successful(true))
        when(mockCorpTaxRegistrationRepo.retrieveCorporationTaxRegistration(any()))
          .thenReturn(Future.successful(Some(reg)))

        await(service.updateDocSessionID(regId, newSessionId, newCredId, userName)) shouldBe sessionIdData.copy(sessionId = Some(newSessionId), credId = Some(newCredId))
      }
    }

    "fail to update when" when {
      "the document does not exist" in new Setup {
        when(mockCorpTaxRegistrationRepo.retrieveSessionIdentifiers(any()))
          .thenReturn(Future.successful(None))

        intercept[RuntimeException](await(service.updateDocSessionID(regId, newSessionId, newCredId, userName)))
      }
      "storeSessionIdentifiers fails" in new Setup {
        when(mockCorpTaxRegistrationRepo.retrieveSessionIdentifiers(any()))
          .thenReturn(Future.successful(Some(SessionIds(sessionIdData.sessionId.get, "credId"))))
        when(mockCorpTaxRegistrationRepo.storeSessionIdentifiers(any(), any(), any()))
          .thenReturn(Future.failed(new RuntimeException("Failed to store")))

        intercept[RuntimeException](await(service.updateDocSessionID(regId, newSessionId, newCredId, userName)))
      }
      "retrieveCorporationTaxRegistration fails" in new Setup {
        when(mockCorpTaxRegistrationRepo.retrieveCorporationTaxRegistration(any()))
          .thenReturn(Future.failed(new RuntimeException("Failed to retrieve")))
        when(mockCorpTaxRegistrationRepo.retrieveSessionIdentifiers(any()))
          .thenReturn(Future.successful(Some(SessionIds(sessionIdData.sessionId.get, "credId"))))
        when(mockCorpTaxRegistrationRepo.storeSessionIdentifiers(any(), any(), any()))
          .thenReturn(Future.successful(true))

        intercept[RuntimeException](await(service.updateDocSessionID(regId, newSessionId, newCredId, userName)))
      }

    }
  }
}