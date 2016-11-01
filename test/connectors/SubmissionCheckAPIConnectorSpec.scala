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

package connectors

import java.util.UUID

import mocks.WSHttpMock
import models.{IncorpUpdate, SubmissionCheckResponse}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ShouldMatchers, WordSpecLike}
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._
import services.CorporationTaxRegistrationService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future


class SubmissionCheckAPIConnectorSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  val testProxyUrl = "testBusinessRegUrl"
  implicit val hc = HeaderCarrier()

  val mockWSHttp = mock[WSHttp]

  trait Setup {
    val connector = new IncorporationCheckAPIConnector {
      val proxyUrl = testProxyUrl
      val http = mockWSHttp
    }
  }

  val validSubmissionResponse = SubmissionCheckResponse(
                                  Seq(
                                    IncorpUpdate(
                                      "transactionId",
                                      "status",
                                      "crn",
                                      "incorpDate",
                                      100000011)
                                  ),
                                  "testNextLink")

  "checkSubmission" should {

    val testTimepoint = UUID.randomUUID().toString

    "return a submission status response when no timepoint is provided" in new Setup {
      val testTimepoint = UUID.randomUUID().toString

      when(mockWSHttp.GET[SubmissionCheckResponse](Matchers.anyString())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission()) shouldBe validSubmissionResponse
    }

    "return a submission status response when a timepoint is provided" in new Setup {
      val testTimepoint = UUID.randomUUID().toString

      when(mockWSHttp.GET[SubmissionCheckResponse](Matchers.anyString())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(Some(testTimepoint))) shouldBe validSubmissionResponse
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/company-registration/internal/check-submission?timepoint=$testTimepoint"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(Some(testTimepoint)))

      urlCaptor.getValue shouldBe url
    }

    "verify nothing is appended as a query string if a timepoint is not supplied" in new Setup {
      val url = s"$testProxyUrl/company-registration/internal/check-submission"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(None))

      urlCaptor.getValue shouldBe url
    }
  }
}
