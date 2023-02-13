/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers

import ch.qos.logback.classic.Level
import config.LangConstants
import helpers.BaseSpec
import mocks.AuthorisationMocks
import models.Language
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito._
import org.mongodb.scala.MongoException
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, MissingCTDocument}
import services.LanguageService
import uk.gov.hmrc.auth.core.MissingBearerToken
import utils.LogCapturingHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LanguageControllerSpec extends BaseSpec with AuthorisationMocks with LogCapturingHelper {

  val mockLanguageService: LanguageService = mock[LanguageService]
  val mockCTRepo: CorporationTaxRegistrationMongoRepository = mock[CorporationTaxRegistrationMongoRepository]

  override val mockResource: CorporationTaxRegistrationMongoRepository = mockCTRepo

  val registrationID = "reg-12345"
  val internalId = "int-12345"
  val otherInternalID = "other-int-12345"

  val language: Language = Language(LangConstants.english)
  val languageJson: JsValue = Json.toJson(language)

  val languageController: LanguageController = new LanguageController(
    mockLanguageService,
    mockAuthConnector,
    mockCTRepo,
    stubControllerComponents()
  )

  "calling .getLanguage(regId: String)" must {

    "return OK (200) and an Language json object if the user is authorised" in {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockLanguageService.retrieveLanguage(eqTo(registrationID))).thenReturn(Future.successful(Some(language)))

      val result: Future[Result] = languageController.getLanguage(registrationID)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(language)
    }

    "return UNAUTHORIZED (401) when the user is not logged in" in {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result: Future[Result] = languageController.getLanguage(registrationID)(FakeRequest())

      status(result) mustBe UNAUTHORIZED
    }

    "return FORBIDDEN (403) when the user is logged in but not authorised to access the resource" in {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(otherInternalID))

      val result: Future[Result] = languageController.getLanguage(registrationID)(FakeRequest())

      status(result) mustBe FORBIDDEN
    }

    "return NOT_FOUND (404) when the user is logged in but a CT record doesn't exist" in {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.failed(new MissingCTDocument("hfbhdbf")))

      val result: Future[Result] = languageController.getLanguage(registrationID)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return ISE (500) when any unexpected failed future is returned from the downward chain and log an error" in {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockLanguageService.retrieveLanguage(eqTo(registrationID))).thenReturn(Future.failed(new MongoException("FooBar")))

      withCaptureOfLoggingFrom(languageController.logger) { logs =>

        val result: Future[Result] = languageController.getLanguage(registrationID)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR

        logs.containsMsg(Level.ERROR, s"[getLanguage] An unexpected exception of type 'MongoException' occurred for regId '$registrationID'")
      }
    }
  }

  "calling .updateLanguage(regId: String, lang: Language)" must {

    val request = FakeRequest().withBody(Json.toJson(language))

    "return NO_CONTENT (204) when the user is authorised and the update is successful" in {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockLanguageService.updateLanguage(eqTo(registrationID), eqTo(language)))
        .thenReturn(Future.successful(Some(language)))

      val result: Future[Result] = languageController.updateLanguage(registrationID)(request)

      status(result) mustBe NO_CONTENT
    }

    "return UNAUTHORIZED (401) when the user is not logged in" in {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result: Future[Result] = languageController.updateLanguage(registrationID)(request)

      status(result) mustBe UNAUTHORIZED
    }

    "return FORBIDDEN (403) when the user is logged in but not authorised to access the resource" in {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(otherInternalID))

      val result: Future[Result] = languageController.updateLanguage(registrationID)(request)

      status(result) mustBe FORBIDDEN
    }

    "return NOT_FOUND (404) when the user is authorised but the CT document doesn't exist" in {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.failed(new MissingCTDocument("hfbhdbf")))

      val result: Future[Result] = languageController.updateLanguage(registrationID)(request)

      status(result) mustBe NOT_FOUND
    }

    "return ISE (500) when any unexpected failed future is returned from the downward chain and log an error" in {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))

      when(mockLanguageService.updateLanguage(eqTo(registrationID), eqTo(language))).thenReturn(Future.failed(new MongoException("FooBar")))

      withCaptureOfLoggingFrom(languageController.logger) { logs =>

        val result: Future[Result] = languageController.updateLanguage(registrationID)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR

        logs.containsMsg(Level.ERROR, s"[updateLanguage] An unexpected exception of type 'MongoException' occurred for regId '$registrationID'")
      }
    }
  }
}
