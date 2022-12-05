# Native Image Building

## Prerequisites

- [graal-vm](https://sdkman.io/jdks)
- [jq](https://stedolan.github.io/jq/)

## Steps

1. Start local env

TODO: to be defined

2. Open the root directory of the project in a separate terminal.

3. Create the directory `META-INF/native-image` to track execution of `native-image-agent`:

```bash
export META_INF_DIR=./cli/src/main/resources/META-INF/native-image
mkdir -p "${META_INF_DIR}"
```

4. Having `java` pointing to `GraalVM`, run the app to trace execution.

```bash
sdk use java 22.3.r19-grl
gu install native-image

# stage your app so you can run it locally without having the app packaged
sbt stage

export APP_BIN_DIR="./cli/target/universal/stage/bin"

# (1) copy events from queue1 to queue2
JAVA_OPTS=-agentlib:native-image-agent=config-output-dir="${META_INF_DIR}" "${APP_BIN_DIR}/sqsmove" -- -s queue1 -d queue2
```

After execution, `META-INF/native-image` directory will have a set of files for `native-image`.

5. Open `reflect-config.json` and remove lines with `Lambda` text inside.
   These entries could be different from compilation to compilation, generate warnings during native image building and are likely not required.

```bash
# jq >= 1.6

export REFLECT_CONFIG_FILE="${META_INF_DIR}/reflect-config.json"
jq 'del( .[] | select(.name | contains("Lambda")))' < "${REFLECT_CONFIG_FILE}" > "${REFLECT_CONFIG_FILE}.bak"
mv "${REFLECT_CONFIG_FILE}.bak" "${REFLECT_CONFIG_FILE}"
```

6. Execute build:

```bash
./native-image-build.sh
```

After building via `native-image`, the files:

```
-H:JNIConfigurationResources=META-INF/native-image/jni-config.json \
-H:ReflectionConfigurationResources=META-INF/native-image/reflect-config.json \
-H:ResourceConfigurationResources=META-INF/native-image/resource-config.json \
-H:DynamicProxyConfigurationResources=META-INF/native-image/proxy-config.json \
-H:SerializationConfigurationResources=META-INF/native-image/serialization-config.json \
```

will be picked up automatically.

7. Verify that the native app works:

```bash
export APP_BUILD_DIR="./cli/target/graalvm-native-image"

# should print help
./sqsmove --help

# run
./sqsmove -s queue1 -d queue2

# copy
sudo cp ${APP_BUILD_DIR}/sqsmove /usr/local/bin/
```
