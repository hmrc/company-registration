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

import auth.{AuthClientConnector, AuthClientConnectorImpl}
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import connectors._
import controllers._
import controllers.admin.{AdminController, AdminControllerImpl}
import controllers.test._
import jobs._
import services._
import services.admin.{AdminService, AdminServiceImpl}
import uk.gov.hmrc.play.config.inject.{DefaultServicesConfig, ServicesConfig}
import uk.gov.hmrc.play.scheduling.ScheduledJob

class Module extends AbstractModule {

  override def configure(): Unit = {
    bindJobs()
    bindConfig()
    bindControllers()
    bindServices()
    bindConnectors()
  }

  private def bindJobs() = {
    bind(classOf[CheckSubmissionJob]).to(classOf[CheckSubmissionJobImpl])
    bind(classOf[MissingIncorporationJob]).to(classOf[MissingIncorporationJobImpl])
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("remove-stale-documents-job")).to(classOf[RemoveStaleDocumentsJobImpl])
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("metrics-job")).to(classOf[MetricsJobImpl])
  }

  private def bindConfig() = {
    bind(classOf[MicroserviceAppConfig]).to(classOf[MicroserviceAppConfigImpl])
    bind(classOf[ServicesConfig]).to(classOf[DefaultServicesConfig])
  }

  private def bindControllers() = {
    bind(classOf[EmailController]) to classOf[EmailControllerImpl]
    bind(classOf[AccountingDetailsController]) to classOf[AccountingDetailsControllerImpl]
    bind(classOf[ProcessIncorporationsController]) to classOf[ProcessIncorporationsControllerImpl]
    bind(classOf[CompanyDetailsController]) to classOf[CompanyDetailsControllerImpl]
    bind(classOf[ContactDetailsController]) to classOf[ContactDetailsControllerImpl]
    bind(classOf[CorporationTaxRegistrationController]) to classOf[CorporationTaxRegistrationControllerImpl]
    bind(classOf[TradingDetailsController]) to classOf[TradingDetailsControllerImpl]
    bind(classOf[UserAccessController]) to classOf[UserAccessControllerImpl]
    bind(classOf[AdminController]) to classOf[AdminControllerImpl]
    bind(classOf[HeldController]) to classOf[HeldControllerImpl]
    bind(classOf[AccountingDetailsController]) to classOf[AccountingDetailsControllerImpl]
    bind(classOf[SubmissionCheckController]) to classOf[SubmissionCheckControllerImpl]
    bind(classOf[TestEndpointController]) to classOf[TestEndpointControllerImpl]
  }

  private def bindServices() {
    bind(classOf[AdminService]).to(classOf[AdminServiceImpl])
    bind(classOf[AuditService]).to(classOf[AuditServiceImpl])
    bind(classOf[AccountingDetailsService]).to(classOf[AccountingDetailsServiceImpl])
    bind(classOf[MetricsService]) to classOf[MetricsServiceImpl]
    bind(classOf[CompanyDetailsService]) to classOf[CompanyDetailsServiceImpl]
    bind(classOf[ContactDetailsService]) to classOf[ContactDetailsServiceImpl]
    bind(classOf[CorporationTaxRegistrationService]) to classOf[CorporationTaxRegistrationServiceImpl]
    bind(classOf[CorporationTaxRegistrationService]) to classOf[CorporationTaxRegistrationServiceImpl]
    bind(classOf[EmailService]) to classOf[EmailServiceImpl]
    bind(classOf[HeldSubmissionController]) to classOf[HeldSubmissionControllerImpl]
    bind(classOf[SendEmailService]) to classOf[SendEmailServiceImpl]
    bind(classOf[MetricsService]) to classOf[MetricsServiceImpl]
    bind(classOf[PrepareAccountService]) to classOf[PrepareAccountServiceImpl]
    bind(classOf[ThrottleService]) to classOf[ThrottleServiceImpl]
    bind(classOf[TradingDetailsService]) to classOf[TradingDetailsServiceImpl]
    bind(classOf[UserAccessService]) to classOf[UserAccessServiceImpl]
    bind(classOf[RegistrationHoldingPenService]) to classOf[RegistrationHoldingPenServiceImpl]
  }

  private def bindConnectors() = {
    bind(classOf[IncorporationInformationConnector]).to(classOf[IncorporationInformationConnectorImpl])
    bind(classOf[BusinessRegistrationConnector]).to(classOf[BusinessRegistrationConnectorImpl])
    bind(classOf[DesConnector]).to(classOf[DesConnectorImpl])
    bind(classOf[AuthClientConnector]).to(classOf[AuthClientConnectorImpl])
    bind(classOf[AuthConnector]).to(classOf[AuthConnectorImpl])
    bind(classOf[SendEmailConnector]).to(classOf[SendEmailConnectorImpl])
    bind(classOf[DesConnector]).to(classOf[DesConnectorImpl])
    bind(classOf[IncorporationCheckAPIConnector]).to(classOf[IncorporationCheckAPIConnectorImpl])
  }
}
