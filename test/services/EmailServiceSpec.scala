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

package services

import models.Email
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository

import scala.concurrent.Future

class EmailServiceSpec extends PlaySpec with MockitoSugar {

  val mockCTRepository = mock[CorporationTaxRegistrationMongoRepository]

  class Setup {
    val emailService = new EmailService {
      val ctRepository = mockCTRepository
    }
  }

  val registrationId = "12345"
  val email = Email("testAddress", "GG", linkSent = true, verified = true, returnLinkEmailSent = true)

  "updateEmail" must {

    "update and return the supplied email case class" in new Setup {
      when(mockCTRepository.updateEmail(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(email)))
        .thenReturn(Future.successful(Some(email)))

      await(emailService.updateEmail(registrationId, email)) mustBe Some(email)
    }
  }

  "retrieveEmail" must {

    "return an email case class" in new Setup {
      when(mockCTRepository.retrieveEmail(ArgumentMatchers.eq(registrationId)))
        .thenReturn(Future.successful(Some(email)))

      await(emailService.retrieveEmail(registrationId)) mustBe Some(email)
    }
  }
}
