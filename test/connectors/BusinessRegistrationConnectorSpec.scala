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

package connectors

import fixtures.BusinessRegistrationFixture
import helpers.BaseSpec
import models.{BusinessRegistration, BusinessRegistrationRequest}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, NotFoundException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class BusinessRegistrationConnectorSpec extends BaseSpec with BusinessRegistrationFixture {

  val busRegBaseUrl = "testBusinessRegUrl"

  trait Setup {
    val connector = new BusinessRegistrationConnector {
      implicit val ec: ExecutionContext = global
      override val businessRegUrl = busRegBaseUrl
      override val http = mockWSHttp
    }
  }

  implicit val hc = HeaderCarrier()

  val regId = "reg-id-12345"

  "createMetadataEntry" must {
    "make a http POST request to business registration micro-service to create a metadata entry" in new Setup {
      mockHttpPOST[BusinessRegistrationRequest, BusinessRegistration](connector.businessRegUrl, validBusinessRegistrationResponse)

      await(connector.createMetadataEntry) mustBe validBusinessRegistrationResponse
    }
  }

  "retrieveMetadata" must {

    "return a a metadata response if one is found in business registration using the supplied RegId" in new Setup {
      mockHttpGet[BusinessRegistrationResponse]("testUrl", BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse))

      await(connector.retrieveMetadataByRegId(regId)) mustBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
    }

    "return a a metadata response if one is found in business registration micro-service" in new Setup {
      mockHttpGet[BusinessRegistrationResponse]("testUrl", BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse))

      await(connector.retrieveMetadata) mustBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
    }

    "return a Not Found response when a metadata record can not be found" in new Setup {
      when(mockWSHttp.GET[BusinessRegistrationResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationNotFoundResponse))

      await(connector.retrieveMetadata) mustBe BusinessRegistrationNotFoundResponse
    }

    "return a Forbidden response when a metadata record can not be accessed by the user" in new Setup {
      when(mockWSHttp.GET[BusinessRegistrationResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationForbiddenResponse))

      await(connector.retrieveMetadata) mustBe BusinessRegistrationForbiddenResponse
    }

    "return an Exception response when an unspecified error has occurred" in new Setup {

      val exception = new Exception("exception")

      when(mockWSHttp.GET[BusinessRegistrationResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(exception))

      await(connector.retrieveMetadata) mustBe BusinessRegistrationErrorResponse(exception)
    }
  }

  "dropMetadata" must {

    "return a success message upon successfully dropping the collection" in new Setup {
      when(mockWSHttp.GET[String](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful("success"))

      val result = connector.dropMetadataCollection

      await(result) mustBe "success"
    }

    "return a failed message upon successfully dropping the collection" in new Setup {
      when(mockWSHttp.GET[String](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful("failed"))

      val result = connector.dropMetadataCollection

      await(result) mustBe "failed"
    }
  }
}
