package org.biobank.controllers.users

import javax.inject.{Inject, Singleton}
import org.biobank.domain.user._
import org.biobank.controllers._
import org.biobank.infrastructure.command.UserCommands._
import org.biobank.service.studies.StudiesService
import org.biobank.service.users.UsersService
import org.biobank.service.{AuthToken, PagedQuery, PagedResults}
import play.api.Logger
import play.api.cache.CacheApi
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc._
import play.api.{Environment, Logger}
import scala.language.reflectiveCalls
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._
import scalaz._

@Singleton
class UsersController @Inject() (val env:            Environment,
                                 val cacheApi:       CacheApi,
                                 val authToken:      AuthToken,
                                 val usersService:   UsersService,
                                 val studiesService: StudiesService)
    extends CommandController
    with JsonController {

  val log = Logger(this.getClass)

  private val PageSizeMax = 20

  /** Used for obtaining the email and password from the HTTP login request */
  case class LoginCredentials(email: String, password: String)

  /** JSON reader for [[LoginCredentials]]. */
  implicit val loginCredentialsReads: Reads[LoginCredentials] = Json.reads[LoginCredentials]

  /**
    * Log-in a user. Expects the credentials in the body in JSON format.
    *
    * Set the cookie [[AuthTokenCookieKey]] to have AngularJS set the X-XSRF-TOKEN in the HTTP
    * header.
    *
    * @return The token needed for subsequent requests
    */
  def login() = Action(parse.json) { implicit request =>
    request.body.validate[LoginCredentials].fold(
      errors => {
        BadRequest(JsError.toJson(errors))
      },
      loginCredentials => {
        usersService.validatePassword(loginCredentials.email, loginCredentials.password).fold(
          err => {
            val errStr = err.list.toList.mkString(", ")
            // FIXME: what if user attempts multiple failed logins? lock the account after 3 attempts?
            // how long to lock the account?
            if (errStr.contains("InvalidPassword")) {
              Forbidden(errStr)
            } else if (errStr.contains("not found")) {
              Forbidden("invalid email")
            } else if (errStr.contains("not active") || errStr.contains("is locked")) {
              Forbidden(err.list.toList.mkString(", "))
            } else {
              NotFound(err.list.toList.mkString(", "))
            }
          },
          user => {
            val token = authToken.newToken(user.id)
            log.debug(s"user logged in: ${user.email}, token: $token")
            Ok(token).withCookies(Cookie(AuthTokenCookieKey, token, None, httpOnly = false))
          }
        )
      }
    )
  }

  /** Retrieves the user associated with the token, if it is valid.
    */
  def authenticateUser() = AuthAction(parse.empty) { (token, userId, request) =>
    usersService.getUser(userId.id).fold(
      err  => BadRequest(err.list.toList.mkString(", ")),
      user => Ok(user)
    )
  }

  /**
    * Log-out a user. Invalidates the authentication token.
    *
    * Discard the cookie [[AuthTokenCookieKey]] to have AngularJS no longer set the
    * X-XSRF-TOKEN in HTTP header.
    */
  def logout() = AuthAction(parse.empty) { (token, userId, request) =>
    cacheApi.remove(token)
    Ok("user has been logged out")
      .discardingCookies(DiscardingCookie(name = AuthTokenCookieKey))
      .withNewSession
  }

  /** Resets the user's password.
    */
  def passwordReset() = commandActionAsync { cmd: ResetUserPasswordCmd =>
      processCommand(cmd)
    }

  def userCounts() =
    AuthAction(parse.empty) { (token, userId, request) =>
      Ok(usersService.getCountsByStatus)
    }

  def list(nameFilterMaybe:  Option[String],
           emailFilterMaybe: Option[String] ,
           statusMaybe:      Option[String],
           sortMaybe:        Option[String],
           pageMaybe:        Option[Int],
           pageSizeMaybe:    Option[Int],
           orderMaybe:       Option[String]) =
    AuthAction(parse.empty) { (token, userId, request) =>
      val nameFilter  = nameFilterMaybe.fold { "" } { nf => nf }
      val emailFilter = emailFilterMaybe.fold { "" } { ef => ef }
      val status      = statusMaybe.fold { "all" } { s => s }
      val sort        = sortMaybe.fold { "name" } { s => s }
      val page        = pageMaybe.fold { 1 } { p => p }
      val pageSize    = pageSizeMaybe.fold { 5 } { ps => ps }
      val order       = orderMaybe.fold { "asc" } { o => o }

      log.debug(
        s"""|UsersController:list: nameFilter/$nameFilter, emailFilter/$emailFilter,
            |  status/$status, sort/$sort, page/$page, pageSize/$pageSize,  order/$order""".stripMargin)


      val pagedQuery = PagedQuery(page, pageSize, order)

      val validation = for {
          sortFunc     <- User.sort2Compare.get(sort).toSuccessNel(ControllerError(s"invalid sort field: $sort"))
           sortOrder   <- pagedQuery.getSortOrder
           users       <- usersService.getUsers[User](nameFilter, emailFilter, status, sortFunc, sortOrder)
           page        <- pagedQuery.getPage(PageSizeMax, users.size)
           pageSize    <- pagedQuery.getPageSize(PageSizeMax)
           results     <- PagedResults.create(users, page, pageSize)
        } yield results

      validation.fold(
        err => BadRequest(err.list.toList.mkString),
        results =>  Ok(results)
      )
    }

  /** Retrieves the user for the given id as JSON */
  def user(id: String) = AuthAction(parse.empty) { (token, userId, request) =>
    validationReply(usersService.getUser(id))
  }

  def registerUser() =
    commandActionAsync { cmd: RegisterUserCmd =>
      processCommand(cmd)
    }

  def updateName(id: String) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UpdateUserNameCmd =>
      processCommand(cmd)
    }

  def updateEmail(id: String) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UpdateUserEmailCmd =>
      processCommand(cmd)
  }

  def updatePassword(id: String) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UpdateUserPasswordCmd =>
      processCommand(cmd)
  }

  def updateAvatarUrl(id: String) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UpdateUserAvatarUrlCmd =>
      processCommand(cmd)
  }

  def activateUser(id: String) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: ActivateUserCmd =>
      processCommand(cmd)
  }

  def lockUser(id: String) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: LockUserCmd =>
      processCommand(cmd)
  }

  def unlockUser(id: String) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UnlockUserCmd =>
      processCommand(cmd)
  }

  def userStudies(id: String, query: Option[String], sort: Option[String], order: Option[String]) =
    AuthAction(parse.empty) { (token, userId, request) =>
      // FIXME this should return only the studies this user has access to
      //
      // This this for now, but fix once user groups have been implemented
      val studies = studiesService.getStudyCount
      Ok(studies)
    }


  private def processCommand(cmd: UserCommand) = {
    validationReply(usersService.processCommand(cmd))
  }
}