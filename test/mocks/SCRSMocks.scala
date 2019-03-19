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

package mocks

import auth.{AuthClientConnector, CryptoSCRS}
import connectors.{BusinessRegistrationConnector, IncorporationCheckAPIConnector}
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import play.api.Configuration
import repositories.{CorporationTaxRegistrationMongoRepository, SequenceRepository}
import services._
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.lock.LockKeeper

import scala.concurrent.Future

trait SCRSMocks
  extends WSHttpMock
    with AccountingServiceMock {
  this: MockitoSugar =>

  lazy val mockCTDataService = mock[CorporationTaxRegistrationService]
  lazy val mockCTDataRepository = mock[CorporationTaxRegistrationMongoRepository]
  lazy val mockCompanyDetailsService = mock[CompanyDetailsService]
  lazy val mockContactDetailsService = mock[ContactDetailsService]
  lazy val mockSequenceRepository = mock[SequenceRepository]
  lazy val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  lazy val mockMetrics = mock[MetricsService]
  lazy val mockProcessIncorporationService = mock[ProcessIncorporationService]
  val mockBusRegConnector = mock[BusinessRegistrationConnector]
  val mockLockKeeper = mock[LockKeeper]
  val mockInstanceOfCrypto = new CryptoSCRS {
    val crypto = new ApplicationCrypto(Configuration("json.encryption.key" -> "MTIzNDU2Nzg5MDEyMzQ1Ng==").underlying).JsonCrypto
  }

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
      when(mockCTDataRepository.retrieveCorporationTaxRegistration(ArgumentMatchers.any[String]))
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

    def retrieveContactDetails(response: Option[ContactDetails]) = {
      when(mockCTDataRepository.retrieveContactDetails(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(response))
    }

    def updateContactDetails(response: Option[ContactDetails]) = {
      when(mockCTDataRepository.updateContactDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[ContactDetails]()))
        .thenReturn(Future.successful(response))
    }
  }

  object CompanyDetailsServiceMocks {
    def retrieveCompanyDetails(registrationID: String, result: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCompanyDetailsService.retrieveCompanyDetails(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(result))
    }

    def updateCompanyDetails(registrationID: String, result: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCompanyDetailsService.updateCompanyDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[CompanyDetails]()))
        .thenReturn(Future.successful(result))
    }
  }

  object ContactDetailsServiceMocks {
    def retrieveContactDetails(registrationID: String, response: Option[ContactDetails]) = {
      when(mockContactDetailsService.retrieveContactDetails(ArgumentMatchers.contains(registrationID)))
        .thenReturn(Future.successful(response))
    }

    def updateContactDetails(registrationID: String, response: Option[ContactDetails]) = {
      when(mockContactDetailsService.updateContactDetails(ArgumentMatchers.any(), ArgumentMatchers.any[ContactDetails]()))
        .thenReturn(Future.successful(response))
    }
  }

  object SequenceRepositoryMocks {
    def getNext(sequenceID: String, returns: Int) = {
      when(mockSequenceRepository.getNext(ArgumentMatchers.any()))
        .thenReturn(Future.successful(returns))
    }
  }
}