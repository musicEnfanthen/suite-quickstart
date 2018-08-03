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

package org.knora.webapi.routing.v2

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.search.gravsearch.GravsearchParser
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi._
import org.knora.webapi.routing.v2.ResourcesRouteV2.getUserADM

import scala.concurrent.ExecutionContextExecutor

/**
  * Provides a function for API routes that deal with search.
  */
object SearchRouteV2 extends Authenticator {
    private val LIMIT_TO_PROJECT = "limitToProject"
    private val LIMIT_TO_RESOURCE_CLASS = "limitToResourceClass"
    private val OFFSET = "offset"
    private val LIMIT_TO_STANDOFF_CLASS = "limitToStandoffClass"

    /**
      * Gets the requested offset. Returns zero if no offset is indicated.
      *
      * @param params the GET parameters.
      * @return the offset to be used for paging.
      */
    private def getOffsetFromParams(params: Map[String, String]): Int = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val offsetStr = params.get(OFFSET)

        offsetStr match {
            case Some(offset: String) =>

                val offsetInt: Int = stringFormatter.validateInt(offset, throw BadRequestException(s"offset is expected to be an Integer, but $offset given"))

                if (offsetInt < 0) throw BadRequestException(s"offset must be an Integer >= 0, but $offsetInt given.")

                offsetInt


            case None => 0
        }
    }

    /**
      * Gets the the project the search should be restricted to, if any.
      *
      * @param params the GET parameters.
      * @return the project Iri, if any.
      */
    private def getProjectFromParams(params: Map[String, String]): Option[IRI] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val limitToProjectIriStr = params.get(LIMIT_TO_PROJECT)

        val limitToProjectIri: Option[IRI] = limitToProjectIriStr match {

            case Some(projectIriStr: String) =>
                val projectIri = stringFormatter.validateAndEscapeIri(projectIriStr, throw BadRequestException(s"$projectIriStr is not a valid Iri"))

                Some(projectIri)

            case None => None

        }

        limitToProjectIri

    }

    /**
      * Gets the resource class the search should be restricted to, if any.
      *
      * @param params the GET parameters.
      * @return the internal resource class, if any.
      */
    private def getResourceClassFromParams(params: Map[String, String]): Option[SmartIri] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val limitToResourceClassIriStr = params.get(LIMIT_TO_RESOURCE_CLASS)

        limitToResourceClassIriStr match {
            case Some(resourceClassIriStr: String) =>
                val externalResourceClassIri = resourceClassIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid resource class IRI: $resourceClassIriStr"))

                if (!externalResourceClassIri.isKnoraApiV2EntityIri) {
                    throw BadRequestException(s"$resourceClassIriStr is not a valid knora-api resource class IRI")
                }

                Some(externalResourceClassIri.toOntologySchema(InternalSchema))

            case None => None
        }
    }

    /**
      * Gets the standoff class the search should be restricted to.
      *
      * @param params the GET parameters.
      * @return the internal standoff class, if any.
      */
    private def getStandoffClass(params: Map[String, String]): Option[SmartIri] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val limitToStandoffClassIriStr: Option[String] = params.get(LIMIT_TO_STANDOFF_CLASS)

        limitToStandoffClassIriStr match {
            case Some(standoffClassIriStr: String) =>
                val externalStandoffClassIri = standoffClassIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid standoff class IRI: $limitToStandoffClassIriStr"))

                if (!externalStandoffClassIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
                    throw BadRequestException(s"$externalStandoffClassIri is not a valid standoff class IRI")
                }

                Some(externalStandoffClassIri.toOntologySchema(InternalSchema))

            case None => None
        }
    }

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val responderManager = system.actorSelection("/user/responderManager")

        path("v2" / "search" / "count" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext =>

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, throw BadRequestException(s"Invalid search string: '$searchval'"))

                    if (searchString.length < settings.searchValueMinLength) {
                        throw BadRequestException(s"A search value is expected to have at least length of ${settings.searchValueMinLength}, but '$searchString' given of length ${searchString.length}.")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    val limitToProject: Option[IRI] = getProjectFromParams(params)

                    val limitToResourceClass: Option[SmartIri] = getResourceClassFromParams(params)

                    val limitToStandoffClass: Option[SmartIri] = getStandoffClass(params)

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield FullTextSearchCountRequestV2(
                        searchValue = searchString,
                        limitToProject = limitToProject,
                        limitToResourceClass = limitToResourceClass,
                        limitToStandoffClass = limitToStandoffClass,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
            }
        } ~ path("v2" / "search" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext => {

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, throw BadRequestException(s"Invalid search string: '$searchval'"))

                    if (searchString.length < settings.searchValueMinLength) {
                        throw BadRequestException(s"A search value is expected to have at least length of ${settings.searchValueMinLength}, but '$searchString' given of length ${searchString.length}.")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    val offset = getOffsetFromParams(params)

                    val limitToProject: Option[IRI] = getProjectFromParams(params)

                    val limitToResourceClass: Option[SmartIri] = getResourceClassFromParams(params)

                    val limitToStandoffClass: Option[SmartIri] = getStandoffClass(params)

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield FulltextSearchRequestV2(
                        searchValue = searchString,
                        offset = offset,
                        limitToProject = limitToProject,
                        limitToResourceClass = limitToResourceClass,
                        limitToStandoffClass,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
                }
            }
        } ~ path("v2" / "searchextended" / "count") {
            post {
                entity(as[String]) { gravsearchQuery =>
                    requestContext => {
                        val constructQuery = GravsearchParser.parseQuery(gravsearchQuery)
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield GravsearchCountRequestV2(constructQuery = constructQuery, requestingUser = requestingUser)

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            RouteUtilV2.getOntologySchema(requestContext)
                        )
                    }
                }
            }
        } ~ path("v2" / "searchextended" / "count" / Segment) { gravsearchQuery => // Segment is a URL encoded string representing a Gravsearch query
            get {

                requestContext => {
                    val constructQuery = GravsearchParser.parseQuery(gravsearchQuery)
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield GravsearchCountRequestV2(constructQuery = constructQuery, requestingUser = requestingUser)

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
                }
            }
        } ~ path("v2" / "searchextended") {
            post {
                entity(as[String]) { gravsearchQuery =>
                    requestContext => {
                        val constructQuery = GravsearchParser.parseQuery(gravsearchQuery)
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield GravsearchRequestV2(constructQuery = constructQuery, requestingUser = requestingUser)

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            RouteUtilV2.getOntologySchema(requestContext)
                        )
                    }
                }
            }
        } ~ path("v2" / "searchextended" / Segment) { sparql => // Segment is a URL encoded string representing a Gravsearch query
            get {

                requestContext => {
                    val constructQuery = GravsearchParser.parseQuery(sparql)
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield GravsearchRequestV2(constructQuery = constructQuery, requestingUser = requestingUser)

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
                }

            }


        } ~ path("v2" / "searchbylabel" / "count" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext => {

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, throw BadRequestException(s"Invalid search string: '$searchval'"))

                    if (searchString.length < settings.searchValueMinLength) {
                        throw BadRequestException(s"A search value is expected to have at least length of ${settings.searchValueMinLength}, but '$searchString' given of length ${searchString.length}.")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    val limitToProject: Option[IRI] = getProjectFromParams(params)

                    val limitToResourceClass: Option[SmartIri] = getResourceClassFromParams(params)

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield SearchResourceByLabelCountRequestV2(
                        searchValue = searchString,
                        limitToProject = limitToProject,
                        limitToResourceClass = limitToResourceClass,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
                }
            }
        } ~ path("v2" / "searchbylabel" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext => {

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, throw BadRequestException(s"Invalid search string: '$searchval'"))

                    if (searchString.length < settings.searchValueMinLength) {
                        throw BadRequestException(s"A search value is expected to have at least length of ${settings.searchValueMinLength}, but '$searchString' given of length ${searchString.length}.")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    val offset = getOffsetFromParams(params)

                    val limitToProject: Option[IRI] = getProjectFromParams(params)

                    val limitToResourceClass: Option[SmartIri] = getResourceClassFromParams(params)

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield SearchResourceByLabelRequestV2(
                        searchValue = searchString,
                        offset = offset,
                        limitToProject = limitToProject,
                        limitToResourceClass = limitToResourceClass,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )
                }
            }

        }
    }


}