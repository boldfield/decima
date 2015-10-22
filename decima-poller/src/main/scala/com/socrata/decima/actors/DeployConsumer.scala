package com.socrata.decima.actors

import akka.actor.{Actor, ActorLogging}
import com.socrata.decima.data_access.DeploymentAccess
import com.socrata.decima.data_access.DeploymentAccess.{DeployCreated, DuplicateDeploy}
import com.socrata.decima.models.Deploy

object DeployConsumer {

  trait DeployMessageMetadata
  case class DeployMessage(deploy: Deploy, metadata: DeployMessageMetadata)
  case class DeployProcessedMessage(metadata: DeployMessageMetadata)
}

class DeployConsumer(deploymentAccess: DeploymentAccess) extends Actor with ActorLogging {
  // scalastyle:ignore import.grouping
  import DeployConsumer._

  override def preStart(): Unit = {
    log.info("Starting DeployConsumer")
  }

  override def postStop(): Unit = {
    log.info("Shutting down DeployConsumer")
  }

  def receive: PartialFunction[Any, Unit] = {
    case DeployMessage(deploy, metadata) =>
      log.debug(s"Saving deploy event: $deploy")
      sender ! deploymentAccess.createDeploy(deploy).map {
        case DeployCreated(createdDeploy) =>
          log.info(s"Successfully saved deploy $createdDeploy")
          DeployProcessedMessage(metadata)
        case DuplicateDeploy(service, environment, time) =>
          log.warning(s"Duplicate deploy event for service '$service' in environment '$environment' at '$time'")
          // A duplicate event is typically OK; it is likely a result of
          // re-reading a previously read message
          DeployProcessedMessage(metadata)
      }.get
  }
}
