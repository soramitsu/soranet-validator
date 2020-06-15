/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration.helper

import org.testcontainers.containers.wait.strategy.Wait
import java.io.File

open class IrohaDockerComposeStarter : DockerComposeStarter() {

    override val composePath = File(System.getProperty("user.dir")).absolutePath +
            "/deploy/docker-compose-iroha-only.yml"

    override val dockerEnvironment: KDockerComposeContainer by lazy {
        KDockerComposeContainer(File(composePath))
            .withLocalCompose(true)
            .waitingFor(
                "d3-iroha",
                Wait.forLogMessage(".*iroha initialized.*", 1)
            )
    }
}
