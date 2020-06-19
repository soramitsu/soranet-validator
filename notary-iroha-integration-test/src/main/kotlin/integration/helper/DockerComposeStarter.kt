/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration.helper

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.WaitingConsumer
import org.testcontainers.containers.wait.strategy.WaitStrategy
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget
import java.io.File
import java.lang.reflect.Field
import java.time.Duration
import java.util.*


open class DockerComposeStarter : BeforeAllCallback, BeforeEachCallback {

    open val irohaServiceName = "d3-iroha"

    open val irohaHostServicesProperties = listOf(
        "EXCHANGER_IROHA_HOSTNAME",
        "ETH-DEPOSIT_IROHA_HOSTNAME",
        "TEST_IROHA_HOSTNAME",
        "IROHA_HOST",
        "IROHA_HOSTNAME"
    )

    open val composePath = File(System.getProperty("user.dir")).absolutePath +
            "/deploy/docker-compose.yml"

    open val dockerEnvironment: KDockerComposeContainer by lazy {
        LoggerFactory.getLogger(WaitingConsumer::class.java)
        KDockerComposeContainer(File(composePath))
            .withLocalCompose(true)
            .withExposedService(
                irohaServiceName,
                50051,
                object : WaitStrategy {
                    override fun withStartupTimeout(startupTimeout: Duration?): WaitStrategy {
                        return this
                    }

                    override fun waitUntilReady(waitStrategyTarget: WaitStrategyTarget?) {

                    }
                }
            )
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
        if (context?.root?.getStore(ExtensionContext.Namespace.GLOBAL)?.get(startedTag) == true) return
        dockerEnvironment.start()
        context?.root?.getStore(ExtensionContext.Namespace.GLOBAL)?.put(startedTag, true)
    }

    override fun beforeEach(context: ExtensionContext?) {
        val irohaContainerName = dockerEnvironment
            .getContainerByServiceName(irohaServiceName + "_1")
            .get()
            .containerInfo
            .name
            .replace("/", "")

        val envMap = irohaHostServicesProperties.associateWith { irohaContainerName }.toMutableMap()
        envMap[dcContainerIpProperty] =
            "${dockerEnvironment.getServiceHost(
                dcServiceName,
                dcServicePort
            )}:${dockerEnvironment.getServicePort(
                dcServiceName,
                dcServicePort
            )}"

        setEnv(envMap)
    }

    // Weirdest hack
    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun setEnv(newEnvMap: Map<String, String>) {
        try {
            val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
            val theEnvironmentField: Field = processEnvironmentClass.getDeclaredField("theEnvironment")
            theEnvironmentField.isAccessible = true
            val env = theEnvironmentField.get(null) as MutableMap<String, String>
            env.putAll(newEnvMap)
            val theCaseInsensitiveEnvironmentField =
                processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment")
            theCaseInsensitiveEnvironmentField.isAccessible = true
            val cienv = theCaseInsensitiveEnvironmentField.get(null) as MutableMap<String, String>
            cienv.putAll(newEnvMap)
        } catch (e: NoSuchFieldException) {
            val classes: Array<Class<*>> = Collections::class.java.declaredClasses
            val env = System.getenv()
            for (cl in classes) {
                if ("java.util.Collections\$UnmodifiableMap" == cl.name) {
                    val field: Field = cl.getDeclaredField("m")
                    field.isAccessible = true
                    val obj: Any = field.get(env)
                    val map = obj as MutableMap<String, String>
                    map.clear()
                    map.putAll(newEnvMap)
                }
            }
        }
    }

    companion object {
        const val dcServiceName = "data-collector"
        const val dcServicePort = 8080
        const val dcContainerIpProperty = "DC_CONTAINER_IP"
        const val startedTag = "STARTED_COMPOSE"
    }
}
