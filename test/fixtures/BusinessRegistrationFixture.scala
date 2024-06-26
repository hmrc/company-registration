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

package fixtures

import config.LangConstants
import models.BusinessRegistration

trait BusinessRegistrationFixture {

  lazy val validBusinessRegistrationResponse: BusinessRegistration = businessRegistrationResponse("12345")

  def businessRegistrationResponse(regId: String): BusinessRegistration = BusinessRegistration(
    regId,
    "2016-08-03T10:49:11Z",
    LangConstants.english,
    Some("CompCap")
  )
}
