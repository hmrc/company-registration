/*
 * Copyright 2024 HM Revenue & Customs
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

package mocks

import auth.CryptoSCRS
import connectors.{BusinessRegistrationConnector, IncorporationCheckAPIConnector}
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.mongodb.scala.bson.conversions.Bson
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.JsObject
import repositories.{CorporationTaxRegistrationMongoRepository, SequenceMongoRepository}
import services._
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.mongo.lock.LockService

import scala.concurrent.Future

trait SCRSMocks
  extends WSHttpMock
    with AccountingServiceMock {
  this: MockitoSugar =>

  lazy val mockCTDataService: CorporationTaxRegistrationService = mock[CorporationTaxRegistrationService]
  lazy val mockCTDataRepository: CorporationTaxRegistrationMongoRepository = mock[CorporationTaxRegistrationMongoRepository]
  lazy val mockCompanyDetailsService: CompanyDetailsService = mock[CompanyDetailsService]
  lazy val mockContactDetailsService: ContactDetailsService = mock[ContactDetailsService]
  lazy val mockSequenceMongoRepository: SequenceMongoRepository = mock[SequenceMongoRepository]
  lazy val mockIncorporationCheckAPIConnector: IncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  lazy val mockMetrics: MetricsService = mock[MetricsService]
  lazy val mockProcessIncorporationService: ProcessIncorporationService = mock[ProcessIncorporationService]
  lazy val mockSubmissionService: SubmissionService = mock[SubmissionService]
  val mockBusRegConnector: BusinessRegistrationConnector = mock[BusinessRegistrationConnector]
  val mockLockService: LockService = mock[LockService]
  val mockInstanceOfCrypto: CryptoSCRS = new CryptoSCRS {
    val crypto: Encrypter with Decrypter = new ApplicationCrypto(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==").underlying).JsonCrypto
  }
  val mockGroupsService: GroupsService = mock[GroupsService]

  object CTServiceMocks {
    def createCTDataRecord(result: CorporationTaxRegistration): OngoingStubbing[Future[CorporationTaxRegistration]] = {
      when(mockCTDataService.createCorporationTaxRegistrationRecord(ArgumentMatchers.any[String], ArgumentMatchers.any[String], ArgumentMatchers.any[String]))
        .thenReturn(Future.successful(result))
    }

    def retrieveCTDataRecord(regId: String, result: Option[CorporationTaxRegistration]): OngoingStubbing[Future[Option[CorporationTaxRegistration]]] = {
      when(mockCTDataService.retrieveCorporationTaxRegistrationRecord(ArgumentMatchers.eq(regId), ArgumentMatchers.any()))
        .thenReturn(Future.successful(result))
    }
  }

  object CTDataRepositoryMocks {
    def createCorporationTaxRegistration(ctData: CorporationTaxRegistration): OngoingStubbing[Future[CorporationTaxRegistration]] = {
      when(mockCTDataRepository.createCorporationTaxRegistration(ArgumentMatchers.any[CorporationTaxRegistration]()))
        .thenReturn(Future.successful(ctData))
    }

    def retrieveCorporationTaxRegistration(ctData: Option[CorporationTaxRegistration]): OngoingStubbing[Future[Option[CorporationTaxRegistration]]] = {
      when(mockCTDataRepository.findOneBySelector(ArgumentMatchers.any[Bson]))
        .thenReturn(Future.successful(ctData))
    }

    def retrieveCompanyDetails(companyDetails: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCTDataRepository.retrieveCompanyDetails(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(companyDetails))
    }

    def updateCompanyDetails(companyDetails: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCTDataRepository.updateCompanyDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.successful(companyDetails))
    }

    def retrieveAccountingDetails(accountingDetails: Option[AccountingDetails]): OngoingStubbing[Future[Option[AccountingDetails]]] = {
      when(mockCTDataRepository.retrieveAccountingDetails(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(accountingDetails))
    }

    def updateAccountingDetails(accountingDetails: Option[AccountingDetails]): OngoingStubbing[Future[Option[AccountingDetails]]] = {
      when(mockCTDataRepository.updateAccountingDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[AccountingDetails]()))
        .thenReturn(Future.successful(accountingDetails))
    }

    def retrieveContactDetails(response: Option[ContactDetails]): OngoingStubbing[Future[Option[ContactDetails]]] = {
      when(mockCTDataRepository.retrieveContactDetails(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(response))
    }

    def updateContactDetails(response: Option[ContactDetails]): OngoingStubbing[Future[Option[ContactDetails]]] = {
      when(mockCTDataRepository.updateContactDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[ContactDetails]()))
        .thenReturn(Future.successful(response))
    }
  }

  object CompanyDetailsServiceMocks {
    def retrieveCompanyDetails(registrationID: String, result: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCompanyDetailsService.retrieveCompanyDetails(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(result))
    }

    def saveTxidAndGenerateAckRef(result: Future[JsObject]): OngoingStubbing[Future[JsObject]] = {
      when(mockCompanyDetailsService.saveTxIdAndAckRef(ArgumentMatchers.anyString(),ArgumentMatchers.anyString()))
        .thenReturn(result)
    }

    def updateCompanyDetails(registrationID: String, result: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCompanyDetailsService.updateCompanyDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.successful(result))
    }
  }

  object ContactDetailsServiceMocks {
    def retrieveContactDetails(registrationID: String, response: Option[ContactDetails]): OngoingStubbing[Future[Option[ContactDetails]]] = {
      when(mockContactDetailsService.retrieveContactDetails(ArgumentMatchers.contains(registrationID)))
        .thenReturn(Future.successful(response))
    }

    def updateContactDetails(registrationID: String, response: Option[ContactDetails]): OngoingStubbing[Future[Option[ContactDetails]]] = {
      when(mockContactDetailsService.updateContactDetails(ArgumentMatchers.any(), ArgumentMatchers.any[ContactDetails]()))
        .thenReturn(Future.successful(response))
    }
  }

  object SequenceMongoRepositoryMocks {
    def getNext(sequenceID: String, returns: Int): OngoingStubbing[Future[Int]] = {
      when(mockSequenceMongoRepository.getNext(ArgumentMatchers.any()))
        .thenReturn(Future.successful(returns))
    }
  }
}