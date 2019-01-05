@file:Suppress("MayBeConstant")

import org.gradle.api.Project

val codeVersion = "0.0.3"

val gitHubUser = "boazy"
val gitHubRepo = "kbuilder"
val gitHubRepoDomain = "github.com/$gitHubUser/$gitHubRepo"

val gitTag = "release/$codeVersion"
val gitRepo = "$gitHubRepoDomain.git"

val mainRepoUrl = "https://$gitHubRepoDomain"
val taggedRepoUrl = "$mainRepoUrl/tree/$gitTag"

val Projects.libName
  get() = when (this) {
    Projects.annotations -> "kbuilder-annotations"
    Projects.processor -> "kbuilder"
  }
val libDescription = "A source code generator for Kotlin data classes to automatically create a Builder class."
val libUrl = mainRepoUrl

val libGroupId = "io.github.boazy.kbuilder"
val libVersion = codeVersion
val Projects.libArtifactId: String
    get() = this.libName

val Projects.publicationName
  get() = this.libArtifactId.split("-").joinToString("") { it -> it.capitalize() }.decapitalize()
val Projects.publicationTaskName
  get() = "publish${publicationName.capitalize()}PublicationToMavenRepository"

val authorName = "Boaz Yaniv"

val licenseName = "Apache-2.0"
val Project.licenseFile get() = rootDir.resolve("LICENSE")
val Project.licenseUrl get() = "$mainRepoUrl/blob/$gitTag/${licenseFile.toRelativeString(rootDir)}"

val issuesSystem = "GitHub"
val issuesUrl = "$mainRepoUrl/issues"

val bintrayRepo = "releases"
val bintrayTags = arrayOf("kotlin", "kbuilder")

val Project.bintrayPublish by extraOrDefault(true)
val Project.bintrayOverride by extraOrDefault(false)
val Project.bintrayDryRun by extraOrDefault(false)
val Project.bintrayGpgSign by extraOrDefault(true)
val Project.bintrayMavenCentralSync by extraOrDefault(true)
val Project.bintrayMavenCentralClose by extraOrDefault(true)

val Project.bintrayUser by extraOrEnv("BINTRAY_USER")
val Project.bintrayKey by extraOrEnv("BINTRAY_KEY")

val Project.sonatypeUser by extraOrEnv("SONATYPE_USER")
val Project.sonatypePassword by extraOrEnv("SONATYPE_PASSWORD")

val Project.outputDir get() = buildDir.resolve("out")

enum class Projects { annotations, processor }
