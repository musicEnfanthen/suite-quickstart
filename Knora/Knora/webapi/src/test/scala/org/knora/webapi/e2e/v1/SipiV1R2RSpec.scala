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

package org.knora.webapi.e2e.v1

import java.io.File
import java.net.URLEncoder
import java.nio.file.{Files, Paths}

import akka.actor.{Props, _}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.messages.v1.responder.resourcemessages.{CreateResourceApiRequestV1, CreateResourceValueV1}
import org.knora.webapi.messages.v1.responder.valuemessages.{ChangeFileValueApiRequestV1, CreateFileV1, CreateRichtextV1}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1._
import org.knora.webapi.routing.v1.{ResourcesRouteV1, ValuesRouteV1}
import org.knora.webapi.store._

import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * End-to-end test specification for the resources endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class SipiV1R2RSpec extends R2RSpec {

    override def testConfigSource: String =
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin



    private val responderManager = system.actorOf(Props(new TestResponderManager(Map(SIPI_ROUTER_V1_ACTOR_NAME -> system.actorOf(Props(new MockSipiResponderV1))))), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val resourcesPath = ResourcesRouteV1.knoraApiPath(system, settings, log)
    private val valuesPath = ValuesRouteV1.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout



    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(30).second)

    private val rootEmail = SharedTestDataV1.rootUser.userData.email.get
    private val incunabulaProjectAdminEmail = SharedTestDataV1.incunabulaProjectAdminUser.userData.email.get
    private val testPass = "test"

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 300.seconds)
        Await.result(responderManager ? LoadOntologiesRequest(SharedTestDataADM.rootUser), 30.seconds)
    }

    object RequestParams {

        val createResourceParams = CreateResourceApiRequestV1(
            restype_id = "http://www.knora.org/ontology/0803/incunabula#page",
            properties = Map(
                "http://www.knora.org/ontology/0803/incunabula#pagenum" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = Some("test_page")
                    ))
                )),
                "http://www.knora.org/ontology/0803/incunabula#origname" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = Some("test")
                    ))
                )),
                "http://www.knora.org/ontology/0803/incunabula#partOf" -> Seq(CreateResourceValueV1(
                    link_value = Some("http://rdfh.ch/5e77e98d2603")
                )),
                "http://www.knora.org/ontology/0803/incunabula#seqnum" -> Seq(CreateResourceValueV1(
                    int_value = Some(999)
                ))
            ),
            label = "test",
            project_id = "http://rdfh.ch/projects/0803"
        )

        val pathToFile = "_test_data/test_route/images/Chlaus.jpg"

        def createTmpFileDir(): Unit = {
            // check if tmp datadir exists and create it if not
            if (!Files.exists(Paths.get(settings.tmpDataDir))) {
                try {
                    val tmpDir = new File(settings.tmpDataDir)
                    tmpDir.mkdir()
                } catch {
                    case e: Throwable => throw FileWriteException(s"Tmp data directory ${settings.tmpDataDir} could not be created: ${e.getMessage}")
                }
            }
        }

    }

    "The Resources Endpoint" should {

        "create a resource with a digital representation doing a multipart request containing the binary data (non GUI-case)" in {

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.createResourceParams.toJsValue.compactPrint)
                ),
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            RequestParams.createTmpFileDir()

            Post("/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> resourcesPath ~> check {

                val tmpFile = SourcePath.getSourcePath()

                //println("response in test: " + responseAs[String])
                assert(!tmpFile.exists(), s"Tmp file $tmpFile was not deleted.")
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }
        }

        "try to create a resource sending binaries (multipart request) but fail because the mimetype is wrong" in {

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(MediaTypes.`application/json`, RequestParams.createResourceParams.toJsValue.compactPrint)
                ),
                // set mimetype tiff, but jpeg is expected
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/tiff`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            RequestParams.createTmpFileDir()

            Post("/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> Route.seal(resourcesPath) ~> check {

                val tmpFile = SourcePath.getSourcePath()

                // this test is expected to fail

                // check that the tmp file is also deleted in case the test fails
                assert(!tmpFile.exists(), s"Tmp file $tmpFile was not deleted.")
                //FIXME: Check for correct status code. This would then also test if the negative case is handled correctly inside Knora.
                assert(status != StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }
        }

        "create a resource with a digital representation doing a params only request without binary data (GUI-case)" in {

            val params = RequestParams.createResourceParams.copy(
                file = Some(CreateFileV1(
                    originalFilename = "Chlaus.jpg",
                    originalMimeType = "image/jpeg",
                    filename = "./test_server/images/Chlaus.jpg"
                ))
            )

            Post("/v1/resources", HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint)) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> resourcesPath ~> check {
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }
        }

    }

    "The Values endpoint" should {

        "change the file value of an existing page (submitting binaries)" in {

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            RequestParams.createTmpFileDir()

            val resIri = URLEncoder.encode("http://rdfh.ch/8a0b1e75", "UTF-8")

            Put("/v1/filevalue/" + resIri, formData) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> valuesPath ~> check {

                val tmpFile = SourcePath.getSourcePath()

                assert(!tmpFile.exists(), s"Tmp file $tmpFile was not deleted.")
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }

        }

        "try to change the file value of an existing page (submitting binaries) but fail because the mimetype is wrong" in {

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = Multipart.FormData(
                // set mimetype tiff, but jpeg is expected
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/tiff`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            RequestParams.createTmpFileDir()

            val resIri = URLEncoder.encode("http://rdfh.ch/8a0b1e75", "UTF-8")

            Put("/v1/filevalue/" + resIri, formData) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> valuesPath ~> check {

                val tmpFile = SourcePath.getSourcePath()

                // this test is expected to fail

                // check that the tmp file is also deleted in case the test fails
                assert(!tmpFile.exists(), s"Tmp file $tmpFile was not deleted.")
                //FIXME: Check for correct status code. This would then also test if the negative case is handled correctly inside Knora.
                assert(status != StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }

        }


        "change the file value of an existing page (submitting params only, no binaries)" in {

            val params = ChangeFileValueApiRequestV1(
                file = CreateFileV1(
                    originalFilename = "Chlaus.jpg",
                    originalMimeType = "image/jpeg",
                    filename = "./test_server/images/Chlaus.jpg"
                )
            )

            val resIri = URLEncoder.encode("http://rdfh.ch/8a0b1e75", "UTF-8")

            Put("/v1/filevalue/" + resIri, HttpEntity(MediaTypes.`application/json`, params.toJsValue.compactPrint)) ~> addCredentials(BasicHttpCredentials(incunabulaProjectAdminEmail, testPass)) ~> valuesPath ~> check {
                assert(status == StatusCodes.OK, "Status code is not set to OK, Knora says:\n" + responseAs[String])
            }

        }
    }
}
