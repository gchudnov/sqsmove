# Native Image Building

1. Go to the root directory of the project.

2. Create the directory `META-INF/native-image` to track execution of `native-image-agent`:

```bash
mkdir -p ./src/main/resources/META-INF/native-image
```

3. Having `java` pointing to `GraalVM`, run the app with `native-image-agent` to trace execution.

```bash
sdk use java 21.0.0.2.r11-grl
java -agentlib:native-image-agent=config-output-dir=./src/main/resources/META-INF/native-image -jar ./target/sqsmove.jar -s queue1 -d queue2
```

After execution, `META-INF/native-image` directory will have a set of files for `native-image`.

4. Open `reflect-config.json` and remove lines with `Lambda` text inside.
   These entries could be different from compilation to compilation, generate warnings during native image building and are likely not required.

```bash
# jq >= 1.6

export REFLECT_CONFIG_FILE=./src/main/resources/META-INF/native-image/reflect-config.json
jq 'del( .[] | select(.name | contains("Lambda")))' < "${REFLECT_CONFIG_FILE}" > "${REFLECT_CONFIG_FILE}.bak"
mv "${REFLECT_CONFIG_FILE}.bak" "${REFLECT_CONFIG_FILE}"
```

5. Execute build:

```bash
./native-image-build.sh
```

After building with `native-image`, the files:

```text
-H:JNIConfigurationResources=META-INF/native-image/jni-config.json \
-H:ReflectionConfigurationResources=META-INF/native-image/reflect-config.json \
-H:ResourceConfigurationResources=META-INF/native-image/resource-config.json \
-H:DynamicProxyConfigurationResources=META-INF/native-image/proxy-config.json \
-H:SerializationConfigurationResources=META-INF/native-image/serialization-config.json \
```

will be picked up automatically.

6. Verify that the native app works:

```bash
# should print help
./sqsmove --help

# run
./sqsmove -s queue1 -d queue2

# copy
cp ./sqsmove /usr/local/bin/
```
