/*
 * Copyright 2023 HM Revenue & Customs
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

package services

import fixtures.CompanyDetailsFixture
import helpers.BaseSpec
import models.ConfirmationReferences
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class CompanyDetailsServiceSpec extends BaseSpec with CompanyDetailsFixture {

  trait Setup {
    reset(mockCTDataRepository)
    reset(mockSubmissionService)

    val service = new CompanyDetailsService {
      override val corporationTaxRegistrationMongoRepository = mockCTDataRepository
      override val submissionService: SubmissionService = mockSubmissionService
      implicit val ec: ExecutionContext = global
    }
  }

  val registrationID = "12345"


  "retrieveCompanyDetails" must {
    "return the CompanyDetails when a company details record is found" in new Setup {
      CTDataRepositoryMocks.retrieveCompanyDetails(Some(validCompanyDetails))

      await(service.retrieveCompanyDetails(registrationID)) mustBe Some(validCompanyDetails)
    }

    "return a None when the record to retrieve is not found in the repository" in new Setup {
      CTDataRepositoryMocks.retrieveCompanyDetails(None)

      await(service.retrieveCompanyDetails(registrationID)) mustBe None
    }
  }

  "updateCompanyDetails" must {
    "return a CompanyDetailsResponse when a company details record is updated" in new Setup {
      CTDataRepositoryMocks.updateCompanyDetails(Some(validCompanyDetails))

      await(service.updateCompanyDetails(registrationID, validCompanyDetails)) mustBe Some(validCompanyDetails)
    }

    "return a None when the record to update is not found in the repository" in new Setup {
      CTDataRepositoryMocks.updateCompanyDetails(None)

      await(service.updateCompanyDetails(registrationID, validCompanyDetails)) mustBe None
    }
  }
  "convertAckRefToJsObject" must {
    "return jsObject" in new Setup {
      service.convertAckRefToJsObject("testAckRef") mustBe Json.obj("acknowledgement-reference" -> "testAckRef")
    }
  }

  "saveTxidAndGenerateAckRef" must {
    val ackRefJsObject = Json.obj("acknowledgement-reference" -> "testAckRef")
    val conf = ConfirmationReferences("testAckRef", "txId", None, None)
    "return jsObject with new ackref from repo" in new Setup {
      when(mockCTDataRepository.retrieveConfirmationReferences(any())).thenReturn(Future.successful(None))
      when(mockSubmissionService.generateAckRef).thenReturn(Future.successful("testAckRef"))
      when(mockCTDataRepository.updateConfirmationReferences(any(), eqTo(conf)))
        .thenReturn(Future.successful(Some(conf)))
      val res = await(service.saveTxIdAndAckRef(registrationID, "txId"))
      verify(mockSubmissionService, times(1)).generateAckRef
      res mustBe ackRefJsObject
    }
    "return jsobject with updated txId, generate ackref not called if already exists" in new Setup {
      when(mockCTDataRepository.retrieveConfirmationReferences(any())).thenReturn(Future.successful(Some(conf.copy(transactionId = "willnotbethis"))))
      when(mockCTDataRepository.updateConfirmationReferences(any(), eqTo(conf)))
        .thenReturn(Future.successful(Some(conf)))
      val res = await(service.saveTxIdAndAckRef(registrationID, "txId"))
      verify(mockSubmissionService, times(0)).generateAckRef
      verify(mockCTDataRepository, times(1)).updateConfirmationReferences(any(), any())
      res mustBe ackRefJsObject
    }
    "return exception when retrieve fails" in new Setup {
      val ex = new Exception("failure reason")
      when(mockCTDataRepository.retrieveConfirmationReferences(any())).thenReturn(Future.failed(ex))
      intercept[Exception](await(service.saveTxIdAndAckRef(registrationID, "txId")))
      verify(mockSubmissionService, times(0)).generateAckRef

    }
    "return exception when save fails" in new Setup {
      val ex = new Exception("failure reason")
      when(mockCTDataRepository.retrieveConfirmationReferences(any())).thenReturn(Future.successful(None))
      when(mockSubmissionService.generateAckRef).thenReturn(Future.successful("testAckRef"))
      when(mockCTDataRepository.updateConfirmationReferences(any(), any())).thenReturn(Future.failed(ex))

      val res = intercept[Exception](await(service.saveTxIdAndAckRef(registrationID, "txId")))
      verify(mockSubmissionService, times(1)).generateAckRef

    }
  }
}
