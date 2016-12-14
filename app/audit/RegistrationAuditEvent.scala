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

package audit

import audit.RegistrationAuditEvent.buildTags
import play.api.libs.json.JsObject
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.http.HeaderCarrier

case class TagSet(
                   clientIP: Boolean,
                   clientPort: Boolean,
                   requestId: Boolean,
                   sessionId: Boolean,
                   authorisation: Boolean
                 )

object TagSet {
  val ALL_TAGS = TagSet(true, true, true, true, true)
  val NO_TAGS = TagSet(false, false, false, false, false)
}

import audit.TagSet.ALL_TAGS

abstract class RegistrationAuditEvent(auditType: String, detail: JsObject, tagSet: TagSet = ALL_TAGS)(implicit hc: HeaderCarrier)
  extends ExtendedDataEvent(
    auditSource = "company-registration",
    auditType = auditType,
    detail = detail,
    tags = buildTags(auditType, tagSet)
  )

object RegistrationAuditEvent {

  val AUTH_PROVIDER_ID = "authProviderId"
  val JOURNEY_ID = "journeyId"

  def buildTags(auditType: String, tagSet: TagSet)(implicit hc: HeaderCarrier) = {
    Map("transactionName" -> auditType) ++
      buildClientIP(tagSet) ++
      buildClientPort(tagSet) ++
      buildRequestId(tagSet) ++
      buildSessionId(tagSet) ++
      buildAuthorization(tagSet)
  }

  private def buildClientIP(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.clientIP) Map("clientIP" -> hc.trueClientIp.getOrElse("-")) else Map()

  private def buildClientPort(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.clientPort) Map("clientPort" -> hc.trueClientPort.getOrElse("-")) else Map()

  private def buildRequestId(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.requestId) Map(hc.names.xRequestId -> hc.requestId.map(_.value).getOrElse("-")) else Map()

  private def buildSessionId(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.sessionId) Map(hc.names.xSessionId -> hc.sessionId.map(_.value).getOrElse("-")) else Map()

  private def buildAuthorization(tagSet: TagSet)(implicit hc: HeaderCarrier) =
    if(tagSet.authorisation) Map(hc.names.authorisation -> hc.authorization.map(_.value).getOrElse("-")) else Map()

}
