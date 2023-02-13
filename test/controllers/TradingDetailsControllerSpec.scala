/*
 * Copyright 2023 HM Revenue & Customs
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
 * Copyright 2018 HM Revenue & Customs
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

package controllers

import java.util.UUID

import helpers.BaseSpec
import mocks.{AuthorisationMocks, MockMetricsService, SCRSMocks}
import models.TradingDetails
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.TradingDetailsService
import uk.gov.hmrc.auth.core.MissingBearerToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TradingDetailsControllerSpec extends BaseSpec with MockitoSugar with SCRSMocks with AuthorisationMocks {

  val mockTradingDetailsService: TradingDetailsService = mock[TradingDetailsService]
  val mockRepositories: Repositories = mock[Repositories]
  override val mockResource: CorporationTaxRegistrationMongoRepository = mockTypedResource[CorporationTaxRegistrationMongoRepository]

  trait Setup {
    val controller: TradingDetailsController =
      new TradingDetailsController(
        MockMetricsService,
        mockTradingDetailsService,
        mockAuthConnector,
        mockRepositories,
        stubControllerComponents()
      ) {
      override lazy val resource: CorporationTaxRegistrationMongoRepository = mockResource
    }
  }

  val regID: String = UUID.randomUUID.toString
  val internalId: String = "int-12345"
  val otherInternalID: String = "other-int-12345"

  "retrieveTradingDetails" must {
    "retrieve a 200 - Ok and a Json package of TradingDetails" in new Setup {

      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockTradingDetailsService.retrieveTradingDetails(ArgumentMatchers.eq(regID)))
        .thenReturn(Future.successful(Some(TradingDetails("true"))))


      val result: Future[Result] = controller.retrieveTradingDetails(regID)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(TradingDetails("true"))
    }

    "return a 404 - Not Found if the record does not exist" in new Setup {

      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockTradingDetailsService.retrieveTradingDetails(ArgumentMatchers.eq(regID)))
        .thenReturn(Future.successful(None))

      val result: Future[Result] = controller.retrieveTradingDetails(regID)(FakeRequest())
      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.parse(s"""{"statusCode":"404","message":"Could not find trading details record"}""")
    }

    "return a 401 - if the user cannot be authenticated" in new Setup {

      mockAuthorise(Future.failed(MissingBearerToken()))
      val result: Future[Result] = controller.retrieveTradingDetails(regID)(FakeRequest())
      status(result) mustBe UNAUTHORIZED
    }

    "return a 403 - Forbidden if the user is not authorised to view this record" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(otherInternalID))

      val result: Future[Result] = controller.retrieveTradingDetails(regID)(FakeRequest())
      status(result) mustBe FORBIDDEN
    }

    "return a 404 - Not found when an authority is found but nothing is returned from" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      val result: Future[Result] = controller.retrieveTradingDetails(regID)(FakeRequest())
      status(result) mustBe NOT_FOUND
    }
  }

  "updateTradingDetails" must {
    "return a 200 - Ok and a company details response if a record is updated" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockTradingDetailsService.updateTradingDetails(ArgumentMatchers.eq("testRegID"), ArgumentMatchers.eq(TradingDetails("true"))))
        .thenReturn(Future.successful(Some(TradingDetails("true"))))

      val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result: Future[Result] = controller.updateTradingDetails("testRegID")(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(TradingDetails("true"))
    }

    "return a 404 - Not Found if the record to update does not exist" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockTradingDetailsService.updateTradingDetails(ArgumentMatchers.eq("testRegID"), ArgumentMatchers.eq(TradingDetails("true"))))
        .thenReturn(Future.successful(None))

      val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(TradingDetails("true")))

      val result: Future[Result] = controller.updateTradingDetails("testRegID")(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.parse(s"""{"statusCode":"404","message":"Could not find trading details record"}""")
    }

    "return a 401 - if the user cannot be authenticated" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))
      val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result: Future[Result] = controller.updateTradingDetails(regID)(request)
      status(result) mustBe UNAUTHORIZED
    }

    "return a 403 - Forbidden if the user is not authorised to view the record" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(otherInternalID))

      val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result: Future[Result] = controller.updateTradingDetails(regID)(request)

      status(result) mustBe FORBIDDEN
    }

    "return a 404 - Not found when an authority is found but nothing is updated" in new Setup {

      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockTradingDetailsService.updateTradingDetails(ArgumentMatchers.eq(regID), ArgumentMatchers.eq(TradingDetails("true"))))
        .thenReturn(Future.successful(None))

      val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result: Future[Result] = controller.updateTradingDetails(regID)(request)

      status(result) mustBe NOT_FOUND
    }
  }
}
