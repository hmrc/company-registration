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

package repositories

import javax.inject.{Inject, Singleton}

import play.api.{Play, Application}
import uk.gov.hmrc.lock.{LockMongoRepository, LockRepository}
import play.modules.reactivemongo.{ReactiveMongoComponent, MongoDbConnection}
import reactivemongo.api.DB
import uk.gov.hmrc.mongo.MongoConnector

class Repositories @Inject()(app: Application) extends Repos {
  //private implicit val mongo = new MongoDbConnection{}.db
  //private implicit val mongo = db
  lazy val mongoConnector:MongoConnector = app.injector.instanceOf[ReactiveMongoComponent].mongoConnector
  lazy val db = mongoConnector.db

  override lazy val cTRepository = new CorporationTaxRegistrationMongoRepository(db)
  override lazy val sequenceRepository = new SequenceMongoRepository(db)
  override lazy val throttleRepository = new ThrottleMongoRepository(db)
  override lazy val stateDataRepository = new StateDataMongoRepository(db)
  override lazy val heldSubmissionRepository = new HeldSubmissionMongoRepository(db)
  override lazy val lockRepository = LockMongoRepository(db)
}

trait Repos {
  val cTRepository: CorporationTaxRegistrationMongoRepository
  val sequenceRepository: SequenceMongoRepository
  val throttleRepository: ThrottleMongoRepository
  val stateDataRepository: StateDataMongoRepository
  val heldSubmissionRepository: HeldSubmissionMongoRepository
  val lockRepository: LockRepository
}

