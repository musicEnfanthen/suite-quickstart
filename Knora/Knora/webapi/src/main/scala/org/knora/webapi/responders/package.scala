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

package org.knora.webapi

/**
  * Contains constants representing the Akka names and paths of the responder actors.
  */
package object responders {

    val RESPONDER_MANAGER_ACTOR_NAME = "responderManager"
    val RESPONDER_MANAGER_ACTOR_PATH = "/user/" + RESPONDER_MANAGER_ACTOR_NAME

    val RESOURCES_ROUTER_V1_ACTOR_NAME = "resourcesRouterV1"
    val RESOURCES_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + RESOURCES_ROUTER_V1_ACTOR_NAME

    val VALUES_ROUTER_V1_ACTOR_NAME = "valuesRouterV1"
    val VALUES_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + VALUES_ROUTER_V1_ACTOR_NAME

    val SIPI_ROUTER_V1_ACTOR_NAME = "sipiRouterV1"
    val SIPI_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + SIPI_ROUTER_V1_ACTOR_NAME

    val STANDOFF_ROUTER_V1_ACTOR_NAME = "standoffRouterV1"
    val STANDOFF_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + STANDOFF_ROUTER_V1_ACTOR_NAME

    val USERS_ROUTER_V1_ACTOR_NAME = "usersRouterV1"
    val USERS_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + USERS_ROUTER_V1_ACTOR_NAME

    val LISTS_ROUTER_V1_ACTOR_NAME = "listsRouterV1"
    val LISTS_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + LISTS_ROUTER_V1_ACTOR_NAME

    val SEARCH_ROUTER_V1_ACTOR_NAME = "searchRouterV1"
    val SEARCH_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + SEARCH_ROUTER_V1_ACTOR_NAME

    val ONTOLOGY_ROUTER_V1_ACTOR_NAME = "ontologyRouterV1"
    val ONTOLOGY_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + ONTOLOGY_ROUTER_V1_ACTOR_NAME

    val PROJECTS_ROUTER_V1_ACTOR_NAME = "projectsRouterV1"
    val PROJECTS_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + PROJECTS_ROUTER_V1_ACTOR_NAME

    val CKAN_ROUTER_V1_ACTOR_NAME = "ckanRouterV1"
    val CKAN_ROUTER_V1_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + CKAN_ROUTER_V1_ACTOR_NAME



    // ------------------------------------------------------------------------------------------
    // --------------------------------------- V2 Routers ---------------------------------------
    // ------------------------------------------------------------------------------------------

    val ONTOLOGY_ROUTER_V2_ACTOR_NAME = "ontologyRouterV2"
    val ONTOLOGY_ROUTER_V2_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + ONTOLOGY_ROUTER_V2_ACTOR_NAME

    val SEARCH_ROUTER_V2_ACTOR_NAME = "searchRouterV2"
    val SEARCH_ROUTER_V2_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + SEARCH_ROUTER_V2_ACTOR_NAME

    val RESOURCES_ROUTER_V2_ACTOR_NAME = "resourcesRouterV2"
    val RESOURCES_ROUTER_V2_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + RESOURCES_ROUTER_V2_ACTOR_NAME

    val PERSISTENT_MAP_ROUTER_V2_ACTOR_NAME = "persistentMapRouterV2"
    val PERSISTENT_MAP_ROUTER_V2_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + PERSISTENT_MAP_ROUTER_V2_ACTOR_NAME

    val STANDOFF_ROUTER_V2_ACTOR_NAME = "standoffRouterV2"
    val STANDOFF_ROUTER_V2_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + STANDOFF_ROUTER_V2_ACTOR_NAME

    val LISTS_ROUTER_V2_ACTOR_NAME = "listsRouterV2"
    val LISTS_ROUTER_V2_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + LISTS_ROUTER_V2_ACTOR_NAME


    // ------------------------------------------------------------------------------------------
    // ------------------------------------- Admin Routers --------------------------------------
    // ------------------------------------------------------------------------------------------

    val GROUPS_ROUTER_ADM_ACTOR_NAME = "groupsRouterADM"
    val GROUPS_ROUTER_ADM_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + GROUPS_ROUTER_ADM_ACTOR_NAME

    val LISTS_ROUTER_ADM_ACTOR_NAME = "listsRouterADM"
    val LISTS_ROUTER_ADM_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + LISTS_ROUTER_ADM_ACTOR_NAME

    val ONTOLOGIES_ROUTER_ADM_ACTOR_NAME = "ontologiesRouterADM"
    val ONTOLOGIES_ROUTER_ADM_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + ONTOLOGIES_ROUTER_ADM_ACTOR_NAME

    val PERMISSIONS_ROUTER_ADM_ACTOR_NAME = "permissionsRouterADM"
    val PERMISSIONS_ROUTER_ADM_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + PERMISSIONS_ROUTER_ADM_ACTOR_NAME

    val PROJECTS_ROUTER_ADM_ACTOR_NAME = "projectsRouterADM"
    val PROJECTS_ROUTER_ADM_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + PROJECTS_ROUTER_ADM_ACTOR_NAME

    val STORE_ROUTER_ADM_ACTOR_NAME = "storeRouterADM"
    val STORE_ROUTER_ADM_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + STORE_ROUTER_ADM_ACTOR_NAME

    val USERS_ROUTER_ADM_ACTOR_NAME = "usersRouterADM"
    val USERS_ROUTER_ADM_ACTOR_PATH = RESPONDER_MANAGER_ACTOR_PATH + "/" + USERS_ROUTER_ADM_ACTOR_NAME
}
