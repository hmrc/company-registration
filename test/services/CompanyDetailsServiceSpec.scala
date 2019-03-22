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

package services

import fixtures.CompanyDetailsFixture
import mocks.SCRSMocks
import models.ConfirmationReferences
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}

import scala.concurrent.Future

class CompanyDetailsServiceSpec extends UnitSpec with MockitoSugar with SCRSMocks with CompanyDetailsFixture {

  trait Setup {
    reset(mockCTDataRepository)
    reset(mockSubmissionService)

    val service = new CompanyDetailsService {
      override val corporationTaxRegistrationRepository = mockCTDataRepository
      override val submissionService: SubmissionService = mockSubmissionService
    }
  }

  val registrationID = "12345"
  

  "retrieveCompanyDetails" should {
    "return the CompanyDetails when a company details record is found" in new Setup {
      CTDataRepositoryMocks.retrieveCompanyDetails(Some(validCompanyDetails))

      await(service.retrieveCompanyDetails(registrationID)) shouldBe Some(validCompanyDetails)
    }

    "return a None when the record to retrieve is not found in the repository" in new Setup {
      CTDataRepositoryMocks.retrieveCompanyDetails(None)

      await(service.retrieveCompanyDetails(registrationID)) shouldBe None
    }
  }

  "updateCompanyDetails" should {
    "return a CompanyDetailsResponse when a company detaisl record is updated" in new Setup {
      CTDataRepositoryMocks.updateCompanyDetails(Some(validCompanyDetails))

      await(service.updateCompanyDetails(registrationID, validCompanyDetails)) shouldBe Some(validCompanyDetails)
    }

    "return a None when the record to update is not found in the repository" in new Setup {
      CTDataRepositoryMocks.updateCompanyDetails(None)

      await(service.updateCompanyDetails(registrationID, validCompanyDetails)) shouldBe None
    }
  }
  "convertAckRefToJsObject" should {
    "return jsObject" in new Setup {
      service.convertAckRefToJsObject("foo") shouldBe Json.obj("acknowledgement-reference" -> "foo")
    }
  }

  "saveTxidAndGenerateAckRef" should {
    val ackRefJsObject = Json.obj("acknowledgement-reference" -> "fooBar")
    val conf =  ConfirmationReferences("fooBar","txId",None,None)
    "return DidNotExistInCRNowSaved containing jsObject with ackref from repo" in new Setup {
      when(mockCTDataRepository.retrieveConfirmationReferences(any())).thenReturn(Future.successful(None))
      when(mockSubmissionService.generateAckRef).thenReturn(Future.successful("fooBar"))
      when(mockCTDataRepository.updateConfirmationReferences(any(),eqTo(conf)))
        .thenReturn(Future.successful(Some(conf)))
     val res = await(service.saveTxIdAndAckRef(registrationID, "txId"))
      verify(mockSubmissionService, times(1)).generateAckRef
      res shouldBe DidNotExistInCRNowSaved(ackRefJsObject)
    }
    "return ExistedInCRAlready with jsObject with ackref when ConfirmationReferences Block already exists" in new Setup {
      when(mockCTDataRepository.retrieveConfirmationReferences(any())).thenReturn(Future.successful(Some(conf)))
      val res = await(service.saveTxIdAndAckRef(registrationID, "txId"))
      verify(mockSubmissionService, times(0)).generateAckRef
      res shouldBe ExistedInCRAlready(ackRefJsObject)
    }
    "return SomethingWentWrongWhenSaving when retrieve fails" in new Setup {
      val ex = new Exception("foo")
      when(mockCTDataRepository.retrieveConfirmationReferences(any())).thenReturn(Future.failed(ex))
      val res = await(service.saveTxIdAndAckRef(registrationID, "txId"))
      verify(mockSubmissionService, times(0)).generateAckRef
      res shouldBe SomethingWentWrongWhenSaving(ex,registrationID,"txId")
    }
    "return SomethingWentWrongWhenSaving when save fails" in new Setup {
      val ex = new Exception("bar")
      when(mockCTDataRepository.retrieveConfirmationReferences(any())).thenReturn(Future.successful(None))
      when(mockSubmissionService.generateAckRef).thenReturn(Future.successful("foo"))
      when(mockCTDataRepository.updateConfirmationReferences(any(),any())).thenReturn(Future.failed(ex))

      val res = await(service.saveTxIdAndAckRef(registrationID, "txId"))
      verify(mockSubmissionService, times(1)).generateAckRef
      res shouldBe SomethingWentWrongWhenSaving(ex,registrationID,"txId")
    }
  }
}
