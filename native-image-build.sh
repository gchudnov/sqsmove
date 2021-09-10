#!/usr/bin/env bash
set -ex

export APP_NAME=sqsmove
export APP_JAR_PATH="./target/${APP_NAME}.jar"

rm -f "${APP_JAR_PATH}"
rm -f "./${APP_NAME}"
sbt "test; assembly"

# 21.0.0.2.r11

RUNTIME_INIT_LIST="$(cat ./res/graalvm/init-run-time.txt | tr '\n' ',')"

native-image \
  --verbose \
  --initialize-at-build-time \
  --initialize-at-run-time="${RUNTIME_INIT_LIST}" \
  --no-fallback \
  --allow-incomplete-classpath \
  --enable-http \
  --enable-https \
  -H:+ReportUnsupportedElementsAtRuntime \
  -H:+ReportExceptionStackTraces \
  -jar "${APP_JAR_PATH}" "${APP_NAME}"
