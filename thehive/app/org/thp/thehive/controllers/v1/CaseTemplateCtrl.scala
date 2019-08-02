package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{PropertyUpdater, PublicProperty}
import org.thp.thehive.dto.v1.InputCaseTemplate
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{CaseTemplateSrv, OrganisationSrv, UserSrv}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class CaseTemplateCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    caseTemplateSrv: CaseTemplateSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv
) {

  import CaseTemplateConversion._
  import CustomFieldConversion._
  import TaskConversion._

  val publicProperties: List[PublicProperty[_, _]] = caseTemplateProperties(caseTemplateSrv)

  def create: Action[AnyContent] =
    entryPoint("create case template")
      .extract("caseTemplate", FieldsParser[InputCaseTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputCaseTemplate: InputCaseTemplate = request.body("caseTemplate")
        for {
          organisation <- organisationSrv.getOrFail(request.organisation)
          tasks        = inputCaseTemplate.tasks.map(fromInputTask)
          customFields = inputCaseTemplate.customFieldValue.map(fromInputCustomField)
          richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate, organisation, inputCaseTemplate.tags, tasks, customFields)
        } yield Results.Created(richCaseTemplate.toJson)
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("get case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        caseTemplateSrv
          .get(caseTemplateNameOrId)
          .visible
          .richCaseTemplate
          .getOrFail()
          .map(richCaseTemplate => Results.Ok(richCaseTemplate.toJson))
      }

  def list: Action[AnyContent] =
    entryPoint("list case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val caseTemplates = caseTemplateSrv
          .initSteps
          .visible
          .richCaseTemplate
          .map(_.toJson)
          .toList
        Success(Results.Ok(Json.toJson(caseTemplates)))
      }

  def update(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("update case template")
      .extract("caseTemplate", FieldsParser.update("caseTemplate", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("caseTemplate")
        caseTemplateSrv
          .update(
            _.get(caseTemplateNameOrId)
              .can(Permissions.manageCaseTemplate),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }
}
