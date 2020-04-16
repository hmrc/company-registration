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

package config

import auth.{AuthClientConnector, CryptoSCRS, CryptoSCRSImpl}
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import connectors._
import controllers._
import controllers.admin.{AdminController, AdminControllerImpl}
import controllers.test._
import jobs._
import repositories._
import services._
import services.admin.{AdminService, AdminServiceImpl}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.{AlertLogging, AlertLoggingImpl}

class Module extends AbstractModule {

  override def configure(): Unit = {
    bindConfig()
    bindRepositories()
    bindServices()
    bindConnectors()
    bindControllers()
    bindJobs()
  }

  private def bindJobs() = {
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("missing-incorporation-job")).to(classOf[MissingIncorporationJob]).asEagerSingleton()
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("remove-stale-documents-job")).to(classOf[RemoveStaleDocumentsJob]).asEagerSingleton()
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("metrics-job")).to(classOf[MetricsJob]).asEagerSingleton()
    bind(classOf[AppStartupJobs]).to(classOf[AppStartupJobsImpl]).asEagerSingleton()
    bind(classOf[Startup]).asEagerSingleton()
  }

  private def bindConfig() = {
    bind(classOf[MicroserviceAppConfig]).to(classOf[MicroserviceAppConfigImpl]).asEagerSingleton()
    bind(classOf[HttpClient]).to(classOf[WSHttpSCRSImpl]).asEagerSingleton()
    bind(classOf[CryptoSCRS]).to(classOf[CryptoSCRSImpl]).asEagerSingleton()
    bind(classOf[AlertLogging]).to(classOf[AlertLoggingImpl]).asEagerSingleton()
  }

  private def bindControllers() = {
    bind(classOf[EmailController]).to(classOf[EmailControllerImpl]).asEagerSingleton()
    bind(classOf[AccountingDetailsController]).to(classOf[AccountingDetailsControllerImpl]).asEagerSingleton()
    bind(classOf[ProcessIncorporationsController]).to(classOf[ProcessIncorporationsControllerImpl]).asEagerSingleton()
    bind(classOf[CompanyDetailsController]).to(classOf[CompanyDetailsControllerImpl]).asEagerSingleton()
    bind(classOf[ContactDetailsController]).to(classOf[ContactDetailsControllerImpl]).asEagerSingleton()
    bind(classOf[CorporationTaxRegistrationController]).to(classOf[CorporationTaxRegistrationControllerImpl]).asEagerSingleton()
    bind(classOf[TradingDetailsController]).to(classOf[TradingDetailsControllerImpl]).asEagerSingleton()
    bind(classOf[UserAccessController]).to(classOf[UserAccessControllerImpl]).asEagerSingleton()
    bind(classOf[AdminController]).to(classOf[AdminControllerImpl]).asEagerSingleton()
    bind(classOf[HeldController]).to(classOf[HeldControllerImpl]).asEagerSingleton()
    bind(classOf[AccountingDetailsController]).to(classOf[AccountingDetailsControllerImpl]).asEagerSingleton()
    bind(classOf[TestEndpointController]).to(classOf[TestEndpointControllerImpl]).asEagerSingleton()
    bind(classOf[SubmissionController]).to(classOf[SubmissionControllerImpl]).asEagerSingleton()
    bind(classOf[FeatureSwitchController]).to(classOf[FeatureSwitchControllerImpl]).asEagerSingleton()
    bind(classOf[GroupsController]).to(classOf[GroupsControllerImpl]).asEagerSingleton()
    bind(classOf[TakeoverDetailsController]).to(classOf[TakeoverDetailsControllerImpl]).asEagerSingleton()

  }

  private def bindServices() {
    bind(classOf[AdminService]).to(classOf[AdminServiceImpl]).asEagerSingleton()
    bind(classOf[AuditService]).to(classOf[AuditServiceImpl]).asEagerSingleton()
    bind(classOf[AccountingDetailsService]).to(classOf[AccountingDetailsServiceImpl]).asEagerSingleton()
    bind(classOf[MetricsService]).to(classOf[MetricsServiceImpl]).asEagerSingleton()
    bind(classOf[CompanyDetailsService]).to(classOf[CompanyDetailsServiceImpl]).asEagerSingleton()
    bind(classOf[ContactDetailsService]).to(classOf[ContactDetailsServiceImpl]).asEagerSingleton()
    bind(classOf[GroupsService]).to(classOf[GroupsServiceImpl]).asEagerSingleton()
    bind(classOf[CorporationTaxRegistrationService]).to(classOf[CorporationTaxRegistrationServiceImpl]).asEagerSingleton()
    bind(classOf[CorporationTaxRegistrationService]).to(classOf[CorporationTaxRegistrationServiceImpl]).asEagerSingleton()
    bind(classOf[EmailService]).to(classOf[EmailServiceImpl]).asEagerSingleton()
    bind(classOf[SendEmailService]).to(classOf[SendEmailServiceImpl]).asEagerSingleton()
    bind(classOf[MetricsService]).to(classOf[MetricsServiceImpl]).asEagerSingleton()
    bind(classOf[PrepareAccountService]).to(classOf[PrepareAccountServiceImpl]).asEagerSingleton()
    bind(classOf[ThrottleService]).to(classOf[ThrottleServiceImpl]).asEagerSingleton()
    bind(classOf[TradingDetailsService]).to(classOf[TradingDetailsServiceImpl]).asEagerSingleton()
    bind(classOf[UserAccessService]).to(classOf[UserAccessServiceImpl]).asEagerSingleton()
    bind(classOf[ProcessIncorporationService]).to(classOf[ProcessIncorporationServiceImpl]).asEagerSingleton()
    bind(classOf[SubmissionService]).to(classOf[SubmissionServiceImpl]).asEagerSingleton()

  }

  private def bindConnectors() = {
    bind(classOf[IncorporationInformationConnector]).to(classOf[IncorporationInformationConnectorImpl]).asEagerSingleton()
    bind(classOf[BusinessRegistrationConnector]).to(classOf[BusinessRegistrationConnectorImpl]).asEagerSingleton()
    bind(classOf[DesConnector]).to(classOf[DesConnectorImpl]).asEagerSingleton()
    bind(classOf[AuthConnector]).to(classOf[AuthClientConnector]).asEagerSingleton()
    bind(classOf[SendEmailConnector]).to(classOf[SendEmailConnectorImpl]).asEagerSingleton()
    bind(classOf[DesConnector]).to(classOf[DesConnectorImpl]).asEagerSingleton()
    bind(classOf[IncorporationCheckAPIConnector]).to(classOf[IncorporationCheckAPIConnectorImpl]).asEagerSingleton()
  }

  private def bindRepositories() = {
    bind(classOf[LockRepositoryProvider]).to(classOf[LockRepositoryProviderImpl]).asEagerSingleton()
    bind(classOf[SequenceMongoRepo]).asEagerSingleton()
    bind(classOf[ThrottleMongoRepo]).asEagerSingleton()
    bind(classOf[Repositories]).asEagerSingleton()
  }
}