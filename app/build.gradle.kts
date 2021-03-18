import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

  val jupiter = "5.6.0"
  testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter-engine:$jupiter")

}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
  freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
}