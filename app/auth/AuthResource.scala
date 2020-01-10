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

package auth

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AuthResource {

  val resource : AuthorisationResource[String]

  def fetchInternalID(regId : String): Future[String] = {
    resource.getInternalId(regId).map{case(_, intId) => intId }
  }
}
