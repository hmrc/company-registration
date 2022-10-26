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

package itutil

import config.LangConstants
import models._
import play.api.libs.json._

import java.time.Instant


object ItTestConstants {

  implicit class AppendableJsValue(jsValue: JsObject) {
    def plusOptional[A](optValue: (String, Option[A]))(implicit writes: Writes[A]): JsObject = optValue match {
      case (label, Some(value)) => jsValue ++ Json.obj(label -> writes.writes(value))
      case _ => jsValue
    }
  }

  object CorporationTaxRegistration {

    val testInternalId = "tiid"
    val testRegistrationId = "0123456789"
    val testStatus = "held"
    val testFormCreationTimestamp = "2001-12-31T12:00:00Z"
    val testLanguage = LangConstants.english
    val testCreatedTime: Long = 1485859623928L
    val testLastSignedIn: Long = 1485859613928L

    val testAcknowledgementReference = "BRCT12345678910"
    val testTransactionId = "TX1"
    val testPaymentReference = "PY1"
    val testPaymentAmount = "12.00"

    val testConfirmationReferences = Json.obj(fields =
      "acknowledgement-reference" -> testAcknowledgementReference,
      "transaction-id" -> testTransactionId,
      "payment-reference" -> testPaymentReference,
      "payment-amount" -> testPaymentAmount
    )

    val testAccountingDateStatus = "FUTURE_DATE"
    val testStartDateOfBusiness = "2019-12-31"

    val testAccountingDetails = Json.obj(fields =
      "accountingDateStatus" -> testAccountingDateStatus,
      "startDateOfBusiness" -> testStartDateOfBusiness
    )

    val testPPOBAddrType = "MANUAL"
    val testPPOBLine1 = "ppob line 1"
    val testPPOBLine2 = "ppob line 2"
    val testPPOBLine3 = "ppob line 3"
    val testPPOBLine4 = "ppob line 4"
    val testPPOBPostcode = "ZZ1 1ZZ"
    val testPPOBCountry = "ppob country"

    val testPPOBAddress = Json.obj(fields =
      "addressType" -> testPPOBAddrType,
      "address" -> Json.obj(fields =
        "addressLine1" -> testPPOBLine1,
        "addressLine2" -> testPPOBLine2,
        "addressLine3" -> testPPOBLine3,
        "addressLine4" -> testPPOBLine4,
        "postCode" -> testPPOBPostcode,
        "country" -> testPPOBCountry,
        "txid" -> testTransactionId
      )
    )

    val testPremises = "premises"
    val testRegOffLine1 = "reg office line 1"
    val testRegOffLine2 = "reg office line 2"
    val testRegOffCountry = "reg office country"
    val testRegOffLocality = "reg office locality"
    val testRegOffPostcode = "AB1 1AB"
    val testRegOffPoBox = "reg office po box"
    val testRegOffRegion = "reg office region"

    val testRegisteredOfficeAddress = Json.obj(
      "premises" -> testPremises,
      "address_line_1" -> testRegOffLine1,
      "address_line_2" -> testRegOffLine2,
      "country" -> testRegOffCountry,
      "locality" -> testRegOffLocality,
      "po_box" -> testRegOffPoBox,
      "postal_code" -> testRegOffPostcode,
      "region" -> testRegOffRegion
    )

    def fullCorpTaxRegJson(optAccountingDetails: Option[JsObject] = None,
                           optTakeoverDetails: Option[JsObject] = None,
                           createdTime: Instant = Instant.now,
                           lastSignedIn: Instant = Instant.now
                          ): JsObject =
      Json.obj(fields =
        "internalId" -> testInternalId,
        "registrationID" -> testRegistrationId,
        "status" -> testStatus,
        "formCreationTimestamp" -> testFormCreationTimestamp,
        "language" -> testLanguage,
        "confirmationReferences" -> testConfirmationReferences,
        "createdTime" -> createdTime,
        "lastSignedIn" -> lastSignedIn
      ) plusOptional (
        "accountingDetails" -> optAccountingDetails
        ) plusOptional (
        "takeoverDetails" -> optTakeoverDetails
        )

    def corpTaxRegModel(optConfirmationDetails: Option[ConfirmationReferences] = None,
                        optAccountingDetails: Option[AccountingDetails] = None,
                        optTakeoverDetails: Option[TakeoverDetails] = None,
                        createdTime: Instant = Instant.now,
                        lastSignedIn: Instant = Instant.now
                       ): CorporationTaxRegistration =
      models.CorporationTaxRegistration(
        internalId = testInternalId,
        registrationID = testRegistrationId,
        status = testStatus,
        formCreationTimestamp = testFormCreationTimestamp,
        language = testLanguage,
        confirmationReferences = optConfirmationDetails,
        createdTime = createdTime,
        lastSignedIn = lastSignedIn,
        accountingDetails = optAccountingDetails,
        takeoverDetails = optTakeoverDetails
      )
  }

  object TakeoverDetails {

    case class TestAddress(line1: String,
                           optLine2: Option[JsValue],
                           optLine3: Option[JsValue],
                           optLine4: Option[JsValue],
                           optPostcode: Option[JsValue],
                           optCountry: Option[JsValue]
                          ) {

      def toJson: JsObject =
        Json.obj(fields =
          "line1" -> line1
        ) plusOptional (
          "line2" -> optLine2
          ) plusOptional (
          "line3" -> optLine3
          ) plusOptional (
          "line4" -> optLine4
          ) plusOptional (
          "postcode" -> optPostcode
          ) plusOptional (
          "country" -> optCountry
          )
    }

    val testBusinessName = "Business name"

    val testTakeoverAddrLine1 = "Takeover 1"
    val testTakeoverAddrLine2 = "Takeover 2"
    val testTakeoverAddrLine3 = "Takeover 3"
    val testTakeoverAddrLine4 = "Takeover 4"
    val testTakeoverPostcode = "ZZ1 1ZZ"
    val testTakeoverCountry = "takeover country"

    val testTakeoverAddress = TestAddress(
      line1 = testTakeoverAddrLine1,
      optLine2 = Some(JsString(testTakeoverAddrLine2)),
      optLine3 = Some(JsString(testTakeoverAddrLine3)),
      optLine4 = Some(JsString(testTakeoverAddrLine4)),
      optPostcode = Some(JsString(testTakeoverPostcode)),
      optCountry = Some(JsString(testTakeoverCountry))
    )

    val testTakeoverAddressModel = Address(
      line1 = testTakeoverAddrLine1,
      line2 = testTakeoverAddrLine2,
      line3 = Some(testTakeoverAddrLine3),
      line4 = Some(testTakeoverAddrLine4),
      postcode = Some(testTakeoverPostcode),
      country = Some(testTakeoverCountry)
    )

    val testPrevOwnerName = "Prev owner name"

    val testPrevOwnerAddrLine1 = "Prev owner 1"
    val testPrevOwnerAddrLine2 = "Prev owner 2"
    val testPrevOwnerAddrLine3 = "Prev owner 3"
    val testPrevOwnerAddrLine4 = "Prev owner 4"
    val testPrevOwnerPostcode = "AB1 3YZ"
    val testPrevOwnerCountry = "Prev owner country"

    val testPrevOwnerAddress = TestAddress(
      line1 = testPrevOwnerAddrLine1,
      optLine2 = Some(JsString(testPrevOwnerAddrLine2)),
      optLine3 = Some(JsString(testPrevOwnerAddrLine3)),
      optLine4 = Some(JsString(testPrevOwnerAddrLine4)),
      optPostcode = Some(JsString(testPrevOwnerPostcode)),
      optCountry = Some(JsString(testPrevOwnerCountry))
    )

    val testPrevOwnerAddressModel = Address(
      line1 = testPrevOwnerAddrLine1,
      line2 = testPrevOwnerAddrLine2,
      line3 = Some(testPrevOwnerAddrLine3),
      line4 = Some(testPrevOwnerAddrLine4),
      postcode = Some(testPrevOwnerPostcode),
      country = Some(testPrevOwnerCountry)
    )

    val testTakeoverDetails = Json.obj(fields =
      "replacingAnotherBusiness" -> true,
      "businessName" -> testBusinessName,
      "businessTakeoverAddress" -> testTakeoverAddress.toJson,
      "prevOwnersName" -> testPrevOwnerName,
      "prevOwnersAddress" -> testPrevOwnerAddress.toJson
    )

    val testTakeoverDetailsModel = models.TakeoverDetails(
      replacingAnotherBusiness = true,
      businessName = Some(testBusinessName),
      businessTakeoverAddress = Some(testTakeoverAddressModel),
      prevOwnersName = Some(testPrevOwnerName),
      prevOwnersAddress = Some(testPrevOwnerAddressModel)
    )
  }

}
