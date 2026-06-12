# JavaConnect runtime patch for LeviLaunchroid

This patched source adds a Gradle task that packages the Android Java 21 `bin/java` executable into the APK native lib directory as:

```text
lib/arm64-v8a/libjavaconn_java.so
```

This is needed because Android 10+ blocks executing files unpacked to app-writable paths such as `/sdcard` or `/data/data/<package>/files`.

## How to use

1. Put a Pojav/Android arm64 Java 21 runtime archive here:

```text
app/java-runtime/jre21-android-arm64.tar.xz
```

Accepted archive formats/names include:

```text
jre21-android-arm64.tar
jre21-android-arm64.tar.xz
jre21-android-aarch64.tar
jre21-android-aarch64.tar.xz
jre21-aarch64.tar
jre21-aarch64.tar.xz
jre21-aarch64.zip
```

Or set an environment variable before building:

```bash
export JAVACONNECT_JRE_TAR=/path/to/jre21-android-arm64.tar.xz
```

2. Build the launcher APK normally.

3. Install the rebuilt launcher APK.

4. Build/install the companion JavaConnect `.so` from `JavaConnect_apk_native_exec_source.zip`.

## What changed

- `app/build.gradle`
  - adds `prepareJavaConnectJavaExec`
  - extracts only `bin/java`
  - packages it as generated JNI lib `libjavaconn_java.so`
- `AndroidManifest.xml`
  - adds `android:extractNativeLibs="true"`
- `app/java-runtime/README.md`
  - explains where to place the runtime archive

The full JRE is still embedded/extracted by JavaConnect.so. The APK only needs the `bin/java` executable in an Android-approved executable location.
