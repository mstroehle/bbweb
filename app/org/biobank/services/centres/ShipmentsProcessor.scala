package org.biobank.services.centres

import akka.actor._
import akka.persistence.{RecoveryCompleted, SnapshotOffer, SaveSnapshotSuccess, SaveSnapshotFailure}
import com.github.ghik.silencer.silent
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import org.biobank.domain.LocationId
import org.biobank.domain.centres._
import org.biobank.domain.participants.{SpecimenId, SpecimenRepository}
import org.biobank.infrastructure.commands.ShipmentCommands._
import org.biobank.infrastructure.commands.ShipmentSpecimenCommands._
import org.biobank.infrastructure.events.EventUtils
import org.biobank.infrastructure.events.ShipmentEvents._
import org.biobank.infrastructure.events.ShipmentSpecimenEvents._
import org.biobank.services.{Processor, ServiceError, ServiceValidation, SnapshotWriter}
import play.api.libs.json._
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._
import scalaz._

object ShipmentsProcessor {

  def props: Props = Props[ShipmentsProcessor]

  final case class SnapshotState(shipments: Set[Shipment],
                                 shipmentSpecimens: Set[ShipmentSpecimen])

  implicit val snapshotStateFormat: Format[SnapshotState] = Json.format[SnapshotState]

}

/**
 * Handles commands related to shipments.
 */
class ShipmentsProcessor @Inject() (val shipmentRepository:         ShipmentRepository,
                                    val shipmentSpecimenRepository: ShipmentSpecimenRepository,
                                    val centreRepository:           CentreRepository,
                                    val specimenRepository:         SpecimenRepository,
                                    val snapshotWriter:             SnapshotWriter)
    extends Processor
    with ShipmentValidations
    with ShipmentConstraints {
  import ShipmentsProcessor._
  import org.biobank.CommonValidations._

  override def persistenceId: String = "shipments-processor-id"


  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private var replyTo: Option[ActorRef] = None

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  val receiveRecover: Receive = {
    case event: ShipmentEvent =>
      log.debug(s"ShipmentsProcessor: receiveRecover: $event")
      event.eventType match {
        case et: ShipmentEvent.EventType.Added                  => applyAddedEvent(event)
        case et: ShipmentEvent.EventType.CourierNameUpdated     => applyCourierNameUpdatedEvent(event)
        case et: ShipmentEvent.EventType.TrackingNumberUpdated  => applyTrackingNumberUpdatedEvent(event)
        case et: ShipmentEvent.EventType.FromLocationUpdated    => applyFromLocationUpdatedEvent(event)
        case et: ShipmentEvent.EventType.ToLocationUpdated      => applyToLocationUpdatedEvent(event)
        case et: ShipmentEvent.EventType.Created                => applyCreatedEvent(event)
        case et: ShipmentEvent.EventType.Packed                 => applyPackedEvent(event)
        case et: ShipmentEvent.EventType.Sent                   => applySentEvent(event)
        case et: ShipmentEvent.EventType.Received               => applyReceivedEvent(event)
        case et: ShipmentEvent.EventType.Unpacked               => applyUnpackedEvent(event)
        case et: ShipmentEvent.EventType.Completed              => applyCompletedEvent(event)
        case et: ShipmentEvent.EventType.Lost                   => applyLostEvent(event)
        case et: ShipmentEvent.EventType.Removed                => applyRemovedEvent(event)
        case et: ShipmentEvent.EventType.SkippedToSentState     => applySkippedToSentStateEvent(event)
        case et: ShipmentEvent.EventType.SkippedToUnpackedState => applySkippedToUnpackedStateEvent(event)

        case event => log.error(s"event not handled: $event")
      }

    case event: ShipmentSpecimenEvent => event.eventType match {
      case et: ShipmentSpecimenEvent.EventType.Added            => applySpecimenAddedEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Removed          => applySpecimenRemovedEvent(event)
      case et: ShipmentSpecimenEvent.EventType.ContainerUpdated => applySpecimenContainerAddedEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Present          => applySpecimenPresentEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Received         => applySpecimenReceivedEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Missing          => applySpecimenMissingEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Extra            => applySpecimenExtraEvent(event)

      case event => log.error(s"event not handled: $event")
    }


    case SnapshotOffer(_, snapshotFilename: String) =>
      applySnapshot(snapshotFilename)

    case RecoveryCompleted =>
      log.debug(s"ShipmentsProcessor: recovery completed")
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Throw"))
  val receiveCommand: Receive = {

    case cmd: AddShipmentCmd =>
      process(addCmdToEvent(cmd))(applyAddedEvent)

    case cmd: UpdateShipmentCourierNameCmd     =>
      processUpdateCmdOnCreated(cmd, updateCourierNameCmdToEvent, applyCourierNameUpdatedEvent)

    case cmd: UpdateShipmentTrackingNumberCmd =>
      processUpdateCmdOnCreated(cmd, updateTrackingNumberCmdToEvent, applyTrackingNumberUpdatedEvent)

    case cmd: UpdateShipmentFromLocationCmd =>
      processUpdateCmdOnCreated(cmd, updateFromLocationCmdToEvent, applyFromLocationUpdatedEvent)

    case cmd: UpdateShipmentToLocationCmd =>
      processUpdateCmdOnCreated(cmd, updateToLocationCmdToEvent, applyToLocationUpdatedEvent)

    case cmd: CreatedShipmentCmd =>
      processUpdateCmd(cmd, createdCmdToEvent, applyCreatedEvent)

    case cmd: PackShipmentCmd =>
      processUpdateCmd(cmd, packCmdToEvent, applyPackedEvent)

    case cmd: SendShipmentCmd =>
      processUpdateCmd(cmd, sendCmdToEvent, applySentEvent)

    case cmd: ReceiveShipmentCmd =>
      processUpdateCmd(cmd, receiveCmdToEvent, applyReceivedEvent)

    case cmd: UnpackShipmentCmd =>
      processUpdateCmd(cmd, unpackCmdToEvent, applyUnpackedEvent)

    case cmd: CompleteShipmentCmd =>
      processUpdateCmd(cmd, completeCmdToEvent, applyCompletedEvent)

    case cmd: LostShipmentCmd =>
      processUpdateCmd(cmd, lostCmdToEvent, applyLostEvent)

    case cmd: ShipmentSkipStateToSentCmd =>
      processUpdateCmdOnCreated(cmd, skipStateToSentCmdToEvent, applySkippedToSentStateEvent)

    case cmd: ShipmentSkipStateToUnpackedCmd =>
      processUpdateCmd(cmd, skipStateToUnpackedCmdToEvent, applySkippedToUnpackedStateEvent)

    case cmd: ShipmentRemoveCmd =>
      processUpdateCmdOnCreated(cmd, removeCmdToEvent, applyRemovedEvent)

    case cmd: ShipmentAddSpecimensCmd =>
      process(addSpecimenCmdToEvent(cmd))(applySpecimenAddedEvent)

    case cmd: ShipmentSpecimenRemoveCmd =>
      processSpecimenUpdateCmd(cmd, removeSpecimenCmdToEvent, applySpecimenRemovedEvent)

    case cmd: ShipmentSpecimenUpdateContainerCmd =>
      processSpecimenUpdateCmd(cmd, updateSpecimenContainerCmdToEvent, applySpecimenContainerAddedEvent)

    case cmd: ShipmentSpecimensPresentCmd =>
      processSpecimenUpdateCmd(cmd, presentSpecimensCmdToEvent, applySpecimenPresentEvent)

    case cmd: ShipmentSpecimensReceiveCmd =>
      processSpecimenUpdateCmd(cmd, receiveSpecimensCmdToEvent, applySpecimenReceivedEvent)

    case cmd: ShipmentSpecimenMissingCmd =>
      processSpecimenUpdateCmd(cmd, specimenMissingCmdToEvent, applySpecimenMissingEvent)

    case cmd: ShipmentSpecimenExtraCmd =>
      processSpecimenUpdateCmd(cmd, specimenExtraCmdToEvent, applySpecimenExtraEvent)

    case "persistence_restart" =>
      throw new Exception("Intentionally throwing exception to test persistence by restarting the actor")

    case "snap" =>
      mySaveSnapshot
     replyTo = Some(sender())

    case SaveSnapshotSuccess(metadata) =>
      log.debug(s"SaveSnapshotSuccess: $metadata")
      replyTo.foreach(_ ! akka.actor.Status.Success(s"snapshot saved: $metadata"))
      replyTo = None

    case SaveSnapshotFailure(metadata, reason) =>
      log.debug(s"SaveSnapshotFailure: $metadata, reason: $reason")
      replyTo.foreach(_ ! akka.actor.Status.Failure(reason))
      replyTo = None

    case cmd => log.error(s"shipmentsProcessor: message not handled: $cmd")
  }

  private def mySaveSnapshot(): Unit = {
    val snapshotState = SnapshotState(shipmentRepository.getValues.toSet,
                                      shipmentSpecimenRepository.getValues.toSet)
    val filename = snapshotWriter.save(persistenceId, Json.toJson(snapshotState).toString)
    saveSnapshot(filename)
  }

  private def applySnapshot(filename: String): Unit = {
    log.debug(s"snapshot recovery file: $filename")
    val fileContents = snapshotWriter.load(filename);
    Json.parse(fileContents).validate[SnapshotState].fold(
        errors => log.error(s"could not apply snapshot: $filename: $errors"),
        snapshot =>  {
          log.debug(s"snapshot contains ${snapshot.shipments.size} shipments")
          log.debug(s"snapshot contains ${snapshot.shipmentSpecimens.size} shipment specimens")
          snapshot.shipments.foreach(shipmentRepository.put)
          snapshot.shipmentSpecimens.foreach(shipmentSpecimenRepository.put)
        }
    )
  }

  private def addCmdToEvent(cmd: AddShipmentCmd) = {
    for {
      id         <- validNewIdentity(shipmentRepository.nextIdentity, shipmentRepository)
      fromCentre <- centreRepository.getByLocationId(LocationId(cmd.fromLocationId))
      toCentre   <- centreRepository.getByLocationId(LocationId(cmd.toLocationId))
      shipment   <- CreatedShipment.create(id             = id,
                                           version        = 0L,
                                           timeAdded      = OffsetDateTime.now,
                                           courierName    = cmd.courierName,
                                           trackingNumber = cmd.trackingNumber,
                                           fromCentreId   = fromCentre.id,
                                           fromLocationId = LocationId(cmd.fromLocationId),
                                           toCentreId     = toCentre.id,
                                           toLocationId   = LocationId(cmd.toLocationId))
    } yield ShipmentEvent(id.id).update(
      _.sessionUserId        := cmd.sessionUserId,
      _.time                 := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      _.added.courierName    := shipment.courierName,
      _.added.trackingNumber := shipment.trackingNumber,
      _.added.fromCentreId   := shipment.fromCentreId.id,
      _.added.fromLocationId := shipment.fromLocationId.id,
      _.added.toCentreId     := shipment.toCentreId.id,
      _.added.toLocationId   := shipment.toLocationId.id)
  }

  private def updateCourierNameCmdToEvent(cmd:      UpdateShipmentCourierNameCmd,
                                          shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    shipment.withCourier(cmd.courierName).map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.sessionUserId                  := cmd.sessionUserId,
        _.time                           := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.courierNameUpdated.version     := cmd.expectedVersion,
        _.courierNameUpdated.courierName := cmd.courierName)
    }
  }

  private def updateTrackingNumberCmdToEvent(cmd: UpdateShipmentTrackingNumberCmd,
                                             shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    shipment.withTrackingNumber(cmd.trackingNumber).map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.sessionUserId                        := cmd.sessionUserId,
        _.time                                 := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.trackingNumberUpdated.version        := cmd.expectedVersion,
        _.trackingNumberUpdated.trackingNumber := cmd.trackingNumber)
    }
  }

  private def updateFromLocationCmdToEvent(cmd: UpdateShipmentFromLocationCmd,
                                           shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    for {
      centre      <- centreRepository.getByLocationId(LocationId(cmd.locationId))
      location    <- centre.locationWithId(LocationId(cmd.locationId))
      newShipment <- shipment.withFromLocation(centre.id, location.id)
    } yield ShipmentEvent(shipment.id.id).update(
      _.sessionUserId                  := cmd.sessionUserId,
      _.time                           := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      _.fromLocationUpdated.version    := cmd.expectedVersion,
      _.fromLocationUpdated.centreId   := centre.id.id,
      _.fromLocationUpdated.locationId := cmd.locationId)
  }

  private def updateToLocationCmdToEvent(cmd: UpdateShipmentToLocationCmd,
                                         shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    for {
      centre      <- centreRepository.getByLocationId(LocationId(cmd.locationId))
      location    <- centre.locationWithId(LocationId(cmd.locationId))
      newShipment <- shipment.withToLocation(centre.id, location.id)
    } yield ShipmentEvent(shipment.id.id).update(
      _.sessionUserId                := cmd.sessionUserId,
      _.time                         := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      _.toLocationUpdated.version    := cmd.expectedVersion,
      _.toLocationUpdated.centreId   := centre.id.id,
      _.toLocationUpdated.locationId := cmd.locationId)
  }

  private def createdCmdToEvent(cmd: CreatedShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    shipment.isPacked.fold(
      err => InvalidState(s"shipment is not packed: ${shipment.id}").failureNel[ShipmentEvent],
      s => ShipmentEvent(shipment.id.id).update(
        _.sessionUserId   := cmd.sessionUserId,
        _.time            := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.created.version := cmd.expectedVersion).successNel[String]
    )
  }

  private def packCmdToEvent(cmd: PackShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    val numPresentSpecimens = shipmentSpecimenCount(shipment.id, ShipmentItemState.Present)
    if (numPresentSpecimens <= 0) {
      InvalidState(s"shipment has no specimens: ${shipment.id}").failureNel[ShipmentEvent]
    } else {
      shipment match {
        case _: CreatedShipment | _: SentShipment =>
          ShipmentEvent(shipment.id.id).update(
            _.sessionUserId          := cmd.sessionUserId,
            _.time                   := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            _.packed.version         := cmd.expectedVersion,
            _.packed.stateChangeTime := cmd.datetime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).successNel[String]
        case _ =>
          InvalidState(s"cannot change to packed state: ${shipment.id}").failureNel[ShipmentEvent]
      }
    }
  }

  private def sendCmdToEvent(cmd: SendShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    val valid = shipment match {
        case ps: PackedShipment   => ps.send(cmd.datetime)
        case rs: ReceivedShipment => rs.backToSent.successNel[String]
        case ls: LostShipment     => ls.backToSent.successNel[String]
        case _ =>
          InvalidState(s"cannot change to sent state: ${shipment.id}").failureNel[Shipment]
      }

    valid.map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.sessionUserId        := cmd.sessionUserId,
        _.time                 := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.sent.version         := cmd.expectedVersion,
        _.sent.stateChangeTime := cmd.datetime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
  }

  private def receiveCmdToEvent(cmd: ReceiveShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    val valid = shipment match {
        case ss: SentShipment =>
          ss.receive(cmd.datetime)
        case us: UnpackedShipment =>
          // all items must be in present state to allow this state transition
          val nonPresentExist = shipmentSpecimenRepository.allForShipment(us.id).
            exists { ss => ss.state != ShipmentItemState.Present }
          if (nonPresentExist)
            InvalidState(s"cannot change to received state, items have already been processed: ${us.id}").
              failureNel[Shipment]
          else
            us.backToReceived.successNel[String]
        case _ =>
          InvalidState(s"cannot change to received state: ${shipment.id}").failureNel[Shipment]
      }
    valid.map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.sessionUserId            := cmd.sessionUserId,
        _.time                     := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.received.version         := cmd.expectedVersion,
        _.received.stateChangeTime := cmd.datetime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
  }

  private def unpackCmdToEvent(cmd: UnpackShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    val valid = shipment match {
        case rs: ReceivedShipment  => rs.unpack(cmd.datetime)
        case cs: CompletedShipment => cs.backToUnpacked.successNel[String]
        case _ =>
          InvalidState(s"cannot change to unpacked state: ${shipment.id}").failureNel[Shipment]
      }
    valid.map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.sessionUserId            := cmd.sessionUserId,
        _.time                     := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.unpacked.version         := cmd.expectedVersion,
        _.unpacked.stateChangeTime := cmd.datetime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
  }

  private def completeCmdToEvent(cmd: CompleteShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    val numPresentSpecimens = shipmentSpecimenCount(shipment.id, ShipmentItemState.Present)
    if (numPresentSpecimens > 0) {
      InvalidState(s"shipment has specimens in present state: ${shipment.id}").failureNel[ShipmentEvent]
    } else {
      val valid = shipment match {
          case us: UnpackedShipment => us.complete(cmd.datetime)
          case _ =>
            InvalidState(s"cannot change to completed state: ${shipment.id}").failureNel[Shipment]
        }
      valid.map { s =>
        ShipmentEvent(shipment.id.id).update(
          _.sessionUserId             := cmd.sessionUserId,
          _.time                      := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
          _.completed.version         := cmd.expectedVersion,
          _.completed.stateChangeTime := cmd.datetime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
      }
    }
  }

  private def lostCmdToEvent(cmd: LostShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    shipment.isSent.fold(
      err => InvalidState(s"cannot change to lost state: ${shipment.id}").failureNel[ShipmentEvent],
      s   => ShipmentEvent(shipment.id.id).update(
        _.sessionUserId := cmd.sessionUserId,
        _.time          := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.lost.version  := cmd.expectedVersion).successNel[String]
    )
  }

  private def skipStateToSentCmdToEvent(cmd: ShipmentSkipStateToSentCmd,
                                        shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    shipment.skipToSent(cmd.timePacked, cmd.timeSent).map { _ =>
      ShipmentEvent(shipment.id.id).update(
        _.sessionUserId                 := cmd.sessionUserId,
        _.time                          := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.skippedToSentState.version    := cmd.expectedVersion,
        _.skippedToSentState.timePacked := cmd.timePacked.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.skippedToSentState.timeSent   := cmd.timeSent.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
  }

  private def skipStateToUnpackedCmdToEvent(cmd: ShipmentSkipStateToUnpackedCmd,
                                            shipment: Shipment): ServiceValidation[ShipmentEvent] = {
    shipment match {
      case s: SentShipment =>
        s.skipToUnpacked(cmd.timeReceived, cmd.timeUnpacked).map { _ =>
          ShipmentEvent(s.id.id).update(
            _.sessionUserId                       := cmd.sessionUserId,
            _.time                                := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            _.skippedToUnpackedState.version      := cmd.expectedVersion,
            _.skippedToUnpackedState.timeReceived := cmd.timeReceived.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            _.skippedToUnpackedState.timeUnpacked := cmd.timeUnpacked.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
      case _ =>
        InvalidState(s"shipment not sent: ${shipment.state}").failureNel[ShipmentEvent]
    }
  }

  @silent private def removeCmdToEvent(cmd: ShipmentRemoveCmd, shipment: CreatedShipment)
      : ServiceValidation[ShipmentEvent] = {
    val shipmentId = ShipmentId(cmd.id)
    for {
      shipment     <- shipmentRepository.getByKey(shipmentId)
      isCreated    <- shipment.isCreated
      hasSpecimens <- {
        if (shipmentSpecimenRepository.allForShipment(shipmentId).isEmpty) true.successNel[String]
        else ServiceError(s"shipment has specimens, remove specimens first").failureNel[Boolean]
      }
    } yield ShipmentEvent(shipment.id.id).update(
      _.sessionUserId   := cmd.sessionUserId,
      _.time            := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      _.removed.version := cmd.expectedVersion)
  }

  private def addSpecimenCmdToEvent(cmd : ShipmentAddSpecimensCmd)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    val shipmentId = ShipmentId(cmd.shipmentId)
    val shipmentContainerId = cmd.shipmentContainerId.map(ShipmentContainerId.apply)

    for {
      shipment          <- shipmentRepository.getCreated(shipmentId)
      shipmentSpecimens <- createShipmentSpecimens(shipment, shipmentContainerId, cmd.specimenInventoryIds:_*)
    } yield {
      val shipmentSpecimenAddData = shipmentSpecimens.map { ss =>
          ShipmentSpecimenEvent.ShipmentSpecimenAddInfo().update(
            _.specimenId         := ss.specimenId.id,
            _.shipmentSpecimenId := ss.id.id)
        }
      ShipmentSpecimenEvent(cmd.shipmentId).update(
        _.sessionUserId                     := cmd.sessionUserId,
        _.time                              := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.added.optionalShipmentContainerId := cmd.shipmentContainerId,
        _.added.shipmentSpecimenAddData     := shipmentSpecimenAddData
      )
    }
  }

  private def validShipmentSpecimen(shipmentSpecimenId: String, expectedVersion: Long)
      : ServiceValidation[ShipmentSpecimen] = {
    for {
      shipmentSpecimen <- shipmentSpecimenRepository.getByKey(ShipmentSpecimenId(shipmentSpecimenId))
      validVersion     <- shipmentSpecimen.requireVersion(expectedVersion)
    } yield shipmentSpecimen
  }

  private def removeSpecimenCmdToEvent(cmd: ShipmentSpecimenRemoveCmd, shipment: Shipment)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    val validateShipmentSpecimen =
      shipmentSpecimenRepository.getByKey(ShipmentSpecimenId(cmd.shipmentSpecimenId)).
        flatMap { shipmentSpecimen =>
          if (shipmentSpecimen.state == ShipmentItemState.Present) {
            for {
              shipment  <- shipmentRepository.getCreated(ShipmentId(cmd.shipmentId))
              isPresent <- shipmentSpecimen.isStatePresent
            } yield shipmentSpecimen

          } else if (shipmentSpecimen.state == ShipmentItemState.Extra) {
            for {
              shipment <- shipmentRepository.getUnpacked(ShipmentId(cmd.shipmentId))
              isExtra  <- shipmentSpecimen.isStateExtra
            } yield shipmentSpecimen
          } else {
            EntityCriteriaError(
              s"cannot remove, shipment specimen state is invalid: ${shipmentSpecimen.state}" ).
              failureNel[ShipmentSpecimen]
          }
        }

    for {
      shipmentSpecimen <- validateShipmentSpecimen
      validVersion     <- validShipmentSpecimen(cmd.shipmentSpecimenId, cmd.expectedVersion)
    } yield ShipmentSpecimenEvent(shipment.id.id).update(
      _.sessionUserId              := cmd.sessionUserId,
      _.time                       := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      _.removed.version            := cmd.expectedVersion,
      _.removed.shipmentSpecimenId := cmd.shipmentSpecimenId)
  }

  @silent private def updateSpecimenContainerCmdToEvent(cmd:      ShipmentSpecimenUpdateContainerCmd,
                                                        shipment: Shipment)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    // FIXME: validate that shipmentContainerId is a container in the repository
    //
    //val shipmentContainerId = cmd.shipmentContainerId.map(ShipmentContainerId.apply)

    for {
      shipment          <- shipmentRepository.getCreated(ShipmentId(cmd.shipmentId))
      shipmentSpecimens <- shipmentSpecimensPresent(shipment.id, cmd.specimenInventoryIds:_*)
      container         <- s"shipping specimens with containers has not been implemented yet".failureNel[Boolean]
    } yield {
      val shipmentSpecimenData = shipmentSpecimens.map(EventUtils.shipmentSpecimenInfoToEvent).toSeq
      ShipmentSpecimenEvent(cmd.shipmentId).update(
        _.sessionUserId                                       := cmd.sessionUserId,
        _.time                                         := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.containerUpdated.optionalShipmentContainerId := cmd.shipmentContainerId,
        _.containerUpdated.shipmentSpecimenData        := shipmentSpecimenData)
    }
  }

  private def presentSpecimensCmdToEvent(cmd: ShipmentSpecimensPresentCmd, shipment: Shipment)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    for {
      isUnpacked        <- shipment.isUnpacked
      shipmentSpecimens <- shipmentSpecimensNotPresent(shipment.id, cmd.specimenInventoryIds:_*)
      canMakePresent    <- shipmentSpecimens.map(_.present).sequenceU
    } yield {
      val shipmentSpecimenData = shipmentSpecimens.map(EventUtils.shipmentSpecimenInfoToEvent).toSeq
      ShipmentSpecimenEvent(cmd.shipmentId).update(
        _.sessionUserId                       := cmd.sessionUserId,
        _.time                         := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.present.shipmentSpecimenData := shipmentSpecimenData)
    }
  }

  private def receiveSpecimensCmdToEvent(cmd: ShipmentSpecimensReceiveCmd, shipment: Shipment)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    for {
      isUnpacked        <- shipment.isUnpacked
      shipmentSpecimens <- shipmentSpecimensPresent(shipment.id, cmd.specimenInventoryIds:_*)
      canReceive        <- shipmentSpecimens.map(_.received).sequenceU
    } yield {
      val shipmentSpecimenData = shipmentSpecimens.map(EventUtils.shipmentSpecimenInfoToEvent).toSeq
      ShipmentSpecimenEvent(cmd.shipmentId).update(
        _.sessionUserId                        := cmd.sessionUserId,
        _.time                          := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.received.shipmentSpecimenData := shipmentSpecimenData)
    }
  }

  private def specimenMissingCmdToEvent(cmd: ShipmentSpecimenMissingCmd, shipment: Shipment)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    for {
      isUnpacked        <- shipment.isUnpacked
      shipmentSpecimens <- shipmentSpecimensPresent(shipment.id, cmd.specimenInventoryIds:_*)
      canMakeMissing    <- shipmentSpecimens.map(_.missing).sequenceU
    } yield {
      val shipmentSpecimenData = shipmentSpecimens.map(EventUtils.shipmentSpecimenInfoToEvent).toSeq
      ShipmentSpecimenEvent(cmd.shipmentId).update(
        _.sessionUserId                       := cmd.sessionUserId,
        _.time                         := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.missing.shipmentSpecimenData := shipmentSpecimenData)
    }
  }

  /**
   * Specimens that were not recorded to be in this shipment were actually received in the shipment.
   *
   * The specimens must be moved to this centre if and only if they are already at the centre the shipment is
   * coming from.
   */
  private def specimenExtraCmdToEvent(cmd: ShipmentSpecimenExtraCmd, shipment: Shipment)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    for {
      isUnpacked        <- shipment.isUnpacked
      specimens         <- getSpecimens(cmd.specimenInventoryIds:_*)
      notInThisShipment <- specimensNotInShipment(shipment.id, specimens:_*)
      notInAnyShipments <- specimensNotPresentInShipment(specimens:_*)
      shipmentSpecimens <- createShipmentSpecimens(shipment, None, cmd.specimenInventoryIds:_*)
      canMakeExtra      <- shipmentSpecimens.map(_.extra).toList.sequenceU
    } yield {
      val shipmentSpecimenData = shipmentSpecimens.map { ss =>
          ShipmentSpecimenEvent.ShipmentSpecimenAddInfo().update(
            _.specimenId         := ss.specimenId.id,
            _.shipmentSpecimenId := ss.id.id)
        }
      ShipmentSpecimenEvent(cmd.shipmentId).update(
        _.sessionUserId                     := cmd.sessionUserId,
        _.time                       := OffsetDateTime.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        _.extra.shipmentSpecimenData := shipmentSpecimenData)
    }
  }

  private def applyAddedEvent(event: ShipmentEvent) = {
    if (!event.eventType.isAdded) {
      log.error(s"invalid event type: $event")
    } else {
      val addedEvent = event.getAdded
      val eventTime  = OffsetDateTime.parse(event.getTime)
      val add = CreatedShipment.create(id             = ShipmentId(event.id),
                                       version        = 0L,
                                       timeAdded      = eventTime,
                                       courierName    = addedEvent.getCourierName,
                                       trackingNumber = addedEvent.getTrackingNumber,
                                       fromCentreId   = CentreId(addedEvent.getFromCentreId),
                                       fromLocationId = LocationId(addedEvent.getFromLocationId),
                                       toCentreId     = CentreId(addedEvent.getToCentreId),
                                       toLocationId   = LocationId(addedEvent.getToLocationId))
      add.foreach(shipmentRepository.put)

      if (add.isFailure) {
        log.error(s"could not add shipment from event: $event, err: $add")
      }
    }
  }

  private def applyCourierNameUpdatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isCourierNameUpdated,
                           event.getCourierNameUpdated.getVersion) { (shipment, _, time) =>
      val v = for {
          created <- shipment.isCreated
          updated <- created.withCourier(event.getCourierNameUpdated.getCourierName)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyTrackingNumberUpdatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isTrackingNumberUpdated,
                           event.getTrackingNumberUpdated.getVersion) { (shipment, _, time) =>
      val v = for {
          created <- shipment.isCreated
          updated <- created.withTrackingNumber(event.getTrackingNumberUpdated.getTrackingNumber)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyFromLocationUpdatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isFromLocationUpdated,
                           event.getFromLocationUpdated.getVersion) { (shipment, _, time) =>
      val centreId = CentreId(event.getFromLocationUpdated.getCentreId)
      val locationId = LocationId(event.getFromLocationUpdated.getLocationId)
      val v = for {
          created <- shipment.isCreated
          updated <- created.withFromLocation(centreId, locationId)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyToLocationUpdatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isToLocationUpdated,
                           event.getToLocationUpdated.getVersion) { (shipment, _, time) =>
      val centreId = CentreId(event.getToLocationUpdated.getCentreId)
      val locationId = LocationId(event.getToLocationUpdated.getLocationId)
      val v = for {
          created <- shipment.isCreated
          updated <- created.withToLocation(centreId, locationId)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyCreatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isCreated,
                           event.getCreated.getVersion) { (shipment, _, time) =>

      shipment.isPacked.map { p =>
        val created = p.created.copy(timeModified = Some(time))
        shipmentRepository.put(created)
        true
      }
    }
  }

  private def applyPackedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isPacked,
                           event.getPacked.getVersion) { (shipment, _, time) =>
      val stateChangeTime = OffsetDateTime.parse(event.getPacked.getStateChangeTime)

      val updated = shipment match {
          case created: CreatedShipment =>
            created.pack(stateChangeTime).copy(timeModified = Some(time)).successNel[String]

          case sent: SentShipment =>
            sent.backToPacked.copy(timeModified = Some(time)).successNel[String]

          case _ =>
            InvalidState(s"cannot change to packed state: ${shipment.id}").failureNel[Shipment]
        }

      updated.map { s =>
        shipmentRepository.put(s)
        true
      }
    }
  }

  private def applySentEvent(event: ShipmentEvent): Unit =  {
    onValidEventAndVersion(event,
                           event.eventType.isSent,
                           event.getSent.getVersion) { (shipment, _, time) =>
      val stateChangeTime = OffsetDateTime.parse(event.getSent.getStateChangeTime)
      val updated = shipment match {
          case packed: PackedShipment =>
            packed.send(stateChangeTime).map(_.copy(timeModified = Some(time)))

          case received: ReceivedShipment =>
            received.backToSent.copy(timeModified = Some(time)).successNel[String]

          case lost: LostShipment =>
            lost.backToSent.copy(timeModified = Some(time)).successNel[String]

          case _ =>
            InvalidState(s"cannot change to sent state: ${shipment.id}").failureNel[Shipment]
        }

      updated.map { s =>
        shipmentRepository.put(s)
        true
      }
    }
  }

  private def applyReceivedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isReceived,
                           event.getReceived.getVersion) { (shipment, _, time) =>
      val stateChangeTime = OffsetDateTime.parse(event.getReceived.getStateChangeTime)
      val updated = shipment match {
          case sent: SentShipment =>
            sent.receive(stateChangeTime).map(_.copy(timeModified = Some(time)))

          case unpacked: UnpackedShipment =>
            unpacked.backToReceived.copy(timeModified = Some(time)).successNel[String]

          case _ =>
            InvalidState(s"cannot change to received state: ${shipment.id}").failureNel[Shipment]
        }

      updated.map { s =>
        shipmentRepository.put(s)
        true
      }
    }
  }

  private def applyUnpackedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isUnpacked,
                           event.getUnpacked.getVersion) { (shipment, _, time) =>
      val stateChangeTime = OffsetDateTime.parse(event.getUnpacked.getStateChangeTime)

      val unpacked = shipment match {
          case received: ReceivedShipment =>
            received.unpack(stateChangeTime).map(_.copy(timeModified = Some(time)))

          case completed: CompletedShipment =>
            completed.backToUnpacked.copy(timeModified = Some(time)).successNel[String]

          case _ =>
            InvalidState(s"cannot change to received state: ${shipment.id}").failureNel[Shipment]
        }

      unpacked.map { s =>
        shipmentRepository.put(s)
        true
      }
    }
  }

  private def applyCompletedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isCompleted,
                           event.getCompleted.getVersion) { (shipment, _, time) =>
      val stateChangeTime = OffsetDateTime.parse(event.getCompleted.getStateChangeTime)
      for {
        unpacked  <- shipment.isUnpacked
        completed <- unpacked.copy(timeModified = Some(time)).complete(stateChangeTime)
      } yield {
        shipmentRepository.put(completed)
        true
      }
    }
  }

  private def applyLostEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isLost,
                           event.getLost.getVersion) { (shipment, _, time) =>

      shipment.isSent.map { sent =>
        val lost = sent.lost
        shipmentRepository.put(lost)
        true
      }
    }
  }

  private def applySkippedToSentStateEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isSkippedToSentState,
                           event.getSkippedToSentState.getVersion) { (shipment, _, time) =>
      val timePacked = OffsetDateTime.parse(event.getSkippedToSentState.getTimePacked)
      val timeSent   = OffsetDateTime.parse(event.getSkippedToSentState.getTimeSent)
      val v = for {
          created <- shipment.isCreated
          updated <- created.skipToSent(timePacked, timeSent)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applySkippedToUnpackedStateEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isSkippedToUnpackedState,
                           event.getSkippedToUnpackedState.getVersion) { (shipment, _, time) =>
      val timeReceived = OffsetDateTime.parse(event.getSkippedToUnpackedState.getTimeReceived)
      val timeUnpacked = OffsetDateTime.parse(event.getSkippedToUnpackedState.getTimeUnpacked)

      val v = for {
          sent     <- shipment.isSent
          updated  <- sent.skipToUnpacked(timeReceived, timeUnpacked)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyRemovedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isRemoved,
                           event.getRemoved.getVersion) { (shipment, _, time) =>
      shipmentRepository.remove(shipment)
      true.successNel[String]
    }
  }

  private def applySpecimenAddedEvent(event: ShipmentSpecimenEvent): Unit = {
    if (!event.eventType.isAdded) {
      log.error(s"invalid event type: $event")
    } else {
      val addedEvent          = event.getAdded
      val eventTime           = OffsetDateTime.parse(event.getTime)
      val shipmentContainerId = addedEvent.shipmentContainerId.map(ShipmentContainerId.apply)

      addedEvent.shipmentSpecimenAddData.foreach { info =>
        val add = ShipmentSpecimen.create(id                  = ShipmentSpecimenId(info.getShipmentSpecimenId),
                                          version             = 0L,
                                          shipmentId          = ShipmentId(event.shipmentId),
                                          specimenId          = SpecimenId(info.getSpecimenId),
                                          state               = ShipmentItemState.Present,
                                          shipmentContainerId = shipmentContainerId)
        add.foreach { s => shipmentSpecimenRepository.put(s.copy(timeAdded = eventTime)) }
        if (add.isFailure) {
          log.error(s"could not add shipment specimen from event: $event, err: $add")
        }
      }
    }
  }

  private def applySpecimenRemovedEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEvent(event, event.eventType.isRemoved) { (shipment, _, time) =>
      val removedEvent = event.getRemoved
      validShipmentSpecimen(removedEvent.getShipmentSpecimenId, removedEvent.getVersion).
        map { shipmentSpecimen =>
          shipmentSpecimenRepository.remove(shipmentSpecimen)
          true
        }
    }
  }

  private def applySpecimenContainerAddedEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEvent(event, event.eventType.isContainerUpdated) { (shipment, _, time) =>
      val containerUpdatedEvent = event.getContainerUpdated
      val shipmentContainerId = containerUpdatedEvent.shipmentContainerId.map(ShipmentContainerId.apply)

      val v = containerUpdatedEvent.shipmentSpecimenData.
        map { info =>
          for {
            shipmentSpecimen <- validShipmentSpecimen(info.getShipmentSpecimenId, info.getVersion)
            updated          <- shipmentSpecimen.withShipmentContainer(shipmentContainerId)
          } yield updated.copy(timeModified = Some(time))
        }.
        toList.
        sequenceU

      v.foreach(_.foreach(shipmentSpecimenRepository.put))
      v.map(_ => true)
    }
  }

  private def specimenStateUpdate
    (shipmentSpecimenData: Seq[ShipmentSpecimenEvent.ShipmentSpecimenInfo], eventTime: OffsetDateTime)
    (stateUpdateFn: ShipmentSpecimen => ServiceValidation[ShipmentSpecimen])
      : ServiceValidation[Boolean] = {
    if (shipmentSpecimenData.isEmpty) {
      ServiceError(s"shipmentSpecimenData is empty").failureNel[Boolean]
    } else {
      val v = shipmentSpecimenData.
        map { info =>
          for {
            shipmentSpecimen <- validShipmentSpecimen(info.getShipmentSpecimenId, info.getVersion)
            updated          <- stateUpdateFn(shipmentSpecimen)
          } yield updated.copy(timeModified = Some(eventTime))
        }.
        toList.
      sequenceU

      v.foreach(_.foreach(shipmentSpecimenRepository.put))
      v.map(_ => true)
    }
  }

  private def applySpecimenPresentEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEvent(event, event.eventType.isPresent) { (shipment, _, time) =>
      specimenStateUpdate(event.getPresent.shipmentSpecimenData, time) { shipmentSpecimen =>
        shipmentSpecimen.present
      }
    }
  }

  private def applySpecimenReceivedEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEvent(event, event.eventType.isReceived) { (shipment, _, time) =>
      specimenStateUpdate(event.getReceived.shipmentSpecimenData, time) { shipmentSpecimen =>
        shipmentSpecimen.received
      }
    }
  }

  private def applySpecimenMissingEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEvent(event, event.eventType.isMissing) { (shipment, _, time) =>
      specimenStateUpdate(event.getMissing.shipmentSpecimenData, time) { shipmentSpecimen =>
        shipmentSpecimen.missing
      }
    }
  }

  private def applySpecimenExtraEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEvent(event, event.eventType.isExtra) { (shipment, _, time) =>
      val extraEvent = event.getExtra
      val eventTime  = OffsetDateTime.parse(event.getTime)
      extraEvent.shipmentSpecimenData.foreach { info =>
        val add = ShipmentSpecimen.create(id                  = ShipmentSpecimenId(info.getShipmentSpecimenId),
                                          version             = 0L,
                                          shipmentId          = ShipmentId(shipment.id.id),
                                          specimenId          = SpecimenId(info.getSpecimenId),
                                          state               = ShipmentItemState.Extra,
                                          shipmentContainerId = None)
        add.foreach { s => shipmentSpecimenRepository.put(s.copy(timeAdded = eventTime)) }
      }
      true.successNel[String]
    }
  }

  private def processUpdateCmd[T <: ShipmentModifyCommand]
    (cmd: T,
     cmdToEvent: (T, Shipment) => ServiceValidation[ShipmentEvent],
     applyEvent: ShipmentEvent => Unit): Unit = {
    val event = for {
        shipment     <- shipmentRepository.getByKey(ShipmentId(cmd.id))
        validVersion <- shipment.requireVersion(cmd.expectedVersion)
        event        <- cmdToEvent(cmd, shipment)
      } yield event
    process(event)(applyEvent)
  }

  private def processUpdateCmdOnCreated[T <: ShipmentModifyCommand]
    (cmd: T,
     cmdToEvent: (T, CreatedShipment) => ServiceValidation[ShipmentEvent],
     applyEvent: ShipmentEvent => Unit): Unit = {

    def internal(cmd: T, shipment: Shipment): ServiceValidation[ShipmentEvent] =
      shipment match {
        case s: CreatedShipment => cmdToEvent(cmd, s)
        case s => InvalidState(s"shipment not created: ${shipment.id}").failureNel[ShipmentEvent]
      }

    processUpdateCmd(cmd, internal, applyEvent)
  }

  private def processSpecimenUpdateCmd[T <: ShipmentSpecimenModifyCommand]
    (cmd: T,
     cmdToEvent: (T, Shipment) => ServiceValidation[ShipmentSpecimenEvent],
     applyEvent: ShipmentSpecimenEvent => Unit): Unit = {
    val event = for {
        shipment <- shipmentRepository.getByKey(ShipmentId(cmd.shipmentId))
        event    <- cmdToEvent(cmd, shipment)
      } yield event
    process(event)(applyEvent)
  }

  private def onValidEventAndVersion(event:        ShipmentEvent,
                                     eventType:    Boolean,
                                     eventVersion: Long)
                                    (applyEvent:  (Shipment,
                                                   ShipmentEvent,
                                                   OffsetDateTime) => ServiceValidation[Boolean])
      : Unit = {
    if (!eventType) {
      log.error(s"invalid event type: $event")
    } else {
      shipmentRepository.getByKey(ShipmentId(event.id)).fold(
        err => log.error(s"shipment from event does not exist: $err"),
        shipment => {
          if (shipment.version != eventVersion) {
            log.error(s"event version check failed: shipment version: ${shipment.version}, event: $event")
          } else {
            val eventTime = OffsetDateTime.parse(event.getTime)
            val update = applyEvent(shipment, event, eventTime)
            if (update.isFailure) {
              log.error(s"shipment update from event failed: event: $event, reason: $update")
            }
          }
        }
      )
    }
  }

  private def onValidSpecimenEvent(event:     ShipmentSpecimenEvent,
                                   eventType: Boolean)
                                  (applyEvent:  (Shipment,
                                                 ShipmentSpecimenEvent,
                                                 OffsetDateTime) => ServiceValidation[Boolean])
      : Unit = {
    if (!eventType) {
      log.error(s"invalid event type: $event")
    } else {
      shipmentRepository.getByKey(ShipmentId(event.shipmentId)).fold(
        err => log.error(s"shipment from event does not exist: $err"),
        shipment => {
          val eventTime = OffsetDateTime.parse(event.getTime)
          val update = applyEvent(shipment, event, eventTime)
          if (update.isFailure) {
            log.error(s"shipment specimen update from event failed: $update")
          }
        }
      )
    }
  }

  private def createShipmentSpecimens(shipment:             Shipment,
                                      shipmentContainerId:  Option[ShipmentContainerId],
                                      specimenInventoryIds: String*):
      ServiceValidation[Seq[ShipmentSpecimen]] = {

    for {
      specimens         <- inventoryIdsToSpecimens(specimenInventoryIds:_*)
      validCentres      <- specimensAtCentre(shipment.fromLocationId, specimens:_*)
      canBeAdded        <- specimensNotPresentInShipment(specimens:_*)
      shipmentSpecimens <- specimens.map { specimen =>
        for {
          id <- validNewIdentity(shipmentSpecimenRepository.nextIdentity, shipmentSpecimenRepository)
          ss <- ShipmentSpecimen.create(id                  = id,
                                        version             = 0L,
                                        shipmentId          = shipment.id,
                                        specimenId          = specimen.id,
                                        state               = ShipmentItemState.Present,
                                        shipmentContainerId = shipmentContainerId)
        } yield ss
      }.sequenceU
    } yield shipmentSpecimens
  }

  private def init(): Unit = {
    shipmentRepository.init
    shipmentSpecimenRepository.init
  }

  init

}
