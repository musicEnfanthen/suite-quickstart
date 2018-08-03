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

package org.knora.webapi.routing.v1

import java.io.File
import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiResponderConversionFileRequestV1, SipiResponderConversionPathRequestV1}
import org.knora.webapi.messages.v1.responder.valuemessages.ApiValueV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import org.knora.webapi.util.{DateUtilV1, FileUtil, StringFormatter}

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}

/**
  * Provides a spray-routing function for API routes that deal with values.
  */
object ValuesRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, loggingAdapter: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getGeneralInstance

        def makeVersionHistoryRequestMessage(iris: Seq[IRI], userADM: UserADM): ValueVersionHistoryGetRequestV1 = {
            if (iris.length != 3) throw BadRequestException("Version history request requires resource IRI, property IRI, and current value IRI")

            val Seq(resourceIriStr, propertyIriStr, currentValueIriStr) = iris

            val resourceIri = stringFormatter.validateAndEscapeIri(resourceIriStr, throw BadRequestException(s"Invalid resource IRI: $resourceIriStr"))
            val propertyIri = stringFormatter.validateAndEscapeIri(propertyIriStr, throw BadRequestException(s"Invalid property IRI: $propertyIriStr"))
            val currentValueIri = stringFormatter.validateAndEscapeIri(currentValueIriStr, throw BadRequestException(s"Invalid value IRI: $currentValueIriStr"))

            ValueVersionHistoryGetRequestV1(
                resourceIri = resourceIri,
                propertyIri = propertyIri,
                currentValueIri = currentValueIri,
                userProfile = userADM
            )
        }

        def makeLinkValueGetRequestMessage(iris: Seq[IRI], userADM: UserADM): LinkValueGetRequestV1 = {
            if (iris.length != 3) throw BadRequestException("Link value request requires subject IRI, predicate IRI, and object IRI")

            val Seq(subjectIriStr, predicateIriStr, objectIriStr) = iris

            val subjectIri = stringFormatter.validateAndEscapeIri(subjectIriStr, throw BadRequestException(s"Invalid subject IRI: $subjectIriStr"))
            val predicateIri = stringFormatter.validateAndEscapeIri(predicateIriStr, throw BadRequestException(s"Invalid predicate IRI: $predicateIriStr"))
            val objectIri = stringFormatter.validateAndEscapeIri(objectIriStr, throw BadRequestException(s"Invalid object IRI: $objectIriStr"))

            LinkValueGetRequestV1(
                subjectIri = subjectIri,
                predicateIri = predicateIri,
                objectIri = objectIri,
                userProfile = userADM
            )
        }

        def makeCreateValueRequestMessage(apiRequest: CreateValueApiRequestV1, userADM: UserADM): Future[CreateValueRequestV1] = {
            val resourceIri = stringFormatter.validateAndEscapeIri(apiRequest.res_id, throw BadRequestException(s"Invalid resource IRI ${apiRequest.res_id}"))
            val propertyIri = stringFormatter.validateAndEscapeIri(apiRequest.prop, throw BadRequestException(s"Invalid property IRI ${apiRequest.prop}"))

            for {
                (value: UpdateValueV1, commentStr: Option[String]) <- apiRequest.getValueClassIri match {

                    case OntologyConstants.KnoraBase.TextValue =>
                        val richtext: CreateRichtextV1 = apiRequest.richtext_value.get

                        // check if text has markup
                        if (richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty) {
                            // simple text
                            Future((TextValueSimpleV1(stringFormatter.toSparqlEncodedString(richtext.utf8str.get, throw BadRequestException(s"Invalid text: '${richtext.utf8str.get}'")), richtext.language),
                                apiRequest.comment))
                        } else if (richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty) {
                            // XML: text with markup

                            val mappingIri = stringFormatter.validateAndEscapeIri(richtext.mapping_id.get, throw BadRequestException(s"mapping_id ${richtext.mapping_id.get} is invalid"))

                            for {

                                textWithStandoffTags: TextWithStandoffTagsV2 <- RouteUtilV1.convertXMLtoStandoffTagV1(
                                    xml = richtext.xml.get,
                                    mappingIri = mappingIri,
                                    acceptStandoffLinksToClientIDs = false,
                                    userProfile = userADM,
                                    settings = settings,
                                    responderManager = responderManager,
                                    log = loggingAdapter
                                )

                                // collect the resource references from the linking standoff nodes
                                resourceReferences: Set[IRI] = stringFormatter.getResourceIrisFromStandoffTags(textWithStandoffTags.standoffTagV2)

                            } yield (TextValueWithStandoffV1(
                                utf8str = stringFormatter.toSparqlEncodedString(textWithStandoffTags.text, throw InconsistentTriplestoreDataException("utf8str for for TextValue contains invalid characters")),
                                language = textWithStandoffTags.language,
                                resource_reference = resourceReferences,
                                standoff = textWithStandoffTags.standoffTagV2,
                                mappingIri = textWithStandoffTags.mapping.mappingIri,
                                mapping = textWithStandoffTags.mapping.mapping
                            ), apiRequest.comment)

                        }
                        else {
                            throw BadRequestException("invalid parameters given for TextValueV1")
                        }

                    case OntologyConstants.KnoraBase.LinkValue =>
                        val resourceIRI = stringFormatter.validateAndEscapeIri(apiRequest.link_value.get, throw BadRequestException(s"Invalid resource IRI: ${apiRequest.link_value.get}"))
                        Future(LinkUpdateV1(targetResourceIri = resourceIRI), apiRequest.comment)

                    case OntologyConstants.KnoraBase.IntValue =>
                        Future((IntegerValueV1(apiRequest.int_value.get), apiRequest.comment))

                    case OntologyConstants.KnoraBase.DecimalValue =>
                        Future((DecimalValueV1(apiRequest.decimal_value.get), apiRequest.comment))

                    case OntologyConstants.KnoraBase.BooleanValue =>
                        Future(BooleanValueV1(apiRequest.boolean_value.get), apiRequest.comment)

                    case OntologyConstants.KnoraBase.UriValue =>
                        Future((UriValueV1(stringFormatter.validateAndEscapeIri(apiRequest.uri_value.get, throw BadRequestException(s"Invalid URI: ${apiRequest.uri_value.get}"))), apiRequest.comment))

                    case OntologyConstants.KnoraBase.DateValue =>
                        Future(DateUtilV1.createJDNValueV1FromDateString(apiRequest.date_value.get), apiRequest.comment)

                    case OntologyConstants.KnoraBase.ColorValue =>
                        val colorValue = stringFormatter.validateColor(apiRequest.color_value.get, throw BadRequestException(s"Invalid color value: ${apiRequest.color_value.get}"))
                        Future(ColorValueV1(colorValue), apiRequest.comment)

                    case OntologyConstants.KnoraBase.GeomValue =>
                        val geometryValue = stringFormatter.validateGeometryString(apiRequest.geom_value.get, throw BadRequestException(s"Invalid geometry value: ${apiRequest.geom_value.get}"))
                        Future(GeomValueV1(geometryValue), apiRequest.comment)

                    case OntologyConstants.KnoraBase.ListValue =>
                        val listNodeIri = stringFormatter.validateAndEscapeIri(apiRequest.hlist_value.get, throw BadRequestException(s"Invalid value IRI: ${apiRequest.hlist_value.get}"))
                        Future(HierarchicalListValueV1(listNodeIri), apiRequest.comment)

                    case OntologyConstants.KnoraBase.IntervalValue =>
                        val timeVals: Seq[BigDecimal] = apiRequest.interval_value.get

                        if (timeVals.length != 2) throw BadRequestException("parameters for interval_value invalid")

                        Future(IntervalValueV1(timeVals(0), timeVals(1)), apiRequest.comment)

                    case OntologyConstants.KnoraBase.GeonameValue =>
                        Future(GeonameValueV1(apiRequest.geom_value.get), apiRequest.comment)

                    case _ => throw BadRequestException(s"No value submitted")
                }
            } yield
                CreateValueRequestV1(
                    resourceIri = resourceIri,
                    propertyIri = propertyIri,
                    value = value,
                    comment = commentStr.map(str => stringFormatter.toSparqlEncodedString(str, throw BadRequestException(s"Invalid comment: '$str'"))),
                    userProfile = userADM,
                    apiRequestID = UUID.randomUUID
                )
        }

        def makeAddValueVersionRequestMessage(valueIriStr: IRI, apiRequest: ChangeValueApiRequestV1, userADM: UserADM): Future[ChangeValueRequestV1] = {
            val valueIri = stringFormatter.validateAndEscapeIri(valueIriStr, throw BadRequestException(s"Invalid value IRI: $valueIriStr"))

            for {
                (value: UpdateValueV1, commentStr: Option[String]) <- apiRequest.getValueClassIri match {

                    case OntologyConstants.KnoraBase.TextValue =>
                        val richtext: CreateRichtextV1 = apiRequest.richtext_value.get

                        // check if text has markup
                        if (richtext.utf8str.nonEmpty && richtext.xml.isEmpty && richtext.mapping_id.isEmpty) {
                            // simple text
                            Future((TextValueSimpleV1(stringFormatter.toSparqlEncodedString(richtext.utf8str.get, throw BadRequestException(s"Invalid text: '${richtext.utf8str.get}'")), richtext.language),
                                apiRequest.comment))
                        } else if (richtext.xml.nonEmpty && richtext.mapping_id.nonEmpty) {
                            // XML: text with markup

                            val mappingIri = stringFormatter.validateAndEscapeIri(richtext.mapping_id.get, throw BadRequestException(s"mapping_id ${richtext.mapping_id.get} is invalid"))

                            for {

                                textWithStandoffTags: TextWithStandoffTagsV2 <- RouteUtilV1.convertXMLtoStandoffTagV1(
                                    xml = richtext.xml.get,
                                    mappingIri = mappingIri,
                                    acceptStandoffLinksToClientIDs = false,
                                    userProfile = userADM,
                                    settings = settings,
                                    responderManager = responderManager,
                                    log = loggingAdapter
                                )

                                // collect the resource references from the linking standoff nodes
                                resourceReferences: Set[IRI] = stringFormatter.getResourceIrisFromStandoffTags(textWithStandoffTags.standoffTagV2)

                            } yield (TextValueWithStandoffV1(
                                utf8str = stringFormatter.toSparqlEncodedString(textWithStandoffTags.text, throw InconsistentTriplestoreDataException("utf8str for for TextValue contains invalid characters")),
                                language = richtext.language,
                                resource_reference = resourceReferences,
                                standoff = textWithStandoffTags.standoffTagV2,
                                mappingIri = textWithStandoffTags.mapping.mappingIri,
                                mapping = textWithStandoffTags.mapping.mapping
                            ), apiRequest.comment)

                        }
                        else {
                            throw BadRequestException("invalid parameters given for TextValueV1")
                        }

                    case OntologyConstants.KnoraBase.LinkValue =>
                        val resourceIRI = stringFormatter.validateAndEscapeIri(apiRequest.link_value.get, throw BadRequestException(s"Invalid resource IRI: ${apiRequest.link_value.get}"))
                        Future(LinkUpdateV1(targetResourceIri = resourceIRI), apiRequest.comment)

                    case OntologyConstants.KnoraBase.IntValue =>
                        Future((IntegerValueV1(apiRequest.int_value.get), apiRequest.comment))

                    case OntologyConstants.KnoraBase.DecimalValue =>
                        Future((DecimalValueV1(apiRequest.decimal_value.get), apiRequest.comment))

                    case OntologyConstants.KnoraBase.BooleanValue =>
                        Future(BooleanValueV1(apiRequest.boolean_value.get), apiRequest.comment)

                    case OntologyConstants.KnoraBase.UriValue =>
                        Future((UriValueV1(stringFormatter.validateAndEscapeIri(apiRequest.uri_value.get, throw BadRequestException(s"Invalid URI: ${apiRequest.uri_value.get}"))), apiRequest.comment))

                    case OntologyConstants.KnoraBase.DateValue =>
                        Future(DateUtilV1.createJDNValueV1FromDateString(apiRequest.date_value.get), apiRequest.comment)

                    case OntologyConstants.KnoraBase.ColorValue =>
                        val colorValue = stringFormatter.validateColor(apiRequest.color_value.get, throw BadRequestException(s"Invalid color value: ${apiRequest.color_value.get}"))
                        Future(ColorValueV1(colorValue), apiRequest.comment)

                    case OntologyConstants.KnoraBase.GeomValue =>
                        val geometryValue = stringFormatter.validateGeometryString(apiRequest.geom_value.get, throw BadRequestException(s"Invalid geometry value: ${apiRequest.geom_value.get}"))
                        Future(GeomValueV1(geometryValue), apiRequest.comment)

                    case OntologyConstants.KnoraBase.ListValue =>
                        val listNodeIri = stringFormatter.validateAndEscapeIri(apiRequest.hlist_value.get, throw BadRequestException(s"Invalid value IRI: ${apiRequest.hlist_value.get}"))
                        Future(HierarchicalListValueV1(listNodeIri), apiRequest.comment)

                    case OntologyConstants.KnoraBase.IntervalValue =>
                        val timeVals: Seq[BigDecimal] = apiRequest.interval_value.get

                        if (timeVals.length != 2) throw BadRequestException("parameters for interval_value invalid")

                        Future(IntervalValueV1(timeVals(0), timeVals(1)), apiRequest.comment)

                    case OntologyConstants.KnoraBase.GeonameValue =>
                        Future(GeonameValueV1(apiRequest.geom_value.get), apiRequest.comment)

                    case _ => throw BadRequestException(s"No value submitted")
                }
            } yield ChangeValueRequestV1(
                valueIri = valueIri,
                value = value,
                comment = commentStr.map(str => stringFormatter.toSparqlEncodedString(str, throw BadRequestException(s"Invalid comment: '$str'"))),
                userProfile = userADM,
                apiRequestID = UUID.randomUUID
            )
        }

        def makeChangeCommentRequestMessage(valueIriStr: IRI, comment: Option[String], userADM: UserADM): ChangeCommentRequestV1 = {
            ChangeCommentRequestV1(
                valueIri = stringFormatter.validateAndEscapeIri(valueIriStr, throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
                comment = comment.map(str => stringFormatter.toSparqlEncodedString(str, throw BadRequestException(s"Invalid comment: '$str'"))),
                userProfile = userADM,
                apiRequestID = UUID.randomUUID
            )
        }

        def makeDeleteValueRequest(valueIriStr: IRI, deleteComment: Option[String], userADM: UserADM): DeleteValueRequestV1 = {
            DeleteValueRequestV1(
                valueIri = stringFormatter.validateAndEscapeIri(valueIriStr, throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
                deleteComment = deleteComment.map(comment => stringFormatter.toSparqlEncodedString(comment, throw BadRequestException(s"Invalid comment: '$comment'"))),
                userProfile = userADM,
                apiRequestID = UUID.randomUUID
            )
        }

        def makeGetValueRequest(valueIriStr: IRI, userADM: UserADM): ValueGetRequestV1 = {
            ValueGetRequestV1(
                stringFormatter.validateAndEscapeIri(valueIriStr, throw BadRequestException(s"Invalid value IRI: $valueIriStr")),
                userADM
            )
        }

        def makeChangeFileValueRequest(resIriStr: IRI, apiRequest: Option[ChangeFileValueApiRequestV1], multipartConversionRequest: Option[SipiResponderConversionPathRequestV1], userADM: UserADM): ChangeFileValueRequestV1 = {
            if (apiRequest.nonEmpty && multipartConversionRequest.nonEmpty) throw BadRequestException("File information is present twice, only one is allowed.")

            val resourceIri = stringFormatter.validateAndEscapeIri(resIriStr, throw BadRequestException(s"Invalid resource IRI: $resIriStr"))

            if (apiRequest.nonEmpty) {
                // GUI-case
                val fileRequest = SipiResponderConversionFileRequestV1(
                    originalFilename = stringFormatter.toSparqlEncodedString(apiRequest.get.file.originalFilename, throw BadRequestException(s"The original filename is invalid: '${apiRequest.get.file.originalFilename}'")),
                    originalMimeType = stringFormatter.toSparqlEncodedString(apiRequest.get.file.originalMimeType, throw BadRequestException(s"The original MIME type is invalid: '${apiRequest.get.file.originalMimeType}'")),
                    filename = stringFormatter.toSparqlEncodedString(apiRequest.get.file.filename, throw BadRequestException(s"Invalid filename: '${apiRequest.get.file.filename}'")),
                    userProfile = userADM.asUserProfileV1
                )
                ChangeFileValueRequestV1(
                    resourceIri = resourceIri,
                    file = fileRequest,
                    apiRequestID = UUID.randomUUID,
                    userProfile = userADM)
            }
            else if (multipartConversionRequest.nonEmpty) {
                // non GUI-case
                ChangeFileValueRequestV1(
                    resourceIri = resourceIri,
                    file = multipartConversionRequest.get,
                    apiRequestID = UUID.randomUUID,
                    userProfile = userADM)
            } else {
                // no file information was provided
                throw BadRequestException("A file value change was requested but no file information was provided")
            }

        }

        // Version history request requires 3 URL path segments: resource IRI, property IRI, and current value IRI
        path("v1" / "values" / "history" / Segments) { iris =>
            get {
                requestContext => {
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                    } yield makeVersionHistoryRequestMessage(iris = iris, userADM = userADM)


                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "values") {
            post {
                entity(as[CreateValueApiRequestV1]) { apiRequest =>
                    requestContext =>
                        val requestMessageFuture = for {
                            userADM <- getUserADM(requestContext)
                            request <- makeCreateValueRequestMessage(apiRequest = apiRequest, userADM = userADM)
                        } yield request

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }
        } ~ path("v1" / "values" / Segment) { valueIriStr =>
            get {
                requestContext => {
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                    } yield makeGetValueRequest(valueIriStr = valueIriStr, userADM = userADM)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            } ~ put {
                entity(as[ChangeValueApiRequestV1]) { apiRequest =>
                    requestContext =>
                        // In API v1, you cannot change a value and its comment in a single request. So we know that here,
                        // we are getting a request to change either the value or the comment, but not both.
                        val requestMessageFuture = for {
                            userADM <- getUserADM(requestContext)
                            request <- apiRequest match {
                                case ChangeValueApiRequestV1(_, _, _, _, _, _, _, _, _, _, _, _, Some(comment)) => FastFuture.successful(makeChangeCommentRequestMessage(valueIriStr = valueIriStr, comment = Some(comment), userADM = userADM))
                                case _ => makeAddValueVersionRequestMessage(valueIriStr = valueIriStr, apiRequest = apiRequest, userADM = userADM)
                            }
                        } yield request

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            } ~ delete {
                requestContext => {
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                        params = requestContext.request.uri.query().toMap
                        deleteComment = params.get("deleteComment")
                    } yield makeDeleteValueRequest(valueIriStr = valueIriStr, deleteComment = deleteComment, userADM = userADM)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "valuecomments" / Segment) { valueIriStr =>
            delete {
                requestContext => {
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                    } yield makeChangeCommentRequestMessage(valueIriStr = valueIriStr, comment = None, userADM = userADM)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "links" / Segments) { iris =>
            // Link value request requires 3 URL path segments: subject IRI, predicate IRI, and object IRI
            get {
                requestContext => {
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                    } yield makeLinkValueGetRequestMessage(iris = iris, userADM = userADM)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "filevalue" / Segment) { (resIriStr: IRI) =>
            put {
                entity(as[ChangeFileValueApiRequestV1]) { apiRequest =>
                    requestContext =>

                        val requestMessage = for {
                            userADM <- getUserADM(requestContext)
                        } yield makeChangeFileValueRequest(resIriStr = resIriStr, apiRequest = Some(apiRequest), multipartConversionRequest = None, userADM = userADM)

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            } ~ put {
                entity(as[Multipart.FormData]) { formdata =>
                    requestContext =>

                        loggingAdapter.debug("/v1/filevalue - PUT - Multipart.FormData - Route")



                        val FILE_PART = "file"

                        type Name = String

                        val receivedFile = Promise[File]

                        // this file will be deleted by Knora once it is not needed anymore
                        // TODO: add a script that cleans files in the tmp location that have a certain age
                        // TODO  (in case they were not deleted by Knora which should not happen -> this has also to be implemented for Sipi for the thumbnails)
                        // TODO: how to check if the user has sent multiple files?

                        /* get the file data and save file to temporary location */
                        // collect all parts of the multipart as it arrives into a map
                        val allPartsFuture: Future[Map[Name, Any]] = formdata.parts.mapAsync[(Name, Any)](1) {
                            case b: BodyPart =>
                                if (b.name == FILE_PART) {
                                    loggingAdapter.debug(s"inside allPartsFuture - processing $FILE_PART")
                                    val filename = b.filename.getOrElse(throw BadRequestException(s"Filename is not given"))
                                    val tmpFile = FileUtil.createTempFile(settings)
                                    val written = b.entity.dataBytes.runWith(FileIO.toPath(tmpFile.toPath))
                                    written.map { written =>
                                        loggingAdapter.debug(s"written result: ${written.wasSuccessful}, ${b.filename.get}, ${tmpFile.getAbsolutePath}")
                                        receivedFile.success(tmpFile)
                                        (b.name, FileInfo(b.name, filename, b.entity.contentType))
                                    }
                                } else {
                                    throw BadRequestException(s"Unexpected body part '${b.name}' in multipart request")
                                }
                        }.runFold(Map.empty[Name, Any])((map, tuple) => map + tuple)

                        val requestMessageFuture = for {
                            userADM <- getUserADM(requestContext)
                            allParts <- allPartsFuture
                            sourcePath <- receivedFile.future
                            // get the file info containing the original filename and content type.
                            fileInfo = allParts.getOrElse(FILE_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$FILE_PART' part!")).asInstanceOf[FileInfo]
                            originalFilename = fileInfo.fileName
                            originalMimeType = fileInfo.contentType.toString

                            sipiConvertPathRequest = SipiResponderConversionPathRequestV1(
                                originalFilename = stringFormatter.toSparqlEncodedString(originalFilename, throw BadRequestException(s"The original filename is invalid: '$originalFilename'")),
                                originalMimeType = stringFormatter.toSparqlEncodedString(originalMimeType, throw BadRequestException(s"The original MIME type is invalid: '$originalMimeType'")),
                                source = sourcePath,
                                userProfile = userADM.asUserProfileV1
                            )

                        } yield makeChangeFileValueRequest(resIriStr = resIriStr, apiRequest = None, multipartConversionRequest = Some(sipiConvertPathRequest), userADM = userADM)

                        RouteUtilV1.runJsonRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }
        }
    }
}
