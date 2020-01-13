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

package connectors

import fixtures.BusinessRegistrationFixture
import mocks.SCRSMocks
import models.BusinessRegistration
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class BusinessRegistrationConnectorSpec extends UnitSpec with MockitoSugar with SCRSMocks with BusinessRegistrationFixture {

  val busRegBaseUrl = "testBusinessRegUrl"

  trait Setup {
    val connector = new BusinessRegistrationConnector {
      override val businessRegUrl = busRegBaseUrl
      override val http = mockWSHttp
    }
  }

  implicit val hc = HeaderCarrier()

  val regId = "reg-id-12345"

  "createMetadataEntry" should {
    "make a http POST request to business registration micro-service to create a metadata entry" in new Setup {
      mockHttpPOST[JsValue, BusinessRegistration](connector.businessRegUrl, validBusinessRegistrationResponse)

      await(connector.createMetadataEntry) shouldBe validBusinessRegistrationResponse
    }
  }

  "retrieveMetadata" should {

    "return a a metadata response if one is found in business registration using the supplied RegId" in new Setup {
      mockHttpGet[BusinessRegistration]("testUrl", validBusinessRegistrationResponse)

      await(connector.retrieveMetadata(regId)) shouldBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
    }

    "return a a metadata response if one is found in business registration micro-service" in new Setup {
      mockHttpGet[BusinessRegistration]("testUrl", validBusinessRegistrationResponse)

      await(connector.retrieveMetadata) shouldBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
    }

    "return a Not Found response when a metadata record can not be found" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("Bad request")))

      await(connector.retrieveMetadata) shouldBe BusinessRegistrationNotFoundResponse
    }

    "return a Forbidden response when a metadata record can not be accessed by the user" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new ForbiddenException("Forbidden")))

      await(connector.retrieveMetadata) shouldBe BusinessRegistrationForbiddenResponse
    }

    "return an Exception response when an unspecified error has occurred" in new Setup {
      when(mockWSHttp.GET[BusinessRegistration](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("exception")))

      await(connector.retrieveMetadata).getClass shouldBe BusinessRegistrationErrorResponse(new Exception).getClass
    }
  }

  "dropMetadata" should {

    val url = s"$busRegBaseUrl//business-registration/test-only/drop-collection"

    val successMessage = Json.parse("""{"message":"success"}""")
    val failureMessage = Json.parse("""{"message":"failed"}""")

    "return a success message upon successfully dropping the collection" in new Setup {
      when(mockWSHttp.GET[JsValue](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(successMessage))

      val result = connector.dropMetadataCollection

      await(result) shouldBe "success"
    }

    "return a failed message upon successfully dropping the collection" in new Setup {
      when(mockWSHttp.GET[JsValue](ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(failureMessage))

      val result = connector.dropMetadataCollection

      await(result) shouldBe "failed"
    }
  }
}
