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

package controllers

import helpers.BaseSpec
import mocks.AuthorisationMocks
import models.{CorporationTaxRegistration, RegistrationStatus}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future


class HeldControllerSpec extends BaseSpec with AuthorisationMocks {

  implicit val hc = HeaderCarrier()

  override val mockResource = mockTypedResource[CorporationTaxRegistrationMongoRepository]

  trait Setup {
    val controller = new HeldController {
      override val service = mockRegHoldingPen
      override val heldRepo = mockHeldSubRepo
      override val authConnector = mockAuthClientConnector
      override val resource = mockResource
    }
  }

  val regId = "reg-12345"
  val otherRegId = "other-reg-12345"
  val internalId = "int-12345"
  val timestamp = "2016-12-31T12:00:00.000Z"
  val dateTime = DateTime.parse(timestamp)

  def doc(timestamp: Option[DateTime]) = {
    CorporationTaxRegistration(internalId = "",
      registrationID = regId,
      status = RegistrationStatus.HELD,
      formCreationTimestamp = "",
      language = "",
      registrationProgress = None,
      acknowledgementReferences = None,
      confirmationReferences = None,
      companyDetails = None,
      accountingDetails = None,
      tradingDetails = None,
      contactDetails = None,
      accountsPreparation = None,
      crn = None,
      submissionTimestamp = None,
      verifiedEmail = None,
      createdTime = CorporationTaxRegistration.now,
      lastSignedIn = CorporationTaxRegistration.now,
      heldTimestamp = timestamp
    )

  }

  "fetchHeldSubmissionTime" should {

    "return a 200 response along with a submission time from the Held Document when CR has no timestamp" in new Setup {
      mockAuthorise()

      when(mockResource.retrieveCorporationTaxRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(doc(None))))
      when(mockHeldSubRepo.retrieveHeldSubmissionTime(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(dateTime)))

      val result = await(controller.fetchHeldSubmissionTime(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(dateTime)
    }

    "return a 200 response along with a submission time from the Held Document when there is no CR document" in new Setup {
      mockAuthorise()

      when(mockResource.retrieveCorporationTaxRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))
      when(mockHeldSubRepo.retrieveHeldSubmissionTime(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(dateTime)))

      val result = await(controller.fetchHeldSubmissionTime(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(dateTime)
    }

    "return a 200 response along with a submission time from the CR Document" in new Setup {
      mockAuthorise()

      when(mockResource.retrieveCorporationTaxRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(doc(Some(dateTime)))))

      val result = await(controller.fetchHeldSubmissionTime(regId)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(dateTime)
    }

    "return a 404 (Not found) response" in new Setup {
      mockAuthorise()

      when(mockResource.retrieveCorporationTaxRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(doc(None))))
      when(mockHeldSubRepo.retrieveHeldSubmissionTime(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val result = await(controller.fetchHeldSubmissionTime(regId)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
    }
  }

  "deleteSubmissionData" should {

    "return a 200 response when a user is logged in and their rejected submission data is deleted" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      when(controller.service.deleteRejectedSubmissionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(true))

      val result = await(controller.deleteSubmissionData(regId)(FakeRequest()))
      status(result) shouldBe OK
    }

    "return a 404 (Not found) response when a user's rejected submission data is not found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(Some(internalId)))

      when(controller.service.deleteRejectedSubmissionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(false))

      val result = await(controller.deleteSubmissionData(regId)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
    }
  }
}
