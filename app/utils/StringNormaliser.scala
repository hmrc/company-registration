/*
 * Copyright 2023 HM Revenue & Customs
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


import java.text.Normalizer
import java.text.Normalizer.Form

import models.validation.APIValidation

import scala.util.matching.Regex

object StringNormaliser {
  val specialCharacterConverts: Map[Char, String] = Map('æ' -> "ae", 'Æ' -> "AE", 'œ' -> "oe", 'Œ' -> "OE", 'ß' -> "ss", 'ø' -> "o", 'Ø' -> "O")
  val illegalCharacterTransformations: Map[Char, String] = Map(';' -> ",", ':' -> ".", '\\' -> "/")

  def normaliseString(string: String, charFilter: Regex): String = {
    val ourString = Normalizer.normalize(string, Form.NFKD)
      .replaceAll("\\p{M}", "")
      .trim
      .map((char: Char) => specialCharacterConverts.getOrElse[String](char, char.toString))
      .mkString

    charFilter.findAllMatchIn(ourString).mkString

  }

  def removeIllegalCharacters(string: String): String = {
    Normalizer.normalize(
      string.map {
        originalCharacter =>
          illegalCharacterTransformations.getOrElse(
            key = originalCharacter,
            default = originalCharacter
          )
      }.mkString,
      Form.NFD
    )
  }

  def normaliseAndRemoveIllegalCharacters(string: String) = {
    val normalisedString = normaliseString(string, APIValidation.lineInvert)
    removeIllegalCharacters(normalisedString)
  }

  def normaliseAndRemoveIllegalNameCharacters(string: String) = {
    val normalisedString = normaliseString(string, APIValidation.takeoverNameInvert)
    removeIllegalCharacters(normalisedString)
  }
}