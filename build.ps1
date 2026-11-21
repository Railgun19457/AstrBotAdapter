$ErrorActionPreference = "Stop"

$gradleCmd = if (Test-Path "E:/Tools/gradle-9.5.0/bin/gradle.bat") {
    "E:/Tools/gradle-9.5.0/bin/gradle.bat"
} else {
    "gradle"
}

& $gradleCmd --no-daemon --console=plain --no-watch-fs @args
exit $LASTEXITCODE
