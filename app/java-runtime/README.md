# JavaConnect runtime note

This patched launcher no longer needs a JRE archive here.

The launcher now builds an APK-native wrapper executable:

```text
lib/arm64-v8a/libjavaconn_java.so
```

The full Java 21 runtime is still embedded/extracted by `JavaConnect.so`.
