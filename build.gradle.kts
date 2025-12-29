plugins {
    `java-library`
    id("com.diffplug.spotless") version "8.1.0"
}

group = "club.minnced"

version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // TODO: Fix this version on proper release
    compileOnly("net.dv8tion:JDA:6.3.0_DEV")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()

    jvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

spotless {
    kotlinGradle { ktfmt().kotlinlangStyle() }

    java {
        palantirJavaFormat()

        removeUnusedImports()
        trimTrailingWhitespace()
    }
}
