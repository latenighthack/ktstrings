plugins {
    kotlin("jvm")
    application
    id("com.vanniktech.maven.publish")
}

kotlin { jvmToolchain(17) }
application { mainClass.set("com.latenighthack.ktstrings.compiler.MainKt") }
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.ibm.icu:icu4j:78.3")
    testImplementation(kotlin("test"))
    testImplementation(project(":ktstrings"))
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.10")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
}

tasks.test { useJUnitPlatform() }
