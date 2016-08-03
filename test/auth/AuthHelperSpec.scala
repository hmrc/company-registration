/*
 * Copyright 2016 HM Revenue & Customs
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

package auth

import connectors.{AuthConnector, Authority}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, ShouldMatchers, WordSpecLike}
import play.api.mvc.Results
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
/**
  * Created by crispy on 03/08/16.
  */
class AuthHelperSpec extends FakeApplication with WordSpecLike with ShouldMatchers with MockitoSugar with BeforeAndAfter {

	implicit val hc = HeaderCarrier()

	val mockAuth = mock[AuthConnector]

	object Authenticated extends Authenticated {
		val auth = mockAuth
	}

	before {
		reset(mockAuth)
	}

	"The auth connector" should {

		"return auth info when an authority is found" in {

			val a = Authority("x", "y", "z")

			when(mockAuth.getCurrentAuthority()(Matchers.any())).
				thenReturn(Future.successful(Some(a)))

			val result = Authenticated.authenticated { authResult => {
				authResult shouldBe LoggedIn(a)
				Future.successful(Results.Ok)
			}
			}
			val response = await(result)
			response.header.status shouldBe OK
		}

		"return None when an authority isn't found" in {

			when(mockAuth.getCurrentAuthority()(Matchers.any())).
				thenReturn(Future.successful(None))

			val result = Authenticated.authenticated { authResult => {
				authResult shouldBe NotLoggedIn
				Future.successful(Results.Forbidden)
			}
			}
			val response = await(result)
			response.header.status shouldBe FORBIDDEN
		}
	}

}
