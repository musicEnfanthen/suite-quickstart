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

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.knora.webapi.E2ESimSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

import scala.concurrent.duration._

/**
  * Simulation Scenario for testing the projects endpoints.
  *
  * This simulation scenario accesses the projects endpoint with
  * 1000 users concurrently.
  */
class ProjectsADME2ESimSpec extends E2ESimSpec {

    override val rdfDataObjects: Seq[RdfDataObject] = Seq.empty[RdfDataObject]

    val protobolBuilder = http
            .baseURL("http://localhost:3333")

    val projects = scenario("Users")
            .exec(
                http("Get all projects")
                .get("/admin/projects")
                .check(status.is(200))
            )

    val injections = Seq(rampUsers(1000) over 5.seconds)

    val assertions = Seq(
        global.responseTime.mean.lt(500)
        , forAll.failedRequests.count.lt(1)
    )

    setUp(
        projects.inject(injections).protocols(protobolBuilder)
    ).assertions(assertions)

}


