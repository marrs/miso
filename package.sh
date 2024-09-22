#!/bin/sh

# Generate classes
clj -M -e "(compile 'miso.core)"

# Build uberjar
clj -Sdeps '{:aliases {:uberjar {:replace-deps {uberdeps/uberdeps {:mvn/version "1.4.0"}} :replace-paths []}}}' \
    -M:uberjar -m uberdeps.uberjar --main-class miso.core 

# Build graalvm native image.
native-image -jar "target/miso.jar" \
    --no-fallback \
    --initialize-at-build-time="" \
    target/miso
