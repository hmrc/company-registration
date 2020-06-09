/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import auth.CryptoSCRS
import helpers.BaseSpec
import mocks.AuthorisationMocks
import models._
import models.des.BusinessAddress
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.GroupsService

import scala.concurrent.Future

class GroupsControllerSpec extends BaseSpec with AuthorisationMocks {
  val internalId = "testInternalId"
  val regId = "testRegId"
  implicit val act = ActorSystem()
  implicit val mat = ActorMaterializer()

  val validGroupsModel = Groups(
    groupRelief = true,
    nameOfCompany = Some(GroupCompanyName("testGroupName", GroupCompanyNameEnum.Other)),
    addressAndType = Some(GroupsAddressAndType(GroupAddressTypeEnum.ALF, BusinessAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("UK")))),
    groupUTR = Some(GroupUTR(Some("1234567890"))))


  class Setup {
    reset(mockGroupsService)

    val controller = new GroupsController {
      val resource = mockResource
      val authConnector = mockAuthConnector
      val groupsService: GroupsService = mockGroupsService
      val cryptoSCRS: CryptoSCRS = mockInstanceOfCrypto
    }
  }

  "private method groupsBlockValidation" should {

    "return Left groups when group block is in the correct state to be inserted (everything exists)" in new Setup {
      controller.groupsBlockValidation(validGroupsModel).left.get shouldBe validGroupsModel
    }
    "return Left groups when group block has everything apart from utr filled in" in new Setup {
      val noneUTRGroupsModel = validGroupsModel.copy(groupUTR = None)
      controller.groupsBlockValidation(noneUTRGroupsModel).left.get shouldBe noneUTRGroupsModel
    }
    "return Left groups when group block has everything apart from utr + address filled in" in new Setup {
      val noneUTRAndAddressGroupsModel = validGroupsModel.copy(groupUTR = None, addressAndType = None)
      controller.groupsBlockValidation(noneUTRAndAddressGroupsModel).left.get shouldBe noneUTRAndAddressGroupsModel
    }
    "return Left groups when group block has everything apart from utr + address + name filled in" in new Setup {
      val noneUTRAndAddressAndNameGroupsModel = validGroupsModel.copy(groupUTR = None, addressAndType = None, nameOfCompany = None)
      controller.groupsBlockValidation(noneUTRAndAddressAndNameGroupsModel).left.get shouldBe noneUTRAndAddressAndNameGroupsModel
    }
    "return Right when UTR just filled in" in new Setup {
      val justUTR = validGroupsModel.copy(nameOfCompany = None, addressAndType = None)
      controller.groupsBlockValidation(justUTR).right.get.isInstanceOf[Exception] shouldBe true
    }
    "return Right when address just filled in" in new Setup {
      val justAddress = validGroupsModel.copy(groupUTR = None, nameOfCompany = None)
      controller.groupsBlockValidation(justAddress).right.get.isInstanceOf[Exception] shouldBe true
    }
    "return Right when UTR and address just filled in" in new Setup {
      val justUTRAndAddress = validGroupsModel.copy(nameOfCompany = None)
      controller.groupsBlockValidation(justUTRAndAddress).right.get.isInstanceOf[Exception] shouldBe true
    }
  }

  "deleteBlock" should {
    "return 204 if groups deleted successfully" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockGroupsService.deleteGroups(eqTo(regId))).thenReturn(Future.successful(true))
      val res = controller.deleteBlock(regId)(FakeRequest())
      status(res) shouldBe NO_CONTENT
    }
    "return 500 if false is returned from deleteGroups" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockGroupsService.deleteGroups(eqTo(regId))).thenReturn(Future.successful(false))
      val res = controller.deleteBlock(regId)(FakeRequest())
      status(res) shouldBe INTERNAL_SERVER_ERROR
    }
    "return exception if groups returns future failed" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockGroupsService.deleteGroups(eqTo(regId))).thenReturn(Future.failed(new Exception("Failure Reason")))
      intercept[Exception](await(controller.deleteBlock(regId)(FakeRequest())))
    }
  }
  "getBlock" should {

    "return 200 with json body of group if group complete" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockGroupsService.returnGroups(eqTo(regId))).thenReturn(Future.successful(Some(validGroupsModel)))
      val res = controller.getBlock(regId)(FakeRequest())
      status(res) shouldBe OK
      contentAsJson(res) shouldBe Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "testGroupName",
          |     "nameType" : "Other"
          |   },
          |  "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "UK",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |     "UTR" : "1234567890"
          |   }
          |}
        """.stripMargin)
    }
    "return 200 if the user has selected No previously to the page group relief" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockGroupsService.returnGroups(eqTo(regId))).thenReturn(Future.successful(Some(Groups(groupRelief = false, None, None, None))))
      val res = controller.getBlock(regId)(FakeRequest())
      status(res) shouldBe OK
      contentAsJson(res) shouldBe Json.parse(
        """
          |{
          |   "groupRelief": false
          |}
        """.stripMargin)
    }
    "return 204 if block does not exist" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockGroupsService.returnGroups(eqTo(regId))).thenReturn(Future.successful(None))
      val res = controller.getBlock(regId)(FakeRequest())
      status(res) shouldBe NO_CONTENT
    }
    "return exception if returnGroups returns an exception" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockGroupsService.returnGroups(eqTo(regId))).thenReturn(Future.failed(new Exception("Failure Reason")))
      intercept[Exception](await(controller.getBlock(regId)(FakeRequest())))

    }
  }
  "saveBlock" should {
    "return 200 if save is successful and group is in correct state" in new Setup {
      val request = FakeRequest().withBody(Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "testGroupName",
          |     "nameType" : "Other"
          |   },
          |  "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "UK",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |     "UTR" : "1234567890"
          |   }
          |}
        """.stripMargin))
      val expected = Json.parse(
        """
          |{
          |   "groupRelief": false,
          |   "nameOfCompany": {
          |     "name": "testGroupName",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "UK",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |     "UTR" : "1234567890"
          |   }
          |}
        """.stripMargin)
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockGroupsService.updateGroups(eqTo(regId), any())).thenReturn(Future.successful(validGroupsModel.copy(groupRelief = false)))
      val res = controller.saveBlock(regId)(request)
      status(res) shouldBe 200
      contentAsJson(res) shouldBe expected

    }
    "return exception if user has skipped pages and data is in incorrect state" in new Setup {
      val request = FakeRequest().withBody(Json.parse(
        """
          |{
          |   "groupRelief": true,
          |  "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "UK",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |     "UTR" : "1234567890"
          |   }
          |}
        """.stripMargin))
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      intercept[Exception](await(controller.saveBlock(regId)(request)))
    }
    "return 400 if json is in the incorrect format" in new Setup {
      val request = FakeRequest().withBody(Json.parse(
        """
          |{
          |   "groupRelief": "",
          |   "nameOfCompany": {
          |     "name": "testGroupName",
          |     "nameType" : "Other"
          |   },  "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "UK",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |     "UTR" : "1234567890"
          |   }
          |}
        """.stripMargin))

      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      val res = controller.saveBlock(regId)(request)
      status(res) shouldBe 400
    }
  }
  "validateListOfNamesAgainstGroupNameValidation" should {
    "return a reduced list of names based on group name validation" in new Setup {

      val request = FakeRequest().withBody(Json.parse(
        """
          [
          "$","testGroupName1", "££", "testGroupName2"
          ]
        """.stripMargin))
      val res = controller.validateListOfNamesAgainstGroupNameValidation(request)
      status(res) shouldBe 200
      contentAsJson(res) shouldBe Json.parse(
        """
          |[
          | "testGroupName1", "testGroupName2"
          |]
        """.stripMargin)
    }
    "return a 204 if all names are invalid" in new Setup {
      val request = FakeRequest().withBody(Json.parse(
        """
          [
          "$","$%"
          ]
        """.stripMargin))
      val res = controller.validateListOfNamesAgainstGroupNameValidation(request)
      status(res) shouldBe 204
    }
    "return 204 if empty array passed in" in new Setup {
      val request = FakeRequest().withBody(Json.parse(
        """
          [
          ]
        """.stripMargin))
      val res = controller.validateListOfNamesAgainstGroupNameValidation(request)
      status(res) shouldBe 204
    }
  }
}