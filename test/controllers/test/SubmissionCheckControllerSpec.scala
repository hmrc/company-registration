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

package controllers.test

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.joda.time.Duration
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import services.RegistrationHoldingPenService
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class SubmissionCheckControllerSpec extends UnitSpec with MockitoSugar {

  implicit val system = ActorSystem("CR")
  implicit val materializer = ActorMaterializer()

  val mockRegHoldingPenService = mock[RegistrationHoldingPenService]

  class Setup(withLock: Boolean) {
    val controller = new SubmissionCheckController {
      val service = mockRegHoldingPenService
      val name = "test"
      override lazy val lock = new LockKeeper {
        override def lockId: String = "testLockId"
        override def repo: LockRepository = mock[LockRepository]
        override val forceLockReleaseAfter: Duration = Duration.standardSeconds(FiniteDuration(1L, "seconds").toSeconds)

        override def tryLock[T](body: => Future[T])(implicit ec : ExecutionContext): Future[Option[T]] = {
          withLock match {
            case true => body.map(Some(_))
            case false => body.map(_ => None)
          }
        }
      }
    }
  }

  "triggerSubmissionCheck" should {

    implicit val ex = scala.concurrent.ExecutionContext.Implicits.global
    val res = "testString"

    "return a 200 when a successful future is supplied" in new Setup(true) {
      when(mockRegHoldingPenService.updateNextSubmissionByTimepoint(Matchers.any()))
        .thenReturn(Future.successful(res))

      val result = await(controller.triggerSubmissionCheck(FakeRequest()))
      status(result) shouldBe 200
      bodyOf(result) shouldBe res
    }

    "return a 500 with the exception message when a failed future is supplied" in new Setup(true) {
      when(mockRegHoldingPenService.updateNextSubmissionByTimepoint(Matchers.any()))
        .thenReturn(Future.failed(new Exception("ex message")))

      val result = await(controller.triggerSubmissionCheck(FakeRequest()))
      status(result) shouldBe 500
      bodyOf(result) shouldBe s"${controller.name} failed - An error has occurred during the submission - ex message"
    }

    "return a 200 with a failed to acquire lock message when the lock could not be acquired" in new Setup(false) {
      when(mockRegHoldingPenService.updateNextSubmissionByTimepoint(Matchers.any()))
        .thenReturn(Future.successful(res))

      val result = await(controller.triggerSubmissionCheck(FakeRequest()))
      status(result) shouldBe 200
      bodyOf(result) shouldBe s"${controller.name} failed - could not acquire lock"
    }
  }
}
