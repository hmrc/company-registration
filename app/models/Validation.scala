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

package models

import play.api.libs.json.{Format, Reads, Writes}
import Reads.{maxLength, minLength, pattern}
import play.api.libs.functional.syntax._


object Validation {

  def length(maxLen:Int, minLen: Int = 1): Reads[String] = maxLength[String](maxLen) keepAnd minLength[String](minLen)
  def readToFmt(rds:Reads[String])(implicit wts:Writes[String]): Format[String] = Format(rds,wts)
  def lengthFmt(maxLen:Int, minLen: Int = 1): Format[String] = readToFmt(length(maxLen, minLen))

}

trait CHAddressValidator {
  import Validation.lengthFmt

  val premisesValidator = lengthFmt(120)
  val lineValidator = lengthFmt(50)
  val postcodeValidator = lengthFmt(20)
}

trait HMRCAddressValidator {
  import Validation._

  val lineValidator = readToFmt( length(27) keepAnd pattern("^[a-zA-Z0-9,.\\(\\)/&amp;'&quot;\\-]{1}[a-zA-Z0-9, .\\(\\)/&amp;'&quot;\\-]{0,26}$".r))
  val postcodeValidator = readToFmt( length(20) keepAnd pattern("^[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}$".r) )
  val countryValidator = readToFmt( length(20) keepAnd pattern("^[A-Za-z0-9]{1}[A-Za-z 0-9]{0,19}$".r) )
}