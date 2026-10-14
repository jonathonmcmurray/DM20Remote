# Template for the local, machine-specific toolchain bootstrap.
#
#   1. Copy it:      Copy-Item local-env.example.ps1 local-env.ps1
#   2. Edit the two paths below for your machine.
#   3. Dot-source it before any Gradle command:
#
#          . .\local-env.ps1
#          .\gradlew.bat :app:assembleDebug
#
# local-env.ps1 is git-ignored, so your personal paths are never committed.
# If JAVA_HOME and ANDROID_HOME are already set in your environment, you do
# not need this file at all.

$env:JAVA_HOME    = 'C:\path\to\jdk-21'        # JDK 17 or newer (17-21 tested)
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'   # Android SDK (platform 36 + build-tools 35.0.0)
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
