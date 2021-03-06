package org.biobank.domain

import play.api.libs.json._
import org.biobank.infrastructure.EnumUtils._

/** Describes how a [[domain.participants.Specimen Specimen]] should be preserved/stored by describing a
  * preservation method. Also see [[domain.PreservationTemperature PreservationTemperature]].
  */
@SuppressWarnings(Array("org.wartremover.warts.Enumeration"))
object PreservationType extends Enumeration {
  type PreservationType = Value

  val FrozenSpecimen: Value = Value("Frozen Specimen")
  val RnaLater: Value       = Value("RNA Later")
  val FreshSpecimen: Value  = Value("Fresh Specimen")
  val Slide: Value          = Value("Slide")

  implicit val preservationTypeReads: Format[PreservationType] = enumFormat(PreservationType)
}

/** Describes how a [[domain.participants.Specimen Specimen]] should be preserved/stored by describing
  * temperature requirements (degrees Celcius), as well as a preservation method (see
  * [[domain.PreservationType PreservationType]]).
  *
  */
@SuppressWarnings(Array("org.wartremover.warts.Enumeration"))
object PreservationTemperature extends Enumeration {
  type PreservationTemperature = Value

  val Plus4celcius: Value    = Value("4 C")
  val Minus20celcius: Value  = Value("-20 C")
  val Minus80celcius: Value  = Value("-80 C")
  val Minus180celcius: Value = Value("-180 C")
  val RoomTemperature: Value = Value("Room Temperature")

  implicit val preservatioTempTypeReads: Format[PreservationTemperature] =
    enumFormat(PreservationTemperature)
}
