pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "ktstrings"
include(":ktstrings", ":ktstrings-compiler", ":ktstrings-gradle-plugin", ":ktstrings-compose")
