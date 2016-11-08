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

package controllers.test

import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec

class FeatureSwitchControllerSpec extends UnitSpec with BeforeAndAfterEach {

  override def beforeEach() {
    System.clearProperty("feature.submissionCheck")
  }

  class Setup {
    val controller = FeatureSwitchController
  }

  "switch" should {

    "return a first submissionCheck feature state set to false when we specify off" in new Setup {
      val featureName = "submissionCheck"
      val featureState = "off"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(submissionCheck,false)"
    }

    "return a submissionCheck feature state set to true when we specify on" in new Setup {
      val featureName = "submissionCheck"
      val featureState = "on"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(submissionCheck,true)"
    }

    "return a submissionCheck feature state set to false as a default when we specify xxxx" in new Setup {
      val featureName = "submissionCheck"
      val featureState = "xxxx"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(submissionCheck,false)"
    }

    "return a bad request when we specify a non implemented feature name" in new Setup {
      val featureName = "Rubbish"
      val featureState = "on"

      val result = controller.switch(featureName, featureState)(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }
  }
}