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

package repositories

import javax.inject.Inject

import uk.gov.hmrc.lock.LockRepository
import play.modules.reactivemongo.MongoDbConnection

class Repositories @Inject()() {
  private implicit lazy val mongo = new MongoDbConnection {}.db

  lazy val cTRepository = new CorporationTaxRegistrationMongoRepository(mongo)
  lazy val sequenceRepository = new SequenceMongoRepository(mongo)
  lazy val throttleRepository = new ThrottleMongoRepository()
  lazy val stateDataRepository = new StateDataMongoRepository()
  lazy val heldSubmissionRepository = new HeldSubmissionMongoRepository(mongo)
  lazy val lockRepository = new LockRepository()
}
