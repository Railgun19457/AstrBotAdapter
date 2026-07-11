$ErrorActionPreference = "Stop"

& gradle --no-daemon --console=plain --no-watch-fs @args
exit $LASTEXITCODE
