package org.biobank.controllers.participants

import javax.inject.{Inject, Singleton}
import org.biobank.controllers._
import org.biobank.domain.participants.{ParticipantId, CollectionEvent, CollectionEventId}
import org.biobank.infrastructure.command.CollectionEventCommands._
import org.biobank.service.{AuthToken, PagedQuery, PagedResults}
import org.biobank.service.participants.CollectionEventsService
import org.biobank.service.users.UsersService
import play.api.libs.json._
import play.api.{ Environment, Logger }
import scala.language.reflectiveCalls
import scala.concurrent.{ExecutionContext, Future}
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._

@Singleton
class CollectionEventsController @Inject() (val action:         BbwebAction,
                                            val env:          Environment,
                                            val authToken:    AuthToken,
                                            val usersService: UsersService,
                                            val service:      CollectionEventsService)
                                        (implicit ec: ExecutionContext)
    extends CommandController
    with JsonController {

  val log = Logger(this.getClass)

  private val PageSizeMax = 10

  def get(ceventId: String) =
    action(parse.empty) { implicit request =>
      validationReply(service.get(ceventId))
    }

  def list(participantId: ParticipantId,
           sortMaybe:     Option[String],
           pageMaybe:     Option[Int],
           pageSizeMaybe: Option[Int],
           orderMaybe:    Option[String]) =
    action.async(parse.empty) { implicit request =>
      Future {
        val sort     = sortMaybe.fold { "visitNumber" } { s => s }
        val page     = pageMaybe.fold { 1 } { p => p }
        val pageSize = pageSizeMaybe.fold { 5 } { ps => ps }
        val order    = orderMaybe.fold { "asc" } { o => o }

        log.debug(s"""|CollectionEventsController:list: participantId/$participantId,
                      |  sort/$sort, page/$page, pageSize/$pageSize, order/$order""".stripMargin)

        val pagedQuery = PagedQuery(page, pageSize, order)

        val validation = for {
            sortFunc    <- CollectionEvent.sort2Compare.get(sort).toSuccessNel(ControllerError(s"invalid sort field: $sort"))
            sortOrder   <- pagedQuery.getSortOrder
            cevents     <- service.list(participantId, sortFunc, sortOrder)
            page        <- pagedQuery.getPage(PageSizeMax, cevents.size)
            pageSize    <- pagedQuery.getPageSize(PageSizeMax)
            results     <- PagedResults.create(cevents, page, pageSize)
          } yield results

        validation.fold(
          err     => BadRequest(err.list.toList.mkString),
          results =>  Ok(results)
        )
      }
    }

  def getByVisitNumber(participantId: ParticipantId, visitNumber: Int) =
    action(parse.empty) { implicit request =>
      validationReply(service.getByVisitNumber(participantId, visitNumber))
    }

  def add(participantId: ParticipantId) =
    commandActionAsync(Json.obj("participantId" -> participantId)) { cmd: AddCollectionEventCmd =>
      processCommand(cmd)
    }

  def updateVisitNumber(ceventId: CollectionEventId) =
    commandActionAsync(Json.obj("id" -> ceventId)) { cmd: UpdateCollectionEventVisitNumberCmd =>
      processCommand(cmd)
    }

  def updateTimeCompleted(ceventId: CollectionEventId) =
    commandActionAsync(Json.obj("id" -> ceventId)) { cmd: UpdateCollectionEventTimeCompletedCmd =>
      processCommand(cmd)
    }

  def addAnnotation(ceventId: CollectionEventId) =
    commandActionAsync(Json.obj("id" -> ceventId)) { cmd: UpdateCollectionEventAnnotationCmd =>
      processCommand(cmd)
    }

  def removeAnnotation(ceventId: CollectionEventId,
                       annotTypeId:   String,
                       ver:           Long) =
    action.async(parse.empty) { implicit request =>
      val cmd = RemoveCollectionEventAnnotationCmd(userId           = request.authInfo.userId.id,
                                                   id               = ceventId.id,
                                                   expectedVersion  = ver,
                                                   annotationTypeId = annotTypeId)
      processCommand(cmd)
    }

  def remove(participantId: ParticipantId, ceventId: CollectionEventId, ver: Long) =
    action.async(parse.empty) { implicit request =>
      val cmd = RemoveCollectionEventCmd(userId          = request.authInfo.userId.id,
                                         id              = ceventId.id,
                                         participantId   = participantId.id,
                                         expectedVersion = ver)
      val future = service.processRemoveCommand(cmd)
      validationReply(future)
    }

  private def processCommand(cmd: CollectionEventCommand) = {
    val future = service.processCommand(cmd)
    validationReply(future)
  }

}
