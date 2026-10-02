$ErrorActionPreference = "Stop"
# Gradle downloads the Reports.Web engine (ac.pao:reports-java-engine) from Maven Central.
& (Join-Path $PSScriptRoot "gradlew.bat") -p $PSScriptRoot run --quiet
exit $LASTEXITCODE
