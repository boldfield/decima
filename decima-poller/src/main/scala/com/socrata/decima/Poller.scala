package com.socrata.decima

import akka.actor.{ActorSystem, Props}
import com.socrata.decima.actors.Reaper.WatchMe
import com.socrata.decima.actors.SqsDeployProducer.PollDeploysMessage
import com.socrata.decima.actors.{DeployConsumer, Reaper, SqsDeployProducer}
import com.socrata.decima.config.DecimaPollerConfig
import com.socrata.decima.data_access.DeploymentAccessWithPostgres
import com.socrata.decima.database.{ActualPostgresDriver, DeploymentDAO}
import com.socrata.decima.util.DataSourceFromConfig
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.slick.jdbc.JdbcBackend._

object Poller extends App {
  // Load config, setup necessary objects
  val configData = ConfigFactory.load
  val config = new DecimaPollerConfig(configData)
  // Setup database resources
  val cpds = DataSourceFromConfig(config.db)
  val db = Database.forDataSource(cpds)
  val deployAccess = new DeploymentAccessWithPostgres(db, new DeploymentDAO() with ActualPostgresDriver)
  // Setup ActorSystem
  val system = ActorSystem("DecimaSystem")
  // Setup actors
  val reaper = system.actorOf(Props(new Reaper), "reaper")
  val deployConsumer = system.actorOf(Props(new DeployConsumer(deployAccess)), "consumer")
  reaper ! WatchMe(deployConsumer)
  val sqsDeployProducer = system.actorOf(Props(new SqsDeployProducer(config.aws)), "producer")
  reaper ! WatchMe(sqsDeployProducer)
  // Kickoff polling
  system.scheduler.scheduleOnce(0.seconds, sqsDeployProducer, PollDeploysMessage)
}
