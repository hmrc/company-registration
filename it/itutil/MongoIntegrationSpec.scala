/*
 * Copyright 2024 HM Revenue & Customs
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

package itutil

import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.result.{DeleteResult, InsertOneResult}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{Format, JsObject}
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait MongoIntegrationSpec extends GuiceOneAppPerSuite {
  expects: PlaySpec =>

  lazy val mongoComponent = app.injector.instanceOf[MongoComponent]

  implicit class RichMongoRepository[T](repo: PlayMongoRepository[T])(implicit ex: ExecutionContext, ct: ClassTag[T]) {
    def count: Int = await(repo.collection.countDocuments().toFuture()).toInt
    def findAll: Seq[T] = await(repo.collection.find(BsonDocument()).toFuture())
    def deleteAll: DeleteResult = await(repo.collection.deleteMany(BsonDocument()).toFuture())
    def insert(data: T): InsertOneResult = await(repo.collection.insertOne(data).toFuture())
    def insertRaw(raw: JsObject) = {
      val db = mongoComponent.database.withCodecRegistry(
        CodecRegistries.fromRegistries(
          CodecRegistries.fromCodecs(Codecs.playFormatCodec[T](repo.domainFormat)),
          CodecRegistries.fromCodecs(Codecs.playFormatCodec[JsObject](implicitly[Format[JsObject]])),
          DEFAULT_CODEC_REGISTRY
        )
      )
      await(db.getCollection[JsObject](repo.collectionName).insertOne(raw).toFuture())
    }
  }
}
