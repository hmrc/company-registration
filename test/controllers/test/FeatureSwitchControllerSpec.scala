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

package controllers.test

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Future

class FeatureSwitchControllerSpec extends PlaySpec with MockitoSugar {

  implicit val system: ActorSystem = ActorSystem("CR")
  implicit val materializer: Materializer = Materializer(system)

  val mockQuartz: QuartzSchedulerExtension = mock[QuartzSchedulerExtension]

  class Setup {
    val controller: FeatureSwitchController = new FeatureSwitchController(system, stubControllerComponents()) {
      override lazy val scheduler: QuartzSchedulerExtension = mockQuartz
    }
  }

  "show" must {

    "return a 200 and display all feature flags and their status " in new Setup {
      val result: Future[Result] = controller.show(FakeRequest())
      status(result) mustBe 200
      contentAsString(result) mustBe ""
    }
  }

  "switch" must {

    "enable scheduledJob successfully" in new Setup {
      when(mockQuartz.resumeJob(ArgumentMatchers.any())).thenReturn(true)
      val result: Future[Result] = controller.switch("missing-incorporation-job", "enable")(FakeRequest())
      status(result) mustBe OK
      verify(mockQuartz, times(1)).resumeJob(ArgumentMatchers.any())
    }
    "disable scheduledJob successfully" in new Setup {
      when(mockQuartz.suspendJob(ArgumentMatchers.any())).thenReturn(true)
      val result: Future[Result] = controller.switch("missing-incorporation-job", "disable")(FakeRequest())
      status(result) mustBe OK
      verify(mockQuartz, times(1)).suspendJob(ArgumentMatchers.any())
    }
  }
}
