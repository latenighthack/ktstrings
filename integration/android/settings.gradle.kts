pluginManagement { includeBuild("../.."); repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name="ktstrings-android-fixture"
include(":catalog", ":catalogReverse", ":resourceOnly", ":app")
includeBuild("../..")
