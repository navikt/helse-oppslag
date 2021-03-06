import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val slf4jVersion = "1.7.25"
val ktorVersion = "1.1.2"
val arrowVersion = "0.9.0"
val prometheusVersion = "0.6.0"
val cxfVersion = "3.3.1"
val orgJsonVersion = "20180813"
val fuelVersion = "1.15.1"
val wireMockVersion = "2.19.0"
val mockkVersion = "1.9.3.kotlin12"
val tjenestespesifikasjonerVersion = "1.2019.01.16-21.19-afc54bed6f85"
val junitJupiterVersion = "5.4.0"
val assertJVersion = "3.12.0"
val mainClass = "no.nav.helse.AppKt"

fun tjenestespesifikasjon(name: String) = "no.nav.tjenestespesifikasjoner:$name:$tjenestespesifikasjonerVersion"

plugins {
    `build-scan`
    kotlin("jvm") version "1.3.21"
}

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.2.0")
    }
}

dependencies {
    compile(kotlin("stdlib"))
    compile("ch.qos.logback:logback-classic:1.2.3")
    compile("net.logstash.logback:logstash-logback-encoder:5.2")
    compile("io.ktor:ktor-server-netty:$ktorVersion")
    compile("io.ktor:ktor-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }
    compile("io.prometheus:simpleclient_common:$prometheusVersion")
    compile("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    compile("org.json:json:$orgJsonVersion")
    compile("com.github.kittinunf.fuel:fuel:$fuelVersion")

    compile("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    compile("org.apache.cxf:cxf-rt-ws-policy:$cxfVersion")
    compile("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    compile("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    compile("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    compile("javax.activation:activation:1.1.1")

    compile("io.arrow-kt:arrow-core-data:$arrowVersion")

    compile("no.nav.helse:cxf-prometheus-metrics:dd7d125")

    compile("com.sun.xml.ws:jaxws-rt:2.3.2")
    compile(tjenestespesifikasjon("arbeidsfordeling-v1-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("arbeidsforholdv3-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("nav-infotrygdBeregningsgrunnlag-v1-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("nav-infotrygdSak-v1-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("nav-medlemskap-v2-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("person-v3-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("sakogbehandling-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("nav-fim-organisasjon-v5-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("nav-fim-inntekt-v3-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("nav-hentsykepengeliste-v2-tjenestespesifikasjon"))
    compile(tjenestespesifikasjon("nav-meldekortUtbetalingsgrunnlag-v1-tjenestespesifikasjon"))

    testCompile("io.mockk:mockk:$mockkVersion")
    testCompile("com.github.tomakehurst:wiremock:$wireMockVersion") {
        exclude(group = "junit")
    }
    testCompile("com.google.guava:guava:20.0")
    testCompile("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testCompile("org.assertj:assertj-core:$assertJVersion")

    testCompile("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "junit")
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/ktor")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    setTermsOfServiceAgree("yes")
}

tasks.named<Jar>("jar") {
    baseName = "app"

    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations["compile"].joinToString(separator = " ") {
            it.name
        }
    }

    doLast {
        configurations["compile"].forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.2.1"
}
