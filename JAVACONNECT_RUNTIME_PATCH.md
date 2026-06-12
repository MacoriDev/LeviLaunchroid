# JavaConnect runtime patch for LeviLaunchroid

This patched launcher no longer packages `jre/bin/java` directly. Recent Android builds allow executing native ELF files from the APK native-library extraction directory, but the OpenJDK `bin/java` launcher may try to re-exec the app-writable copy of itself and fail with `Permission denied`.

Instead, this patch builds a small APK-native executable wrapper and packages it as:

```text
lib/arm64-v8a/libjavaconn_java.so
```

JavaConnect executes this wrapper from the APK native lib directory. The wrapper then loads:

```text
/data/data/org.levimc.launcher/files/JavaConnect/jre/lib/server/libjvm.so
```

and starts ViaProxy with `JNI_CreateJavaVM()` inside the separate wrapper process. This avoids executing `/sdcard/.../jre/bin/java` or `/data/data/.../files/.../bin/java`.

## How to use

1. Build and install this patched LeviLaunchroid APK.
2. Build/install the companion JavaConnect `.so` from the APK-wrapper source zip.
3. JavaConnect still embeds/extracts the full JRE itself, so this launcher patch no longer needs a `jre21-android-arm64.tar.xz` file.

## Changed files

- `app/build.gradle`
  - builds `src/main/cpp/javaconn_java_exec.cpp` with the NDK
  - packages the result as generated JNI lib `libjavaconn_java.so`
- `app/src/main/cpp/javaconn_java_exec.cpp`
  - standalone native executable wrapper
  - loads HotSpot `libjvm.so`
  - launches the jar Main-Class via `sun.launcher.LauncherHelper`
- `AndroidManifest.xml`
  - keeps `android:extractNativeLibs="true"`

User files remain under:

```text
/sdcard/games/JavaConnect
```

Runtime JRE used by the wrapper remains under:

```text
/data/data/org.levimc.launcher/files/JavaConnect/jre
```
