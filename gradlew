#!/bin/sh
# Thin launcher. Run `gradle wrapper --gradle-version 8.12` once to generate
# gradle/wrapper/gradle-wrapper.jar, after which this script is fully functional.
DIR=$(cd "$(dirname "$0")" && pwd)
if [ ! -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "gradle-wrapper.jar missing. Run: gradle wrapper --gradle-version 8.12" >&2
  exit 1
fi
exec java -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
