package org.biobank.domain.centre

import com.github.nscala_time.time.Imports._
import org.biobank.domain._
import org.biobank.domain.centre.ShipmentState._
import org.biobank.dto.{CentreLocationInfo, ShipmentDto}
import org.biobank.infrastructure.JsonUtils._
import org.biobank.{ValidationKey, ValidationMsgKey}
import org.joda.time.DateTime
import play.api.libs.json.Reads._
import play.api.libs.json._
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._

final case class ShipmentId(id: String) extends IdentifiedValueObject[String]

object ShipmentId {

  // Do not want JSON to create a sub object, we just want it to be converted
  // to a single string
  implicit val shipmentIdReader: Reads[ShipmentId] =
    (__ \ "id").read[String].map( new ShipmentId(_) )

  implicit val shipmentIdWriter: Writes[ShipmentId] =
    Writes{ (shipmentId: ShipmentId) => JsString(shipmentId.id) }

}

trait ShipmentValidations {

  case object CourierNameInvalid extends ValidationKey

  case object TrackingNumberInvalid extends ValidationKey

  case object FromCentreIdInvalid extends ValidationKey

  case object FromLocationIdInvalid extends ValidationKey

  case object ToCentreIdInvalid extends ValidationKey

  case object ToLocationIdInvalid extends ValidationKey

  case object TimePackedUndefined extends ValidationKey

  case object TimeSentBeforePacked extends ValidationKey

  case object TimeSentUndefined extends ValidationKey

  case object TimeReceivedBeforeSent extends ValidationKey

  case object TimeReceivedUndefined extends ValidationKey

  case object TimeUnpackedBeforeReceived extends ValidationKey

  case class InvalidStateTransition(msg: String) extends ValidationMsgKey

  def validateTimeAfter(afterMaybe: Option[DateTime],
                        beforeMaybe: Option[DateTime],
                        errUndefined: ValidationKey,
                        errNotAfter: ValidationKey)
      : DomainValidation[Option[DateTime]] = {
    beforeMaybe.fold {
      if (afterMaybe.isDefined) errUndefined.failureNel[Option[DateTime]]
      else afterMaybe.successNel[String]
    } { before =>
      if (afterMaybe.isEmpty || afterMaybe.exists(after => after > before)) {
        afterMaybe.successNel[String]
      } else {
        errNotAfter.failureNel[Option[DateTime]]
      }
    }
  }

}

/**
 * Represents a transfer of [org.biobank.domain.participants.Specimen]s and / or
 * [org.biobank.domain.containers.Container]s from one [org.biobank.domain.centre.Centre] to another.
 *
 * @see org.biobank.domain.centre.ShipmentSpecimen
 * @see org.biobank.domain.centre.ShipmentContainer
 */
final case class Shipment(id:             ShipmentId,
                          version:        Long,
                          timeAdded:      DateTime,
                          timeModified:   Option[DateTime],
                          state:          ShipmentState,
                          courierName:    String,
                          trackingNumber: String,
                          fromCentreId:   CentreId,
                          fromLocationId: String,
                          toCentreId:     CentreId,
                          toLocationId:   String,
                          timePacked:     Option[DateTime],
                          timeSent:       Option[DateTime],
                          timeReceived:   Option[DateTime],
                          timeUnpacked:   Option[DateTime])
    extends ConcurrencySafeEntity[ShipmentId]
    with ShipmentValidations {

  import org.biobank.domain.CommonValidations._
  import org.biobank.CommonValidations._

  def withState(state: ShipmentState): DomainValidation[Shipment] =
    copy(state       = state,
         version      = version + 1,
         timeModified = Some(DateTime.now)).successNel[String]

  def withCourier(name: String): DomainValidation[Shipment] =
    validateString(name, CourierNameInvalid).map { name =>
      copy(courierName  = name,
           version      = version + 1,
           timeModified = Some(DateTime.now))
    }

  def withTrackingNumber(trackingNumber: String): DomainValidation[Shipment] =
    validateString(trackingNumber, TrackingNumberInvalid).map { _ =>
      copy(trackingNumber = trackingNumber,
           version        = version + 1,
           timeModified   = Some(DateTime.now))
    }

  /**
   * Must be a centre's location.
   */
  def withFromLocation(centreId: CentreId, locationId: String): DomainValidation[Shipment] =
    (validateString(centreId.id, FromCentreIdInvalid) |@|
       validateString(locationId, LocationIdInvalid)) { case (_, _) =>
        copy(fromCentreId   = centreId,
             fromLocationId = locationId,
             version        = version + 1,
             timeModified   = Some(DateTime.now))
    }

  /**
   * Must be a centre's location.
   */
  def withToLocation(centreId: CentreId, locationId: String): DomainValidation[Shipment] =
    (validateString(centreId.id, ToCentreIdInvalid) |@|
       validateString(locationId, LocationIdInvalid)) { case (_, _) =>
        copy(toCentreId   = centreId,
             toLocationId = locationId,
             version      = version + 1,
             timeModified = Some(DateTime.now))
    }

  def created(): DomainValidation[Shipment] =
    if (state == ShipmentState.Packed) {
      copy(state        = ShipmentState.Created,
           timePacked   = None,
           timeSent     = None,
           timeReceived = None,
           timeUnpacked = None,
           version      = version + 1,
           timeModified = Some(DateTime.now)).successNel[String]
    } else {
      InvalidStateTransition(s"cannot set state to CREATED: shipment state is invalid: $state")
        .failureNel[Shipment]
    }

  def packed(time: DateTime): DomainValidation[Shipment] =
    if ((state == ShipmentState.Created) || (state == ShipmentState.Sent)) {
      copy(state        = ShipmentState.Packed,
           timePacked   = Some(time),
           timeSent     = None,
           timeReceived = None,
           timeUnpacked = None,
           version      = version + 1,
           timeModified = Some(DateTime.now)).successNel[String]
    } else {
      InvalidStateTransition(s"cannot set state to PACKED: shipment state is invalid: $state")
        .failureNel[Shipment]
    }

  def sent(timeSent: DateTime): DomainValidation[Shipment] = {
    if ((state == ShipmentState.Packed) || (state == ShipmentState.Received)) {
      timePacked.fold {
        TimePackedUndefined.failureNel[Shipment]
      } { time =>
        if (timeSent < time) {
          TimeSentBeforePacked.failureNel[Shipment]
        } else {
          copy(state        = ShipmentState.Sent,
               timeSent     = Some(timeSent),
               timeReceived = None,
               timeUnpacked = None,
               version      = version + 1,
               timeModified = Some(DateTime.now)).successNel[String]
        }
      }
    } else {
      InvalidStateTransition(s"cannot set state to SENT: shipment state is invalid: $state")
        .failureNel[Shipment]
    }
  }

  def packedAndSent(timePacked: DateTime, timeSent: DateTime): DomainValidation[Shipment] =
    if (state == ShipmentState.Created) {
      copy(state        = ShipmentState.Packed,
           timePacked   = Some(timePacked),
           timeSent     = Some(timeSent),
           timeReceived = None,
           timeUnpacked = None,
           version      = version + 1,
           timeModified = Some(DateTime.now)).successNel[String]
    } else {
      InvalidStateTransition(s"cannot set state to PACKED: shipment state is invalid: $state")
        .failureNel[Shipment]
    }

  def received(timeReceived: DateTime): DomainValidation[Shipment] =
    if ((state == ShipmentState.Sent) || (state == ShipmentState.Unpacked)) {
      timeSent.fold  {
        TimeSentUndefined.failureNel[Shipment]
      } { time =>
        if (timeReceived < time) {
          TimeReceivedBeforeSent.failureNel[Shipment]
        } else {
          copy(state        = ShipmentState.Received,
               timeReceived = Some(timeReceived),
               timeUnpacked = None,
               version      = version + 1,
               timeModified = Some(DateTime.now)).successNel[String]
        }
      }
    } else {
      InvalidStateTransition(s"cannot set state to RECEIVED: shipment state is invalid: $state")
        .failureNel[Shipment]
    }

  def unpacked(timeUnpacked: DateTime): DomainValidation[Shipment] =
    if (state != ShipmentState.Received) {
      InvalidStateTransition(s"cannot set state to UNPACKED: shipment state is invalid: $state")
        .failureNel[Shipment]
    } else {
      timeReceived.fold {
        TimeReceivedUndefined.failureNel[Shipment]
      } { time =>
        if (timeUnpacked < time) {
          TimeUnpackedBeforeReceived.failureNel[Shipment]
        } else {
          copy(state        = ShipmentState.Unpacked,
               timeUnpacked = Some(timeUnpacked),
               version      = version + 1,
               timeModified = Some(DateTime.now)).successNel[String]
        }
      }
    }

  def lost(): DomainValidation[Shipment] =
    if (state != ShipmentState.Sent) {
      InvalidStateTransition(s"cannot set state to LOST: shipment state is invalid: $state")
        .failureNel[Shipment]
    } else {
      copy(state        = ShipmentState.Lost,
           version      = version + 1,
           timeModified = Some(DateTime.now)).successNel[String]
    }

  def skipToSentState(timePacked: DateTime, timeSent: DateTime): DomainValidation[Shipment] = {
    if (state == ShipmentState.Created) {
      if (timeSent < timePacked) {
        TimeSentBeforePacked.failureNel[Shipment]
      } else {
        copy(state        = ShipmentState.Sent,
             timePacked   = Some(timePacked),
             timeSent     = Some(timeSent),
             timeReceived = None,
             timeUnpacked = None,
             version      = version + 1,
             timeModified = Some(DateTime.now)).successNel[String]
      }
    } else {
      InvalidStateTransition(s"cannot set state to SENT: shipment state is invalid: $state")
        .failureNel[Shipment]
    }
  }

  def skipToUnpackedState(timeReceived: DateTime, timeUnpacked: DateTime): DomainValidation[Shipment] = {
    if (state == ShipmentState.Sent) {
      if (timeUnpacked < timeReceived) {
        TimeUnpackedBeforeReceived.failureNel[Shipment]
      } else {
        copy(state          = ShipmentState.Unpacked,
             timeReceived   = Some(timeReceived),
             timeUnpacked   = Some(timeUnpacked),
             version        = version + 1,
             timeModified   = Some(DateTime.now)).successNel[String]
      }
    } else {
      InvalidStateTransition(s"cannot set state to UNPACKED: shipment state is invalid: $state")
        .failureNel[Shipment]
    }
  }

  val stateRequiresTime = List(Packed, Sent, Received, Unpacked)

  def changeState(newState: ShipmentState, stateChangeTimeMaybe: Option[DateTime])
      : DomainValidation[Shipment] = {
    val validTime: DomainValidation[DateTime] = if (stateRequiresTime.contains(newState)) {
        stateChangeTimeMaybe.toSuccessNel(DomainError("state change requires a date and time"))
      } else {
        DateTime.now.successNel[String]
      }
    validTime.flatMap { time =>
      newState match {
        case Created  => created
        case Packed   => packed(time)
        case Sent     => sent(time)
        case Received => received(time)
        case Unpacked => unpacked(time)
        case Lost     => lost
      }
    }
  }

  def isCreated(): DomainValidation[Boolean] =
    if (state == ShipmentState.Created) true.successNel[String]
    else EntityCriteriaError(s"shipment is not in created state").failureNel[Boolean]

  def isReceived(): DomainValidation[Boolean] =
    if (state == ShipmentState.Received) true.successNel[String]
    else EntityCriteriaError(s"shipment is not in received state").failureNel[Boolean]

  def isUnpacked(): DomainValidation[Boolean] =
    if (state == ShipmentState.Unpacked) true.successNel[String]
    else EntityCriteriaError(s"shipment is not in unpacked state").failureNel[Boolean]

  def toDto(fromLocationInfo: CentreLocationInfo,
            toLocationInfo:   CentreLocationInfo,
            specimenCount:     Int,
            containerCount:    Int): ShipmentDto =
      ShipmentDto(id               = this.id.id,
                  version          = this.version,
                  timeAdded        = this.timeAdded,
                  timeModified     = this.timeModified,
                  state            = this.state.toString,
                  courierName      = this.courierName,
                  trackingNumber   = this.trackingNumber,
                  fromLocationInfo = fromLocationInfo,
                  toLocationInfo   = toLocationInfo,
                  timePacked       = this.timePacked,
                  timeSent         = this.timeSent,
                  timeReceived     = this.timeReceived,
                  timeUnpacked     = this.timeUnpacked,
                  specimenCount     = specimenCount,
                  containerCount    = containerCount)

  override def toString: String =
    s"""|Shipment:{
        |  id:             $id,
        |  version:        $version,
        |  timeAdded:      $timeAdded,
        |  timeModified:   $timeModified,
        |  state:          $state,
        |  courierName:    $courierName,
        |  trackingNumber: $trackingNumber,
        |  fromCentreId:   $fromCentreId,
        |  fromLocationId: $fromLocationId,
        |  toCentreId:     $toCentreId,
        |  toLocationId:   $toLocationId,
        |  timePacked:     $timePacked,
        |  timeSent:       $timeSent,
        |  timeReceived:   $timeReceived,
        |  timeUnpacked:   $timeUnpacked
        |}""".stripMargin
}

object Shipment extends ShipmentValidations {
  import org.biobank.domain.CommonValidations._

  implicit val shipmentWrites: Writes[Shipment] = Json.writes[Shipment]

  def create(id:             ShipmentId,
             version:        Long,
             state:          ShipmentState,
             courierName:    String,
             trackingNumber: String,
             fromCentreId:   CentreId,
             fromLocationId: String,
             toCentreId:     CentreId,
             toLocationId:   String,
             timePacked:     Option[DateTime],
             timeSent:       Option[DateTime],
             timeReceived:   Option[DateTime],
             timeUnpacked:   Option[DateTime]): DomainValidation[Shipment] = {
    validate(id,
             version,
             state,
             courierName,
             trackingNumber,
             fromCentreId,
             fromLocationId,
             toCentreId,
             toLocationId,
             timePacked,
             timeSent,
             timeReceived,
             timeUnpacked).map(_ => Shipment(id,
                                             version,
                                             DateTime.now,
                                             None,
                                             state,
                                             courierName,
                                             trackingNumber,
                                             fromCentreId,
                                             fromLocationId,
                                             toCentreId,
                                             toLocationId,
                                             timePacked,
                                             timeSent,
                                             timeReceived,
                                             timeUnpacked))
  }

  def validate(id:             ShipmentId,
               version:        Long,
               state:          ShipmentState,
               courierName:    String,
               trackingNumber: String,
               fromCentreId:   CentreId,
               fromLocationId: String,
               toCentreId:     CentreId,
               toLocationId:   String,
               timePacked:     Option[DateTime],
               timeSent:       Option[DateTime],
               timeReceived:   Option[DateTime],
               timeUnpacked:   Option[DateTime]): DomainValidation[Boolean] = {
    (validateId(id) |@|
       validateVersion(version) |@|
       validateString(courierName, CourierNameInvalid) |@|
       validateString(trackingNumber, TrackingNumberInvalid) |@|
       validateId(fromCentreId, FromCentreIdInvalid) |@|
       validateString(fromLocationId, FromLocationIdInvalid) |@|
       validateId(toCentreId, ToCentreIdInvalid) |@|
       validateString(toLocationId, ToLocationIdInvalid) |@|
       validateTimeAfter(timeSent, timePacked, TimePackedUndefined, TimeSentBeforePacked)  |@|
       validateTimeAfter(timeReceived, timeSent, TimeSentUndefined, TimeReceivedBeforeSent)  |@|
       validateTimeAfter(timeUnpacked, timeReceived, TimeReceivedUndefined, TimeUnpackedBeforeReceived)) {
      case (_, _, _, _, _, _, _, _, _, _, _) => true
    }

  }

}
