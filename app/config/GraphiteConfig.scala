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

package config

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit._

import com.codahale.metrics.{MetricFilter, SharedMetricRegistries}
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import play.api.{Application, Configuration, Logger, Mode}

/**
  * Created by jackie on 16/02/17.
  */
class GraphiteConfig(app: Application) {

  private lazy val env = {
    if (app.mode.equals(Mode.Test)) {"Test"}
    else {app.configuration.getString("run.mode").getOrElse("Dev")}
  }

  private def microserviceMetricsConfig: Option[Configuration] = app.configuration.getConfig(s"$env.microservice.metrics")

  def enabled: Boolean = metricsPluginEnabled && graphitePublisherEnabled

  private def metricsPluginEnabled: Boolean =  app.configuration.getBoolean("metrics.enabled").getOrElse(false)

  private def graphitePublisherEnabled: Boolean =  microserviceMetricsConfig.flatMap(_.getBoolean("graphite.enabled")).getOrElse(false)

  private def registryName = app.configuration.getString("metrics.name").getOrElse("default")

  def startGraphite(): Unit = {
    Logger.info("Graphite metrics enabled, starting the reporter")

    val metricsConfig = microserviceMetricsConfig.getOrElse(throw new Exception("The application does not contain required metrics configuration"))

    val defaultGraphitePort = 2003
    val defaultGraphiteInterval = 10L

    val graphite = new Graphite(new InetSocketAddress(
      metricsConfig.getString("graphite.host").getOrElse("graphite"),
      metricsConfig.getInt("graphite.port").getOrElse(defaultGraphitePort)))

    val prefix = metricsConfig.getString("graphite.prefix").getOrElse(s"tax.${app.configuration.getString("appName")}")

    val reporter = GraphiteReporter.forRegistry(
      SharedMetricRegistries.getOrCreate(registryName))
      .prefixedWith(s"$prefix.${java.net.InetAddress.getLocalHost.getHostName}")
      .convertRatesTo(SECONDS)
      .convertDurationsTo(MILLISECONDS)
      .filter(MetricFilter.ALL)
      .build(graphite)

    reporter.start(metricsConfig.getLong("graphite.interval").getOrElse(defaultGraphiteInterval), SECONDS)
  }

}

