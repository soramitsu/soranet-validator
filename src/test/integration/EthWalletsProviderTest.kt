package integration

import main.CONFIG
import main.ConfigKeys
import notary.db.tables.Wallets
import org.jooq.impl.DSL
import java.sql.DriverManager
import org.junit.jupiter.api.Test

/**
 * Test Postgres ethereum wallets provider
 */
class EthWalletsProviderTest {

    /**
     * List all wallets in database
     */
    //    @Test
    fun listWallets() {
        val connection = DriverManager.getConnection(
            CONFIG[ConfigKeys.dbUrl],
            CONFIG[ConfigKeys.dbUsername],
            CONFIG[ConfigKeys.dbPassword]
        )

        DSL.using(connection).use { ctx ->
            val wallets = Wallets.WALLETS

            // read
            ctx.select(wallets.WALLET, wallets.IROHAUSERNAME)
                .from(wallets)
                .forEach {
                    println(it)
                }
        }
    }
}
