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

package jobs


import akka.actor.{Actor, ActorLogging, Props}
import jobs.SchedulingActor._
import play.api.Logger
import services._
import services.admin.AdminService

class SchedulingActor extends Actor with ActorLogging {
  import context.dispatcher

  override def receive: Receive = {
    case message : ScheduledMessage[_] =>
      Logger.info(s"Received ${message.getClass.getSimpleName}")
      message.service.invoke
  }
}

object SchedulingActor {
  sealed trait ScheduledMessage[A] {
    val service: ScheduledService[A]
  }

  case class RemoveStaleDocuments(service: AdminService) extends ScheduledMessage[Either[Int, LockResponse]]
  case class UpdateMetrics(service: MetricsService) extends ScheduledMessage[Either[Map[String, Int], LockResponse]]
  case class MissingIncorporation(service: CorporationTaxRegistrationService) extends ScheduledMessage[Either[String, LockResponse]]

  def props: Props = Props[SchedulingActor]
}