#!/bin/sh
# Gradle downloads the Reports.Web engine (ac.pao:reports-java-engine) from Maven Central.
cd "$(dirname "$0")" && sh ./gradlew run --quiet
