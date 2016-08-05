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

package services

import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.mongo.Repository

import scala.concurrent.Future

sealed trait ConflictResult
case object Conflicted extends ConflictResult
case class NoConflict[T](data: T) extends ConflictResult

trait Conflict[T] {

//	val collection: JSONCollection
//
//	def conflicted(registrationID: String, data: T)(f: => ConflictResult => Future[T]) = {
//		for{
//			registrationDetails <- collection.find(BSONDocument("registrationId" -> registrationID)).one[T]
//			result <- f(mapToConflictResult(registrationDetails, data))
//		} yield result
//	}
//
//	private def mapToConflictResult(dataToCheck: Option[T], original: T) : ConflictResult = {
//		dataToCheck match {
//			case Some(_) => Conflicted
//			case _ => NoConflict(original)
//		}
//	}
}
