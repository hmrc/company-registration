/*
 * Copyright 2017 HM Revenue & Customs
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

import connectors.{AuthConnector, Authority, IncorporationCheckAPIConnector}
import models._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mock.MockitoSugar
import repositories.{CorporationTaxRegistrationMongoRepository, SequenceRepository, StateDataRepository}
import services._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait SCRSMocks
  extends WSHttpMock
    with AccountingServiceMock
    with ServicesConfigMock {
  this: MockitoSugar =>

  lazy val mockCTDataService = mock[CorporationTaxRegistrationService]
  lazy val mockCTDataRepository = mock[CorporationTaxRegistrationMongoRepository]
  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val mockCompanyDetailsService = mock[CompanyDetailsService]
  lazy val mockContactDetailsService = mock[ContactDetailsService]
  lazy val mockSequenceRepository = mock[SequenceRepository]
  lazy val mockStateDataRepository = mock[StateDataRepository]
  lazy val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  lazy val mockMetrics = mock[MetricsService]

  object CTServiceMocks {
    def createCTDataRecord(result: CorporationTaxRegistration): OngoingStubbing[Future[CorporationTaxRegistration]] = {
      when(mockCTDataService.createCorporationTaxRegistrationRecord(Matchers.any[String], Matchers.any[String], Matchers.any[String]))
        .thenReturn(Future.successful(result))
    }

    def retrieveCTDataRecord(regId: String, result: Option[CorporationTaxRegistration]): OngoingStubbing[Future[Option[CorporationTaxRegistration]]] = {
      when(mockCTDataService.retrieveCorporationTaxRegistrationRecord(Matchers.eq(regId)))
        .thenReturn(Future.successful(result))
    }

    def updateConfirmationReferences(regID: String, returns: Option[ConfirmationReferences])(implicit hc: HeaderCarrier) = {
      when(mockCTDataService.updateConfirmationReferences(Matchers.contains(regID), Matchers.eq(ConfirmationReferences("transactID", "payRef", "payAmount", "")))
      (Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(returns))
    }
  }

  object AuthenticationMocks {
    def getCurrentAuthority(authority: Option[Authority]): OngoingStubbing[Future[Option[Authority]]] = {
      when(mockAuthConnector.getCurrentAuthority()(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(authority))
    }
  }

  object AuthorisationMocks {
    def getInternalId(intId: String, thenReturn: Option[(String, String)]): OngoingStubbing[Future[Option[(String, String)]]] = {
      when(mockCTDataRepository.getInternalId(Matchers.anyString())).thenReturn(Future.successful(thenReturn))
    }

    def mockSuccessfulAuthorisation(registrationId: String, authority: Authority) = {
      when(mockCTDataRepository.getInternalId(Matchers.eq(registrationId))).
        thenReturn(Future.successful(Some((registrationId, authority.ids.internalId))))
      when(mockAuthConnector.getCurrentAuthority()(Matchers.any()))
        .thenReturn(Future.successful(Some(authority)))
    }

    def mockNotLoggedInOrAuthorised = {
      when(mockAuthConnector.getCurrentAuthority()(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))
      when(mockCTDataRepository.getInternalId(Matchers.any())).
        thenReturn(Future.successful(None))
    }

    def mockNotAuthorised(registrationId: String, authority: Authority) = {
      when(mockAuthConnector.getCurrentAuthority()(Matchers.any()))
        .thenReturn(Future.successful(Some(authority)))
      when(mockCTDataRepository.getInternalId(Matchers.contains(registrationId))).
        thenReturn(Future.successful(Some((registrationId, authority.ids.internalId + "xxx"))))
    }

    def mockAuthResourceNotFound(authority: Authority) = {
      AuthenticationMocks.getCurrentAuthority(Some(authority))
      AuthorisationMocks.getInternalId(authority.ids.internalId, None)
    }
  }

  object CTDataRepositoryMocks {
    def createCorporationTaxRegistration(ctData: CorporationTaxRegistration): OngoingStubbing[Future[CorporationTaxRegistration]] = {
      when(mockCTDataRepository.createCorporationTaxRegistration(Matchers.any[CorporationTaxRegistration]()))
        .thenReturn(Future.successful(ctData))
    }

    def retrieveCorporationTaxRegistration(ctData: Option[CorporationTaxRegistration]): OngoingStubbing[Future[Option[CorporationTaxRegistration]]] = {
      when(mockCTDataRepository.retrieveCorporationTaxRegistration(Matchers.any[String]))
        .thenReturn(Future.successful(ctData))
    }

    def retrieveCompanyDetails(companyDetails: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCTDataRepository.retrieveCompanyDetails(Matchers.anyString()))
        .thenReturn(Future.successful(companyDetails))
    }

    def updateCompanyDetails(companyDetails: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCTDataRepository.updateCompanyDetails(Matchers.anyString(), Matchers.any[CompanyDetails]()))
        .thenReturn(Future.successful(companyDetails))
    }

    def retrieveAccountingDetails(accountingDetails: Option[AccountingDetails]): OngoingStubbing[Future[Option[AccountingDetails]]] = {
      when(mockCTDataRepository.retrieveAccountingDetails(Matchers.anyString()))
        .thenReturn(Future.successful(accountingDetails))
    }

    def updateAccountingDetails(accountingDetails: Option[AccountingDetails]): OngoingStubbing[Future[Option[AccountingDetails]]] = {
      when(mockCTDataRepository.updateAccountingDetails(Matchers.anyString(), Matchers.any[AccountingDetails]()))
        .thenReturn(Future.successful(accountingDetails))
    }

    def retrieveContactDetails(response: Option[ContactDetails]) = {
      when(mockCTDataRepository.retrieveContactDetails(Matchers.anyString()))
        .thenReturn(Future.successful(response))
    }

    def updateContactDetails(response: Option[ContactDetails]) = {
      when(mockCTDataRepository.updateContactDetails(Matchers.anyString(), Matchers.any[ContactDetails]()))
        .thenReturn(Future.successful(response))
    }
  }

  object CompanyDetailsServiceMocks {
    def retrieveCompanyDetails(registrationID: String, result: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCompanyDetailsService.retrieveCompanyDetails(Matchers.anyString()))
        .thenReturn(Future.successful(result))
    }

    def updateCompanyDetails(registrationID: String, result: Option[CompanyDetails]): OngoingStubbing[Future[Option[CompanyDetails]]] = {
      when(mockCompanyDetailsService.updateCompanyDetails(Matchers.anyString(), Matchers.any[CompanyDetails]()))
        .thenReturn(Future.successful(result))
    }
  }

  object ContactDetailsServiceMocks {
    def retrieveContactDetails(registrationID: String, response: Option[ContactDetails]) = {
      when(mockContactDetailsService.retrieveContactDetails(Matchers.contains(registrationID)))
        .thenReturn(Future.successful(response))
    }

    def updateContactDetails(registrationID: String, response: Option[ContactDetails]) = {
      when(mockContactDetailsService.updateContactDetails(Matchers.any(), Matchers.any[ContactDetails]()))
        .thenReturn(Future.successful(response))
    }
  }

  object SequenceRepositoryMocks {
    def getNext(sequenceID: String, returns: Int) = {
      when(mockSequenceRepository.getNext(Matchers.any()))
        .thenReturn(Future.successful(returns))
    }
  }

}
