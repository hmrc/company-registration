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

import akka.actor.ActorSystem
import akka.stream.Materializer
import mocks.SCRSMocks
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito.reset

import scala.concurrent.ExecutionContext

trait SCRSSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SCRSMocks with BeforeAndAfterEach {

  override lazy val fakeApplication = new GuiceApplicationBuilder().build()

	implicit val actorSystem: ActorSystem = fakeApplication.actorSystem
	implicit val materializer: Materializer = fakeApplication.materializer

	implicit val defaultHC: HeaderCarrier = HeaderCarrier()
	implicit val defaultEC: ExecutionContext = ExecutionContext.global.prepare()

	override def beforeEach() {
		reset(mockCTDataService)
		reset(mockCTDataRepository)
		reset(mockAuthConnector)
		reset(mockContactDetailsService)
		reset(mockCompanyDetailsService)
		reset(mockSequenceRepository)
		reset(mockWSHttp)
	}
}
