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

import models.AccountingDetails
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import services.AccountingDetailsService

import scala.concurrent.Future


trait AccountingServiceMock {
  this: MockitoSugar =>

  lazy val mockAccountingDetailsService = mock[AccountingDetailsService]

  object AccountingDetailsServiceMocks {
    def retrieveAccountingDetails(registrationID: String, result: Option[AccountingDetails]): OngoingStubbing[Future[Option[AccountingDetails]]] = {
      when(mockAccountingDetailsService.retrieveAccountingDetails(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(result))
    }

    def updateAccountingDetails(registrationID: String, result: Option[AccountingDetails]): OngoingStubbing[Future[Option[AccountingDetails]]] = {
      when(mockAccountingDetailsService.updateAccountingDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[AccountingDetails]()))
        .thenReturn(Future.successful(result))
    }
  }
}
