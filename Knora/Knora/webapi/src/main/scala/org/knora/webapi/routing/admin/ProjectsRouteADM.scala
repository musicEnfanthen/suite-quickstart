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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.swagger.annotations.Api
import javax.ws.rs.Path
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilADM}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, SettingsImpl}

import scala.concurrent.ExecutionContextExecutor


@Api(value = "projects", produces = "application/json")
@Path("/admin/projects")
class ProjectsRouteADM(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter) extends Authenticator with ProjectsADMJsonProtocol {

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = settings.defaultTimeout
    val responderManager = system.actorSelection("/user/responderManager")
    val stringFormatter = StringFormatter.getGeneralInstance

    def knoraApiPath: Route = {



        path("admin" / "projects") {
            get {
                /* returns all projects */
                requestContext =>
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ProjectsGetRequestADM(requestingUser = requestingUser)
                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            post {
                /* create a new project */
                entity(as[CreateProjectApiRequestADM]) { apiRequest =>
                    requestContext =>
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield ProjectCreateRequestADM(
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
        } ~ path("admin" / "projects" / "keywords") {
            get {
                /* returns all unique keywords for all projects as a list */
                requestContext =>

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ProjectsKeywordsGetRequestADM(requestingUser = requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("admin" / "projects" / "keywords" / Segment) { value =>
            get {
                /* returns all keywords for a single project */
                requestContext =>
                    val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ProjectKeywordsGetRequestADM(projectIri = checkedProjectIri, requestingUser = requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("admin" / "projects" / Segment) { value =>
            get {
                /* returns a single project identified either through iri, shortname, or shortcode */
                parameters("identifier" ? "iri") { identifier: String =>
                    requestContext =>
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield if (identifier == "shortname") { // identify project by shortname.
                            val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
                            ProjectGetRequestADM(maybeIri = None, maybeShortname = Some(shortNameDec), maybeShortcode = None, requestingUser = requestingUser)
                        } else if (identifier == "shortcode") {
                            val shortcodeDec = java.net.URLDecoder.decode(value, "utf-8")
                            ProjectGetRequestADM(maybeIri = None, maybeShortname = None, maybeShortcode = Some(shortcodeDec), requestingUser = requestingUser)
                        } else { // identify project by iri. this is the default case.
                            val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))
                            ProjectGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)
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
            put {
                /* update a project identified by iri */
                entity(as[ChangeProjectApiRequestADM]) { apiRequest =>
                    requestContext =>
                        val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                        /* the api request is already checked at time of creation. see case class. */

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield ProjectChangeRequestADM(
                            projectIri = checkedProjectIri,
                            changeProjectRequest = apiRequest,
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
            delete {
                /* update project status to false */
                requestContext =>
                    val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ProjectChangeRequestADM(
                        projectIri = checkedProjectIri,
                        changeProjectRequest = ChangeProjectApiRequestADM(status = Some(false)),
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
        } ~ path("admin" / "projects" / "members" / Segment) { value =>
            get {
                /* returns all members part of a project identified through iri or shortname */
                parameters("identifier" ? "iri") { identifier: String =>
                    requestContext =>
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield if (identifier != "iri") {
                            // identify project by shortname.
                            val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
                            ProjectMembersGetRequestADM(maybeIri = None, maybeShortname = Some(shortNameDec), maybeShortcode = None, requestingUser = requestingUser)
                        } else {
                            val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))
                            ProjectMembersGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)
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
        } ~ path("admin" / "projects" / "admin-members" / Segment) { value =>
            get {
                /* returns all admin members part of a project identified through iri or shortname */
                parameters("identifier" ? "iri") { identifier: String =>
                    requestContext =>
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield if (identifier != "iri") {
                            // identify project by shortname.
                            val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
                            ProjectAdminMembersGetRequestADM(maybeIri = None, maybeShortname = Some(shortNameDec), maybeShortcode = None, requestingUser = requestingUser)
                        } else {
                            val checkedProjectIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))
                            ProjectAdminMembersGetRequestADM(maybeIri = Some(checkedProjectIri), maybeShortname = None, maybeShortcode = None, requestingUser = requestingUser)
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
    }
}
