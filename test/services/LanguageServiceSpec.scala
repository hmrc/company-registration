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

package services

import config.LangConstants
import models.Language
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository

import scala.concurrent.Future

class LanguageServiceSpec extends PlaySpec with MockitoSugar {

  val mockCTRepository = mock[CorporationTaxRegistrationMongoRepository]
  val languageService = new LanguageService(mockCTRepository)

  val registrationId = "12345"
  val language = Language(LangConstants.english)

  "calling .updateLanguage(regId: String, lang: Language)" when {

    "successful" must {

      "update and return the supplied email case class" in {

        when(mockCTRepository.updateLanguage(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(language)))
          .thenReturn(Future.successful(Some(language)))

        await(languageService.updateLanguage(registrationId, language)) mustBe Some(language)
      }
    }

    "unsuccessful" must {

      "throw exception" in {

        when(mockCTRepository.updateLanguage(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(language)))
          .thenReturn(Future.failed(new Exception("FooBang")))

        intercept[Exception](await(languageService.updateLanguage(registrationId, language))).getMessage mustBe "FooBang"
      }
    }
  }

  "calling .retrieveLanguage(regId: String)" when {

    "successful" must {

      "update and return the supplied email case class" in {

        when(mockCTRepository.retrieveLanguage(ArgumentMatchers.eq(registrationId)))
          .thenReturn(Future.successful(Some(language)))

        await(languageService.retrieveLanguage(registrationId)) mustBe Some(language)
      }
    }

    "unsuccessful" must {

      "throw exception" in {

        when(mockCTRepository.retrieveLanguage(ArgumentMatchers.eq(registrationId)))
          .thenReturn(Future.failed(new Exception("FooBang")))

        intercept[Exception](await(languageService.retrieveLanguage(registrationId))).getMessage mustBe "FooBang"
      }
    }
  }
}
