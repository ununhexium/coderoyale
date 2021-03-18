plugins {
  id("org.jetbrains.kotlin.jvm") version "1.3.31"
}

repositories {
  jcenter()
}

tasks {
  test {
    useJUnitPlatform()
  }
}

dependencies {
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
  testImplementation("org.assertj:assertj-core:3.19.0")
  testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.23.1")
}
