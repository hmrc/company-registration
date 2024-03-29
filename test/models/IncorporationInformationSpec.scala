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

package models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

import java.time.{LocalDate, ZoneOffset}


class IncorporationInformationSpec extends PlaySpec {

  "IIreads" must {

    val transactionId = "trans12345"
    val subscriber = "SCRS"
    val regime = "CT"
    val callbackUrl = "www.url.com"
    val crn = "crn12345"
    val incDate = LocalDate.parse("2000-12-12")
    val statusacc = "accepted"
    val statusrej = "rejected"

    "return an IncorpUpdate model when suitable success JSON is put in." in {

      val accjson = Json.parse(
        s"""
           |{
           |  "SCRSIncorpStatus":{
           |    "IncorpSubscriptionKey":{
           |      "subscriber":"$subscriber",
           |      "discriminator":"$regime",
           |      "transactionId":"$transactionId"
           |    },
           |    "SCRSIncorpSubscription":{
           |      "callbackUrl":"$callbackUrl"
           |    },
           |    "IncorpStatusEvent":{
           |      "status":"$statusacc",
           |      "crn":"$crn",
           |      "incorporationDate":${incDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli}
           |    }
           |  }
           |}
      """.stripMargin)

      val incorpUpdateReponseAcc = IncorpStatus(transactionId, statusacc, Some(crn), None, Some(incDate))
      accjson.as[IncorpStatus](IncorpStatus.reads) mustBe incorpUpdateReponseAcc
    }

    "return an IncorpUpdate model when suitable rejected JSON is put in." in {

      val rejjson = Json.parse(
        s"""
           |{
           |  "SCRSIncorpStatus":{
           |    "IncorpSubscriptionKey":{
           |      "subscriber":"$subscriber",
           |      "discriminator":"$regime",
           |      "transactionId":"$transactionId"
           |    },
           |    "SCRSIncorpSubscription":{
           |      "callbackUrl":"$callbackUrl"
           |    },
           |    "IncorpStatusEvent":{
           |            "status":"$statusrej",
           |            "description":"description"
           |      }
           |  }
           |}
      """.stripMargin)

      val incorpUpdateReponseRej = IncorpStatus(transactionId, statusrej, None, Some("description"), None)

      rejjson.as[IncorpStatus](IncorpStatus.reads) mustBe incorpUpdateReponseRej

    }
  }
}
