# Bundled native libraries

The Gradle property `nativeLibDir` embeds prebuilt libraries into the mod JAR.
The directory must use these platform paths:

```text
linux-x86_64/liboc2slirp.so
windows-x86_64/oc2slirp.dll
```

For example:

```sh
./gradlew :fabric:remapJar -PnativeLibDir=/path/to/native-package
```

The Java side extracts the matching library to a temporary file and loads it
when the external card is first initialized. The native bridge still needs its
libslirp runtime dependency unless it was linked statically.
