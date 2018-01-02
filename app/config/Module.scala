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

package config

import com.google.inject.AbstractModule
import connectors.{IncorporationInformationConnector, IncorporationInformationConnectorImpl}
import controllers._
import controllers.admin.{AdminController, AdminControllerImpl}
import controllers.test.{EmailController, EmailControllerImp}
import services._
import services.admin.{AdminService, AdminServiceImpl}
import uk.gov.hmrc.play.config.inject.{DefaultServicesConfig, ServicesConfig}

class Module extends AbstractModule {

  override def configure(): Unit = {

    //config
    bind(classOf[MicroserviceAppConfig]).to(classOf[MicroserviceAppConfigImpl])
    bind(classOf[ServicesConfig]).to(classOf[DefaultServicesConfig])

    // controllers
    bind(classOf[EmailController]) to classOf[EmailControllerImp]
    bind(classOf[AccountingDetailsController]) to classOf[AccountingDetailsControllerImp]
    bind(classOf[ProcessIncorporationsController]) to classOf[ProcessIncorporationsControllerImp]
    bind(classOf[CompanyDetailsController]) to classOf[CompanyDetailsControllerImp]
    bind(classOf[ContactDetailsController]) to classOf[ContactDetailsControllerImp]
    bind(classOf[CorporationTaxRegistrationController]) to classOf[CorporationTaxRegistrationControllerImp]
    bind(classOf[TradingDetailsController]) to classOf[TradingDetailsControllerImp]
    bind(classOf[UserAccessController]) to classOf[UserAccessControllerImp]
    bind(classOf[AdminController]) to classOf[AdminControllerImpl]

    bindServices()

    //connectors
    bind(classOf[IncorporationInformationConnector]).to(classOf[IncorporationInformationConnectorImpl])
  }


  private def bindServices() {
    bind(classOf[AdminService]).to(classOf[AdminServiceImpl])
    bind(classOf[MetricsService]) to classOf[MetricsServiceImp]
    bind(classOf[CompanyDetailsService]) to classOf[CompanyDetailsServiceImp]
    bind(classOf[ContactDetailsService]) to classOf[ContactDetailsServiceImp]
    bind(classOf[EmailService]) to classOf[EmailServiceImp]
    bind(classOf[MetricsService]) to classOf[MetricsServiceImp]
    bind(classOf[PrepareAccountService]) to classOf[PrepareAccountServiceImp]
    bind(classOf[ThrottleService]) to classOf[ThrottleServiceImp]
    bind(classOf[TradingDetailsService]) to classOf[TradingDetailsServiceImp]
    bind(classOf[UserAccessService]) to classOf[UserAccessServiceImp]
  }
}
