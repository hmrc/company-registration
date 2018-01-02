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

package models

import models.validation.{APIValidation, BaseJsonFormatting}
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class SubmissionCheckResponse(
                                  items: Seq[IncorpUpdate],
                                  nextLink: String
                                  )

case class IncorpUpdate(transactionId : String,
                        status : String,
                        crn : Option[String],
                        incorpDate:  Option[DateTime],
                        timepoint : String,
                        statusDescription : Option[String] = None)

object SubmissionCheckResponse {
  def format(formatter: BaseJsonFormatting): Reads[SubmissionCheckResponse] = (
    ( __ \ "items" ).read[Seq[IncorpUpdate]](IncorpUpdate.incorpUpdateSequenceReader(formatter)) and
    (__ \ "links" \ "next").read[String]
  )(SubmissionCheckResponse.apply _)

  implicit val reads : Reads[SubmissionCheckResponse] = format(APIValidation)
}

object IncorpUpdate {
  def format(formatter: BaseJsonFormatting): Reads[IncorpUpdate] = (
    ( __ \ "transaction_id" ).read[String] and
    ( __ \ "transaction_status" ).read[String] and
    ( __ \ "company_number" ).readNullable[String] and
    ( __ \ "incorporated_on" ).readNullable[DateTime](formatter.dateFormat) and
    ( __ \ "timepoint" ).read[String] and
    ( __ \ "transaction_status_description" ).readNullable[String]
  )(IncorpUpdate.apply _)

  def incorpUpdateSequenceReader(formatter: BaseJsonFormatting): Reads[Seq[IncorpUpdate]] = Reads.seq[IncorpUpdate](IncorpUpdate.format(formatter))

  implicit val incorpReads : Reads[IncorpUpdate] = format(APIValidation)
}
