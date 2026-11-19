#!/usr/bin/env bash
# Build, test and package without any external dependency (JDK 21+).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes build/test-classes
javac -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/* build/classes/
if [[ "${1:-}" == "test" ]]; then
  javac -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
  java -cp build/classes:build/test-classes io.dhia.matcher.MatcherTest
fi
jar --create --file build/mission-matcher.jar --main-class io.dhia.matcher.App -C build/classes .
echo "Built build/mission-matcher.jar — run: java -jar build/mission-matcher.jar"
