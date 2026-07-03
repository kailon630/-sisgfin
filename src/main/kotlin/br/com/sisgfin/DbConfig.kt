package br.com.sisgfin

import java.io.File
import java.sql.DriverManager
import java.util.Properties

data class DbConfig(
    val host: String = "localhost",
    val port: Int = 5432,
    val database: String = "sisgfin",
    val user: String = "sisgfin",
    val password: String = ""
) {
    val url: String get() = "jdbc:postgresql://$host:$port/$database"
}

object DbConfigStore {
    private val configDir  = File(System.getProperty("user.home"), ".sisgfin")
    private val configFile = File(configDir, "db.properties")

    fun load(): DbConfig? {
        if (!configFile.exists()) return null
        return runCatching {
            val props = Properties().apply { configFile.inputStream().use { load(it) } }
            DbConfig(
                host     = props.getProperty("host",     "localhost"),
                port     = props.getProperty("port",     "5432").toIntOrNull() ?: 5432,
                database = props.getProperty("database", "sisgfin"),
                user     = props.getProperty("user",     "sisgfin"),
                password = props.getProperty("password", "")
            )
        }.getOrNull()
    }

    fun save(config: DbConfig) {
        configDir.mkdirs()
        val props = Properties().apply {
            setProperty("host",     config.host)
            setProperty("port",     config.port.toString())
            setProperty("database", config.database)
            setProperty("user",     config.user)
            setProperty("password", config.password)
        }
        configFile.outputStream().use { props.store(it, "SisgFin DB Config") }
    }

    fun testConnection(config: DbConfig): Result<Unit> = runCatching {
        Class.forName("org.postgresql.Driver")
        DriverManager.getConnection(config.url, config.user, config.password).use { conn ->
            conn.createStatement().executeQuery("SELECT 1").close()
        }
    }
}
