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

package controllers

import helpers.BaseSpec
import mocks.AuthorisationMocks
import models.{CorporationTaxRegistration, RegistrationStatus}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, MissingCTDocument, Repositories}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class HeldControllerSpec extends BaseSpec with AuthorisationMocks {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override val mockResource: CorporationTaxRegistrationMongoRepository = mockTypedResource[CorporationTaxRegistrationMongoRepository]
  val mockRepositories: Repositories = mock[Repositories]

  trait Setup {
    val controller: HeldController = new HeldController(mockAuthConnector, mockProcessIncorporationService, mockRepositories, stubControllerComponents()) {
      override lazy val resource: CorporationTaxRegistrationMongoRepository = mockResource
    }

  }

  val regId: String = "reg-12345"
  val otherRegId: String = "other-reg-12345"
  val internalId: String = "int-12345"
  val timestamp: String = "2016-12-31T12:00:00.000Z"
  val dateTime: DateTime = DateTime.parse(timestamp)

  def doc(timestamp: Option[DateTime]): CorporationTaxRegistration = {
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

  "fetchHeldTimestamp" should {
    "return a 200 and a held time stamp" in new Setup {
      mockAuthorise()
      val now: DateTime = DateTime.now()

      when(mockResource.getExistingRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(doc(Some(now))))

      val result: Future[Result] = controller.fetchHeldSubmissionTime(regId)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) shouldBe s"${now.getMillis}"
    }
    "return 404 If no date exists" in new Setup {
      mockAuthorise()

      when(mockResource.getExistingRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.successful(doc(None)))

      val result: Future[Result] = controller.fetchHeldSubmissionTime(regId)(FakeRequest())
      status(result) shouldBe 404
      contentAsString(result) shouldBe ""
    }

    "return an exception if experieced" in new Setup {
      mockAuthorise()

      when(mockResource.getExistingRegistration(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new MissingCTDocument("regId")))

      intercept[MissingCTDocument](await(controller.fetchHeldSubmissionTime(regId)(FakeRequest())))
    }
  }

  "deleteSubmissionData" should {

    "return a 200 response when a user is logged in and their rejected submission data is deleted" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      when(controller.service.deleteRejectedSubmissionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(true))

      val result: Future[Result] = controller.deleteSubmissionData(regId)(FakeRequest())
      status(result) shouldBe OK
    }

    "return a 404 (Not found) response when a user's rejected submission data is not found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      when(controller.service.deleteRejectedSubmissionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(false))

      val result: Future[Result] = controller.deleteSubmissionData(regId)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }
}
