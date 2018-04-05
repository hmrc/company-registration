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

package jobs

import com.google.inject.name.Names
import itutil.{IntegrationSpecBase, WiremockHelper}
import models.CorporationTaxRegistration
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, QualifierInstance}
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.scheduling.ScheduledJob

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveStaleDocumentsJobISpec extends IntegrationSpecBase with MongoSpecSupport {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$mockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$mockPort"
  )

  class Setup {
    val repository = new CorporationTaxRegistrationMongoRepository(mongo)
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]

  def lookupJob(name: String): ScheduledJob = {
    val qualifier = Some(QualifierInstance(Names.named(name)))
    val key = BindingKey[ScheduledJob](classOf[ScheduledJob], qualifier)
    app.injector.instanceOf[ScheduledJob](key)
  }

  "Remove Stale Documents Job" should {
    "take no action when job is disabled" in new Setup {
      System.setProperty("feature.removeStaleDocuments", "false")

      val job = lookupJob("remove-stale-documents-job")
      val res = await(job.execute)
      res shouldBe job.Result("Feature remove-stale-documents-job is turned off")
    }

    "take no action when job is enabled, return the name of the job" in new Setup {
      System.setProperty("feature.removeStaleDocuments", "true")

      val job = lookupJob("remove-stale-documents-job")
      val res = await(job.execute)

      res shouldBe job.Result("remove-stale-documents-job")
    }
  }

}
