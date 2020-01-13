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

package services

import assets.TestConstants.CorporationTaxRegistration.{corpTaxRegModel, testRegistrationId}
import assets.TestConstants.TakeoverDetails.testTakeoverDetailsModel
import helpers.BaseSpec
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.commands.UpdateWriteResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TakeoverDetailsServiceSpec extends BaseSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCTDataRepository)
  }

  object TestService extends TakeoverDetailsService(mockCTDataRepository)

  "retrieveTakeoverDetailsBlock" should {
    "return a TakeoverDetails model" when {
      "one is found in the database" in {
        val testData = corpTaxRegModel(optTakeoverDetails = Some(testTakeoverDetailsModel))
        when(mockCTDataRepository.findByRegId(eqTo(testRegistrationId))).thenReturn(Future.successful(Some(testData)))

        val res = await(TestService.retrieveTakeoverDetailsBlock(testRegistrationId))

        res shouldBe Some(testTakeoverDetailsModel)
      }
    }

    "throw an exception" when {
      "no document is found in the database" in {
        when(mockCTDataRepository.findByRegId(eqTo(testRegistrationId))).thenReturn(Future.successful(None))

        intercept[Exception](await(TestService.retrieveTakeoverDetailsBlock(testRegistrationId)))
      }
    }
  }

  "updateTakeoverDetailsBlock" should {
    "return a TakeoverDetails model" when {
      "the database is successfully updated" in {
        val testJson = Json.toJson(testTakeoverDetailsModel).as[JsObject]
        val key = "takeoverDetails"
        when(mockCTDataRepository.update(eqTo(testRegistrationId), eqTo(key), eqTo(testJson))).thenReturn(Future.successful(mock[UpdateWriteResult]))

        val res = await(TestService.updateTakeoverDetailsBlock(testRegistrationId, testTakeoverDetailsModel))

        res shouldBe testTakeoverDetailsModel
      }
    }

    "throw an exception" when {
      "no document is found in the database" in {
        val testJson = Json.toJson(testTakeoverDetailsModel).as[JsObject]
        val key = "takeoverDetails"
        when(mockCTDataRepository.update(eqTo(testRegistrationId), eqTo(key), eqTo(testJson))).thenReturn(Future.failed(new NoSuchElementException))

        intercept[Exception](await(TestService.updateTakeoverDetailsBlock(testRegistrationId, testTakeoverDetailsModel)))
      }
    }
  }

}
