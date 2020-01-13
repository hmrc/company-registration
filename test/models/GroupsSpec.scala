/*
 * Copyright 2019 HM Revenue & Customs
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

package models

import helpers.BaseSpec
import models.des.BusinessAddress
import models.validation.{APIValidation, MongoValidation}
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.LogCapturing

class GroupsSpec extends BaseSpec with LogCapturing {

  val formatsOfGroupsAPI = Groups.formats(APIValidation, mockInstanceOfCrypto)
  val formatsOfGroupsMongo = Groups.formats(MongoValidation,mockInstanceOfCrypto)

    val validGroupsModel = Groups(
      groupRelief = true,
      nameOfCompany = Some(GroupCompanyName("foo", GroupCompanyNameEnum.Other)),
      addressAndType = Some(GroupsAddressAndType(GroupAddressTypeEnum.ALF,BusinessAddress("1 abc","2 abc",Some("3 abc"),Some("4 abc"),Some("ZZ1 1ZZ"),Some("country A")))),
      groupUTR = Some(GroupUTR(Some("1234567890"))))

  val encryptedUTR = mockInstanceOfCrypto.wts.writes("1234567890")

  val fullGroupJson = Json.parse(
    """
      |{
      |   "groupRelief": true,
      |   "nameOfCompany": {
      |     "name": "foo",
      |     "nameType" : "Other"
      |   },
      |   "addressAndType" : {
      |     "addressType" : "ALF",
      |       "address" : {
      |         "line1": "1 abc",
      |         "line2" : "2 abc",
      |         "line3" : "3 abc",
      |         "line4" : "4 abc",
      |         "country" : "country A",
      |         "postcode" : "ZZ1 1ZZ"
      |     }
      |   },
      |   "groupUTR" : {
      |     "UTR" : "1234567890"
      |   }
      |}
    """.stripMargin)

  "API Reads" should {
  "read in full json object" in {
    fullGroupJson.as[Groups](formatsOfGroupsAPI) shouldBe validGroupsModel
  }
    "read in json with minimum data" in {
      val minimumJson = Json.parse(
        """ {"groupRelief": true }""".stripMargin)

      minimumJson.as[Groups](formatsOfGroupsAPI) shouldBe Groups(true,None,None,None)
    }
    "read in json with valid data over maximum lengths for all address fields, truncates successfully" in {
      val fullGroupJsonEmptyUTRMaxLengthAddress = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "123 abc def foo bar wizz288d",
          |         "line2" : "123 abc def foo bar wizz288d",
          |         "line3" : "123 abc def foo bar wizz288d",
          |         "line4" : "abc def 123 abcd199",
          |         "country" : "21 chars not abcd 201",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |
          |   }
          |}
        """.stripMargin)
     val res = fullGroupJsonEmptyUTRMaxLengthAddress.as[Groups](formatsOfGroupsAPI)
      res shouldBe validGroupsModel.copy(
        addressAndType = Some(
          GroupsAddressAndType(
            GroupAddressTypeEnum.ALF,
            BusinessAddress(
              "123 abc def foo bar wizz288",
              "123 abc def foo bar wizz288",
              Some("123 abc def foo bar wizz288"),
              Some("abc def 123 abcd19"),
              Some("ZZ1 1ZZ"),
              Some("21 chars not abcd 20"))
          )),
        groupUTR = Some(GroupUTR(None)))
    }
    "read in json with normalisable data for address fields, return normalised data" in {
      val fullGroupJsonEmptyUTRNormalisableAddress = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "Æø",
          |         "line2" : "Æø",
          |         "line3" : "Æø",
          |         "line4" : "Æø",
          |         "country" : "Æø",
          |         "postcode" : "ZZ1 1Æ"
          |     }
          |   },
          |   "groupUTR" : {
          |
          |   }
          |}
        """.stripMargin)
      val res = fullGroupJsonEmptyUTRNormalisableAddress.as[Groups](formatsOfGroupsAPI)
      res shouldBe validGroupsModel.copy(
        addressAndType = Some(
          GroupsAddressAndType(
            GroupAddressTypeEnum.ALF,
            BusinessAddress(
              "AEo",
              "AEo",
              Some("AEo"),
              Some("AEo"),
              Some("ZZ1 1AE"),
              Some("AEo"))
          )),
        groupUTR = Some(GroupUTR(None)))
    }
    "read in json with name that is longer than 20 chars but can be normalised and trimmed so validation passed, no log thrown" in {
      val groupJsonNameOfCompanyValidButLong = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "This is longerÆ than 20 characters but thats fine because it can be normalised and trimmed to 20 chars and matches Des regex",
          |     "nameType" : "CohoEntered"
          |   }
          |}
        """.stripMargin)
      withCaptureOfLoggingFrom(Logger) { logEvents =>

        val expectedMessage = s"[Groups API Reads] name of company invalid when normalised and trimmed this doesn't pass Regex validation"
        logEvents.map(events => (events.getLevel.toString, events.getMessage)).contains(("WARN", expectedMessage)) shouldBe false

        val res = groupJsonNameOfCompanyValidButLong.as[Groups](formatsOfGroupsAPI)
        res shouldBe Groups(
          true,
          nameOfCompany = Some(GroupCompanyName("This is longerÆ than 20 characters but thats fine because it can be normalised and trimmed to 20 chars and matches Des regex", GroupCompanyNameEnum.CohoEntered)),
          None,
          None)
      }
    }

    "read in json with empty utr field indicating that user has not entered one but submitted on the page" in {
      val fullGroupJsonEmptyUTR = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "country A",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |
          |   }
          |}
        """.stripMargin)
      fullGroupJsonEmptyUTR.as[Groups](formatsOfGroupsAPI) shouldBe validGroupsModel.copy(groupUTR = Some(GroupUTR(None)))
    }
    s"allow json reads to read nameOfCompany > 20 chars but log as a warn for ${GroupCompanyNameEnum.Other}" in {
      val json = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "123456789012345678901",
          |     "nameType" : "Other"
          |   }
         }
        """.stripMargin)
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        json.as[Groups](formatsOfGroupsAPI) shouldBe Groups(true, Some(GroupCompanyName("123456789012345678901", GroupCompanyNameEnum.Other)),None,None)
        val expectedMessage = s"[Groups API Reads] nameOfCompany.nameType = ${GroupCompanyNameEnum.Other} but name.size > 20, could indicate frontend validation issue"
        logEvents.map(events => (events.getLevel.toString,events.getMessage)).contains(("WARN", expectedMessage)) shouldBe true

      }
    }
    s"allow json reads to read nameOfCompany > 20 chars but dont log for ${GroupCompanyNameEnum.CohoEntered}" in {
      val json = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "123456789012345678901",
          |     "nameType" : "CohoEntered"
          |   }
         }
        """.stripMargin)
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        json.as[Groups](formatsOfGroupsAPI) shouldBe Groups(true, Some(GroupCompanyName("123456789012345678901", GroupCompanyNameEnum.CohoEntered)),None,None)
        val expectedMessage = s"[Groups API Reads] nameOfCompany.nameType = ${GroupCompanyNameEnum.Other} but name.size > 20, could indicate frontend validation issue"
        logEvents.map(events => (events.getLevel.toString,events.getMessage)).contains(("WARN", expectedMessage)) shouldBe false
      }
    }
    "read if UTR is 1 number" in {
      val utr1NumberInJson = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "groupUTR" : {
          |     "UTR" : "0"
          |   }
          |}
        """.stripMargin)
      utr1NumberInJson.validate[Groups](formatsOfGroupsAPI).isSuccess shouldBe true

    }

    "NOT read json if UTR is over 10 numbers" in {
      val utrJSon11Chars = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "groupUTR" : {
          |     "UTR" : "12345678901"
          |   }
          |}
        """.stripMargin)

      val message = "UTR does not match regex"
      val resToBeSure = utrJSon11Chars.validate[Groups](formatsOfGroupsAPI).fold[Boolean](
        error => error.head._2.head.message == message,_ => false)
      resToBeSure shouldBe true
    }
    "NOT read json if UTR is < 1 numbers" in {
      val jsonNoChars = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "groupUTR" : {
          |     "UTR" : ""
          |   }
          |}
        """.stripMargin)

      val message = "UTR does not match regex"
      val resToBeSure = jsonNoChars.validate[Groups](formatsOfGroupsAPI).fold[Boolean](
        error => error.head._2.head.message == message,_ => false)
      resToBeSure shouldBe true
    }
    "NOT read json if UTR is is chars and numbers " in {
      val jsonMixOfChars = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "groupUTR" : {
          |     "UTR" : "abc 123"
          |   }
          |}
        """.stripMargin)

      val message = "UTR does not match regex"
      val resToBeSure = jsonMixOfChars.validate[Groups](formatsOfGroupsAPI).fold[Boolean](
        error => error.head._2.head.message == message,_ => false)
      resToBeSure shouldBe true
    }
    "NOT read json if name is > 20 Chars and it does not pass the Des regex after normalisation and trim, also log a message as cohos validation is invalid" in {
      val groupJsonNameOfCompanyInvalidFor20chars = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "&^*%&^*%&^*%&^*%&^*%&^*%&^*%&^*%&^*%&^*%&^*%&^*%&^*%",
          |     "nameType" : "CohoEntered"
          |   }
          |}
        """.stripMargin)

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        groupJsonNameOfCompanyInvalidFor20chars.validate[Groups](formatsOfGroupsAPI).isError shouldBe true
        val expectedMessage = s"[Groups API Reads] name of company invalid when normalised and trimmed this doesn't pass Regex validation"
        logEvents.map(events => (events.getLevel.toString,events.getMessage)).contains(("WARN", expectedMessage)) shouldBe true
      }
    }
    "NOT read json if name is ''" in {
      val groupJsonNameOfCompanyInvalidFor20chars = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "",
          |     "nameType" : "CohoEntered"
          |   }
          |}
        """.stripMargin)

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        groupJsonNameOfCompanyInvalidFor20chars.validate[Groups](formatsOfGroupsAPI).isError shouldBe true
        val expectedMessage = s"[Groups API Reads] name of company invalid when normalised and trimmed this doesn't pass Regex validation"
        logEvents.map(events => (events.getLevel.toString,events.getMessage)).contains(("WARN", expectedMessage)) shouldBe true
      }
    }
    "NOT read json if name is ' '" in {
      val groupJsonNameOfCompanyInvalidFor20chars = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": " ",
          |     "nameType" : "CohoEntered"
          |   }
          |}
        """.stripMargin)

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        groupJsonNameOfCompanyInvalidFor20chars.validate[Groups](formatsOfGroupsAPI).isError shouldBe true
        val expectedMessage = s"[Groups API Reads] name of company invalid when normalised and trimmed this doesn't pass Regex validation"
        logEvents.map(events => (events.getLevel.toString,events.getMessage)).contains(("WARN", expectedMessage)) shouldBe true
      }
    }
    "NOT read json if nameType is not an enum" in {
      val nameTypeWrongEnum = Json.parse(
        """
          |{"groupRelief": true,
          |    "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "FOO"
          |   }
          | }""".stripMargin)
      val message = "String value is not an enum: FOO"
      val resToBeSure = nameTypeWrongEnum.validate[Groups](formatsOfGroupsAPI).fold[Boolean](
        error => error.head._2.head.message == message,_ => false
      )
      resToBeSure shouldBe true
    }
    "NOT read json if addressType is not an enum" in {
      val incorrectAddressType = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "FOO",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "country A",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   }
          |}
        """.stripMargin)
      val message = "String value is not an enum: FOO"
      val resToBeSure = incorrectAddressType.validate[Groups](formatsOfGroupsAPI).fold[Boolean](
        error => error.head._2.head.message == message,_ => false
      )
      resToBeSure shouldBe true
    }
  }
  "API Writes" should {
    "write Groups to json" in {
      Json.toJson[Groups](validGroupsModel)(formatsOfGroupsAPI) shouldBe fullGroupJson
    }
    "write mimimum data in groups model to json" in {
      Json.toJson[Groups](Groups(false,None,None,None))(formatsOfGroupsAPI) shouldBe Json.obj("groupRelief" -> false)
    }
    "write group utr not entered to json" in {
      val expectedFullGroupJsonEmptyUTR = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "country A",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |
          |   }
          |}
        """.stripMargin)
      Json.toJson[Groups](validGroupsModel.copy(groupUTR = Some(GroupUTR(None))))(formatsOfGroupsAPI) shouldBe expectedFullGroupJsonEmptyUTR
    }
  }
  "Mongo reads" should {
    "read full json containing encrypted utr into case class" in {

      val fullGroupJsonEncryptedUTR = Json.parse(
        s"""
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "country A",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |     "UTR" : $encryptedUTR
          |   }
          |}
        """.stripMargin)
      fullGroupJsonEncryptedUTR.as[Groups](formatsOfGroupsMongo) shouldBe validGroupsModel
    }
    "convert nameType To 'Other' if nameType is not an enum in db, and log this as a warn" in {
      val jsonUhOh = Json.parse(
        s"""
           |{
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "foo",
           |     "nameType" : "Uh Oh"
           |   }
           |}
          """.stripMargin)
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        jsonUhOh.as[Groups](formatsOfGroupsMongo) shouldBe Groups(true, Some(GroupCompanyName("foo", GroupCompanyNameEnum.Other)), None, None)
        val expectedMessage = "[Groups Mongo Reads] nameOfCompany.nameType was: Uh Oh, converted to Other"
         logEvents.map(events => (events.getLevel.toString,events.getMessage)).contains(("WARN", expectedMessage)) shouldBe true
      }
    }

    "read address over max length without trimming" in {
      val fullGroupJsonEmptyUTRMaxLengthAddress = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "CohoEntered",
          |       "address" : {
          |         "line1": "123 abc def foo bar wizz288d",
          |         "line2" : "123 abc def foo bar wizz288d",
          |         "line3" : "123 abc def foo bar wizz288d",
          |         "line4" : "abc def 123 abcd199",
          |         "country" : "21 chars not abcd 201",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |
          |   }
          |}
        """.stripMargin)
      val res = fullGroupJsonEmptyUTRMaxLengthAddress.as[Groups](formatsOfGroupsMongo)
      res shouldBe validGroupsModel.copy(
        addressAndType = Some(
          GroupsAddressAndType(
            GroupAddressTypeEnum.CohoEntered,
            BusinessAddress(
              "123 abc def foo bar wizz288d",
              "123 abc def foo bar wizz288d",
              Some("123 abc def foo bar wizz288d"),
              Some("abc def 123 abcd199"),
              Some("ZZ1 1ZZ"),
              Some("21 chars not abcd 201"))
          )),
        groupUTR = Some(GroupUTR(None)))
    }
    "convert addressType to ALF if addressType is not an enum in db, and log this as a warn" in {
      val jsonUhOh = Json.parse(
        s"""
           |{
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "foo",
           |     "nameType" : "Other"
           |   },
           |   "addressAndType" : {
           |     "addressType" : "Uh Oh",
           |       "address" : {
           |         "line1": "1 abc",
           |         "line2" : "2 abc",
           |         "line3" : "3 abc",
           |         "line4" : "4 abc",
           |         "country" : "country A",
           |         "postcode" : "ZZ1 1ZZ"
           |     }
           |   }
           |}
        """.stripMargin)
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        jsonUhOh.as[Groups](formatsOfGroupsMongo) shouldBe Groups(
          true,
          Some(GroupCompanyName("foo", GroupCompanyNameEnum.Other)),
          Some(GroupsAddressAndType(
            GroupAddressTypeEnum.ALF,
            BusinessAddress("1 abc","2 abc",Some("3 abc"),Some("4 abc"),Some("ZZ1 1ZZ"),Some("country A")))),
          None)
        val expectedMessage = "[Groups Mongo Reads] addressType was: Uh Oh, converted to ALF"
        logEvents.map(events => (events.getLevel.toString,events.getMessage)).contains(("WARN", expectedMessage)) shouldBe true
      }
    }
    "read invalid chars successfully" in {
      val encryptedUTRInvalidButCanBeRead = mockInstanceOfCrypto.wts.writes("1234567890ABC TOO MANY CHARS AND NOT JUST NUM")
      val fullGroupJsonEncrypted = Json.parse(
        s"""
           |{
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "Æ",
           |     "nameType" : "Other"
           |   },
           |   "addressAndType" : {
           |     "addressType" : "ALF",
           |       "address" : {
           |         "line1": "Æ",
           |         "line2" : "Æ",
           |         "line3" : "Æ",
           |         "line4" : "Æ",
           |         "country" : "",
           |         "postcode" : "Æ"
           |     }
           |   },
           |   "groupUTR" : {
           |     "UTR" : $encryptedUTRInvalidButCanBeRead
           |   }
           |}
        """.stripMargin)
      val res  = fullGroupJsonEncrypted.as[Groups](formatsOfGroupsMongo)
      res shouldBe Groups(
        groupRelief = true,
        nameOfCompany = Some(GroupCompanyName(
          "Æ",GroupCompanyNameEnum.Other
        )),
        addressAndType = Some(GroupsAddressAndType(
          GroupAddressTypeEnum.ALF,
          BusinessAddress("Æ","Æ",Some("Æ"),Some("Æ"), Some("Æ"),Some("")))),
        groupUTR = Some(GroupUTR(Some("1234567890ABC TOO MANY CHARS AND NOT JUST NUM")))
      )
    }
  }
  "Mongo Writes" should {
    "write full model to json encrypting the UTR" in {
      val fullGroupJsonEncrypted = Json.parse(
        s"""
           |{
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "foo",
           |     "nameType" : "Other"
           |   },
           |   "addressAndType" : {
           |     "addressType" : "ALF",
           |       "address" : {
           |         "line1": "1 abc",
           |         "line2" : "2 abc",
           |         "line3" : "3 abc",
           |         "line4" : "4 abc",
           |         "country" : "country A",
           |         "postcode" : "ZZ1 1ZZ"
           |     }
           |   },
           |   "groupUTR" : {
           |     "UTR" : $encryptedUTR
           |   }
           |}
        """.stripMargin)
      Json.toJson[Groups](validGroupsModel)(formatsOfGroupsMongo) shouldBe fullGroupJsonEncrypted
    }
    "write minimum model to json" in {
      val expected = Json.obj("groupRelief" -> true)
      Json.toJson[Groups](Groups(true,None,None,None))(formatsOfGroupsMongo) shouldBe expected
    }
    "write utr block with empty utr correctly" in {

      val expectedFullGroupJsonEmptyUTR = Json.parse(
        """
          |{
          |   "groupRelief": true,
          |   "nameOfCompany": {
          |     "name": "foo",
          |     "nameType" : "Other"
          |   },
          |   "addressAndType" : {
          |     "addressType" : "ALF",
          |       "address" : {
          |         "line1": "1 abc",
          |         "line2" : "2 abc",
          |         "line3" : "3 abc",
          |         "line4" : "4 abc",
          |         "country" : "country A",
          |         "postcode" : "ZZ1 1ZZ"
          |     }
          |   },
          |   "groupUTR" : {
          |
          |   }
          |}
        """.stripMargin)
      Json.toJson[Groups](validGroupsModel.copy(groupUTR = Some(GroupUTR(None))))(formatsOfGroupsMongo) shouldBe expectedFullGroupJsonEmptyUTR
    }
  }
  "GroupNameListValidator reads" should {
    "successfully read a list of names that are all valid" in {
      val jsonToTest = Json.parse(
        """
          |[
          |   "foo","bar","wizz","bang"
          |]
        """.stripMargin)

      val res = jsonToTest.as[Seq[String]](GroupNameListValidator.formats)
      res shouldBe Seq("foo","bar", "wizz","bang")
    }
    "successfully filter out a name that is not valid" in {
      val jsonToTest = Json.parse(
        """
          |[
          |   "foo","$","wizz","bang"
          |]
        """.stripMargin)

      val res = jsonToTest.as[Seq[String]](GroupNameListValidator.formats)
      res shouldBe Seq("foo","wizz","bang")
    }
    "successfully return nothing when no names are valid" in {
      val jsonToTest = Json.parse(
        """
          |[
          |   "$","$","$","$"
          |]
        """.stripMargin)

      val res = jsonToTest.as[Seq[String]](GroupNameListValidator.formats)
      res shouldBe Seq.empty
    }
    "successfully return original name when normalisation can occur" in {
      val jsonToTest = Json.parse(
        """
          |[
          |   "Æ","Æ","foo","bar"
          |]
        """.stripMargin)
      val res = jsonToTest.as[Seq[String]](GroupNameListValidator.formats)
      res shouldBe Seq("Æ","Æ","foo","bar")
    }
    "successfully return a name over the maximum char limit because this can be trimmed after on des submission" in {
      val jsonToTest = Json.parse(
        """
          |[
          |   "abc edgy foo bar wizz bang wollop fuzz buzz 1234","bar"
          |]
        """.stripMargin)
      val res = jsonToTest.as[Seq[String]](GroupNameListValidator.formats)
      res shouldBe Seq("abc edgy foo bar wizz bang wollop fuzz buzz 1234","bar")
    }
    "can handle non strings in array, these are filtered out" in {
      val jsonToTest = Json.parse(
        """
          |[
          |1, "foo", true
          |]
        """.stripMargin)
      val res = jsonToTest.as[Seq[String]](GroupNameListValidator.formats)
      res shouldBe Seq("foo")
    }
    "can handle an empty array" in {
      val jsonToTest = Json.parse(
        """
          |[
          |]
        """.stripMargin)
      val res = jsonToTest.as[Seq[String]](GroupNameListValidator.formats)
      res shouldBe Seq.empty
    }
    "can handle an array with one empty string in" in {
      val jsonToTest = Json.parse(
        """
          |[
          |""
          |]
        """.stripMargin)
      val res = jsonToTest.as[Seq[String]](GroupNameListValidator.formats)
      res shouldBe Seq.empty
    }
  }
  "GroupNameListValidator writes" should {
    "write an empty seq to array" in {
      Json.toJson(Seq.empty[String])(GroupNameListValidator.formats) shouldBe Json.parse(
        """
          |[
          |]
        """.stripMargin)
    }
    "write a seq of strings to array" in {
      Json.toJson(Seq("foo","bar"))(GroupNameListValidator.formats) shouldBe Json.parse(
        """
          |[
          |"foo","bar"
          |]
        """.stripMargin)
    }
  }
}