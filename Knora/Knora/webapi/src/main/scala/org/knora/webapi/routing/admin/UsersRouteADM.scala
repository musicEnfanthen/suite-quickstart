/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing.admin

import java.util.UUID

import akka.actor.{ActorSelection, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilADM}
import org.knora.webapi.util.StringFormatter

import scala.concurrent.ExecutionContextExecutor

/**
  * Provides a spray-routing function for API routes that deal with users.
  */

@Api(value = "users", produces = "application/json")
@Path("/admin/users")
class UsersRouteADM(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter) extends Authenticator {

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = settings.defaultTimeout
    val responderManager: ActorSelection = system.actorSelection("/user/responderManager")
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    @ApiOperation(value = "Get users", nickname = "getUsers", httpMethod = "GET", response = classOf[UsersGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* return all users */
    def getUsers = path("admin" / "users") {
        get {
            requestContext =>
                val requestMessage = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UsersGetRequestADM(requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    @ApiOperation(value = "Add new user", nickname = "addUser", httpMethod = "POST", response = classOf[UserOperationResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"user\" to create", required = true,
            dataTypeClass = classOf[CreateUserApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* create a new user */
    def postUser = path("admin" / "users") {
        post {
            entity(as[CreateUserApiRequestADM]) { apiRequest =>
                requestContext =>
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield UserCreateRequestADM(
                        createRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    @Path("/{IRI}")
    @ApiOperation(value = "Return a single user identified by IRI or Email", notes = "", nickname = "getUser", httpMethod = "GET")
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "name", value = "Name of person to greet", required = false, dataType = "string", paramType = "path")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 200, message = "Return User", response = classOf[UserResponseADM]),
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* return a single user identified by iri or email */
    def getUser = path("admin" / "users" / Segment) { value =>
        get {
            parameters("identifier" ? "iri") { (identifier: String) =>
                requestContext =>
                    /* check if email or iri was supplied */
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield  if (identifier == "email") {
                        UserGetRequestADM(maybeIri = None, maybeEmail = Some(value), UserInformationTypeADM.RESTRICTED, requestingUser)
                    } else {
                        val userIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid user IRI $value"))
                        UserGetRequestADM(maybeIri = Some(userIri), maybeEmail = None, UserInformationTypeADM.RESTRICTED, requestingUser)
                    }

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    /* concatenate paths in the CORRECT order and return */
    def knoraApiPath: Route = getUsers ~ postUser ~ getUser ~
            path("admin" / "users" / Segment) { value =>
                put {
                    /* update a user identified by iri */
                    entity(as[ChangeUserApiRequestADM]) { apiRequest =>
                        requestContext =>

                            val userIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid user IRI $value"))

                            /* the api request is already checked at time of creation. see case class. */

                            val requestMessage = for {
                                requestingUser <- getUserADM(requestContext)
                            } yield  if (apiRequest.requesterPassword.isDefined && apiRequest.newPassword.isDefined) {
                                /* update existing user's password */
                                UserChangePasswordRequestADM(
                                    userIri = userIri,
                                    changeUserRequest = apiRequest,
                                    requestingUser,
                                    apiRequestID = UUID.randomUUID()
                                )
                            } else if (apiRequest.status.isDefined) {
                                /* update existing user's status */
                                UserChangeStatusRequestADM(
                                    userIri,
                                    changeUserRequest = apiRequest,
                                    requestingUser,
                                    apiRequestID = UUID.randomUUID()
                                )
                            } else if (apiRequest.systemAdmin.isDefined) {
                                /* update existing user's system admin membership status */
                                UserChangeSystemAdminMembershipStatusRequestADM(
                                    userIri,
                                    changeUserRequest = apiRequest,
                                    requestingUser,
                                    apiRequestID = UUID.randomUUID()
                                )
                            } else {
                                /* update existing user's basic information */
                                UserChangeBasicUserInformationRequestADM(
                                    userIri,
                                    changeUserRequest = apiRequest,
                                    requestingUser,
                                    apiRequestID = UUID.randomUUID()
                                )
                            }

                            RouteUtilADM.runJsonRoute(
                                requestMessage,
                                requestContext,
                                settings,
                                responderManager,
                                log
                            )
                    }
                } ~
                delete {
                    /* delete a user identified by iri */
                    requestContext => {
                        val userIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid user IRI $value"))

                        /* update existing user's status to false */
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserChangeStatusRequestADM(
                            userIri,
                            changeUserRequest = ChangeUserApiRequestADM(status = Some(false)),
                            requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                    }
                }
            } ~
            path("admin" / "users" / "projects" / Segment) { userIri =>
                get {
                    /* get user's project memberships */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserProjectMembershipsGetRequestADM(
                            userIri = checkedUserIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            path("admin" / "users" / "projects" / Segment / Segment) { (userIri, projectIri) =>
                post {
                    /* add user to project */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                        val checkedProjectIri = stringFormatter.validateAndEscapeIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserProjectMembershipAddRequestADM(
                            userIri = checkedUserIri,
                            projectIri = checkedProjectIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                } ~
                delete {
                    /* remove user from project (and all groups belonging to this project) */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                        val checkedProjectIri = stringFormatter.validateAndEscapeIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserProjectMembershipRemoveRequestADM(
                            userIri = checkedUserIri,
                            projectIri = checkedProjectIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            path("admin" / "users" / "projects-admin" / Segment) { userIri =>
                get {
                    /* get user's project admin memberships */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserProjectAdminMembershipsGetRequestADM(
                            userIri = checkedUserIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            path("admin" / "users" / "projects-admin" / Segment / Segment) { (userIri, projectIri) =>
                post {
                    /* add user to project admin */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                        val checkedProjectIri = stringFormatter.validateAndEscapeIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserProjectAdminMembershipAddRequestADM(
                            userIri = checkedUserIri,
                            projectIri = checkedProjectIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                } ~
                delete {
                    /* remove user from project admin */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                        val checkedProjectIri = stringFormatter.validateAndEscapeIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserProjectAdminMembershipRemoveRequestADM(
                            userIri = checkedUserIri,
                            projectIri = checkedProjectIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            path("admin" / "users" / "groups" / Segment) { userIri =>
                get {
                    /* get user's group memberships */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserGroupMembershipsGetRequestADM(
                            userIri = checkedUserIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            path("admin" / "users" / "groups" / Segment / Segment) { (userIri, groupIri) =>
                post {
                    /* add user to group */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                        val checkedGroupIri = stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserGroupMembershipAddRequestADM(
                            userIri = checkedUserIri,
                            groupIri = checkedGroupIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                } ~
                delete {
                    /* remove user from group */
                    requestContext =>
                        val checkedUserIri = stringFormatter.validateAndEscapeIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                        val checkedGroupIri = stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield UserGroupMembershipRemoveRequestADM(
                            userIri = checkedUserIri,
                            groupIri = checkedGroupIri,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            }
}
