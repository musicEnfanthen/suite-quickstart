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

package org.knora.webapi.e2e.admin

import io.gatling.core.Predef.{forAll, global, rampUsers, scenario, _}
import io.gatling.http.Predef.{http, status}
import org.knora.webapi.E2ESimSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

import scala.concurrent.duration._

/**
  * Simulation Scenario for testing the admin endpoints.
  *
  * This simulation scenario accesses the users, groups, and projects endpoint.
  */
class AdminMixE2ESimSpec extends E2ESimSpec {

    override val rdfDataObjects: Seq[RdfDataObject] = Seq.empty[RdfDataObject]

    val protobolBuilder = http
            .baseURL("http://localhost:3333")

    val users = scenario("Users")
            .exec(
                http("Get all users")
                        .get("/admin/users")
                        .check(status.is(200))
            )

    val groups = scenario("Groups")
            .exec(
                http("Get all groups")
                        .get("/admin/groups")
                        .check(status.is(200))
            )

    val projects = scenario("Projects")
            .exec(
                http("Get all projects")
                        .get("/admin/projects")
                        .check(status.is(200))
            )

    val injections = Seq(rampUsers(500) over 5.seconds)

    val assertions = Seq(
        global.responseTime.mean.lt(1500)
        , forAll.failedRequests.count.lt(1)
    )

    setUp(
        users.inject(injections).protocols(protobolBuilder),
        groups.inject(injections).protocols(protobolBuilder),
        projects.inject(injections).protocols(protobolBuilder)
    ).assertions(assertions)

}
