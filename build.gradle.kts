import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val slf4jVersion = "1.7.25"
val ktorVersion = "1.0.0"
val prometheusVersion = "0.5.0"
val cxfVersion = "3.2.6"
val orgJsonVersion = "20180813"
val fuelVersion = "1.15.1"
val wireMockVersion = "2.19.0"
val mockkVersion = "1.8.12.kotlin13"

val junitJupiterVersion = "5.3.1"
val mainClass = "no.nav.helse.AppKt"

plugins {
    application
    kotlin("jvm") version "1.3.10"
    id("com.github.johnrengelman.shadow") version "4.0.3"
    idea
}

buildscript {
    dependencies {
        classpath("org.junit.platform:junit-platform-gradle-plugin:1.2.0")
    }
}

application {
    mainClassName = "$mainClass"
}

val jaxws by configurations.creating

dependencies {
    compile(kotlin("stdlib"))
    compile("ch.qos.logback:logback-classic:1.2.3")
    compile("net.logstash.logback:logstash-logback-encoder:5.2")
    compile("io.ktor:ktor-server-netty:$ktorVersion")
    compile("io.ktor:ktor-auth-jwt:$ktorVersion")
    compile("io.prometheus:simpleclient_common:$prometheusVersion")
    compile("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    compile("org.json:json:$orgJsonVersion")
    compile("com.github.kittinunf.fuel:fuel:$fuelVersion")

    jaxws("com.sun.xml.ws:jaxws-tools:2.3.0.2")
    compile("javax.jws:javax.jws-api:1.1")
    compile("javax.xml.ws:jaxws-api:2.3.1")

    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-policy:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testCompile("com.github.tomakehurst:wiremock:$wireMockVersion")
    testCompile("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/ktor")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    val mainJavaSourceSet: SourceDirectorySet = sourceSets.getByName("main").java
    mainJavaSourceSet.srcDir("$projectDir/build/generated-sources/main")
}

idea {
    module {
        // Marks the already(!) added srcDir as "generated"
        generatedSourceDirs.add(file("$projectDir/build/generated-sources/main"))
    }
}

val wsdlDir = "$projectDir/src/main/resources/wsdl"

val wsdlsToGenerate = listOf(
        "$wsdlDir/person/Binding.wsdl",
        "$wsdlDir/arbeidsforhold/Binding.wsdl",
        "$wsdlDir/inntekt/Binding.wsdl",
        "$wsdlDir/organisasjon/Binding.wsdl",
        "$wsdlDir/sakogbehandling/Binding.wsdl"
)

val generatedDir = "$projectDir/build/generated-sources"

tasks.register("wsimport") {
    group = "other"
    doLast {
        mkdir("$generatedDir/main")
        wsdlsToGenerate.forEach {
            ant.withGroovyBuilder {
                "taskdef"("name" to "wsimport",
                        "classname" to "com.sun.tools.ws.ant.WsImport",
                        "classpath" to configurations.getAt("jaxws").asPath)

                "wsimport"("wsdl" to it,
                        "sourcedestdir" to "$generatedDir/main",
                        "xnocompile" to true) {}
            }
        }
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn("wsimport")
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
    gradleVersion = "5.0"
}
