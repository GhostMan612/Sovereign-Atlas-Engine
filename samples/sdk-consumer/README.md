# Standalone Atlas SDK Consumer (`samples/sdk-consumer`)

Standalone pure-JVM Gradle consumer that resolves **`com.sovereignatlas:atlas-sdk:1.1.0`** as a published Maven binary artifact + metadata (`.jar`, `.pom`, `.module`), with **zero** `includeBuild`, **zero** `project(':atlas-sdk')` substitution, and **zero** Android SDK / Compose / MapLibre / Firebase dependencies.

## Toolchain & Target Versions

- **Gradle:** `8.13` (wrapper included)
- **JDK:** `17+` (verified with JDK `21`)
- **Java bytecode target:** `17` (`sourceCompatibility = targetCompatibility = 17`)
- **Kotlin Gradle Plugin & JVM target:** `2.1.0` (`jvmTarget = "17"`)
- **Published artifact coordinates:** `com.sovereignatlas:atlas-sdk:1.1.0` (while `AtlasSdk.VERSION` runtime facade constant remains `"1.0.0"` per `DEC-024`)

## Standalone Verification (Isolated Directory + Temporary Maven Repo)

### Linux / macOS (Bash)

```bash
# 1. Publish :atlas-sdk to a fresh temporary Maven repository
REPO_DIR="$(mktemp -d)"
(cd apps/atlas-android && ./gradlew :atlas-sdk:publishMavenJavaPublicationToVerificationRepository -PatlasVerifyRepo="$REPO_DIR" --console=plain --no-build-cache)

# 2. Copy the sample consumer to an isolated directory outside the repository
ISO_DIR="$(mktemp -d)/sdk-consumer"
cp -R samples/sdk-consumer/. "$ISO_DIR/"

# 3. Verify positive build & JUnit execution against the published binary artifact
(cd "$ISO_DIR" && unset ANDROID_HOME ANDROID_SDK_ROOT && ./gradlew test --no-daemon --no-build-cache --rerun-tasks --console=plain -PatlasSdkRepo="$REPO_DIR")
```

### Windows PowerShell

```powershell
$env:JAVA_HOME = "C:\Users\612co\.jdks\jbr-21.0.11"
$env:ANDROID_HOME = "C:\android"

$repoDir = Join-Path $env:TEMP "atlas-verify-repo"
Remove-Item -Recurse -Force $repoDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $repoDir | Out-Null

# 1. Publish :atlas-sdk to temporary local Maven repository
Push-Location apps\atlas-android
.\gradlew.bat :atlas-sdk:publishMavenJavaPublicationToVerificationRepository "-PatlasVerifyRepo=$repoDir" --console=plain --no-build-cache
Pop-Location

# 2. Copy consumer to an isolated temp directory outside the repository
$isoDir = Join-Path $env:TEMP "atlas-isolated-consumer"
Remove-Item -Recurse -Force $isoDir -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force samples\sdk-consumer $isoDir

# 3. Run standalone tests against published JAR + metadata
Push-Location $isoDir
$env:ANDROID_HOME = ""
.\gradlew.bat test --no-daemon --no-build-cache --rerun-tasks --console=plain "-PatlasSdkRepo=$repoDir"
Pop-Location
```

## Missing Publication / Metadata Failure Checks

Because `settings.gradle` does not include `mavenLocal()` unless explicitly requested (`-PatlasUseMavenLocal=true`), omitting `-PatlasSdkRepo` or pointing it at an empty/corrupt repository fails dependency resolution cleanly:

```bash
# Fails cleanly when no verification repository is configured:
(cd "$ISO_DIR" && ./gradlew test --no-daemon --console=plain)

# Fails cleanly when the repository directory has no publication:
EMPTY_REPO="$(mktemp -d)"
(cd "$ISO_DIR" && ./gradlew test --no-daemon --console=plain -PatlasSdkRepo="$EMPTY_REPO")
```

## Optional `~/.m2/repository` Consumption

If you published with `./gradlew :atlas-sdk:publishToMavenLocal`, opt into `mavenLocal()` explicitly:

```bash
(cd "$ISO_DIR" && ./gradlew test --no-daemon -PatlasUseMavenLocal=true)
```
