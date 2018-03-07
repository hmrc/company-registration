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

package config

import fixtures.CorporationTaxRegistrationFixture
import org.scalatest.mock.MockitoSugar
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmission, HeldSubmissionMongoRepository, Repositories}
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.Eventually
import play.api.Logger
import play.api.libs.json.Json
import services.admin.AdminServiceImpl

import scala.concurrent.Future

class AppStartupJobsSpec extends UnitSpec with MockitoSugar with LogCapturing
  with CorporationTaxRegistrationFixture with Eventually {

  val mockHeldRepo: HeldSubmissionMongoRepository = mock[HeldSubmissionMongoRepository]
  val mockCTRepository: CorporationTaxRegistrationMongoRepository = mock[CorporationTaxRegistrationMongoRepository]
  val mockAdminService: AdminServiceImpl = mock[AdminServiceImpl]
  val mockRepositories: Repositories = mock[Repositories]

  val expectedLockedReg = List()
  val expectedRegStats  = Map.empty[String,Int]

  trait Setup {
    when(mockRepositories.cTRepository)
      .thenReturn(mockCTRepository)

    when(mockRepositories.heldSubmissionRepository)
      .thenReturn(mockHeldRepo)

    when(mockCTRepository.retrieveLockedRegIDs())
      .thenReturn(Future.successful(expectedLockedReg))

    when(mockCTRepository.getRegistrationStats())
      .thenReturn(Future.successful(expectedRegStats))

    val appStartupJobs: AppStartupJobs = new AppStartupJobs(mockAdminService, mockRepositories)
  }

  "getHeldDocsInfo" should {

    val json = Json.obj("foo" -> "bar")

    val regId1 = "reg-1"
    val ackRef1 = "ack-1"
    val heldSubmission1 = HeldSubmission(regId1, ackRef1, json)

    val regId2 = "reg-2"
    val ackRef2 = "ack-2"
    val heldSubmission2 = HeldSubmission(regId2, ackRef2, json)

    val ctDoc1 = validHeldCTRegWithData(regId1, Some(ackRef1))
    val ctDoc2 = validHeldCTRegWithData(regId2, Some(ackRef2))

    "log specific registration information relating to each held document found in the held repo" in new Setup {

      when(mockHeldRepo.getAllHeldDocs)
        .thenReturn(Future.successful(Seq(heldSubmission1, heldSubmission2)))

      when(mockCTRepository.retrieveCorporationTaxRegistration(any()))
        .thenReturn(Future.successful(Some(ctDoc1)), Future.successful(Some(ctDoc2)))

      withCaptureOfLoggingFrom(Logger){ logEvents =>
        eventually {
          await(appStartupJobs.getHeldDocsInfo)
          val expectedLogs = List(
            s"[HeldDocs] status : held - reg Id : $regId1 - conf refs : txId : TX1 - ack ref : $ackRef1",
            s"[HeldDocs] status : held - reg Id : $regId2 - conf refs : txId : TX1 - ack ref : $ackRef2"
          )

          expectedLogs.diff(logEvents.map(_.getMessage)) shouldBe List.empty
        }
      }
    }

    "not log anything is there are no documents in the held repo" in new Setup {
      when(mockHeldRepo.getAllHeldDocs)
        .thenReturn(Future.successful(Seq()))

      withCaptureOfLoggingFrom(Logger){ logEvents =>
        eventually {
          await(appStartupJobs.getHeldDocsInfo)
          logEvents.size shouldBe 0
        }
      }
    }
  }
}
