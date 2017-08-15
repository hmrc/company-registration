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

package helpers

import play.api.libs.json.{JsObject, Json}

/**
  * Created by george on 15/08/17.
  */
trait ControllerHelper {

  def links(a:JsObject) = {Json.obj("links" ->
    a.value.map { s =>
      Json.obj(s._1 -> {
        if (s._2.as[String].startsWith("""/corporation-tax-registration"""))
          (s._2.as[String].replace("corporation-tax-registration",
            """company-registration/corporation-tax-registration"""))
        else
          s._2
      })
    }.reduce((a, b) => a ++ b))
  }

}
