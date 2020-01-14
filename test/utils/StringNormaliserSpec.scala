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

package utils

import models.validation.APIValidation
import uk.gov.hmrc.play.test.UnitSpec

class StringNormaliserSpec extends UnitSpec {

  "normaliseString" should {
    Seq(
      ("ZZ1 1ZZ", "[A-Za-z0-9 ]", "ZZ1 1ZZ"),
      ("ZZ1 1ZZ", "[A-Za-z0-9]", "ZZ11ZZ"),
      ("ZZ1 1ZZ", "[a-z0-9 ]", "1 1"),
      ("My nænæ is juúst like yØû", "[A-Za-z0-9 ]", "My naenae is juust like yOu"),
      ("My nænæ is just like you", "[0-9]", ""),
      ("St Bob ; ; Lane", APIValidation.lineInvert.toString(), "St Bob   Lane"),
      ("""1:-,.:;0-9A-Z&@$£¥€'\"«»‘’“”?!/\n()[]{}<>*=#%+àáâãäåāăąæǽçćĉċčþďðèéêëēĕėęěĝģğġĥħìíîïĩīĭįĵķĺļľŀłñńņňŋòóôõöøōŏőǿœŕŗřśŝşšţťŧùúûüũūŭůűųŵẁẃẅỳýŷÿźżžſÀÁÂÃÄÅĀĂĄÆǼÇĆĈĊČÞĎÐÈÉÊËĒĔĖĘĚĜĞĠĢĤĦÌÍÎÏĨĪĬĮİĴĶĹĻĽĿŁÑŃŅŇŊÒÓÔÕÖØŌŎŐǾŒŔŖŘŚŜŞŠŢŤŦÙÚÛÜŨŪŬŮŰŲŴẀẂẄỲÝŶŸŹŻŽſa-zÀ-ÖØ-ſƒǺ-ǿẀ-ẅỲỳ""", APIValidation.lineInvert.toString(), """1-,.0-9A-Z&'\"/\n()aaaaaaaaaaeaecccccdeeeeeeeeegggghiiiiiiiijkllllnnnnoooooooooooerrrssssttuuuuuuuuuuwwwwyyyyzzzsAAAAAAAAAAEAECCCCCDEEEEEEEEEGGGGHIIIIIIIIIJKLLLLNNNNOOOOOOOOOOOERRRSSSSTTUUUUUUUUUUWWWWYYYYZZZsa-zA-OO-sA-oW-wYy"""),
      ("fwibble #", APIValidation.lineInvert.toString(), "fwibble "),
      ("fwibble#", APIValidation.lineInvert.toString(), "fwibble"),
      ("Ted & Bob's Farm", APIValidation.lineInvert.toString(), "Ted & Bob's Farm"),
      ("Ted #& Bob;'~s @Farm", APIValidation.lineInvert.toString(), "Ted & Bob's Farm")

    ).foreach{
      case (string, filter, result) => s"return '$result' when '$string' is passed in using regex '$filter'" in {
        StringNormaliser.normaliseString(string, filter.r) shouldBe result
      }
    }
  }
}
