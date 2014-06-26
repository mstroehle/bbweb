package org.biobank.service.json

import org.biobank.infrastructure.event.Events._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

object Events {

  def eventToJsonReply[T <: Event](event: T)(implicit writes: Writes[T]): JsObject = {
    Json.obj(
      "status" ->"success",
      "data" -> Json.obj("event" -> Json.toJson(event))
    )
  }

}