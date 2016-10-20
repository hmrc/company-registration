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

package models.des

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._
import play.api.libs.json.Writes._
import play.api.libs.functional.syntax._

object DesFormats {

  private val datetime = ISODateTimeFormat.dateTime()

  def formatTimestamp( ts: DateTime ) : String = datetime.print(ts)

}

case class DesRegistration(ackRef: String, wibble: String = "xxx")

object DesRegistration {
  implicit val format = (
    (__ \ "acknowledgementReference").format[String] and
    (__ \ "wibble").format[String]
    )(DesRegistration.apply, unlift(DesRegistration.unapply))
}

object BusinessType {
  val LimitedCompany = "Limited company"
}

sealed trait CompletionCapacity { def text : String }

object CompletionCapacity {
  implicit val writes = new Writes[CompletionCapacity] {
    def writes( cc: CompletionCapacity ) = JsString(cc.text)
  }
}

case object Director extends CompletionCapacity { val text = "Director" }
case object Agent extends CompletionCapacity { val text = "Agent" }
case class Other(text: String) extends CompletionCapacity

case class Metadata(
                     sessionId: String,
                     credId: String,
                     language: String, // TODO - Tighten - enum?
                     submissionTs: DateTime,
                     completionCapacity: CompletionCapacity
                   )

object Metadata {

  import DesFormats._

  implicit val writes = new Writes[Metadata] {
    def writes( m: Metadata ) = {
      Json.obj(
        "businessType" -> BusinessType.LimitedCompany,
        "submissionFromAgent" -> false,
        "declareAccurateAndComplete" -> true,
        "sessionId" -> m.sessionId,
        "credentialId" -> m.credId,
        "language" -> m.language,
        "formCreationTimestamp" -> formatTimestamp( m.submissionTs )
      ) ++ (
        m.completionCapacity match {
          case Other(cc) => {
            Json.obj(
              "completionCapacity" -> "Other",
              "completionCapacityOther" -> cc
            ) }
          case _ => Json.obj( "completionCapacity" -> m.completionCapacity )
        }
      )
    }
  }
}
