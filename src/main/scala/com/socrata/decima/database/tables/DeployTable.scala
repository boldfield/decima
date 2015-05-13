package com.socrata.decima.database.tables

import com.socrata.decima.database.DatabaseDriver
import java.sql.Timestamp

import com.socrata.decima.models.{DeployForCreate, Deploy}
import com.socrata.decima.util.TimeUtils

import scala.slick.jdbc.{GetResult, StaticQuery}

trait DeployTable {
  self: DatabaseDriver =>

  import self.driver.simple._ // scalastyle:ignore import.grouping

  case class DeployRow(id: Long,
                       service: String,
                       environment: String,
                       version: String,
                       serviceSha: String,
                       dockerSha: Option[String],
                       configuration: Option[String],
                       deployedBy: String,
                       deployMethod: String,
                       deployedAt: Timestamp)

  class Deploys(tag: Tag) extends Table[DeployRow](tag, "deploys") {
    // scalastyle:off
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc) // primary key column
    def service = column[String]("service")
    def environment = column[String]("environment")
    def version = column[String]("version")
    def serviceSha = column[String]("service_sha")
    def dockerSha = column[Option[String]]("docker_sha")
    def configuration = column[Option[String]]("configuration", O.DBType("text"))
    def deployedBy = column[String]("deployed_by")
    def deployMethod = column[String]("deploy_method")
    def deployedAt = column[Timestamp]("deployed_at")
    def * = (id, service, environment, version, serviceSha, dockerSha, configuration, deployedBy, deployMethod, deployedAt) <> (DeployRow.tupled,
      DeployRow.unapply)
    // scalastyle:on
  }

  def rowToModelDeploy(row: DeployRow): Deploy = {
    Deploy(row.id,
      row.service,
      row.environment,
      row.version,
      row.serviceSha,
      row.dockerSha,
      row.configuration,
      row.deployedBy,
      row.deployMethod,
      TimeUtils.toJodaDateTime(row.deployedAt))
  }

  def modelToRowDeploy(deploy: DeployForCreate): DeployRow = {
    DeployRow(0,
              deploy.service,
              deploy.environment,
              deploy.version,
              deploy.serviceSha,
              deploy.dockerSha,
              deploy.configuration,
              deploy.deployedBy,
              deploy.deployMethod,
              TimeUtils.toSqlTimestamp(TimeUtils.now))
  }

  val deployTable = TableQuery[Deploys]

  implicit val getDeployResult = GetResult(r => DeployRow(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

  val currentDeploymentQuery = StaticQuery.queryNA[DeployRow]( """
                                  select a.id, a.service, a.environment,
                                    b.version, b.service_sha, b.docker_sha, b.configuration, b.deployed_by,
                                    b.deploy_method, b.deployed_at
                                  from (
                                    select distinct deploys.service, deploys.environment, max(deploys.id) as id
                                    from deploys
                                    group by deploys.service, deploys.environment) a,
                                  deploys b
                                  where a.id = b.id""")

  object DeployCompiledQueries {
    private def lookupByIdQuery(id: Column[Long]) = deployTable.filter(d => d.id === id)

    val lookup = Compiled(lookupByIdQuery _)
  }
}
