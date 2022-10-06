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

package auth

import utils.Logging
import play.api.mvc._
import repositories.MissingCTDocument
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticatedActions extends MicroserviceAuthorisedFunctions with Logging {
  self: BackendController =>

  implicit val ec: ExecutionContext
  private[auth] val predicate = ConfidenceLevel.L50 and AuthProviders(GovernmentGateway)

  val internalId: Retrieval[Option[String]] = Retrievals.internalId

  object AuthenticatedAction extends ActionBuilder[Request, AnyContent] {

    override val parser: BodyParser[AnyContent] = controllerComponents.parsers.defaultBodyParser
    override protected val executionContext: ExecutionContext = controllerComponents.executionContext


    override def invokeBlock[T](request: Request[T], block: (Request[T]) => Future[Result]): Future[Result] = {
      implicit val req: Request[T] = request
      authorised(predicate)(block(request)).recover(authenticationErrorHandling[T])
    }

    def retrieve[T](retrieval: Retrieval[T]) = new AuthenticatedWithRetrieval(retrieval)

    class AuthenticatedWithRetrieval[T](retrieval: Retrieval[T]) extends AsyncRequest[T](controllerComponents) {

      override protected[auth] def withRetrieval[A](bodyParser: BodyParser[A])(f: T => Action[A]): Action[A] = {
        Action.async(bodyParser) {
          implicit request =>
            authConnector.authorise(predicate, retrieval)
              .flatMap(f(_)(request))
              .recover(authenticationErrorHandling[A])
        }
      }
    }

  }

  private def authenticationErrorHandling[A](implicit request: Request[A]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession =>
      logger.error(s"[authenticationErrorHandling] User has no active session when trying to access ${request.path}")
      Unauthorized
    case e: AuthorisationException =>
      logger.error(s"[authenticationErrorHandling] User forbidden when trying to access ${request.path}", e)
      Forbidden
  }
}

trait AuthorisedActions extends AuthenticatedActions with AuthResource with Logging {
  self: BackendController =>

  case class AuthorisedAction(regId: String) extends ActionBuilder[Request, AnyContent] {

    override val parser: BodyParser[AnyContent] = controllerComponents.parsers.defaultBodyParser
    override protected val executionContext: ExecutionContext = controllerComponents.executionContext


    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      implicit val req: Request[A] = request
      authorised(ConfidenceLevel.L50).retrieve(internalId) {
        case Some(authIntId) =>
          fetchInternalID(regId).flatMap {
            case `authIntId` => block(request)
            case _ => throw UnauthorisedAccess()
          }
        case _ => throw NoInternalIdRetrieved()
      }.recover(authorisationErrorHandling[A](regId))
    }

    def retrieve[T](retrieval: Retrieval[T]) = new AuthorisedWithRetrieval(retrieval)

    class AuthorisedWithRetrieval[T](retrieval: Retrieval[T]) extends AsyncRequest[T](controllerComponents) {

      override protected[auth] def withRetrieval[A](bodyParser: BodyParser[A])(block: T => Action[A]): Action[A] = {
        Action.async(bodyParser) {
          implicit request =>
            authConnector.authorise(predicate, internalId and retrieval).flatMap {
              case Some(authIntId) ~ retrievals =>
                fetchInternalID(regId).flatMap {
                  case `authIntId` => block(retrievals)(request)
                  case _ => throw UnauthorisedAccess()
                }
              case _ => throw NoInternalIdRetrieved()
            }.recover(authorisationErrorHandling[A](regId))
        }
      }
    }

  }

  private def authorisationErrorHandling[A](regId: String)(implicit request: Request[A]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession =>
      logger.error(s"[authorisationErrorHandling] User with regId $regId has no active session when trying to access ${request.path}")
      Unauthorized
    case _: MissingCTDocument =>
      logger.error(s"[authorisationErrorHandling] No CT document found for regId $regId when trying to access ${request.path}")
      NotFound
    case _: UnauthorisedAccess =>
      logger.error(s"[authorisationErrorHandling] User with regId $regId tried to access a matching document with a different internalId  when trying to access ${request.path}")
      Forbidden
    case _: NoInternalIdRetrieved =>
      logger.error(s"[authorisationErrorHandling] User with regId $regId had no internalId retrieved from call to Auth")
      Forbidden
    case e: AuthorisationException =>
      logger.error(s"[authorisationErrorHandling] User forbidden when trying to access ${request.path}", e)
      Forbidden
  }
}

abstract class AsyncRequest[T](controllerComponents: ControllerComponents) {
  private val IGNORE_BODY: BodyParser[AnyContent] = BodyParsers.utils.ignore(AnyContentAsEmpty: AnyContent)

  protected def withRetrieval[A](bodyParser: BodyParser[A])(f: T => Action[A]): Action[A]

  def async(body: T => Request[AnyContent] => Future[Result]): Action[AnyContent] = async(IGNORE_BODY)(body)

  def async[A](parser: BodyParser[A])(body: T => Request[A] => Future[Result]): Action[A] =
    withRetrieval(parser)(r => controllerComponents.actionBuilder.async(parser)(body(r)))
}