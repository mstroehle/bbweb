package org.biobank.service.studies

import akka.actor.ActorRef
import akka.pattern.ask
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Named}
import org.biobank.domain.access._
import org.biobank.domain.study._
import org.biobank.domain.participants.CollectionEventRepository
import org.biobank.domain.user.UserId
import org.biobank.infrastructure.AscendingOrder
import org.biobank.infrastructure.command.CollectionEventTypeCommands._
import org.biobank.infrastructure.event.CollectionEventTypeEvents._
import org.biobank.service._
import org.biobank.service.access.AccessService
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.Future
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._

/**
 * This is the CollectionEventType Aggregate Application Service.
 *
 * Handles the commands to configure Collection Event Types. the commands are forwarded to the
 * CollectionEventType Aggregate Processor.
 *
 */
@ImplementedBy(classOf[CollectionEventTypeServiceImpl])
trait CollectionEventTypeService extends BbwebService {

  def collectionEventTypeWithId(requestUserId:         UserId,
                                studyId:               StudyId,
                                collectionEventTypeId: CollectionEventTypeId)
      : ServiceValidation[CollectionEventType]

  def collectionEventTypeInUse(requestUserId: UserId, collectionEventTypeId: CollectionEventTypeId)
      : ServiceValidation[Boolean]

  def list(requestUserId: UserId,
           studyId:       StudyId,
           filter:        FilterString,
           sort:          SortString): ServiceValidation[Seq[CollectionEventType]]

  def processCommand(cmd: CollectionEventTypeCommand)
      : Future[ServiceValidation[CollectionEventType]]

  def processRemoveCommand(cmd: RemoveCollectionEventTypeCmd)
      : Future[ServiceValidation[Boolean]]

  def snapshotRequest(requestUserId: UserId): ServiceValidation[Unit]

}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class CollectionEventTypeServiceImpl @Inject()(
  @Named("collectionEventType") val processor: ActorRef,
  val accessService:                 AccessService,
  val collectionEventTypeRepository: CollectionEventTypeRepository,
  val studyRepository:               StudyRepository,
  val specimenGroupRepository:       SpecimenGroupRepository,
  val collectionEventRepository:     CollectionEventRepository)
                                            (implicit executionContext: BbwebExecutionContext)
    extends CollectionEventTypeService
    with AccessChecksSerivce
    with ServicePermissionChecks {

  val log: Logger = LoggerFactory.getLogger(this.getClass)

  def collectionEventTypeWithId(requestUserId:         UserId,
                                studyId:               StudyId,
                                collectionEventTypeId: CollectionEventTypeId)
      : ServiceValidation[CollectionEventType] = {
    whenPermittedAndIsMember(requestUserId,
                             PermissionId.StudyRead,
                             Some(studyId),
                             None) { () =>
      withStudy(studyId) { study =>
        collectionEventTypeRepository.withId(study.id, collectionEventTypeId)
      }
    }
  }

  def collectionEventTypeInUse(requestUserId: UserId, id: CollectionEventTypeId): ServiceValidation[Boolean] = {
    collectionEventTypeRepository.getByKey(id).flatMap { ceventType =>
      whenPermittedAndIsMember(requestUserId,
                               PermissionId.StudyRead,
                               Some(ceventType.studyId),
                               None) { () =>
        collectionEventRepository.collectionEventTypeInUse(id).successNel[String]
      }
    }
  }

  def list(requestUserId: UserId,
           studyId:       StudyId,
           filter:        FilterString,
           sort:          SortString): ServiceValidation[Seq[CollectionEventType]] = {
    whenPermittedAndIsMember(requestUserId,
                             PermissionId.StudyRead,
                             Some(studyId),
                             None) { () =>
      val allCeventTypes = collectionEventTypeRepository.allForStudy(studyId).toSet
      val sortStr = if (sort.expression.isEmpty) new SortString("name")
                    else sort

      for {
        study           <- studyRepository.getByKey(studyId)
        ceventTypes     <- CollectionEventTypeFilter.filterCollectionEvents(allCeventTypes, filter)
        sortExpressions <- { QuerySortParser(sortStr).
                              toSuccessNel(ServiceError(s"could not parse sort expression: $sort")) }
        firstSort       <- { sortExpressions.headOption.
                              toSuccessNel(ServiceError("at least one sort expression is required")) }
        sortFunc        <- { CollectionEventType.sort2Compare.get(firstSort.name).
                              toSuccessNel(ServiceError(s"invalid sort field: ${firstSort.name}")) }
      } yield {
        val result = ceventTypes.toSeq.sortWith(sortFunc)
        if (firstSort.order == AscendingOrder) result
        else result.reverse
      }
    }
  }

  def processCommand(cmd: CollectionEventTypeCommand): Future[ServiceValidation[CollectionEventType]] = {
    val v = for {
        validCommand <- cmd match {
          case c: RemoveCollectionEventTypeCmd =>
            ServiceError(s"invalid service call: $cmd, use processRemoveCommand").failureNel[DisabledStudy]
          case c => c.successNel[String]
        }
        study  <- studyRepository.getDisabled(StudyId(cmd.studyId))
      } yield study

    v.fold(
      err => Future.successful(err.failure[CollectionEventType]),
      study => whenPermittedAndIsMemberAsync(UserId(cmd.sessionUserId),
                                             PermissionId.StudyUpdate,
                                             Some(study.id),
                                             None) { () =>
        ask(processor, cmd).mapTo[ServiceValidation[CollectionEventTypeEvent]].map { validation =>
          for {
            event  <- validation
            result <- collectionEventTypeRepository.getByKey(CollectionEventTypeId(event.id))
          } yield result
        }
      }
    )
  }

  def processRemoveCommand(cmd: RemoveCollectionEventTypeCmd): Future[ServiceValidation[Boolean]] = {
    studyRepository.getDisabled(StudyId(cmd.studyId)).fold(
      err => Future.successful(err.failure[Boolean]),
      study => whenPermittedAndIsMemberAsync(UserId(cmd.sessionUserId),
                                             PermissionId.StudyUpdate,
                                             Some(study.id),
                                             None) { () =>
        ask(processor, cmd)
          .mapTo[ServiceValidation[CollectionEventTypeEvent]]
          .map { validation =>
            validation.map(event => true)
          }
      }
    )
  }

  private def withStudy[T](studyId: StudyId)(fn: Study => ServiceValidation[T]): ServiceValidation[T] = {
    for {
      study <- studyRepository.getByKey(studyId)
      result <- fn(study)
    } yield result
  }
}
