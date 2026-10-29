#!/bin/sh
# Managed-cloud helper. Local users should run ./gradlew normally.
set -eu
cd "$(dirname "$0")/.."
: "${JAVA_HOME:=/workspace/toolchains/jdk-25.0.5+8}"
: "${GRADLE_USER_HOME:=/workspace/.apexcraft-gradle}"
export JAVA_HOME GRADLE_USER_HOME
if [ -n "${HTTPS_PROXY:-}" ]; then
    # This environment's supported proxy and existing CA trust; never disable TLS.
    export JAVA_OPTS="${JAVA_OPTS:-} -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
exec ./gradlew --no-daemon "$@"
