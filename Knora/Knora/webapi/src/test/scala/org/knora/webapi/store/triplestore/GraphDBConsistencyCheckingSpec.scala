package org.knora.webapi.store.triplestore

import akka.actor.Props
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK, SparqlUpdateRequest}
import org.knora.webapi.store._
import org.knora.webapi.{CoreSpec, LiveActorMaker, TriplestoreResponseException}

import scala.concurrent.duration._

/**
  * Tests the GraphDB triplestore consistency checking rules in webapi/scripts/KnoraRules.pie.
  */
class GraphDBConsistencyCheckingSpec extends CoreSpec(GraphDBConsistencyCheckingSpec.config) with ImplicitSender {
    import GraphDBConsistencyCheckingSpec._

    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), STORE_MANAGER_ACTOR_NAME)

    private val timeout = 30.seconds

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/store.triplestore.GraphDBConsistencyCheckingSpec/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    if (settings.triplestoreType.startsWith("graphdb")) {
        "Load test data" in {
            storeManager ! ResetTriplestoreContent(rdfDataObjects)
            expectMsg(300.seconds, ResetTriplestoreContentACK())
        }

        "not create a new resource with a missing property that has owl:cardinality 1" in {
            storeManager ! SparqlUpdateRequest(missingPartOf)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    (msg.contains(s"$CONSISTENCY_CHECK_ERROR cardinality_1_not_less_any_object") &&
                        msg.trim.endsWith("http://rdfh.ch/missingPartOf http://www.knora.org/ontology/0803/incunabula#partOf *")) should ===(true)
            }
        }

        "not create a new resource with a missing inherited property that has owl:minCardinality 1" in {
            storeManager ! SparqlUpdateRequest(missingFileValue)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    (msg.contains(s"$CONSISTENCY_CHECK_ERROR min_cardinality_1_any_object") &&
                        msg.trim.endsWith("http://rdfh.ch/missingFileValue http://www.knora.org/ontology/knora-base#hasStillImageFileValue *")) should ===(true)
            }
        }

        "not create a new resource with two values for a property that has owl:maxCardinality 1" in {
            storeManager ! SparqlUpdateRequest(tooManyPublocs)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR max_cardinality_1_with_deletion_flag") should ===(true)
            }
        }

        "not create a new resource with more than one lastModificationDate" in {
            storeManager ! SparqlUpdateRequest(tooManyLastModificationDates)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR max_cardinality_1_without_deletion_flag") should ===(true)
            }
        }

        "not create a new resource with a property that cannot have a resource as a subject" in {
            storeManager ! SparqlUpdateRequest(wrongSubjectClass)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR subject_class_constraint") should ===(true)
            }
        }

        "not create a new resource with properties whose objects have the wrong types" in {
            storeManager ! SparqlUpdateRequest(wrongObjectClass)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR object_class_constraint") should ===(true)
            }
        }

        "not create a new resource with a link to a resource of the wrong class" in {
            storeManager ! SparqlUpdateRequest(wrongLinkTargetClass)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR object_class_constraint") should ===(true)
            }
        }

        "not create a new resource with a property for which there is no cardinality" in {
            storeManager ! SparqlUpdateRequest(resourcePropWithNoCardinality)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR resource_prop_cardinality_any") should ===(true)
            }
        }

        "not create a new resource containing a value with a property for which there is no cardinality" in {
            storeManager ! SparqlUpdateRequest(valuePropWithNoCardinality)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR value_prop_cardinality_any") should ===(true)
            }
        }

        "not create a new resource with two labels" in {
            storeManager ! SparqlUpdateRequest(twoLabels)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR cardinality_1_not_greater_rdfs_label") should ===(true)
            }
        }

        "not create a LinkValue without permissions" in {
            storeManager ! SparqlUpdateRequest(linkValueWithoutPermissions)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"$CONSISTENCY_CHECK_ERROR cardinality_1_not_less_any_object") should ===(true)
            }
        }
    } else {
        s"Not running GraphDBConsistencyCheckingSpec with triplestore type ${settings.triplestoreType}" in {}
    }
}

object GraphDBConsistencyCheckingSpec {
    // A string that's found in all consistency check error messages from GraphDB.
    private val CONSISTENCY_CHECK_ERROR = "Consistency check"

    private val config = ConfigFactory.parseString(
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin)

    // Tries to create a new incunabula:page with a missing incunabula:partOf link.
    private val missingPartOf =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource0 rdf:type ?resourceClass0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label0 ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/0803/incunabula#pagenum
          |
          |
          |        ?newValue0_1 rdf:type ?valueType0_1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0_1 knora-base:valueHasString "recto" .
          |
          |
          |
          |            ?newValue0_1 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0_1 knora-base:valueHasOrder ?nextOrder0_1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource0 ?property0_1 ?newValue0_1 .
          |
          |
          |
          |
          |        # Value 2
          |        # Property: http://www.knora.org/ontology/knora-base#hasStillImageFileValue
          |
          |
          |        ?newValue0_2 rdf:type ?valueType0_2 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |                ?newValue0_2 knora-base:originalFilename "test.jpg" ;
          |                                     knora-base:originalMimeType "image/jpeg" ;
          |                                     knora-base:internalFilename "full.jp2" ;
          |                                     knora-base:internalMimeType "image/jp2" ;
          |                                     knora-base:dimX 800 ;
          |                                     knora-base:dimY 800 ;
          |                                     knora-base:qualityLevel 100 ;
          |                                     knora-base:valueHasQname "full" .
          |
          |
          |
          |                ?newValue0_2 knora-base:valueHasString "test.jpg" .
          |
          |
          |            ?newValue0_2 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                 knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0_2 knora-base:valueHasOrder ?nextOrder0_2 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource0 ?property0_2 ?newValue0_2 .
          |
          |
          |
          |
          |        # Value 3
          |        # Property: http://www.knora.org/ontology/knora-base#hasStillImageFileValue
          |
          |
          |        ?newValue0_3 rdf:type ?valueType0_3 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |                ?newValue0_3 knora-base:originalFilename "test.jpg" ;
          |                                     knora-base:originalMimeType "image/jpeg" ;
          |                                     knora-base:internalFilename "thumb.jpg" ;
          |                                     knora-base:internalMimeType "image/jpeg" ;
          |                                     knora-base:dimX 80 ;
          |                                     knora-base:dimY 80 ;
          |                                     knora-base:qualityLevel 10 ;
          |                                     knora-base:valueHasQname "thumbnail" .
          |
          |
          |                    ?newValue0_3 knora-base:isPreview true .
          |
          |
          |                ?newValue0_3 knora-base:valueHasString "test.jpg" .
          |
          |
          |
          |            ?newValue0_3 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                 knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0_3 knora-base:valueHasOrder ?nextOrder0_3 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource0 ?property0_3 ?newValue0_3 .
          |
          |
          |
          |
          |        # Value 4
          |        # Property: http://www.knora.org/ontology/0803/incunabula#hasRightSideband
          |
          |
          |
          |            ?resource0 ?linkProperty0_4 ?linkTarget0_4 .
          |
          |
          |
          |        ?newLinkValue0_4 rdf:type knora-base:LinkValue ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            rdf:subject ?resource0 ;
          |            rdf:predicate ?linkProperty0_4 ;
          |            rdf:object ?linkTarget0_4 ;
          |            knora-base:valueHasRefCount 1 ;
          |
          |            knora-base:valueHasOrder ?nextOrder0_4 ;
          |            knora-base:valueCreationDate ?currentTime .
          |
          |
          |            ?newLinkValue0_4 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                 knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?resource0 ?linkValueProperty0_4 ?newLinkValue0_4 .
          |
          |
          |
          |
          |        # Value 5
          |        # Property: http://www.knora.org/ontology/0803/incunabula#origname
          |
          |
          |        ?newValue0_5 rdf:type ?valueType0_5 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0_5 knora-base:valueHasString "Blatt" .
          |
          |
          |
          |
          |            ?newValue0_5 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0_5 knora-base:valueHasOrder ?nextOrder0_5 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource0 ?property0_5 ?newValue0_5 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/0803/incunabula#seqnum
          |
          |
          |        ?newValue0_6 rdf:type ?valueType0_6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0_6 knora-base:valueHasInteger 1 ;
          |                                     knora-base:valueHasString "1" .
          |
          |
          |            ?newValue0_6 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0_6 knora-base:valueHasOrder ?nextOrder0_6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource0 ?property0_6 ?newValue0_6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0803/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/missingPartOf") AS ?resource0)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#page") AS ?resourceClass0)
          |    BIND(IRI("http://rdfh.ch/users/b83acc5f05") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0803") AS ?projectIri)
          |    BIND(str("Test-Page") AS ?label0)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pagenum
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pagenum") AS ?property0_1)
          |    BIND(IRI("http://rdfh.ch/missingPartOf/values/nQ3tRObaQWe74WQv2_OdCg") AS ?newValue0_1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0_1)
          |
          |
          |
          |    ?property0_1 knora-base:objectClassConstraint ?propertyRange0_1 .
          |    ?valueType0_1 rdfs:subClassOf* ?propertyRange0_1 .
          |
          |
          |
          |    ?resourceClass0 rdfs:subClassOf* ?restriction0_1 .
          |    ?restriction0_1 a owl:Restriction .
          |    ?restriction0_1 owl:onProperty ?property0_1 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0_1)
          |
          |
          |
          |
          |
          |
          |    # Value 2
          |    # Property: http://www.knora.org/ontology/knora-base#hasStillImageFileValue
          |
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#hasStillImageFileValue") AS ?property0_2)
          |    BIND(IRI("http://rdfh.ch/missingPartOf/values/GVE754RbT1CykpMnwR3Csw") AS ?newValue0_2)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#StillImageFileValue") AS ?valueType0_2)
          |
          |
          |
          |    ?property0_2 knora-base:objectClassConstraint ?propertyRange0_2 .
          |    ?valueType0_2 rdfs:subClassOf* ?propertyRange0_2 .
          |
          |
          |
          |    ?resourceClass0 rdfs:subClassOf* ?restriction0_2 .
          |    ?restriction0_2 a owl:Restriction .
          |    ?restriction0_2 owl:onProperty ?property0_2 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0_2)
          |
          |
          |
          |
          |
          |
          |    # Value 3
          |    # Property: http://www.knora.org/ontology/knora-base#hasStillImageFileValue
          |
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#hasStillImageFileValue") AS ?property0_3)
          |    BIND(IRI("http://rdfh.ch/missingPartOf/values/LOT71U6hSQu7shi76oRxWQ") AS ?newValue0_3)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#StillImageFileValue") AS ?valueType0_3)
          |
          |
          |
          |    ?property0_3 knora-base:objectClassConstraint ?propertyRange0_3 .
          |    ?valueType0_3 rdfs:subClassOf* ?propertyRange0_3 .
          |
          |
          |
          |    ?resourceClass0 rdfs:subClassOf* ?restriction0_3 .
          |    ?restriction0_3 a owl:Restriction .
          |    ?restriction0_3 owl:onProperty ?property0_3 .
          |
          |
          |
          |
          |            BIND(1 AS ?nextOrder0_3)
          |
          |
          |
          |
          |
          |
          |    # Value 4
          |    # Property: http://www.knora.org/ontology/0803/incunabula#hasRightSideband
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#hasRightSideband") AS ?linkProperty0_4)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#hasRightSidebandValue") AS ?linkValueProperty0_4)
          |    BIND(IRI("http://rdfh.ch/missingPartOf/values/i5tE5i-RRLOH631soexPFw") AS ?newLinkValue0_4)
          |    BIND(IRI("http://rdfh.ch/482a33d65c36") AS ?linkTarget0_4)
          |
          |
          |
          |    ?linkTarget0_4 rdf:type ?linkTargetClass0_4 .
          |    ?linkTargetClass0_4 rdfs:subClassOf+ knora-base:Resource .
          |
          |
          |
          |    ?linkProperty0_4 knora-base:objectClassConstraint ?expectedTargetClass0_4 .
          |    ?linkTargetClass0_4 rdfs:subClassOf* ?expectedTargetClass0_4 .
          |
          |
          |
          |    MINUS {
          |        ?linkTarget4 knora-base:isDeleted true .
          |    }
          |
          |
          |
          |    ?resourceClass0 rdfs:subClassOf* ?restriction0_4 .
          |    ?restriction0_4 a owl:Restriction .
          |    ?restriction0_4 owl:onProperty ?linkProperty0_4 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0_4)
          |
          |
          |
          |
          |
          |
          |    # Value 5
          |    # Property: http://www.knora.org/ontology/0803/incunabula#origname
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#origname") AS ?property0_5)
          |    BIND(IRI("http://rdfh.ch/missingPartOf/values/MLWWT-F8SlKsZmRo4JMLHw") AS ?newValue0_5)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0_5)
          |
          |
          |
          |    ?property0_5 knora-base:objectClassConstraint ?propertyRange0_5 .
          |    ?valueType0_5 rdfs:subClassOf* ?propertyRange0_5 .
          |
          |
          |
          |    ?resourceClass0 rdfs:subClassOf* ?restriction0_5 .
          |    ?restriction0_5 a owl:Restriction .
          |    ?restriction0_5 owl:onProperty ?property0_5 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0_5)
          |
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/0803/incunabula#seqnum
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#seqnum") AS ?property0_6)
          |    BIND(IRI("http://rdfh.ch/missingPartOf/values/uWQtW_X3RxKjFyGrsQwbpQ") AS ?newValue0_6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#IntValue") AS ?valueType0_6)
          |
          |
          |
          |    ?property0_6 knora-base:objectClassConstraint ?propertyRange0_6 .
          |    ?valueType0_6 rdfs:subClassOf* ?propertyRange0_6 .
          |
          |
          |
          |    ?resourceClass0 rdfs:subClassOf* ?restriction0_6 .
          |    ?restriction0_6 a owl:Restriction .
          |    ?restriction0_6 owl:onProperty ?property0_6 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0_6)
          |
          |
          |
          |
          |    # Value 7
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pagenum
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pagenum") AS ?property0_7)
          |    BIND(IRI("http://rdfh.ch/missingPartOf/values/nQ3tRObaQWe74WQv2_OdCg") AS ?newValue0_7)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0_7)
          |
          |
          |
          |    ?property0_7 knora-base:objectClassConstraint ?propertyRange0_7 .
          |    ?valueType0_7 rdfs:subClassOf* ?propertyRange0_7 .
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0_1)
          |
          |}
        """.stripMargin

    // Tries to create an incunabula:page with a missing file value (the cardinality is inherited).
    private val missingFileValue =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0803/incunabula#partOf
          |
          |
          |
          |            ?resource ?linkProperty0 ?linkTarget0 .
          |
          |
          |
          |        ?newLinkValue0 rdf:type knora-base:LinkValue ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            rdf:subject ?resource ;
          |            rdf:predicate ?linkProperty0 ;
          |            rdf:object ?linkTarget0 ;
          |            knora-base:valueHasRefCount 1 ;
          |
          |            knora-base:valueHasOrder ?nextOrder0 ;
          |            knora-base:valueCreationDate ?currentTime .
          |
          |
          |            ?newLinkValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |
          |        ?resource ?linkValueProperty0 ?newLinkValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/0803/incunabula#pagenum
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasString "recto" .
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 4
          |        # Property: http://www.knora.org/ontology/0803/incunabula#hasRightSideband
          |
          |
          |
          |            ?resource ?linkProperty4 ?linkTarget4 .
          |
          |
          |
          |        ?newLinkValue4 rdf:type knora-base:LinkValue ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            rdf:subject ?resource ;
          |            rdf:predicate ?linkProperty4 ;
          |            rdf:object ?linkTarget4 ;
          |            knora-base:valueHasRefCount 1 ;
          |
          |            knora-base:valueHasOrder ?nextOrder4 ;
          |            knora-base:valueCreationDate ?currentTime .
          |
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |
          |        ?resource ?linkValueProperty4 ?newLinkValue4 .
          |
          |
          |
          |
          |        # Value 5
          |        # Property: http://www.knora.org/ontology/0803/incunabula#origname
          |
          |
          |        ?newValue5 rdf:type ?valueType5 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue5 knora-base:valueHasString "Blatt" .
          |
          |
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue5 knora-base:valueHasOrder ?nextOrder5 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property5 ?newValue5 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/0803/incunabula#seqnum
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasInteger 1 ;
          |                                     knora-base:valueHasString "1" .
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0803/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/missingFileValue") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#page") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/b83acc5f05") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0803") AS ?projectIri)
          |    BIND(str("Test-Page") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0803/incunabula#partOf
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#partOf") AS ?linkProperty0)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#partOfValue") AS ?linkValueProperty0)
          |    BIND(IRI("http://rdfh.ch/missingFileValue/values/RFzfHLk1R-mU66NAFrVTYQ") AS ?newLinkValue0)
          |    BIND(IRI("http://rdfh.ch/c5058f3a") AS ?linkTarget0)
          |
          |
          |
          |    ?linkTarget0 rdf:type ?linkTargetClass0 .
          |    ?linkTargetClass0 rdfs:subClassOf+ knora-base:Resource .
          |
          |
          |
          |    ?linkProperty0 knora-base:objectClassConstraint ?expectedTargetClass0 .
          |    ?linkTargetClass0 rdfs:subClassOf* ?expectedTargetClass0 .
          |
          |
          |
          |    MINUS {
          |        ?linkTarget0 knora-base:isDeleted true .
          |    }
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?linkProperty0 .
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pagenum
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pagenum") AS ?property1)
          |    BIND(IRI("http://rdfh.ch/missingFileValue/values/nQ3tRObaQWe74WQv2_OdCg") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |
          |    # Value 4
          |    # Property: http://www.knora.org/ontology/0803/incunabula#hasRightSideband
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#hasRightSideband") AS ?linkProperty4)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#hasRightSidebandValue") AS ?linkValueProperty4)
          |    BIND(IRI("http://rdfh.ch/missingFileValue/values/i5tE5i-RRLOH631soexPFw") AS ?newLinkValue4)
          |    BIND(IRI("http://rdfh.ch/482a33d65c36") AS ?linkTarget4)
          |
          |
          |
          |    ?linkTarget4 rdf:type ?linkTargetClass4 .
          |    ?linkTargetClass4 rdfs:subClassOf+ knora-base:Resource .
          |
          |
          |
          |    ?linkProperty4 knora-base:objectClassConstraint ?expectedTargetClass4 .
          |    ?linkTargetClass4 rdfs:subClassOf* ?expectedTargetClass4 .
          |
          |
          |
          |    MINUS {
          |        ?linkTarget4 knora-base:isDeleted true .
          |    }
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction4 .
          |    ?restriction4 a owl:Restriction .
          |    ?restriction4 owl:onProperty ?linkProperty4 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder4)
          |
          |
          |
          |
          |
          |
          |    # Value 5
          |    # Property: http://www.knora.org/ontology/0803/incunabula#origname
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#origname") AS ?property5)
          |    BIND(IRI("http://rdfh.ch/missingFileValue/values/MLWWT-F8SlKsZmRo4JMLHw") AS ?newValue5)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType5)
          |
          |
          |
          |    ?property5 knora-base:objectClassConstraint ?propertyRange5 .
          |    ?valueType5 rdfs:subClassOf* ?propertyRange5 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction5 .
          |    ?restriction5 a owl:Restriction .
          |    ?restriction5 owl:onProperty ?property5 .
          |
          |
          |            BIND(0 AS ?nextOrder5)
          |
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/0803/incunabula#seqnum
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#seqnum") AS ?property6)
          |    BIND(IRI("http://rdfh.ch/missingFileValue/values/uWQtW_X3RxKjFyGrsQwbpQ") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#IntValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin

    // Tries to create an incunabula:book with two incunabula:publoc values (at most one is allowed).
    private val tooManyPublocs =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property0 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDN 2457360 ;
          |                                     knora-base:valueHasEndJDN 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 2
          |        # Property: http://www.knora.org/ontology/0803/incunabula#citation
          |
          |
          |        ?newValue2 rdf:type ?valueType2 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue2 knora-base:valueHasString "noch ein letztes" .
          |
          |
          |
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue2 knora-base:valueHasOrder ?nextOrder2 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property2 ?newValue2 .
          |
          |
          |
          |
          |        # Value 3
          |        # Property: http://www.knora.org/ontology/0803/incunabula#citation
          |
          |
          |        ?newValue3 rdf:type ?valueType3 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue3 knora-base:valueHasString "ein Zitat" .
          |
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue3 knora-base:valueHasOrder ?nextOrder3 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property3 ?newValue3 .
          |
          |
          |
          |
          |        # Value 4
          |        # Property: http://www.knora.org/ontology/0803/incunabula#citation
          |
          |
          |        ?newValue4 rdf:type ?valueType4 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue4 knora-base:valueHasString "und noch eines" .
          |
          |
          |
          |            ?newValue4 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue4 knora-base:valueHasOrder ?nextOrder4 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property4 ?newValue4 .
          |
          |
          |
          |
          |        # Value 5
          |        # Property: http://www.knora.org/ontology/0803/incunabula#citation
          |
          |
          |        ?newValue5 rdf:type ?valueType5 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue5 knora-base:valueHasString "This citation refers to another resource" .
          |
          |
          |
          |
          |                    ?newValue5 knora-base:valueHasStandoff
          |                        [
          |
          |
          |                                    rdf:type knora-base:StandoffVisualAttribute ;
          |                                    knora-base:standoffHasAttribute "bold" ;
          |
          |
          |                            knora-base:standoffHasStart 5 ;
          |                            knora-base:standoffHasEnd 13
          |                        ] .
          |
          |                    ?newValue5 knora-base:valueHasStandoff
          |                        [
          |
          |
          |                                    rdf:type knora-base:StandoffLink ;
          |                                    knora-base:standoffHasAttribute "_link" ;
          |                                    knora-base:standoffHasLink <http://rdfh.ch/c5058f3a> ;
          |
          |
          |                            knora-base:standoffHasStart 32 ;
          |                            knora-base:standoffHasEnd 40
          |                        ] .
          |
          |
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue5 knora-base:valueHasOrder ?nextOrder5 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property5 ?newValue5 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |
          |
          |
          |        # Value 7
          |        # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |
          |        ?newValue7 rdf:type ?valueType7 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue7 knora-base:valueHasString "Bebenhausen" .
          |
          |
          |
          |            ?newValue7 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue7 knora-base:valueHasOrder ?nextOrder7 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property7 ?newValue7 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0803/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/b83acc5f05") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0803") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#title") AS ?property0)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |
          |    # Value 2
          |    # Property: http://www.knora.org/ontology/0803/incunabula#citation
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#citation") AS ?property2)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs/values/oTvvcMRgR_CC-Os-61I-Qw") AS ?newValue2)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType2)
          |
          |
          |
          |    ?property2 knora-base:objectClassConstraint ?propertyRange2 .
          |    ?valueType2 rdfs:subClassOf* ?propertyRange2 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction2 .
          |    ?restriction2 a owl:Restriction .
          |    ?restriction2 owl:onProperty ?property2 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder2)
          |
          |
          |
          |
          |
          |
          |    # Value 3
          |    # Property: http://www.knora.org/ontology/0803/incunabula#citation
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#citation") AS ?property3)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs/values/Jvcncu3iSr2_fWdWdOfn-w") AS ?newValue3)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType3)
          |
          |
          |
          |    ?property3 knora-base:objectClassConstraint ?propertyRange3 .
          |    ?valueType3 rdfs:subClassOf* ?propertyRange3 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction3 .
          |    ?restriction3 a owl:Restriction .
          |    ?restriction3 owl:onProperty ?property3 .
          |
          |
          |
          |            BIND(1 AS ?nextOrder3)
          |
          |
          |
          |
          |
          |
          |    # Value 4
          |    # Property: http://www.knora.org/ontology/0803/incunabula#citation
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#citation") AS ?property4)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs/values/7wJJcQLtS2mG_tyPKCe1Ig") AS ?newValue4)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType4)
          |
          |
          |
          |    ?property4 knora-base:objectClassConstraint ?propertyRange4 .
          |    ?valueType4 rdfs:subClassOf* ?propertyRange4 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction4 .
          |    ?restriction4 a owl:Restriction .
          |    ?restriction4 owl:onProperty ?property4 .
          |
          |
          |            BIND(2 AS ?nextOrder4)
          |
          |
          |
          |
          |
          |
          |    # Value 5
          |    # Property: http://www.knora.org/ontology/0803/incunabula#citation
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#citation") AS ?property5)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs/values/y7zDf5oNSE6-9GNNgXSbwA") AS ?newValue5)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType5)
          |
          |
          |
          |    ?property5 knora-base:objectClassConstraint ?propertyRange5 .
          |    ?valueType5 rdfs:subClassOf* ?propertyRange5 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction5 .
          |    ?restriction5 a owl:Restriction .
          |    ?restriction5 owl:onProperty ?property5 .
          |
          |
          |
          |            BIND(3 AS ?nextOrder5)
          |
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs/values/1ryBgY4MSn2Y8K8QAPiJBw0") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |    # Value 7
          |    # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#publoc") AS ?property7)
          |    BIND(IRI("http://rdfh.ch/tooManyPublocs/values/1ryBgY4MSn2Y8K8QAPiJBw1") AS ?newValue7)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType7)
          |
          |
          |
          |    ?property7 knora-base:objectClassConstraint ?propertyRange7 .
          |    ?valueType7 rdfs:subClassOf* ?propertyRange7 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction7 .
          |    ?restriction7 a owl:Restriction .
          |    ?restriction7 owl:onProperty ?property7 .
          |
          |
          |
          |            BIND(1 AS ?nextOrder7)
          |}
        """.stripMargin


    private val tooManyLastModificationDates =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            knora-base:lastModificationDate "2016-01-23T11:31:24Z"^^xsd:dateTimeStamp ;
          |			   knora-base:lastModificationDate ?currentTime ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property0 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDN 2457360 ;
          |                                     knora-base:valueHasEndJDN 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0803/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/tooManyLastModificationDates") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/b83acc5f05") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0803") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#title") AS ?property0)
          |    BIND(IRI("http://rdfh.ch/tooManyLastModificationDates/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://rdfh.ch/tooManyLastModificationDates/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |            
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://rdfh.ch/tooManyLastModificationDates/values/1ryBgY4MSn2Y8K8QAPiJBw") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin


    private val wrongSubjectClass =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:valueHasString "A resource is not allowed to have a valueHasString property" ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |			   knora-base:lastModificationDate ?currentTime ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property0 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDN 2457360 ;
          |                                     knora-base:valueHasEndJDN 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0803/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/wrongSubjectClass") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/b83acc5f05") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0803") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#title") AS ?property0)
          |    BIND(IRI("http://rdfh.ch/wrongSubjectClass/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://rdfh.ch/wrongSubjectClass/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://rdfh.ch/wrongSubjectClass/values/1ryBgY4MSn2Y8K8QAPiJBw") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin


    private val wrongObjectClass =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |			   knora-base:lastModificationDate ?currentTime ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property1 ?newValue0 . # ?property0 and ?property1 are reversed to cause an error.
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDN 2457360 ;
          |                                     knora-base:valueHasEndJDN 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property0 ?newValue1 . # ?property0 and ?property1 are reversed to cause an error.
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0803/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/wrongObjectClass") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/b83acc5f05") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0803") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#title") AS ?property0)
          |    BIND(IRI("http://rdfh.ch/wrongObjectClass/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://rdfh.ch/wrongObjectClass/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://rdfh.ch/wrongObjectClass/values/1ryBgY4MSn2Y8K8QAPiJBw") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin


    private val resourcePropWithNoCardinality =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |PREFIX incunabula: <http://www.knora.org/ontology/0803/incunabula#>
          |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |
          |
          |        # A property that incunabula:book has no cardinality for.
          |        incunabula:unused rdf:type owl:ObjectProperty ;
          |            rdfs:subPropertyOf knora-base:hasValue ;
          |            rdfs:label "Unused property"@en ;
          |            rdfs:comment "A property used only in tests"@en ;
          |            knora-base:subjectClassConstraint incunabula:book ;
          |            knora-base:objectClassConstraint knora-base:TextValue ;
          |            salsah-gui:guiElement salsah-gui:SimpleText ;
          |            salsah-gui:guiAttribute "min=4" ,
          |                                    "max=8" .
          |
          |
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |			   knora-base:lastModificationDate ?currentTime ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property0 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDN 2457360 ;
          |                                     knora-base:valueHasEndJDN 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |
          |
          |
          |        # Value 7 (there's no cardinality for it, so it should cause an error)
          |        # Property: http://www.knora.org/ontology/0803/incunabula#unused
          |
          |
          |        ?newValue7 rdf:type ?valueType7 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue7 knora-base:valueHasString "recto" .
          |
          |
          |
          |
          |            ?newValue7 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue7 knora-base:valueHasOrder ?nextOrder7 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property7 ?newValue7 .
          |
          |
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0803/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/resourcePropWithNoCardinality") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/b83acc5f05") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0803") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#title") AS ?property0)
          |    BIND(IRI("http://rdfh.ch/resourcePropWithNoCardinality/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://rdfh.ch/resourcePropWithNoCardinality/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://rdfh.ch/resourcePropWithNoCardinality/values/1ryBgY4MSn2Y8K8QAPiJBw") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |
          |    # Value 7
          |    # Property: http://www.knora.org/ontology/0803/incunabula#unused
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#unused") AS ?property7)
          |    BIND(IRI("http://rdfh.ch/resourcePropWithNoCardinality/values/nQ3tRObaQWe74WQv2_OdCg") AS ?newValue7)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType7)
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder7)
          |}
        """.stripMargin


    private val valuePropWithNoCardinality =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |			   knora-base:lastModificationDate ?currentTime ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |        ?resource ?property0 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDN 2457360 ;
          |                                     knora-base:valueHasEndJDN 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |
          |        # A property that knora-base:TextValue has no cardinality for.
          |        knora-base:valueHasTest rdf:type owl:DatatypeProperty ;
          |                 rdfs:subPropertyOf knora-base:valueHas ;
          |                 knora-base:subjectClassConstraint knora-base:TextValue ;
          |                 knora-base:objectDatatypeConstraint xsd:integer .
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |                ?newValue6 knora-base:valueHasTest "3"^^xsd:integer . # No cardinality for this property, so it should cause an error.
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> ?creatorIri ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0803/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/valuePropWithNoCardinality") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/b83acc5f05") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0803") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0803/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#title") AS ?property0)
          |    BIND(IRI("http://rdfh.ch/valuePropWithNoCardinality/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/0803/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://rdfh.ch/valuePropWithNoCardinality/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/0803/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/0803/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://rdfh.ch/valuePropWithNoCardinality/values/1ryBgY4MSn2Y8K8QAPiJBw") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |}
        """.stripMargin

    private val wrongLinkTargetClass =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource0 rdf:type ?resourceClass0 ;
          |            knora-base:isDeleted false ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label0 ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0001/anything#hasBlueThing
          |
          |            # The property hasBlueThing has an objectClassConstraint of BlueThing, so using a Thing as a link target should fail.
          |
          |            ?resource0 ?linkProperty0_0 ?linkTarget0_0 .
          |
          |
          |
          |        ?newLinkValue0_0 rdf:type knora-base:LinkValue ;
          |            rdf:subject ?resource0 ;
          |            rdf:predicate ?linkProperty0_0 ;
          |            rdf:object ?linkTarget0_0 ;
          |            knora-base:valueHasString "http://rdfh.ch/a-thing" ;
          |            knora-base:valueHasRefCount 1 ;
          |
          |            knora-base:valueHasOrder ?nextOrder0_0 ;
          |            knora-base:isDeleted false ;
          |            knora-base:valueCreationDate ?currentTime .
          |
          |        ?newLinkValue0_0 knora-base:attachedToUser <http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q> ;
          |                knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" .
          |
          |
          |
          |
          |        ?resource0 ?linkValueProperty0_0 ?newLinkValue0_0 .
          |
          |
          |
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0001/anything") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/wrongTargetClass") AS ?resource0)
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#Thing") AS ?resourceClass0)
          |    BIND(IRI("http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0001") AS ?projectIri)
          |    BIND(str("Test Thing") AS ?label0)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0001/anything#hasBlueThing
          |
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#hasBlueThing") AS ?linkProperty0_0)
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#hasBlueThingValue") AS ?linkValueProperty0_0)
          |    BIND(IRI("http://rdfh.ch/wrongTargetClass/values/GjV_4ayjRDebneEQM0zHuw") AS ?newLinkValue0_0)
          |    BIND(IRI("http://rdfh.ch/a-thing") AS ?linkTarget0)
          |
          |
          |
          |    ?linkTarget0_0 rdf:type ?linkTargetClass0_0 ;
          |        knora-base:isDeleted false .
          |    ?linkTargetClass0_0 rdfs:subClassOf+ knora-base:Resource .
          |
          |
          |
          |    ?resourceClass0 rdfs:subClassOf* ?restriction0_0 .
          |    ?restriction0_0 a owl:Restriction .
          |    ?restriction0_0 owl:onProperty ?linkProperty0_0 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0_0)
          |
          |
          |
          |}
        """.stripMargin


    private val twoLabels =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted false ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label;
          |            rdfs:label "Second label not allowed" ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0001/anything") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/twoLabels") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#Thing") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0001") AS ?projectIri)
          |    BIND(str("Test Thing") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0001/anything#hasBlueThing
          |
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#hasBlueThing") AS ?linkProperty0)
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#hasBlueThingValue") AS ?linkValueProperty0)
          |    BIND(IRI("http://rdfh.ch/0001/twoLabels/values/GjV_4ayjRDebneEQM0zHuw") AS ?newLinkValue0)
          |    BIND(IRI("http://rdfh.ch/0001/a-thing") AS ?linkTarget0)
          |}
        """.stripMargin


    private val linkValueWithoutPermissions =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted false ;
          |            knora-base:attachedToUser ?creatorIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |            knora-base:hasPermissions "V knora-base:UnknownUser|M knora-base:ProjectMember" ;
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/0001/anything#hasThing
          |
          |            ?resource ?linkProperty0 ?linkTarget0 .
          |
          |        ?newLinkValue0 rdf:type knora-base:LinkValue ;
          |            rdf:subject ?resource ;
          |            rdf:predicate ?linkProperty0 ;
          |            rdf:object ?linkTarget0 ;
          |            knora-base:valueHasString "http://rdfh.ch/0001/a-thing" ;
          |            knora-base:valueHasRefCount 1 ;
          |            knora-base:valueHasOrder ?nextOrder0 ;
          |            knora-base:isDeleted false ;
          |            knora-base:valueCreationDate ?currentTime .
          |
          |        ?newLinkValue0 knora-base:attachedToUser <http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q> .
          |        ?resource ?linkValueProperty0 ?newLinkValue0 .
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/0001/anything") AS ?dataNamedGraph)
          |    BIND(IRI("http://rdfh.ch/missingValuePermissions") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#Thing") AS ?resourceClass)
          |    BIND(IRI("http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q") AS ?creatorIri)
          |    BIND(IRI("http://rdfh.ch/projects/0001") AS ?projectIri)
          |    BIND(str("Test Thing") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/0001/anything#hasOtherThing
          |
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#hasOtherThing") AS ?linkProperty0)
          |    BIND(IRI("http://www.knora.org/ontology/0001/anything#hasOtherThingValue") AS ?linkValueProperty0)
          |    BIND(IRI("http://rdfh.ch/0001/missingValuePermissions/values/GjV_4ayjRDebneEQM0zHuw") AS ?newLinkValue0)
          |    BIND(IRI("http://rdfh.ch/0001/a-thing") AS ?linkTarget0)
          |
          |
          |
          |    ?linkTarget0 rdf:type ?linkTargetClass0 ;
          |        knora-base:isDeleted false .
          |    ?linkTargetClass0 rdfs:subClassOf+ knora-base:Resource .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?linkProperty0 .
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |}
        """.stripMargin



}
