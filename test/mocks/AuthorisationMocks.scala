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

import auth.{AuthClientConnector, AuthorisationResource}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

trait AuthorisationMocks {
  self: MockitoSugar with BeforeAndAfterEach =>

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockResource: AuthorisationResource[String] = mock[AuthorisationResource[String]]

  def mockTypedResource[T <: AuthorisationResource[String] : ClassTag]: T = mock[T]

  override protected def beforeEach(): Unit = {
    reset(mockAuthConnector, mockResource)
  }

  def mockAuthorise(): OngoingStubbing[Future[Unit]] = mockAuthorise(Future.successful(()))

  def mockAuthorise[T](returns: Future[T]): OngoingStubbing[Future[T]] = {
    when(mockAuthConnector.authorise[T](any(), any())(any(), any()))
      .thenReturn(returns)
  }

  def mockGetInternalId(returns: Future[String]): OngoingStubbing[Future[(String, String)]] = {
    when(mockResource.getInternalId(any()))
      .thenReturn(returns.map(id => "someRegId" -> id))
  }
}
