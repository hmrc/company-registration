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

package helpers

import connectors.{AuthConnector, Authority}
import models.{CorporationTaxRegistration, Language}
import org.mockito.Matchers
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import play.api.mvc.Result
import repositories.CorporationTaxRegistrationMongoRepository
import services.CorporationTaxRegistrationService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait SCRSMocks {
	this: MockitoSugar =>

	lazy val mockCTDataService = mock[CorporationTaxRegistrationService]
	lazy val mockCTDataRepository = mock[CorporationTaxRegistrationMongoRepository]
	lazy val mockAuthConnector = mock[AuthConnector]

	object CTServiceMocks {
		def createCTDataRecord(result: Result): OngoingStubbing[Future[Result]] = {
			when(mockCTDataService.createCorporationTaxRegistrationRecord(Matchers.any[String], Matchers.any[Language]))
				.thenReturn(Future.successful(result))
		}
	}

	object AuthenticationMocks {
		def getCurrentAuthority(authority: Option[Authority]): OngoingStubbing[Future[Option[Authority]]] = {
			when(mockAuthConnector.getCurrentAuthority()(Matchers.any[HeaderCarrier]()))
				.thenReturn(Future.successful(authority))
		}
	}

	object CTDataRepositoryMocks {
		def createMetadata(ctData: CorporationTaxRegistration): OngoingStubbing[Future[CorporationTaxRegistration]] = {
			when(mockCTDataRepository.createCorporationTaxRegistrationData(Matchers.any[CorporationTaxRegistration]()))
				.thenReturn(Future.successful(ctData))
		}
	}
}
