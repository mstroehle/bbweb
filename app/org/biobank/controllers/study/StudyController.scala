package org.biobank.controllers.study

import org.biobank.controllers._
import org.biobank.service._
import org.biobank.infrastructure._
import org.biobank.infrastructure.command.StudyCommands._
import org.biobank.service.{ ServiceComponent, TopComponentImpl }
import org.biobank.domain._
import org.biobank.domain.study.{ Study, StudyId, DisabledStudy }
import org.biobank.domain.AnatomicalSourceType._
import org.biobank.domain.PreservationType._
import org.biobank.domain.PreservationTemperatureType._
import org.biobank.domain.SpecimenType._
import views._

import scala.concurrent._
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api._
import play.api.cache.Cache
import play.api.Play.current
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.Messages
import play.Logger
import securesocial.core.SecureSocial
import securesocial.core.SecuredRequest

import scalaz._
import scalaz.Scalaz._

object StudyTab extends Enumeration {
  type StudyTab = Value
  val Summary = Value("tab-summary")
  val Participants = Value("tab-participants")
  val Specimens = Value("tab-specimens")
  val CollectionEvents = Value("tab-collection-events")
  val ProcessingEvents = Value("tab-processing-events")
}

import StudyTab._

case class StudyFormObject(
  studyId: String, version: Long, name: String, description: Option[String]) {

  def getAddCmd: AddStudyCmd = {
    AddStudyCmd(name, description)
  }

  def getUpdateCmd: UpdateStudyCmd = {
    UpdateStudyCmd(studyId, some(version), name, description)
  }
}

object StudyController extends Controller with SecureSocial {

  private val studyService = ApplicationComponent.studyService

  implicit val studyWrites = new Writes[Study] {

    def writes(study: Study) = Json.obj(
      "id" -> study.id.id,
      "version" -> study.version,
      "name" -> study.name,
      "description" -> study.description
    )

  }

  implicit val addStudyCmdReads: Reads[AddStudyCmd] = (
    (JsPath \ "name").read[String](minLength[String](2)) and
      (JsPath \ "description").readNullable[String](minLength[String](2))
  )(AddStudyCmd.apply _)

  def list = Action {
    val json = Json.toJson(studyService.getAll.toList)
    Ok(json)
  }

  def createStudy = Action.async(BodyParsers.parse.json) { request =>
    val cmdResult = request.body.validate[AddStudyCmd]
    cmdResult.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" ->"KO", "message" -> JsError.toFlatJson(errors))))
      },
      cmd => {
        Logger.info(s"$cmd")
        val future = studyService.addStudy(cmd)(null)
        future.map { validation =>
          validation match {
            case Success(event) =>
              Ok(Json.obj("status" ->"OK", "message" -> (s"Study saved: ${cmd.name}.") ))
            case Failure(err) =>
              BadRequest(Json.obj("status" ->"KO", "message" -> err.list.mkString(", ")))
          }
        }
      }
    )
  }

  //---

  private val studyForm = Form(
    mapping(
      "studyId" -> text,
      "version" -> longNumber,
      "name" -> nonEmptyText,
      "description" -> play.api.data.Forms.optional(text))(StudyFormObject.apply)(StudyFormObject.unapply))

  private def studyBreadcrumbs = {
    Map((Messages("biobank.study.plural") -> null))
  }

  def validateStudy(
    id: String,
    errorHeading: String)(f: Study => Result)(
      implicit request: WrappedRequest[AnyContent]): Result = {
    studyService.getStudy(id) match {
      case Failure(err) =>
        if (err.list.mkString(", ").contains("study does not exist")) {
          BadRequest(html.serviceError(
            errorHeading,
            Messages("biobank.study.error"),
            studyBreadcrumbs,
            routes.StudyController.index))
        } else {
          throw new Error(err.list.mkString(", "))
        }
      case Success(study) => f(study)
    }
  }

  def validateStudy(id: String)(f: Study => Result)(implicit request: WrappedRequest[AnyContent]): Result = {
    validateStudy(id, Messages("biobank.study.error.heading"))(f)
  }

  def selectedStudyTab(tab: StudyTab): Unit = {
    Cache.set("study.tab", tab)
    Logger.debug("selected tab: " + Cache.get("study.tab"))
  }

  def selectedStudyTab: StudyTab = {
    Cache.getAs[StudyTab.Value]("study.tab").getOrElse(StudyTab.Summary)
  }

  def index = SecuredAction { implicit request =>
    // get list of studies the user has access to
    //
    // FIXME add paging and filtering -> see "computer-databse" Play sample app
    val studies = studyService.getAll
    Ok(views.html.study.index(studies))
  }

  // this is the entry point to the study page from external pages: i.e. study list page
  def summary(id: String) = SecuredAction { implicit request =>
    validateStudy(id)(study => {
      selectedStudyTab(StudyTab.Summary)
      Redirect(routes.StudyController.showStudy(study.id.id))
    })
  }

  // this is the entry point to the study page for sub-pages: i.e. add specimen group, add collection
  // event type
  def showStudy(id: String) = SecuredAction { implicit request =>
    validateStudy(id)(study => {
      Ok(html.study.showStudy(study, selectedStudyTab))
    })
  }

  // Ajax call to view the "Specimens" tab
  def summaryTab(studyId: String, studyName: String) = SecuredAction(ajaxCall = true) { implicit request =>
    validateStudy(studyId)(study => {
      Logger.debug("summaryTab: ajax call")

      // returns no content since we only want to update the cache with the selected tab
      selectedStudyTab(StudyTab.Summary)
      NoContent
    })
  }

  // Ajax call to view the "Specimens" tab
  def participantsTab(studyId: String, studyName: String) = SecuredAction(ajaxCall = true) { implicit request =>
    validateStudy(studyId)(study => {
      Logger.debug("participantsTab: ajax call")

      // returns no content since we only want to update the cache with the selected tab
      selectedStudyTab(StudyTab.Participants)
      val counts = Map(
        ("participants" -> "<i>to be implemented</i>"),
        ("collection.events" -> "<i>to be implemented</i>"),
        ("specimen.count" -> "<i>to be implemented</i>"))
      val participantAnnotTypes = studyService.participantAnnotationTypesForStudy(studyId);
      Ok(html.study.participantsTab(study, counts, participantAnnotTypes))
    })
  }

  // Ajax call to view the "Specimens" tab
  def specimensTab(studyId: String, studyName: String) = SecuredAction(ajaxCall = true) { implicit request =>
    validateStudy(studyId)(study => {
      Logger.debug("specimensTab: ajax call")

      selectedStudyTab(StudyTab.Specimens)
      val specimenGroups = studyService.specimenGroupsForStudy(studyId)
      Ok(html.study.specimensTab(studyId, studyName, specimenGroups))
    })
  }

  // Ajax call to view the "Collection Events" tab
  def ceventsTab(studyId: String, studyName: String) = SecuredAction(ajaxCall = true) { implicit request =>
    validateStudy(studyId)(study => {
      Logger.debug("ceventsTab: ajax call")

      selectedStudyTab(StudyTab.CollectionEvents)
      val ceventTypes = studyService.collectionEventTypesForStudy(studyId)
      val specimenGroups = studyService.specimenGroupsForStudy(studyId).map(
        x => (x.id.id, x.name, x.units)).toSeq
      val annotationTypes = studyService.collectionEventAnnotationTypesForStudy(studyId)
      Ok(html.study.ceventsTab(studyId, studyName, ceventTypes, specimenGroups,
        annotationTypes))
    })
  }

  // Ajax call to view the "Collection Events" tab
  def peventsTab(studyId: String, studyName: String) = SecuredAction(ajaxCall = true) { implicit request =>
    validateStudy(studyId)(study => {
      Logger.debug("peventsTab: ajax call")
      selectedStudyTab(StudyTab.ProcessingEvents)
      val annotationTypes = studyService.specimenLinkAnnotationTypesForStudy(studyId)
      Ok(html.study.peventsTab(studyId, studyName, annotationTypes))
    })
  }

  /**
   * Add a study.
   */
  def addStudy() = SecuredAction { implicit request =>
    Ok(html.study.addStudy(studyForm, AddFormType(), ""))
  }

  def addStudySubmit() = SecuredAction.async { implicit request =>
    studyForm.bindFromRequest.fold(
      formWithErrors => {
        Future(BadRequest(html.study.addStudy(formWithErrors, AddFormType(), "")))
      },
      formObj => {
        // FIXME: handle timeout here
        //
        // see http://www.playframework.com/documentation/2.2.x/ScalaAsync
        // "Handling time-outs"
        //

        implicit val userId = UserId(request.user.identityId.userId)
        studyService.addStudy(formObj.getAddCmd).map(study => study match {
          case Success(study) =>
            Redirect(routes.StudyController.summary(study.id)).flashing(
              "success" -> Messages("biobank.study.added", study.name))
          case Failure(x) =>
            if (x.head.contains("study with name already exists")) {
              val form = studyForm.fill(formObj).withError("name",
                Messages("biobank.study.form.error.name"))
              BadRequest(html.study.addStudy(form, AddFormType(), ""))
            } else {
              throw new Error(x.head)
            }
        })
      })
  }

  /**
   * Update a study.
   */
  def updateStudy(studyId: String) = SecuredAction { implicit request =>
    validateStudy(studyId)(study => {
      Logger.debug("study version: " + study.version)
      Ok(html.study.addStudy(
        studyForm.fill(StudyFormObject(studyId, study.version, study.name, study.description)),
        UpdateFormType(),
        studyId))
    })
  }

  def updateStudySubmit(studyId: String) = SecuredAction.async { implicit request =>
    studyForm.bindFromRequest.fold(
      formWithErrors =>
        Future(BadRequest(html.study.addStudy(
          formWithErrors, UpdateFormType(), studyId))), {
        case formObj => {
          // FIXME: handle timeout here
          //
          // see http://www.playframework.com/documentation/2.2.x/ScalaAsync
          // "Handling time-outs"
          //

          implicit val userId = UserId(request.user.identityId.userId)
          studyService.updateStudy(formObj.getUpdateCmd).map(study =>
            study match {
              case Failure(x) =>
                if (x.head.contains("study with name already exists")) {
                  val form = studyForm.fill(formObj).withError("name",
                    Messages("biobank.study.form.error.name"))
                  BadRequest(html.study.addStudy(form, UpdateFormType(), studyId))
                } else {
                  throw new Error(x.head)
                }
              case Success(study) =>
                Redirect(routes.StudyController.summary(study.id)).flashing(
                  "success" -> Messages("biobank.study.updated", study.name))
            })
        }
      })
  }
}

