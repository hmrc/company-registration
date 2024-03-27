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

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.HttpClient
import scala.concurrent.Future

trait WSHttpMock {
  this: MockitoSugar =>

  lazy val mockWSHttp = mock[HttpClient]

  def mockHttpGet[T](url: String, thenReturn: T): OngoingStubbing[Future[T]] = {
    when(mockWSHttp.GET[T](ArgumentMatchers.anyString(), ArgumentMatchers.any(), ArgumentMatchers.any())
      (ArgumentMatchers.any[HttpReads[T]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpGet[T](url: String, thenReturn: Future[T]): OngoingStubbing[Future[T]] = {
    when(mockWSHttp.GET[T](ArgumentMatchers.anyString())(ArgumentMatchers.any[HttpReads[T]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
      .thenReturn(thenReturn)
  }

  def mockHttpPOST[I, O](url: String, thenReturn: O, mockWSHttp: HttpClient = mockWSHttp): OngoingStubbing[Future[O]] = {
    when(mockWSHttp.POST[I, O](ArgumentMatchers.anyString(), ArgumentMatchers.any[I](), ArgumentMatchers.any())
      (ArgumentMatchers.any[Writes[I]](), ArgumentMatchers.any[HttpReads[O]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpPUT[I, O](url: String, thenReturn: O, mockWSHttp: HttpClient = mockWSHttp): OngoingStubbing[Future[O]] = {
    when(mockWSHttp.PUT[I, O](ArgumentMatchers.anyString(), ArgumentMatchers.any[I]())
      (ArgumentMatchers.any[Writes[I]](), ArgumentMatchers.any[HttpReads[O]](), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }
}
