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

package mocks

import connectors.{AuthConnector, Authority}
import models._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mock.MockitoSugar
import repositories.{CorporationTaxRegistrationMongoRepository, SequenceRepository}
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
      when(mockCTDataService.updateConfirmationReferences(Matchers.contains(regID), Matchers.eq(ConfirmationReferences("transactID","payRef","payAmount",""))))
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
    def getOID(oid: String, thenReturn: Option[(String, String)]): OngoingStubbing[Future[Option[(String, String)]]] = {
      when(mockCTDataRepository.getOid(Matchers.anyString())).thenReturn(Future.successful(thenReturn))
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
