package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.dto.v0.{InputObservable, OutputObservable}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, ObservableSrv, ObservableSteps, ObservableTypeSrv, OrganisationSrv}
import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class ObservableCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseSrv: CaseSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl {
  import ObservableConversion._

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "observable"
  override val publicProperties: List[PublicProperty[_, _]] = observableProperties(observableSrv) ::: metaProperties[ObservableSteps]
  override val initialQuery: Query =
    Query.init[ObservableSteps]("listObservable", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.observables)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, ObservableSteps, PagedResult[(RichObservable, JsObject)]](
    "page",
    FieldsParser[OutputParam], {
      case (OutputParam(from, to, withStats), observableSteps, authContext) =>
        observableSteps
          .richPage(from, to, withTotal = true) {
            case o if withStats =>
              o.richObservableWithCustomRenderer(observableStatsRenderer(authContext, db, observableSteps.graph)).raw
            case o =>
              o.richObservable.raw.map(_ -> JsObject.empty)
          }
    }
  )
  override val outputQuery: Query = Query.output[(RichObservable, JsObject), OutputObservable]

  def create(caseId: String): Action[AnyContent] =
    entryPoint("create artifact")
      .extract("artifact", FieldsParser[InputObservable])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputObservable: InputObservable = request.body("artifact")
        for {
          case0 <- caseSrv
            .get(caseId)
            .can(Permissions.manageCase)
            .getOrFail()
          observableType      <- observableTypeSrv.getOrFail(inputObservable.dataType)
          observablesWithData <- inputObservable.data.toTry(d => observableSrv.create(inputObservable, observableType, d, inputObservable.tags, Nil))
          observableWithAttachment <- inputObservable
            .attachment
            .map(a => observableSrv.create(inputObservable, observableType, a, inputObservable.tags, Nil))
            .flip
          createdObservables <- (observablesWithData ++ observableWithAttachment).toTry { richObservables =>
            caseSrv
              .addObservable(case0, richObservables.observable)
              .map(_ => richObservables)
          }
        } yield Results.Created(Json.toJson(createdObservables.map(_.toJson)))
      }

  def get(observableId: String): Action[AnyContent] =
    entryPoint("get observable")
      .authRoTransaction(db) { _ => implicit graph =>
        observableSrv
          .get(observableId)
          //            .availableFor(request.organisation)
          .richObservable
          .getOrFail()
          .map { observable =>
            Results.Ok(observable.toJson)
          }
      }

  def update(observableId: String): Action[AnyContent] =
    entryPoint("update observable")
      .extract("observable", FieldsParser.update("observable", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("observable")
        observableSrv
          .update(
            _.get(observableId).can(Permissions.manageCase),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def findSimilar(obsId: String): Action[AnyContent] =
    entryPoint("find similar")
      .authRoTransaction(db) { _ => implicit graph =>
        val observables = observableSrv
          .get(obsId)
          .similar
          .richObservableWithCustomRenderer(observableLinkRenderer(db, graph))
          .toList
          .map {
            case (org, parent) => org.toJson.as[JsObject] ++ parent
          }

        Success(Results.Ok(JsArray(observables)))
      }

  def bulkUpdate: Action[AnyContent] =
    entryPoint("bulk update")
      .extract("input", FieldsParser.update("observable", publicProperties))
      .extract("ids", FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val properties: Seq[PropertyUpdater] = request.body("input")
        val ids: Seq[String]                 = request.body("ids")
        ids
          .toTry { id =>
            observableSrv
              .update(_.get(id).can(Permissions.manageCase), properties)
          }
          .map(_ => Results.NoContent)
      }

  def delete(obsId: String): Action[AnyContent] =
    entryPoint("delete")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          observable <- observableSrv
            .get(obsId)
            .can(Permissions.manageCase)
            .getOrFail()
          _ = observableSrv.get(observable).remove()
//          _ <- auditSrv.deleteObservable(obs, Json.obj("id" → obs._id, "type" → obs.`type`, "message" → obs.message))(graph, request.authContext)
        } yield Results.NoContent
      }
}
