import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.protobuf.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.google.protobuf") version "0.9.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `java-library`
}

group = "me.eternalblue"
version = (findProperty("version") as String?) ?: "1.0-SNAPSHOT"

val grpcVersion = "1.71.0"
val protobufVersion = "3.25.5"
val paperApiVersion = "1.20.4-R0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    implementation(kotlin("stdlib"))
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-okhttp:$grpcVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("io.grpc:grpc-inprocess:$grpcVersion")
    testImplementation("io.grpc:grpc-testing:$grpcVersion")
    testImplementation("javax.annotation:javax.annotation-api:1.3.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                id("grpc")
            }
        }
    }
}

sourceSets {
    named("main") {
        java.srcDir("build/generated/source/proto/main/java")
        java.srcDir("build/generated/source/proto/main/grpc")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("generateProto")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("io.grpc", "me.eternalblue.agent4minecraft.shadow.io.grpc")
    relocate("com.google.protobuf", "me.eternalblue.agent4minecraft.shadow.com.google.protobuf")
    relocate("com.google.common", "me.eternalblue.agent4minecraft.shadow.com.google.common")
    relocate("com.google.thirdparty", "me.eternalblue.agent4minecraft.shadow.com.google.thirdparty")
    relocate("io.perfmark", "me.eternalblue.agent4minecraft.shadow.io.perfmark")
    relocate("okhttp3", "me.eternalblue.agent4minecraft.shadow.okhttp3")
    relocate("okio", "me.eternalblue.agent4minecraft.shadow.okio")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
