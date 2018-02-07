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

package models.validation

import uk.gov.hmrc.play.test.UnitSpec

class IDRegexSpec extends UnitSpec {

  "RegID regex" should {
    "accept valid registration IDs" in {
      val inputs = List(
        "1",
        "1234567890",
        "1234"
      )

      inputs foreach {_ matches IDRegex.regId.regex shouldBe true}
    }

    "reject invalid registration IDs" in {
      val inputs = List(
        "",
        "a12346",
        "12 34",
        "12345678901",
        "1.2"
      )

      inputs foreach {_ matches IDRegex.regId.regex shouldBe false}
    }
  }

  "AckRef regex" should {
    "accept valid AckRefs" in {
      val inputs = List(
        "BRCT00123456789",
        "BRCT98765432100"
      )

      inputs foreach {_ matches IDRegex.ackRef.regex shouldBe true}
    }

    "reject invalid AckRefs" in {
      val inputs = List(
        "",
        "brct01234567890",
        "ABCD01234567890",
        "BRCT-0123456789",
        "BRCT012345",
        "BRCT012345678900"
      )

      inputs foreach {_ matches IDRegex.ackRef.regex shouldBe false}
    }
  }

}
