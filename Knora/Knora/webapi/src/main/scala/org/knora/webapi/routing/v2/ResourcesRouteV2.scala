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
import org.knora.webapi.messages.v2.responder.resourcemessages.{ResourceTEIGetRequestV2, ResourcesGetRequestV2, ResourcesPreviewGetRequestV2}
import org.knora.webapi.routing.v2.OntologiesRouteV2.getUserADM
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.{BadRequestException, IRI, InternalSchema, SettingsImpl}

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

/**
  * Provides a routing function for API v2 routes that deal with resources.
  */
object ResourcesRouteV2 extends Authenticator {
    private val Text_Property = "textProperty"
    private val Mapping_Iri = "mappingIri"
    private val GravsearchTemplate_Iri = "gravsearchTemplateIri"
    private val TEIHeader_XSLT_IRI = "teiHeaderXSLTIri"

    /**
      * Gets the Iri of the property that represents the text of the resource.
      *
      * @param params the GET parameters.
      * @return the internal resource class, if any.
      */
    private def getTextPropertyFromParams(params: Map[String, String]): SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val textProperty = params.get(Text_Property)

        textProperty match {
            case Some(textPropIriStr: String) =>
                val externalResourceClassIri = textPropIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $textPropIriStr"))

                if (!externalResourceClassIri.isKnoraApiV2EntityIri) {
                    throw BadRequestException(s"$textPropIriStr is not a valid knora-api property IRI")
                }

                externalResourceClassIri.toOntologySchema(InternalSchema)

            case None => throw BadRequestException(s"param $Text_Property not set")
        }
    }

    /**
      * Gets the Iri of the mapping to be used to convert standoff to XML.
      *
      * @param params the GET parameters.
      * @return the internal resource class, if any.
      */
    private def getMappingIriFromParams(params: Map[String, String]): Option[IRI] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val mappingIriStr = params.get(Mapping_Iri)

        mappingIriStr match {
            case Some(mapping: String) =>
                Some(stringFormatter.validateAndEscapeIri(mapping, throw BadRequestException(s"Invalid mapping IRI: '$mapping'")))

            case None => None
        }
    }

    /**
      * Gets the Iri of Gravsearch template to be used to query for the resource's metadata.
      *
      * @param params the GET parameters.
      * @return the internal resource class, if any.
      */
    private def getGravsearchTemplateIriFromParams(params: Map[String, String]): Option[IRI] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val gravsearchTemplateIriStr = params.get(GravsearchTemplate_Iri)

        gravsearchTemplateIriStr match {
            case Some(gravsearch: String) =>
                Some(stringFormatter.validateAndEscapeIri(gravsearch, throw BadRequestException(s"Invalid template IRI: '$gravsearch'")))

            case None => None
        }
    }

    /**
      * Gets the Iri of the XSL transformation to be used to convert the TEI header's metadata.
      *
      * @param params the GET parameters.
      * @return the internal resource class, if any.
      */
    private def getHeaderXSLTIriFromParams(params: Map[String, String]): Option[IRI] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val headerXSLTIriStr = params.get(TEIHeader_XSLT_IRI)

        headerXSLTIriStr match {
            case Some(xslt: String) =>
                Some(stringFormatter.validateAndEscapeIri(xslt, throw BadRequestException(s"Invalid XSLT IRI: '$xslt'")))

            case None => None
        }
    }


    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getGeneralInstance

        path("v2" / "resources" / Segments) { (resIris: Seq[String]) =>
            get {
                requestContext => {

                    if (resIris.size > settings.v2ResultsPerPage) throw BadRequestException(s"List of provided resource Iris exceeds limit of ${settings.v2ResultsPerPage}")

                    val resourceIris: Seq[IRI] = resIris.map {
                        resIri: String =>
                            stringFormatter.validateAndEscapeIri(resIri, throw BadRequestException(s"Invalid resource IRI: '$resIri'"))
                    }

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ResourcesGetRequestV2(resourceIris = resourceIris, requestingUser = requestingUser)

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
        } ~ path("v2" / "resourcespreview" / Segments) { (resIris: Seq[String]) =>
            get {
                requestContext => {
                    if (resIris.size > settings.v2ResultsPerPage) throw BadRequestException(s"List of provided resource Iris exceeds limit of ${settings.v2ResultsPerPage}")

                    val resourceIris: Seq[IRI] = resIris.map {
                        resIri: String =>
                            stringFormatter.validateAndEscapeIri(resIri, throw BadRequestException(s"Invalid resource IRI: '$resIri'"))
                    }

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ResourcesPreviewGetRequestV2(resourceIris = resourceIris, requestingUser = requestingUser)

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

        } ~ path("v2" / "tei" / Segment) { (resIri: String) =>
            get {
                requestContext => {

                    val resourceIri = stringFormatter.validateAndEscapeIri(resIri, throw BadRequestException(s"Invalid resource IRI: '$resIri'"))

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    // the the property that represents the text
                    val textProperty: SmartIri = getTextPropertyFromParams(params)

                    val mappingIri: Option[IRI] = getMappingIriFromParams(params)

                    val gravsearchTemplateIri: Option[IRI] = getGravsearchTemplateIriFromParams(params)

                    val headerXSLTIri = getHeaderXSLTIriFromParams(params)

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ResourceTEIGetRequestV2(
                        resourceIri = resourceIri,
                        textProperty = textProperty,
                        mappingIri = mappingIri,
                        gravsearchTemplateIri = gravsearchTemplateIri,
                        headerXSLTIri = headerXSLTIri,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runTEIXMLRoute(
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