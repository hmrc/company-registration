/*
 * Copyright 2021 HM Revenue & Customs
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

package config

import assets.TestConstants.CorporationTaxRegistration.testTransactionId
import fixtures.CorporationTaxRegistrationFixture
import models.RegistrationStatus._
import models.{ContactDetails, PPOBAddress, TradingDetails, _}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import play.api.{Configuration, Logger}
import repositories._
import services.admin.{AdminService, AdminServiceImpl}
import utils.LogCapturing

import scala.concurrent.Future

class AppStartupJobsSpec extends WordSpec with Matchers with MockitoSugar with LogCapturing
  with CorporationTaxRegistrationFixture with Eventually {

  val mockConfig: Configuration = Configuration.empty
  val mockCTRepository: CorporationTaxRegistrationMongoRepository = mock[CorporationTaxRegistrationMongoRepository]

  val mockAdminService: AdminServiceImpl = mock[AdminServiceImpl]
  val expectedLockedReg = List()
  val expectedRegStats = Map.empty[String, Int]

  "get Company Name" should {

    val regId1 = "reg-1"
    val companyName1 = "ACME1 ltd"
    val companyName2 = "ACME2 ltd"

    val ctDoc1 = validCTRegWithCompanyName(regId1, companyName1)
    val ctDoc2 = validCTRegWithCompanyName(regId1, companyName2)


    "log specific company name relating to reg id passed in" in {
      when(mockCTRepository.retrieveLockedRegIDs())
        .thenReturn(Future.successful(expectedLockedReg))

      when(mockCTRepository.getRegistrationStats)
        .thenReturn(Future.successful(expectedRegStats))
      when(mockCTRepository.retrieveMultipleCorporationTaxRegistration(any()))
        .thenReturn(Future.successful(List(ctDoc1, ctDoc2)))


      val appStartupJobs: AppStartupJobs = new AppStartupJobs {
        override def runEverythingOnStartUp: Future[Unit] = Future.successful(())

        override val config: Configuration = Configuration()
        override val service: AdminService = mockAdminService
        override val ctRepo: CorporationTaxRegistrationMongoRepository = mockCTRepository
      }
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        eventually {
          await(appStartupJobs.getCTCompanyName(regId1))
          val expectedLogs = List(
            s"[CompanyName] status : held - reg Id : $regId1 - Company Name : $companyName1 - Trans ID : $testTransactionId",
            s"[CompanyName] status : held - reg Id : $regId1 - Company Name : $companyName2 - Trans ID : $testTransactionId"
          )
          expectedLogs.diff(logEvents.map(_.getMessage)) shouldBe List.empty
        }
      }
    }

  }
  "fetch Reg Ids" should {

    val dateTime: DateTime = DateTime.parse("2016-10-27T16:28:59.000")

    def corporationTaxRegistration(regId: String,
                                   status: String = SUBMITTED,
                                   transId: String = "transid-1"
                                  ): CorporationTaxRegistration = {
      CorporationTaxRegistration(
        internalId = "testID",
        registrationID = regId,
        formCreationTimestamp = dateTime.toString,
        language = "en",
        companyDetails = Some(CompanyDetails(
          "testCompanyName",
          CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
          PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), None, None, "txid"))),
          "testJurisdiction"
        )),
        contactDetails = Some(ContactDetails(
          Some("0123456789"),
          Some("0123456789"),
          Some("test@email.co.uk")
        )),
        tradingDetails = Some(TradingDetails("false")),
        status = status,
        confirmationReferences = Some(ConfirmationReferences(s"ACKFOR-$regId", transId, Some("PAYREF"), Some("12")))
      )
    }

    val regIds = Seq("regId1", "regId2", "regId3")

    "log specific company name relating to reg id passed in" in {

      when(mockCTRepository.retrieveLockedRegIDs())
        .thenReturn(Future.successful(expectedLockedReg))

      when(mockCTRepository.getRegistrationStats)
        .thenReturn(Future.successful(expectedRegStats))
      when(mockCTRepository.findBySelector(mockCTRepository.regIDSelector("regId1")))
        .thenReturn(Future.successful(Some(corporationTaxRegistration("regId1", "TestStatus", "transid-1"))))
      when(mockCTRepository.findBySelector(mockCTRepository.regIDSelector("regId2")))
        .thenReturn(Future.successful(Some(corporationTaxRegistration("regId2", "TestStatus2", "transid-2"))))
      when(mockCTRepository.findBySelector(mockCTRepository.regIDSelector("regId3")))
        .thenReturn(Future.successful(None))

      val appStartupJobs: AppStartupJobs = new AppStartupJobs {
        override val config: Configuration = mockConfig

        override val service: AdminService = mockAdminService
        override val ctRepo: CorporationTaxRegistrationMongoRepository = mockCTRepository

        override def runEverythingOnStartUp: Future[Unit] = Future.successful(())
      }

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        eventually {
          await(appStartupJobs.fetchDocInfoByRegId(regIds))

          logEvents.size shouldBe 3
        }
      }
    }
  }
}
