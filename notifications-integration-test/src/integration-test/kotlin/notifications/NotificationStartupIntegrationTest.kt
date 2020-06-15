package notifications

import integration.helper.ContainerHelper
import integration.helper.DEFAULT_RMQ_PORT
import integration.helper.DockerComposeStarter
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DockerComposeStarter::class)
class NotificationStartupIntegrationTest {

    private val containerHelper = ContainerHelper()
    private val dockerfile = "${containerHelper.userDir}/notifications/build/docker/Dockerfile"
    private val contextFolder = "${containerHelper.userDir}/notifications/build/docker"

    // Create notification service container
    private val notificationServiceContainer = containerHelper.createSoraPluginContainer(contextFolder, dockerfile)

    @BeforeAll
    fun setUp() {
        // Start Iroha
        containerHelper.irohaContainer.start()

        // Start RMQ
        containerHelper.rmqContainer.start()

        notificationServiceContainer.addEnv(
            "NOTIFICATIONS__IROHA_HOSTNAME",
            containerHelper.irohaContainer.toriiAddress.host
        )
        notificationServiceContainer.addEnv(
            "NOTIFICATIONS__IROHA_PORT",
            containerHelper.irohaContainer.toriiAddress.port.toString()
        )
        notificationServiceContainer.addEnv("NOTIFICATIONS_RMQ_HOST", "127.0.0.1")
        notificationServiceContainer.addEnv(
            "NOTIFICATIONS_RMQ_PORT", containerHelper.rmqContainer.getMappedPort(DEFAULT_RMQ_PORT).toString()
        )

        notificationServiceContainer.start()
    }


    @AfterAll
    fun tearDown() {
        containerHelper.close()
        notificationServiceContainer.stop()
    }

    /**
     * @given the notification service container
     * @when the container is started
     * @then it keeps working and its status is healthy
     */
    @Test
    fun testHealthCheck() {
        // Let service work a little
        Thread.sleep(10_000)
        assertTrue(containerHelper.isServiceHealthy(notificationServiceContainer))
    }
}
