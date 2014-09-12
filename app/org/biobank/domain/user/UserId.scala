package org.biobank.domain.user

import org.biobank.domain.IdentifiedValueObject

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class UserId(id: String) extends IdentifiedValueObject[String] {}

object UserId {

  implicit val userIdWriter = Writes{ (userId: UserId) => JsString(userId.id) }

}
