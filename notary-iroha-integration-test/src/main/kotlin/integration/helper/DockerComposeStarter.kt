/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration.helper

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.WaitingConsumer
import org.testcontainers.containers.wait.strategy.WaitStrategy
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget
import java.io.File
import java.time.Duration

open class DockerComposeStarter : BeforeAllCallback, AfterAllCallback {

    open val composePath = File(System.getProperty("user.dir")).absolutePath +
            "/deploy/docker-compose.yml"

    open val dockerEnvironment: KDockerComposeContainer by lazy {
        LoggerFactory.getLogger(WaitingConsumer::class.java)
        KDockerComposeContainer(File(composePath))
            .withLocalCompose(true)
            .withExposedService(
                dcServiceName,
                dcServicePort,
                object : WaitStrategy {
                    override fun withStartupTimeout(startupTimeout: Duration?): WaitStrategy {
                        return this
                    }

                    override fun waitUntilReady(waitStrategyTarget: WaitStrategyTarget?) {

                    }
                }
            )
            .waitingFor(
                "d3-brvs",
//                Wait.forLogMessage(
//                    ".*Starting pending transactions streaming.*",
//                    1
//                ).withStartupTimeout(Duration.ofSeconds(60))
                object : WaitStrategy {
                    override fun withStartupTimeout(startupTimeout: Duration?): WaitStrategy {
                        return this
                    }

                    override fun waitUntilReady(waitStrategyTarget: WaitStrategyTarget?) {
                        Thread.sleep(60000)
                    }
                }
            )
    }

    override fun beforeAll(context: ExtensionContext?) {
        dockerEnvironment.start()
        System.setProperty(
            dcContainerIpProperty,
            "${dockerEnvironment.getServiceHost(
                dcServiceName,
                dcServicePort
            )}:${dockerEnvironment.getServicePort(
                dcServiceName,
                dcServicePort
            )}"
        )
    }

    override fun afterAll(context: ExtensionContext?) {
        dockerEnvironment.close()
    }

    companion object {
        const val dcServiceName = "data-collector"
        const val dcServicePort = 8080
        const val dcContainerIpProperty = "DC_CONTAINER_IP"
    }
}
