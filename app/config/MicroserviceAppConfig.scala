/*
 * Copyright 2022 HM Revenue & Customs
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

package config

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class MicroserviceAppConfig @Inject()(config: ServicesConfig) {

  def getConfigString(key: String): String = config.getConfString(key, throw new RuntimeException(s"Could not find $key in config"))


  val regime: String = getConfigString("regime")
  val subscriber: String = getConfigString("subscriber")

  val incorpInfoUrl: String = config.baseUrl("incorporation-information")
  val compRegUrl: String = config.baseUrl("company-registration")
  lazy val threshold = config.getConfInt("throttle-threshold", throw new Exception("throttle-threshold not found in config"))

  lazy val welshVatEmailEnabled: Boolean = config.getString("features.welshVatEmailEnabled").toBoolean


}