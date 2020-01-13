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


class APIValidationSpec extends UnitSpec {

  "APIValidation" should {

    val linePatternStringMatchOne = """Abc /123\Z("""
    val linePatternStringMatchTwo = """dEF"), 456.y"""
    val linePatternStringMatchThree = """GhI' &789"""
    val linePatternStringMatchFour = """123jKl"""
    val linePatternStringMatchFive = """4M5n6O """

    Seq(
      (linePatternStringMatchOne, linePatternStringMatchOne, "all valid chars line 1"),
      (linePatternStringMatchTwo, linePatternStringMatchTwo, "all valid chars line 2"),
      (linePatternStringMatchThree, linePatternStringMatchThree, "all valid chars line 3"),
      (linePatternStringMatchFour, linePatternStringMatchFour, "all valid chars line 4"),
      (linePatternStringMatchFive, linePatternStringMatchFive, "all valid chars line 5")
    ).foreach{ tuple =>
      val (testData, expected, tcase) = tuple
      s"linePattern should match valid chars with $tcase" in {
        APIValidation.linePattern.findAllMatchIn(testData).mkString shouldBe expected
      }
    }

    val linePatternStringSpecialOne = """ABC123!"£$"""
    val linePatternStringSpecialTwo = """def%^&*"""
    val linePatternStringSpecialThree = """()-_=+ghI"""
    val linePatternStringSpecialFour = """[j]{K}'l@#~"""
    val linePatternStringSpecialFive = """M|\/,.?¬!`nO"""

    Seq(
      (linePatternStringSpecialOne, """ABC123"""", "invalid chars removed line 1"),
      (linePatternStringSpecialTwo, """def&""", "invalid chars removed line 2"),
      (linePatternStringSpecialThree, """()-ghI""", "invalid chars removed line 3"),
      (linePatternStringSpecialFour, """jK'l""", "invalid chars removed line 4"),
      (linePatternStringSpecialFive, """M\/,.nO""", "invalid chars removed line 5")
    ).foreach{ tuple =>
      val (testData, expected, tcase) = tuple
      s"linePattern should remove invalid chars with $tcase" in {
        APIValidation.linePattern.findAllMatchIn(testData).mkString shouldBe expected
      }
    }

    val line4PatternStringMatchOne = """Abc /123\Z("""
    val line4PatternStringMatchTwo = """dEF"), 456.y"""
    val line4PatternStringMatchThree = """GhI' &789"""
    val line4PatternStringMatchFour = """123jKl"""
    val line4PatternStringMatchFive = """4M5n6O """

    Seq(
      (line4PatternStringMatchOne, line4PatternStringMatchOne, "all valid chars line 1"),
      (line4PatternStringMatchTwo, line4PatternStringMatchTwo, "all valid chars line 2"),
      (line4PatternStringMatchThree, line4PatternStringMatchThree, "all valid chars line 3"),
      (line4PatternStringMatchFour, line4PatternStringMatchFour, "all valid chars line 4"),
      (line4PatternStringMatchFive, line4PatternStringMatchFive, "all valid chars line 5")
    ).foreach{ tuple =>
      val (testData, expected, tcase) = tuple
      s"line4Pattern should match valid chars with $tcase" in {
        APIValidation.line4Pattern.findAllMatchIn(testData).mkString shouldBe expected
      }
    }

    val line4PatternStringSpecialOne = """ABC123!"£$"""
    val line4PatternStringSpecialTwo = """def%^&*"""
    val line4PatternStringSpecialThree = """()-_=+ghI"""
    val line4PatternStringSpecialFour = """[j]{K}'l@#~"""
    val line4PatternStringSpecialFive = """M|\/,.?¬!`nO"""

    Seq(
      (line4PatternStringSpecialOne, """ABC123"""", "invalid chars removed line 1"),
      (line4PatternStringSpecialTwo, """def&""", "invalid chars removed line 2"),
      (line4PatternStringSpecialThree, """()-ghI""", "invalid chars removed line 3"),
      (line4PatternStringSpecialFour, """jK'l""", "invalid chars removed line 4"),
      (line4PatternStringSpecialFive, """M\/,.nO""", "invalid chars removed line 5")
    ).foreach{ tuple =>
      val (testData, expected, tcase) = tuple
      s"line4Pattern should remove invalid chars with $tcase" in {
        APIValidation.line4Pattern.findAllMatchIn(testData).mkString shouldBe expected
      }
    }

    val validPostCodePatternStringOne = "AA1A 1AA"
    val validPostCodePatternStringTwo = "BB2 2BB"
    val validPostCodePatternStringThree = "C3 3CC"
    val validPostCodePatternStringFour = "D44 4DD"
    val validPostCodePatternStringFive = "E55 5EE"

    Seq(
      (validPostCodePatternStringOne, validPostCodePatternStringOne, "valid post code pattern 1"),
      (validPostCodePatternStringTwo, validPostCodePatternStringTwo, "valid post code pattern 2"),
      (validPostCodePatternStringThree, validPostCodePatternStringThree, "valid post code pattern 3"),
      (validPostCodePatternStringFour, validPostCodePatternStringFour, "valid post code pattern 4"),
      (validPostCodePatternStringFive, validPostCodePatternStringFive, "valid post code pattern 5")
    ).foreach{ tuple =>
      val (testPC, expectedPC, testCase) = tuple
      s"postCodePattern should match valid post codes with $testCase" in {
        APIValidation.postCodePattern.findAllMatchIn(testPC).mkString shouldBe expectedPC
      }
    }

      val invalidPostCodePatternStringOne = "aa1a 1aa"
      val invalidPostCodePatternStringTwo = "Bb2 2B!"
      val invalidPostCodePatternStringThree = "C3£ 3$C"
      val invalidPostCodePatternStringFour = "D';4 4D]"
      val invalidPostCodePatternStringFive = "E555EE"
      val invalidPostCodePatternStringSix = "*F|/@#~"
      val invalidPostCodePatternStringSeven = """+=\'"%!"""
      val invalidPostCodePatternStringEight = "G,.?()8GG"
      val invalidPostCodePatternStringNine = "H9>.<9HH"
      val invalidPostCodePatternStringTen = "II10 10II"

      Seq(
        (invalidPostCodePatternStringOne, "", "invalid post code pattern 1"),
        (invalidPostCodePatternStringTwo, "", "invalid post code pattern 2"),
        (invalidPostCodePatternStringThree, "", "ivalid post code pattern 3"),
        (invalidPostCodePatternStringFour, "", "invalid post code pattern 4"),
        (invalidPostCodePatternStringFive, "", "invalid post code pattern 5"),
        (invalidPostCodePatternStringSix, "", "invalid post code pattern 6"),
        (invalidPostCodePatternStringSeven, "", "invalid post code pattern 7"),
        (invalidPostCodePatternStringEight, "", "invalid post code pattern 8"),
        (invalidPostCodePatternStringNine, "", "invalid post code pattern 9"),
        (invalidPostCodePatternStringTen, "", "invalid post code pattern 10")
      ).foreach{ tuple =>
        val (testPC, expectedPC, testCase) = tuple
        s"postCodePattern should remove invalid post codes with $testCase" in {
          APIValidation.postCodePattern.findAllMatchIn(testPC).mkString shouldBe expectedPC
        }
      }

      val validCountryPatternStringOne = "1234567890"
      val validCountryPatternStringTwo = "ABCDEFGHIJ"
      val validCountryPatternStringThree = "Abcef12345"
      val validCountryPatternStringFour = "123ABC45de"
      val validCountryPatternStringFive = "a B c D e 1 2 3 4 5"

      Seq(
        (validCountryPatternStringOne, validCountryPatternStringOne, "valid country pattern 1"),
        (validCountryPatternStringTwo, validCountryPatternStringTwo, "valid country pattern 2"),
        (validCountryPatternStringThree, validCountryPatternStringThree, "valid country pattern 3"),
        (validCountryPatternStringFour, validCountryPatternStringFour, "valid country pattern 4"),
        (validCountryPatternStringFive, validCountryPatternStringFive, "valid country pattern 5")
      ).foreach{ tuple =>
       val (testCountry, expectedCountry, testCase) = tuple
       s"countryPattern should match valid countries with $testCase" in {
         APIValidation.countryPattern.findAllMatchIn(testCountry).mkString shouldBe expectedCountry
       }
      }

      val invalidCountryPatternStringOne = """123!"£ABC"""
      val invalidCountryPatternStringTwo = """DEF$%^456"""
      val invalidCountryPatternStringThree = """&*(GHI789"""
      val invalidCountryPatternStringFour = """J KL)-_0+="""
      val invalidCountryPatternStringFive = """<>M /\?! nO #~ {} [] |`¬"""

      Seq(
        (invalidCountryPatternStringOne, "123ABC", "invalid chars removed from country 1"),
        (invalidCountryPatternStringTwo, "DEF456", "invalid chars removed from country 2"),
        (invalidCountryPatternStringThree, "GHI789", "invalid chars removed from country 3"),
        (invalidCountryPatternStringFour, "J KL0", "invalid chars removed from country 4"),
        (invalidCountryPatternStringFive, "M nO ", "invalid chars removed from country 5")
      ).foreach{ tuple =>
        val (testCountry, expectedCountry, testCase) = tuple
        s"countryPattern should remove invalid chars in countries with $testCase" in {
          APIValidation.countryPattern.findAllMatchIn(testCountry).mkString shouldBe expectedCountry
        }
    }
  }
}
