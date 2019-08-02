package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class ProfileSrv @Inject()(implicit val db: Database) extends VertexSrv[Profile, ProfileSteps] {
  lazy val admin: Profile with Entity = db.roTransaction(graph => getOrFail("admin")(graph)).get
  override val initialValues: Seq[Profile] = Seq(
    Profile("admin", Permissions.all),
    Profile("analyst", Set(Permissions.manageCase, Permissions.manageAlert, Permissions.manageTask)),
    Profile("read-only", Set.empty)
  )

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ProfileSteps = new ProfileSteps(raw)

  override def get(id: String)(implicit graph: Graph): ProfileSteps =
    if (db.isValidId(id)) super.get(id)
    else initSteps.getByName(id)
}

@EntitySteps[Profile]
class ProfileSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Profile, ProfileSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ProfileSteps = new ProfileSteps(raw)

  override def get(id: String): ProfileSteps =
    if (db.isValidId(id)) getById(id)
    else getByName(id)

  def getById(id: String): ProfileSteps = new ProfileSteps(raw.hasId(id))

  def getByName(name: String): ProfileSteps = new ProfileSteps(raw.has(Key("name") of name))
}
