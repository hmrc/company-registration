/*
 * Copyright 2020 HM Revenue & Customs
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
import akka.stream.ActorMaterializer
import auth.AuthorisationResource
import helpers.BaseSpec
import mocks.AuthorisationMocks
import models.{Address, TakeoverDetails}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import services.TakeoverDetailsService
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TakeoverDetailsControllerSpec extends BaseSpec with AuthorisationMocks {

  val validTakeoverDetailsModel: TakeoverDetails = TakeoverDetails(
    businessName = "business",
    businessTakeoverAddress = Some(Address("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A"))),
    prevOwnersName = Some("human"),
    prevOwnersAddress = Some(Address("1 xyz", "2 xyz", Some("3 xyz"), Some("4 xyz"), Some("ZZ2 2ZZ"), Some("country B")))
  )

  val registrationId = "reg-12345"
  val internalId = "int-12345"
  val mockTakeoverDetailsService: TakeoverDetailsService = mock[TakeoverDetailsService]

  implicit val act = ActorSystem()
  implicit val mat = ActorMaterializer()

  class Setup {
    reset(mockTakeoverDetailsService)

    val controller: TakeoverDetailsController = new TakeoverDetailsController {
      val authConnector: AuthConnector = mockAuthConnector
      val resource: AuthorisationResource[String] = mockResource
      val takeoverDetailsService: TakeoverDetailsService = mockTakeoverDetailsService
      implicit val ec: ExecutionContext = global
    }
  }

  "getBlock" should {
    "successfully retrieve a json with a valid TakeoverDetails and a 200 status if the data exists" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      when(mockTakeoverDetailsService.retrieveTakeoverDetailsBlock(registrationId)).thenReturn(Future.successful(Some(validTakeoverDetailsModel)))

      val result: Result = await(controller.getBlock(registrationId)(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.obj(
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
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      when(mockTakeoverDetailsService.retrieveTakeoverDetailsBlock(registrationId)).thenReturn(Future.successful(None))

      val result: Result = await(controller.getBlock(registrationId)(FakeRequest()))
      status(result) shouldBe NO_CONTENT
    }
  }

  "putSubmission" should {
    "return a 200 response if the TakeoverDetails json is valid" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      val request = FakeRequest().withBody(Json.obj(
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

      val result: Result = await(controller.saveBlock(registrationId)(request))
      status(result) shouldBe OK
    }

    "return a 400 response if the TakeoverDetails json is invalid" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      val request = FakeRequest().withBody(Json.obj(
        "businessName" -> true,
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

      val result: Result = await(controller.saveBlock(registrationId)(request))
      status(result) shouldBe BAD_REQUEST
    }
    "return an exception if the request is missing fields" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      val request = FakeRequest().withBody(Json.obj(
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
