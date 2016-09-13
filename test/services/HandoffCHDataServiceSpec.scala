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

package services

import java.util.UUID

import helpers.SCRSSpec
import models.HandoffCHData
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, Json}
import repositories.HandoffRepository

import scala.concurrent.Future

class HandoffCHDataServiceSpec extends SCRSSpec {

  trait Setup {
    val service = new HandoffCHDataService {
      override val handoffRepository: HandoffRepository = mockHandoffRespository
    }
  }

  val registrationID = UUID.randomUUID().toString

  "retrieve the CH handoff data" should {
    "return the CH JSON when an empty handoff record is found" in new Setup {
      val data = HandoffCHData(registrationID,  ch = Json.parse("{}"))
      when(mockHandoffRespository.fetchHandoffCHData(Matchers.eq(registrationID)))
           .thenReturn(Future.successful(Some(data)))

      await(service.retrieveHandoffCHData(registrationID)) shouldBe Some(Json.parse("{}"))
    }

    "return the CH JSON when a simple handoff record is found" in new Setup {
      val data = HandoffCHData(registrationID,  ch = Json.parse("""{"a":1}"""))
      when(mockHandoffRespository.fetchHandoffCHData(Matchers.eq(registrationID)))
        .thenReturn(Future.successful(Some(data)))

      await(service.retrieveHandoffCHData(registrationID)) shouldBe Some(Json.parse("{\"a\":1}"))
    }

    "return None if no handoff record is found" in new Setup {
      when(mockHandoffRespository.fetchHandoffCHData(Matchers.eq(registrationID)))
        .thenReturn(Future.successful(None))

      await(service.retrieveHandoffCHData(registrationID)) shouldBe None
    }
  }

  "save the CH handoff data" should {
    "pass the info to the repository and return true if successful" in new Setup {

      val mockResponse = HandoffCHData(registrationID)
      when(mockHandoffRespository.storeHandoffCHData(Matchers.any[HandoffCHData]()))
        .thenReturn(Future.successful(Some(mockResponse)))

      await(service.storeHandoffCHData(registrationID, Json.parse("{}"))) shouldBe true

      val captor = ArgumentCaptor.forClass(classOf[HandoffCHData])

      verify(mockHandoffRespository).storeHandoffCHData(captor.capture())

      captor.getValue.registrationID shouldBe registrationID
      captor.getValue.ch shouldBe Json.parse("{}")
    }
  }

  "return false if not saved" in new Setup {

    val mockResponse = HandoffCHData(registrationID)
    when(mockHandoffRespository.storeHandoffCHData(Matchers.any[HandoffCHData]()))
      .thenReturn(Future.successful(None))

    await(service.storeHandoffCHData(registrationID, Json.parse("{}"))) shouldBe false
  }

}
