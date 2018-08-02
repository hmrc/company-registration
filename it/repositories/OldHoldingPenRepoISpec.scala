
package repositories

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.Logger
import play.api.libs.json.{JsObject, Json, OWrites}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class OldHoldingPenRepoISpec extends UnitSpec with BeforeAndAfterEach
  with ScalaFutures with Eventually with WithFakeApplication with LogCapturing {

  class Setup extends MongoSpecSupport {
    val repository = new OldHoldingPenRepository(mongo)
    await(repository.drop)
    override protected val databaseName: String = "test-OldHoldingPenRepoISpec"

    def insert(data: HeldSubmissionData) = {
      val currentCount = count
      await(repository.insert(data))
      count shouldBe currentCount + 1
    }

    def deleteDocs = {
      await(repository.removeAll())
      count shouldBe 0
    }

    implicit val jsObjWts: OWrites[JsObject] = OWrites(identity)

    def count = await(repository.count)

    def retrieve(regId: String): Option[HeldSubmissionData] =
      await(repository.collection.find(Json.obj("registrationID" -> regId)).one[HeldSubmissionData])

    def retrieveAll: List[HeldSubmissionData] = await(repository.findAll())
    def isDbDropped: Boolean = !await(repository.drop)
  }

  val testHeldSubmissionData1 = HeldSubmissionData(
    _id = "regId1", acknowledgementReference = Some("ackref1"), partialSubmission = Some("foo1")
  )

  "dropDatabase" must {
    "drop the database if there is no document in the database" in new Setup {
      insert(testHeldSubmissionData1)
      deleteDocs
      withCaptureOfLoggingFrom(Logger) {
        logEvents =>
          await(repository.dropDatabase)
          eventually(logEvents.exists(_.getMessage contains "[dbDropped] 'held-submission' is true") shouldBe true)
      }
      isDbDropped shouldBe true
    }
    "throw an exception when mongo connection is closed, throwing a specific log message" in new Setup {
      insert(testHeldSubmissionData1)
      deleteDocs
      mongoConnectorForTest.close()

      withCaptureOfLoggingFrom(Logger) {
        logEvents =>
          await(repository.dropDatabase)
          eventually(logEvents.exists(_.getMessage contains "[oldHoldingPenRepository] dropDatabase failed with") shouldBe true)
      }
      isDbDropped shouldBe true
    }
    "return a specific log if database does not exist" in new Setup {
      insert(testHeldSubmissionData1)
      deleteDocs
      await(repository.dropDatabase)
      withCaptureOfLoggingFrom(Logger) {
        logEvents =>
          await(repository.dropDatabase)
          eventually(logEvents.exists(_.getMessage contains "[oldHoldingPenRepository] dropDatabase cannot find 'held-submission' to drop no drop took place") shouldBe true)
      }
      isDbDropped shouldBe true
    }
    "log log-able information and do nothing if one item exists in the database" in new Setup {
      insert(testHeldSubmissionData1)
      withCaptureOfLoggingFrom(Logger){
        logEvents =>
          await(repository.dropDatabase)
          eventually(logEvents.exists(_.getMessage contains s"[oldHoldingPenRepository] Documents still exist in 'held-submission' no drop will take place here are the existing docs List(${testHeldSubmissionData1.copy(partialSubmission = Some("cannot be printed to logs"))})") shouldBe true)
          retrieveAll.head shouldBe testHeldSubmissionData1
      }
    }
    "log all log-able information and do nothing if multiple items exist in the database" in new Setup {
      val listOfDocs = List(testHeldSubmissionData1, testHeldSubmissionData1.copy(_id = "regId2"), testHeldSubmissionData1.copy(_id = "regId3"))
      listOfDocs.foreach(insert)
      withCaptureOfLoggingFrom(Logger){
        logEvents =>
          await(repository.dropDatabase)
          eventually(logEvents.exists(_.getMessage contains s"[oldHoldingPenRepository] Documents still exist in 'held-submission' no drop will take place here are the existing docs ${listOfDocs.map(_.copy(partialSubmission = Some("cannot be printed to logs")))}") shouldBe true)
          retrieveAll shouldBe listOfDocs
      }
    }
  }
}
