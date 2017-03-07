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

import com.google.inject.AbstractModule
import config.{AppStartup, DefaultAppStartup}
import controllers.test._
import controllers._
import jobs.{CheckSubmissionJob, CheckSubmissionJobImp}
import services._
import uk.gov.hmrc.play.config.inject.{DefaultServicesConfig, ServicesConfig}

/**
  * Created by jackie on 16/02/17.
  */
class Module extends AbstractModule {

  val gridFSNAme = "cr"

  override def configure(): Unit = {
    bind(classOf[AppStartup])
      .to(classOf[DefaultAppStartup])
      .asEagerSingleton()

    bind(classOf[EmailController]) to classOf[EmailControllerImp]
    bind(classOf[HeldSubmissionController]) to classOf[HeldSubmissionControllerImp]
    bind(classOf[SubmissionCheckController]) to classOf[SubmissionCheckControllerImp]
    bind(classOf[TestEndpointController]) to classOf[TestEndpointControllerImp]
    bind(classOf[AccountingDetailsController]) to classOf[AccountingDetailsControllerImp]
    bind(classOf[CompanyDetailsController]) to classOf[CompanyDetailsControllerImp]
    bind(classOf[ContactDetailsController]) to classOf[ContactDetailsControllerImp]
    bind(classOf[CorporationTaxRegistrationController]) to classOf[CorporationTaxRegistrationControllerImp]
    bind(classOf[TradingDetailsController]) to classOf[TradingDetailsControllerImp]
    bind(classOf[UserAccessController]) to classOf[UserAccessControllerImp]
    bind(classOf[SubmissionCheckController]) to classOf[SubmissionCheckControllerImp]
    bind(classOf[CheckSubmissionJob]) to classOf[CheckSubmissionJobImp]
    bind(classOf[ServicesConfig]) to classOf[DefaultServicesConfig]

    bindServices()

  }

  private def bindServices() {
    bind(classOf[MetricsService]) to classOf[MetricsServiceImp]
    bind(classOf[AccountingDetailsService]) to classOf[AccountingDetailsServiceImp]
    bind(classOf[CompanyDetailsService]) to classOf[CompanyDetailsServiceImp]
    bind(classOf[ContactDetailsService]) to classOf[ContactDetailsServiceImp]
    bind(classOf[CorporationTaxRegistrationService]) to classOf[CorporationTaxRegistrationServiceImp]
    bind(classOf[EmailService]) to classOf[EmailServiceImp]
    bind(classOf[MetricsService]) to classOf[MetricsServiceImp]
    bind(classOf[PrepareAccountService]) to classOf[PrepareAccountServiceImp]
    bind(classOf[RegistrationHoldingPenService]) to classOf[RegistrationHoldingPenServiceImp]
    bind(classOf[ThrottleService]) to classOf[ThrottleServiceImp]
    bind(classOf[TradingDetailsService]) to classOf[TradingDetailsServiceImp]
    bind(classOf[UserAccessService]) to classOf[UserAccessServiceImp]

  }

}
