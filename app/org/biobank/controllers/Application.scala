package org.biobank.controllers

import org.biobank.domain.UserId

import scala.language.postfixOps
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.cache._

/**
  * Controller for the main page, and also the about and contact us pages.
  */
object Application extends Controller with Security {

  private def userService = Play.current.plugin[BbwebPlugin].map(_.userService).getOrElse {
    sys.error("Bbweb plugin is not registered")
  }

  def index = Action {
    Ok(views.html.index())
  }

  /**
    * Returns the JavaScript router that the client can use for "type-safe" routes.
    * @param varName The name of the global variable, defaults to `jsRoutes`
    */
  def jsRoutes(varName: String = "jsRoutes") = Cached(_ => "jsRoutes", duration = 86400) {
    Action { implicit request =>
      Ok(
        Routes.javascriptRouter(varName)(
          routes.javascript.Application.login,
          routes.javascript.Application.logout,
          routes.javascript.UserController.authUser,
          routes.javascript.UserController.user,
          routes.javascript.UserController.addUser,
          routes.javascript.UserController.updateUser,
          routes.javascript.UserController.removeUser,
          org.biobank.controllers.study.routes.javascript.StudyController.list,
          org.biobank.controllers.study.routes.javascript.StudyController.query
        )
      ).as(JAVASCRIPT)
    }
  }

  /** Used for obtaining the email and password from the HTTP login request */
  case class LoginCredentials(email: String, password: String)

  /** JSON reader for [[LoginCredentials]]. */
  implicit val loginCredentialsReads = (
    (__ \ "email").read[String](minLength[String](5)) and
      (__ \ "password").read[String](minLength[String](2))
  )((email, password) => LoginCredentials(email, password))

  /**
    * Log-in a user. Expects the credentials in the body in JSON format.
    *
    * Set the cookie [[AuthTokenCookieKey]] to have AngularJS set the X-XSRF-TOKEN in the HTTP
    * header.
    *
    * @return The token needed for subsequent requests
    */
  def login() = Action(parse.json) { implicit request =>
    val jsonValidation = request.body.validate[LoginCredentials]
    jsonValidation.fold(
      errors => {
        BadRequest(Json.obj("status" ->"KO", "message" -> JsError.toFlatJson(errors)))
      },
      loginCredentials => {
        Logger.info(s"login: $loginCredentials")
        userService.getByEmail(loginCredentials.email).fold(
          err => {
            BadRequest(Json.obj("status" ->"KO", "message" -> err.list.mkString(", ")))
          },
          user => {
            // TODO: token should be derived from salt
            val token = java.util.UUID.randomUUID().toString
            Cache.set(token, user.id)
            Ok(Json.obj("token" -> token))
              .withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
          }
        )
      }
    )
  }

  /**
    * Log-out a user. Invalidates the authentication token.
    *
    * Discard the cookie [[AuthTokenCookieKey]] to have AngularJS no longer set the
    * X-XSRF-TOKEN in HTTP header.
    */
  def logout() = AuthAction(parse.empty) { token => userId => implicit request =>
    Cache.remove(token)
    Ok.discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
  }
}

