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

package controllers

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import helpers.BaseSpec
import mocks.AuthorisationMocks
import models.{Address, TakeoverDetails}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.TakeoverDetailsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TakeoverDetailsControllerSpec extends BaseSpec with AuthorisationMocks {

  override val mockResource: CorporationTaxRegistrationMongoRepository = mockTypedResource[CorporationTaxRegistrationMongoRepository]

  val validTakeoverDetailsModel: TakeoverDetails = TakeoverDetails(
    replacingAnotherBusiness = true,
    businessName = Some("business"),
    businessTakeoverAddress = Some(Address("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A"))),
    prevOwnersName = Some("human"),
    prevOwnersAddress = Some(Address("1 xyz", "2 xyz", Some("3 xyz"), Some("4 xyz"), Some("ZZ2 2ZZ"), Some("country B")))
  )

  val registrationId = "reg-12345"
  val internalId = "int-12345"
  val mockTakeoverDetailsService: TakeoverDetailsService = mock[TakeoverDetailsService]

  implicit val act: ActorSystem = ActorSystem()
  implicit val mat: Materializer = Materializer(act)

  class Setup {
    reset(mockTakeoverDetailsService)
    val mockRepositories: Repositories = mock[Repositories]

    val controller: TakeoverDetailsController = new TakeoverDetailsController(mockRepositories,
      mockTakeoverDetailsService,
      mockAuthConnector,
      stubControllerComponents()) {
      override lazy val resource: CorporationTaxRegistrationMongoRepository = mockResource
      override val ec: ExecutionContext = global
    }
  }

  "getBlock" must {
    "successfully retrieve a json with a valid TakeoverDetails and a 200 status if the data exists" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockTakeoverDetailsService.retrieveTakeoverDetailsBlock(registrationId)).thenReturn(Future.successful(Some(validTakeoverDetailsModel)))

      val result: Future[Result] = controller.getBlock(registrationId)(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj(
        "replacingAnotherBusiness" -> true,
        "businessName" -> "business",
        "businessTakeoverAddress" -> Json.obj(
          "line1" -> "1 abc",
          "line2" -> "2 abc",
          "line3" -> "3 abc",
          "line4" -> "4 abc",
          "postcode" -> "ZZ1 1ZZ",
          "country" -> "country A"
        ),
        "prevOwnersName" -> "human",
        "prevOwnersAddress" -> Json.obj(
          "line1" -> "1 xyz",
          "line2" -> "2 xyz",
          "line3" -> "3 xyz",
          "line4" -> "4 xyz",
          "postcode" -> "ZZ2 2ZZ",
          "country" -> "country B"
        )
      )
    }
    "retrieve an empty json and a 204 response if the data is not found" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockTakeoverDetailsService.retrieveTakeoverDetailsBlock(registrationId)).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.getBlock(registrationId)(FakeRequest())
      status(result) mustBe NO_CONTENT
    }
  }

  "putSubmission" must {
    "return a 200 response if the TakeoverDetails json is valid" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))
      val request: FakeRequest[JsObject] = FakeRequest().withBody(Json.obj(
        "replacingAnotherBusiness" -> true,
        "businessName" -> "business",
        "businessTakeoverAddress" -> Json.obj(
          "line1" -> "1 abc",
          "line2" -> "2 abc",
          "line3" -> "3 abc",
          "line4" -> "4 abc",
          "postcode" -> "ZZ1 1ZZ",
          "country" -> "country A"
        ),
        "prevOwnersName" -> "human",
        "prevOwnersAddress" -> Json.obj(
          "line1" -> "1 xyz",
          "line2" -> "2 xyz",
          "line3" -> "3 xyz",
          "line4" -> "4 xyz",
          "postcode" -> "ZZ2 2ZZ",
          "country" -> "country B"
        )
      ))

      when(mockTakeoverDetailsService.updateTakeoverDetailsBlock(eqTo(registrationId), any())).thenReturn(Future.successful(validTakeoverDetailsModel))

      val result: Future[Result] = controller.saveBlock(registrationId)(request)
      status(result) mustBe OK
    }

    "return a 400 response if the TakeoverDetails json is invalid" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))
      val request: FakeRequest[JsObject] = FakeRequest().withBody(Json.obj(
        "replacingAnotherBusiness" -> true,
        "businessName" -> 123,
        "businessTakeoverAddress" -> Json.obj(
          "line1" -> "1 abc",
          "line2" -> "2 abc",
          "line3" -> "3 abc",
          "line4" -> "4 abc",
          "postcode" -> "ZZ1 1ZZ",
          "country" -> "country A"
        ),
        "prevOwnersName" -> "human",
        "prevOwnersAddress" -> Json.obj(
          "line1" -> "1 xyz",
          "line2" -> "2 xyz",
          "line3" -> "3 xyz",
          "line4" -> "4 xyz",
          "postcode" -> "ZZ2 2ZZ",
          "country" -> "country B"
        )
      ))

      val result: Future[Result] = controller.saveBlock(registrationId)(request)
      status(result) mustBe BAD_REQUEST
    }
    "return an exception if the request is missing fields" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))
      val request: FakeRequest[JsObject] = FakeRequest().withBody(Json.obj(
        "replacingAnotherBusiness" -> true,
        "businessName" -> "business",
        "prevOwnersName" -> "human",
        "prevOwnersAddress" -> Json.obj(
          "line1" -> "1 xyz",
          "line2" -> "2 xyz",
          "line3" -> "3 xyz",
          "line4" -> "4 xyz",
          "postcode" -> "ZZ2 2ZZ",
          "country" -> "country B"
        )
      ))

      when(mockTakeoverDetailsService.updateTakeoverDetailsBlock(eqTo(registrationId), any())).thenReturn(Future.failed(new Exception("Something broke")))

      intercept[Exception](await(controller.saveBlock(registrationId)(request)))
    }
  }
}
