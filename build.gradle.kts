plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "br.com.sisgfin"
version = "1.0-SNAPSHOT"

// Repositories managed in settings.gradle.kts

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.materialIconsExtended)

    // Kotlin Coroutines
    val coroutinesVersion = "1.9.0"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")

    // Exposed ORM (Stable 0.56.0)
    val exposedVersion = "0.56.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion") 

    // PostgreSQL JDBC
    implementation("org.postgresql:postgresql:42.7.3")

    // Flyway 9.22.3 — PostgreSQL support incluído no core
    val flywayVersion = "9.22.3"
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    // BCrypt
    implementation("org.mindrot:jbcrypt:0.4")

    // Koin DI
    val koinVersion = "4.0.0"
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-compose:$koinVersion")

    // Export: Excel (Apache POI) + PDF (Apache PDFBox) — Apache 2.0
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    // Ktor REST API
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.auth0:java-jwt:4.4.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.h2database:h2:2.2.224")
}

tasks.test {
    useJUnitPlatform()
}

// Instala scripts/check-migrations.sh como pre-commit hook.
// Executado automaticamente no build; seguro de rodar múltiplas vezes (idempotente).
// Em Windows (sem bash) a tarefa é ignorada silenciosamente.
tasks.register("installGitHooks") {
    group = "build setup"
    description = "Instala scripts/check-migrations.sh como .git/hooks/pre-commit"
    doLast {
        val hooksDir = rootProject.file(".git/hooks")
        val script   = rootProject.file("scripts/check-migrations.sh")
        if (!hooksDir.exists() || !script.exists()) return@doLast
        val hook = hooksDir.resolve("pre-commit")
        hook.writeText("#!/usr/bin/env bash\nexec \"${script.canonicalPath}\"\n")
        hook.setExecutable(true)
    }
}

tasks.named("build") {
    dependsOn("installGitHooks")
}

compose.desktop {
    application {
        mainClass = "br.com.sisgfin.MainKt"
        jvmArgs("-Djdk.lang.Process.launchMechanism=VFORK")
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
            )
            packageName = "SisgFin"
            packageVersion = "1.0.9"
            description = "Sistema de Gestão Financeira"
            vendor = "Associação Terapêutica Cannabis Medicinal Flor da Vida"
            copyright = "© 2025 Flor da Vida. Todos os direitos reservados."

            // Módulos Java necessários para o JRE mínimo gerado pelo jlink
            modules(
                "java.sql",          // JDBC — sem isso o driver PostgreSQL não carrega
                "java.naming",       // necessário pelo driver PostgreSQL internamente
                "java.security.jgss",// Kerberos/GSSAPI (referenciado pelo pg driver)
                "java.xml",          // Apache POI e PDFBox
                "java.desktop",      // AWT/Swing base (Compose Desktop precisa)
                "java.logging",      // java.util.logging usado por Flyway e Ktor
                "java.management",   // JMX — referenciado por Netty (Ktor)
                "java.net.http",     // HttpClient usado internamente
            )

            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "SisgFin"
                upgradeUuid = "3F2A1B4C-5D6E-7F8A-9B0C-1D2E3F4A5B6C"
                shortcut = true
                dirChooser = true
                perUserInstall = false
            }

            linux {
                shortcut = true
                menuGroup = "Office"
            }
        }
    }
}
