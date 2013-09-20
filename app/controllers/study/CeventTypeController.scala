package controllers.study

import controllers._
import service.commands._
import service.{ ServiceComponent, TopComponentImpl }
import domain._
import domain.study._
import views._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.Messages
import play.Logger
import securesocial.core.SecureSocial

import scalaz._
import Scalaz._

/**
 * This object is needed since it handles the Add and Update commands. It is used by the form to access the
 * data for adding or updating a CollectionEventType.
 *
 * @param collectionEventTypeId
 * @param version
 * @param studyId
 * @param name
 * @param description
 * @param recurring
 * @param specimenGroupData
 * @param annotationTypeData
 */
case class CeventTypeFormObject(
  collectionEventTypeId: String, version: Long, studyId: String, studyName: String, name: String,
  description: Option[String], recurring: Boolean,
  specimenGroupData: List[CollectionEventTypeSpecimenGroup],
  annotationTypeData: List[CollectionEventTypeAnnotationType]) {

  def getAddCmd: AddCollectionEventTypeCmd = {
    AddCollectionEventTypeCmd(studyId, name, description, recurring,
      specimenGroupData.toSet, annotationTypeData.toSet)
  }

  def getUpdateCmd: UpdateCollectionEventTypeCmd = {
    UpdateCollectionEventTypeCmd(
      collectionEventTypeId, Some(version), studyId, name, description, recurring,
      specimenGroupData.toSet, annotationTypeData.toSet)
  }
}

object CollectionEventTypeSelections {
  val annotationValueTypes = Seq("" -> Messages("biobank.form.selection.default")) ++
    AnnotationValueType.values.map(x =>
      x.toString -> Messages("biobank.enumaration.annotation.value.type." + x.toString)).toSeq
}

object CeventTypeController extends Controller with SecureSocial {

  lazy val studyService = WebComponent.studyService

  val ceventTypeForm = Form(
    mapping(
      "collectionEventTypeId" -> text,
      "version" -> longNumber,
      "studyId" -> text,
      "studyName" -> text,
      "name" -> nonEmptyText,
      "description" -> optional(text),
      "recurring" -> boolean,
      "specimenGroupData" -> list(mapping(
        "specimenGroupId" -> text,
        "specimenGroupCount" -> number,
        "specimenGroupAmount" -> bigDecimal)(
          CollectionEventTypeSpecimenGroup.apply)(CollectionEventTypeSpecimenGroup.unapply)),
      "annotationTypeData" -> list(mapping(
        "annotationTypeId" -> text,
        "annotationTypeRequired" -> boolean)(
          CollectionEventTypeAnnotationType.apply)(CollectionEventTypeAnnotationType.unapply)))(
        CeventTypeFormObject.apply)(CeventTypeFormObject.unapply))

  private def specimenGroupInfo(studyId: String) = {
    studyService.specimenGroupsForStudy(studyId).map(x => (x.id.id, x.name, x.units)).toSeq
  }

  private def annotationTypeInfo(studyId: String) = {
    studyService.collectionEventAnnotationTypesForStudy(studyId).map(x => (x.id.id, x.name)).toSeq
  }

  /**
   * Add an attribute type.
   */
  def addCeventType(studyId: String, studyName: String) = SecuredAction { implicit request =>
    StudyController.validateStudy(studyId)(study => {
      Ok(html.study.ceventtype.add(ceventTypeForm, AddFormType(), studyId, study.name,
        specimenGroupInfo(studyId), annotationTypeInfo(studyId)))
    })
  }

  def addCeventTypeSubmit = SecuredAction { implicit request =>
    ceventTypeForm.bindFromRequest.fold(
      formWithErrors => {
        // studyId and studyName are hidden values in the form, they should always be present
        val studyId = formWithErrors("studyId").value.getOrElse("")
        val studyName = formWithErrors("studyName").value.getOrElse("")

        BadRequest(html.study.ceventtype.add(formWithErrors, AddFormType(), studyId, studyName,
          specimenGroupInfo(studyId), annotationTypeInfo(studyId)))
      },
      submittedForm => {
        implicit val userId = new UserId(request.user.identityId.userId)
        val studyId = submittedForm.studyId
        val studyName = submittedForm.studyName

        Async {
          studyService.addCollectionEventType(submittedForm.getAddCmd).map(validation =>
            validation match {
              case Failure(x) =>
                if (x.head.contains("name already exists")) {
                  val form = ceventTypeForm.fill(submittedForm).withError("name",
                    Messages("biobank.study.collection.event.type.form.error.name"))
                  Logger.debug("bad name: " + form)
                  BadRequest(html.study.ceventtype.add(form, AddFormType(),
                    studyId, studyName, specimenGroupInfo(studyId), annotationTypeInfo(studyId)))
                } else {
                  throw new Error(x.head)
                }
              case Success(ceventType) =>
                Redirect(routes.StudyController.showStudy(studyId)).flashing(
                  "success" -> Messages("biobank.study.collection.event.type.added", ceventType.name))
            })
        }
      })
  }

  def updateCeventType(studyId: String, studyName: String, ceventTypeId: String) = SecuredAction { implicit request =>
    studyService.collectionEventTypeWithId(studyId, ceventTypeId) match {
      case Failure(x) => throw new Error(x.head)
      case Success(ceventType) =>
        val form = ceventTypeForm.fill(CeventTypeFormObject(
          ceventType.id.id, ceventType.version, studyId, studyName, ceventType.name,
          ceventType.description, ceventType.recurring, ceventType.specimenGroupData.toList,
          ceventType.annotationTypeData.toList))
        Ok(html.study.ceventtype.add(form, UpdateFormType(), studyId, studyName,
          specimenGroupInfo(studyId), annotationTypeInfo(studyId)))
    }
  }

  def updateCeventTypeSubmit = SecuredAction { implicit request =>
    ceventTypeForm.bindFromRequest.fold(
      formWithErrors => {
        // studyId and studyName are hidden values in the form, they should always be present
        val studyId = formWithErrors("studyId").value.getOrElse("")
        val studyName = formWithErrors("studyName").value.getOrElse("")

        BadRequest(html.study.ceventtype.add(
          formWithErrors, UpdateFormType(), studyId, studyName, specimenGroupInfo(studyId),
          annotationTypeInfo(studyId)))
      },
      submittedForm => {
        implicit val userId = new UserId(request.user.identityId.userId)
        val studyId = submittedForm.studyId
        val studyName = submittedForm.studyName

        Async {
          studyService.updateCollectionEventType(submittedForm.getUpdateCmd).map(validation =>
            validation match {
              case Failure(x) =>
                if (x.head.contains("name already exists")) {
                  val form = ceventTypeForm.fill(submittedForm).withError("name",
                    Messages("biobank.study.collection.event.type.form.error.name"))
                  BadRequest(html.study.ceventtype.add(
                    form, UpdateFormType(), studyId, studyName, specimenGroupInfo(studyId),
                    annotationTypeInfo(studyId)))
                } else {
                  throw new Error(x.head)
                }
              case Success(ceventType) =>
                Redirect(routes.StudyController.showStudy(studyId)).flashing(
                  "success" -> Messages("biobank.study.collection.event.type.updated", ceventType.name))
            })
        }
      })
  }

  def removeCeventType(studyId: String,
    studyName: String,
    ceventTypeId: String) = SecuredAction { implicit request =>
    studyService.collectionEventTypeWithId(studyId, ceventTypeId) match {
      case Failure(x) => throw new Error(x.head)
      case Success(ceventType) =>
        Ok(html.study.ceventtype.removeConfirm(studyId, studyName, ceventType))
    }
  }

  val ceventTypeDeleteForm = Form(
    tuple(
      "studyId" -> text,
      "studyName" -> text,
      "ceventTypeId" -> text))

  def removeCeventTypeSubmit = SecuredAction { implicit request =>
    ceventTypeDeleteForm.bindFromRequest.fold(
      formWithErrors => {
        throw new Error(formWithErrors.globalErrors.mkString(","))
      },
      submittedForm => {
        val studyId = submittedForm._1
        val studyName = submittedForm._2
        val ceventTypeId = submittedForm._3

        studyService.collectionEventTypeWithId(studyId, ceventTypeId) match {
          case Failure(x) => throw new Error(x.head)
          case Success(ceventType) =>
            Async {
              implicit val userId = new UserId(request.user.identityId.userId)
              studyService.removeCollectionEventType(
                RemoveCollectionEventTypeCmd(
                  ceventType.id.id, ceventType.versionOption, ceventType.studyId.id)).map(validation =>
                  validation match {
                    case Success(ceventType) =>
                      Redirect(routes.StudyController.showStudy(studyId)).flashing(
                        "success" -> Messages("biobank.study.collection.event.type.removed", ceventType.name))
                    case Failure(x) =>
                      throw new Error(x.head)
                  })
            }
        }
      })
  }
}
