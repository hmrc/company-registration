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

package controllers.test

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec

class FeatureSwitchControllerSpec extends UnitSpec with MockitoSugar {

  implicit val system = ActorSystem("CR")
  implicit val materializer = ActorMaterializer()


  val mockQuartz = mock[QuartzSchedulerExtension]

  class Setup {
    val controller = new FeatureSwitchController {
      override val scheduler: QuartzSchedulerExtension = mockQuartz
    }
  }

  "show" should {

    "return a 200 and display all feature flags and their status " in new Setup {
      val result = controller.show(FakeRequest())
      status(result) shouldBe 200
      bodyOf(await(result)) shouldBe ""
    }
  }

  "switch" should {

    "enable scheduledJob successfully" in new Setup {
      when(mockQuartz.resumeJob(ArgumentMatchers.any())).thenReturn(true)
      val result = controller.switch("missing-incorporation-job", "enable")(FakeRequest())
      status(result) shouldBe OK
      verify(mockQuartz,times(1)).resumeJob(ArgumentMatchers.any())
    }
    "disable scheduledJob successfully" in new Setup {
      when(mockQuartz.suspendJob(ArgumentMatchers.any())).thenReturn(true)
      val result = controller.switch("missing-incorporation-job", "disable")(FakeRequest())
      status(result) shouldBe OK
      verify(mockQuartz,times(1)).suspendJob(ArgumentMatchers.any())
    }
  }
}
