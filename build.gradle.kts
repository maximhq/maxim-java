import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.JavaVersion
import org.gradle.external.javadoc.StandardJavadocDocletOptions

//tasks.test {
//    useJUnitPlatform()
//    environment("TEST_API_KEY", "test-key-123")
//    environment("TEST_DB_URL", "jdbc:h2:mem:test")
//    environment("ENV", "test")
//}

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    `java-library`
    `maven-publish`
    signing
    id("com.vanniktech.maven.publish") version "0.29.0"
}

kotlin { jvmToolchain(17) }

group = "ai.getmaxim"

version = "1.1.0"

repositories { mavenCentral() }

dependencies {
    compileOnly("org.slf4j:slf4j-api:[1.0,)")

    implementation("io.ktor:ktor-client-core:3.2.3")
    implementation("io.ktor:ktor-client-cio:3.2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("uk.org.lidalia:slf4j-test:1.2.0")
    testImplementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.1")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, true)
    signAllPublications()

    coordinates(group.toString(), "sdk", version.toString())

    pom {
        name.set("Maxim Java Library")
        description.set("A library for Maxim Java functionality")
        url.set("https://www.getmaxim.ai?ref=java-sdk")

        licenses {
            license {
                name.set("Proprietary license")
                url.set("https://www.getmaxim.ai/terms-of-service")
            }
        }

        developers {
            developer {
                id.set("akshay-deo")
                name.set("Akshay Deo")
                email.set("eng@getmaxim.ai")
                organization.set("H3 Labs Inc")
                organizationUrl.set("https://www.getmaxim.ai")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/maximhq/maxim-java.git")
            developerConnection.set("scm:git:ssh://github.com:maximhq/maxim-java.git")
            url.set("https://github.com/maximhq/maxim-java")
        }
    }
}

signing {
    val signingPassword: String by project
    val secretKeyRingFile = file("./keys/private_key.gpg")
    useInMemoryPgpKeys(secretKeyRingFile.readText(), signingPassword)
}

tasks.withType<Sign>().configureEach { onlyIf { !project.version.toString().endsWith("SNAPSHOT") } }

tasks.withType<PublishToMavenRepository>().configureEach { dependsOn(tasks.withType<Sign>()) }

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

