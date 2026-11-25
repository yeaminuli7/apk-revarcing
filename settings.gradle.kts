val isWindows = System.getProperty("os.name").lowercase().contains("win")
val rootDirFile = rootDir
val localPropsFile = java.io.File(rootDirFile, "local.properties")

if (localPropsFile.exists()) {
  val props = java.util.Properties().apply {
    localPropsFile.inputStream().use { load(it) }
  }
  val currentSdk = props.getProperty("sdk.dir")

  if (isWindows) {
    if (currentSdk.isNullOrBlank() || !java.io.File(currentSdk).exists() || !currentSdk.contains(":")) {
      props.setProperty("sdk.dir", "C:\\Users\\gemar\\AppData\\Local\\Android\\Sdk")
      localPropsFile.outputStream().use { props.store(it, "Auto-configured for Windows") }
    }
  } else {
    if (currentSdk.isNullOrBlank() || currentSdk.contains(":") || !java.io.File(currentSdk).exists()) {
      val androidSdkCandidates = listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        "/data/user/0/com.agy/files/android-sdk",
        "/data/user/0/id.vern.antigravity/files/android-sdk",
        "${System.getProperty("user.home")}/android-sdk",
        "/storage/emulated/0/Android/sdk",
        "/storage/emulated/0/android-sdk",
        "/data/data/com.itsaky.androidide/files/usr/android-sdk",
        "/data/data/com.termux/files/home/android-sdk",
        "/data/data/com.termux/files/usr/lib/android-sdk"
      )
      val foundSdk = androidSdkCandidates.firstOrNull { java.io.File(it).exists() }
      if (foundSdk != null) {
        props.setProperty("sdk.dir", foundSdk)
        localPropsFile.outputStream().use { props.store(it, "Auto-configured for Android/Termux/ACSIDE") }
      } else {
        props.remove("sdk.dir")
        localPropsFile.outputStream().use { props.store(it, "Auto-cleared invalid Windows path for ACSIDE/Android environment") }
      }
    }
  }
} else if (isWindows) {
  val props = java.util.Properties().apply {
    setProperty("sdk.dir", "C:\\Users\\gemar\\AppData\\Local\\Android\\Sdk")
  }
  localPropsFile.outputStream().use { props.store(it, "Auto-generated for Windows") }
}

val agyDir = java.io.File("/data/user/0/com.agy/files")
if (agyDir.exists()) {
  val tmpDir = java.io.File(agyDir, "tmp").apply { mkdirs() }
  if (System.getProperty("java.io.tmpdir").isNullOrBlank() || System.getProperty("java.io.tmpdir") == "/tmp") {
    System.setProperty("java.io.tmpdir", tmpDir.absolutePath)
  }
  if (System.getProperty("user.home").isNullOrBlank() || System.getProperty("user.home") == "/") {
    System.setProperty("user.home", agyDir.absolutePath)
  }
  if (System.getProperty("ANDROID_USER_HOME").isNullOrBlank()) {
    System.setProperty("ANDROID_USER_HOME", java.io.File(agyDir, ".android").absolutePath)
  }
}

val localGradlePropsFile = listOf(
  java.io.File(rootDirFile, "gradle-local.properties"),
  java.io.File(rootDirFile, "gradle.local.properties")
).firstOrNull { it.exists() }

if (localGradlePropsFile != null) {
  java.util.Properties().apply {
    localGradlePropsFile.inputStream().use { load(it) }
    forEach { key, value ->
      val k = key.toString()
      val v = value.toString()
      if (k.startsWith("systemProp.")) {
        System.setProperty(k.removePrefix("systemProp."), v)
      }
    }
  }
}

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}

rootProject.name = "Antigravity"

include(":app")

