/*
 * Copyright 2017 HM Revenue & Customs
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

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._


case class SCRSIncorpStatus(ISK: IncorpSubscriptionKey,
                            SCRSIS: SCRSIncorpSubscription,
                            ISE: IncorpStatusEvent)

case class IncorpSubscriptionKey(subscriber : String,
                                 discriminator : String,
                                 transactionId : String
                                )
case class SCRSIncorpSubscription(callbackUrl : String)

case class IncorpStatusEvent(status : String,
                             description : Option[String],
                             crn : Option[String],
                             incorporationDate : Option[Int]
                            )

case class IncorpStatus(transactionId: String,
                        status: String,
                        crn: Option[String],
                        description: Option[String],
                        incorporationDate: Option[DateTime]){

  def toIncorpUpdate: IncorpUpdate = {
    IncorpUpdate(transactionId, status, crn, incorporationDate, "N/A", description)
  }
}

object IncorpStatus {
  val reads = (
    ( __ \ "SCRSIncorpStatus" \ "IncorpSubscriptionKey" \ "transactionId").read[String] and
      ( __ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "status").read[String] and
      ( __ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "crn").readNullable[String] and
      ( __ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "description").readNullable[String] and
      ( __ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "incorporationDate").readNullable[DateTime](DefaultJodaDateReads)
    )(IncorpStatus.apply _)
}


object SCRSIncorpStatus {
  val dateReads = Reads[DateTime]( js =>
    js.validate[String].map[DateTime](
      DateTime.parse(_, DateTimeFormat.forPattern("yyyy-MM-dd"))
    )
  )

  implicit val IIReads : Reads[IncorpUpdate] = (
      ( __ \ "SCRSIncorpStatus" \ "IncorpSubscriptionKey" \ "transactionId").read[String] and
      ( __ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "status").read[String] and
      ( __ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "crn").readNullable[String] and
      ( __ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "incorporationDate").readNullable[DateTime](dateReads) and
      ( __ \ "timepoint").read[String] and
      ( __ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "description").readNullable[String]
           )(IncorpUpdate.apply _)

}
