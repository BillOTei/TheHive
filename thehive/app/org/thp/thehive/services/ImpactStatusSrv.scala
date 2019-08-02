package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.ImpactStatus

@Singleton
class ImpactStatusSrv @Inject()(implicit db: Database) extends VertexSrv[ImpactStatus, ImpactStatusSteps] {
  override val initialValues: Seq[ImpactStatus] = Seq(
    ImpactStatus("NoImpact"),
    ImpactStatus("WithImpact"),
    ImpactStatus("NotApplicable")
  )

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ImpactStatusSteps = new ImpactStatusSteps(raw)

  override def get(id: String)(implicit graph: Graph): ImpactStatusSteps =
    if (db.isValidId(id)) super.get(id)
    else initSteps.getByName(id)
}

class ImpactStatusSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[ImpactStatus, ImpactStatusSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ImpactStatusSteps = new ImpactStatusSteps(raw)
  override def get(id: String): ImpactStatusSteps =
    if (db.isValidId(id)) getById(id)
    else getByName(id)

  def getById(id: String): ImpactStatusSteps = new ImpactStatusSteps(raw.hasId(id))

  def getByName(name: String): ImpactStatusSteps = new ImpactStatusSteps(raw.has(Key("value") of name))
}
