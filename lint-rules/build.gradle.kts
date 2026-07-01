plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}
