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

package auth

import play.api.Logger
import play.api.libs.json.Reads
import play.api.mvc.{ActionBuilder, _}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, SimpleRetrieval}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthorisationException, _}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

trait AuthenticatedController extends MicroserviceAuthorisedFunctions with BaseController {
  private[auth] val IGNORE_BODY: BodyParser[AnyContent] = BodyParsers.parse.ignore(AnyContentAsEmpty: AnyContent)
  private[auth] val predicate = ConfidenceLevel.L50 and AuthProviders(GovernmentGateway)
  val internalId: Retrieval[String] = SimpleRetrieval("internalId", Reads.StringReads)

  object AuthenticatedAction extends ActionBuilder[Request] {

    override def invokeBlock[T](request: Request[T], block: (Request[T]) => Future[Result]): Future[Result] = {
      implicit val req: Request[T] = request
      authorised(predicate)(block(request)).recover(authErrorHandling[T])
    }

    def retrieve[T](retrieval: Retrieval[T]) = new AuthenticatedWithRetrieval(retrieval)

    class AuthenticatedWithRetrieval[T](retrieval: Retrieval[T]) {

      private def withRetrieval[A](bodyParser: BodyParser[A])(f: T => Action[A]): Action[A] = {
        Action.async(bodyParser) {
          implicit request =>
            authConnector.authorise(predicate, retrieval).flatMap(f(_)(request)).recover(authErrorHandling[A])
        }
      }

      def async(body: T => Request[AnyContent] => Future[Result]): Action[AnyContent] = async(IGNORE_BODY)(body)

      def async[A](parser: BodyParser[A])(body: T => Request[A] => Future[Result]): Action[A] = {
        withRetrieval(parser)(r => Action.async(parser)(body(r)))
      }
    }
  }

  private def authErrorHandling[A](implicit request: Request[A]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession =>
      Logger.error(s"User has no active session when trying to access ${request.path}")
      Unauthorized
    case e: AuthorisationException =>
      Logger.error(s"User forbidden when trying to access ${request.path}", e)
      Forbidden
  }
}

trait AuthorisedController extends Authed with AuthenticatedController {

  case class AuthorisedAction(regId: String) extends ActionBuilder[Request] {

    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      implicit val req: Request[A] = request
      authorised(ConfidenceLevel.L50).retrieve(internalId) { intId =>
        fetchInternalID(regId).flatMap {
          case Some(documentIntId) if intId == documentIntId => block(request)
          case Some(_) => throw new UnauthorisedAccess
          case None => throw new NoCTDocumentFound
        }
      }.recover(authErrorHandling[A](regId))
    }

    def retrieve[T](retrieval: Retrieval[T]) = new AuthorisedWithRetrieval(retrieval)

    class AuthorisedWithRetrieval[T](retrieval: Retrieval[T]) {

      private def withRetrieval[A](bodyParser: BodyParser[A])(f: T => Action[A]): Action[A] = {
        Action.async(bodyParser) {
          implicit request =>
            authConnector.authorise(predicate, internalId and retrieval).flatMap{ case (intId ~ retrievals) =>
              fetchInternalID(regId).flatMap {
                case Some(documentIntId) if intId == documentIntId => f(retrievals)(request)
                case Some(_) => throw new UnauthorisedAccess
                case None => throw new NoCTDocumentFound
              }
            }.recover(authErrorHandling[A](regId))
        }
      }


      def async(body: T => Request[AnyContent] => Future[Result]): Action[AnyContent] = async(IGNORE_BODY)(body)

      def async[A](parser: BodyParser[A])(body: T => Request[A] => Future[Result]): Action[A] = {
        withRetrieval(parser)(r => Action.async(parser)(body(r)))
      }
    }
  }

  private def authErrorHandling[A](regId: String)(implicit request: Request[A]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession =>
      Logger.error(s"User with regId $regId has no active session when trying to access ${request.path}")
      Unauthorized
    case _: NoCTDocumentFound =>
      Logger.error(s"No CT document found for regId $regId when trying to access ${request.path}")
      NotFound
    case _: UnauthorisedAccess =>
      Logger.error(s"User with regId $regId tried to access a matching document with a different internalId  when trying to access ${request.path}")
      Forbidden
    case e: AuthorisationException =>
      Logger.error(s"User forbidden when trying to access ${request.path}", e)
      Forbidden
  }
}