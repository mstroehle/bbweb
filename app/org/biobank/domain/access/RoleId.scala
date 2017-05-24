package org.biobank.domain.access

import play.api.libs.json._
import org.biobank.infrastructure.EnumUtils._

@SuppressWarnings(Array("org.wartremover.warts.Enumeration"))
object RoleId extends Enumeration {
  type RoleId = Value

  val WebsiteAdministrator: Value  = Value("WebsiteAdministrator")
  val UserAdministrator: Value     = Value("UserAdministrator")
  val StudyAdministrator: Value    = Value("StudyAdministrator")
  val CentreAdministrator: Value   = Value("CentreAdministrator")
  val ShippingAdministrator: Value = Value("ShippingAdministrator")

  val SpecimenCollector: Value     = Value("SpecimenCollector")
  val SpecimenProcessor: Value     = Value("SpecimenProcessor")

  implicit val roleFormat: Format[RoleId] =
    enumFormat(org.biobank.domain.access.RoleId)
}