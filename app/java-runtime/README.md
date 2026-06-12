# JavaConnect Android Java 21 runtime

Put a Pojav/Android arm64 Java 21 runtime archive here before building the APK.

Accepted names:

- `jre21-android-arm64.tar.xz`
- `jre21-android-arm64.tar`
- `jre21-android-aarch64.tar.xz`
- `jre21-android-aarch64.tar`
- `jre21-aarch64.zip`

The Gradle task extracts only `bin/java` and packages it as:

```text
app/build/generated/javaconnectJniLibs/arm64-v8a/libjavaconn_java.so
```

JavaConnect.so still embeds/extracts the full JRE for `JAVA_HOME`; the APK-native file is only the executable entry point needed to bypass Android 10+ app-data exec restrictions.
