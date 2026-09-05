# OC2 libslirp bridge prototype

This is the native boundary for the future opt-in external network card. It
connects complete guest Ethernet frames to libslirp's user-mode NAT, which
provides DHCP, DNS, IPv4, TCP, UDP, and outbound Internet access without a
host TAP device.

Build the prototype on macOS with:

```sh
cc -dynamiclib -std=c11 -Wall -Wextra \
  -I/opt/homebrew/opt/libslirp/include \
  -L/opt/homebrew/opt/libslirp/lib -lslirp \
  -o /tmp/liboc2slirp.dylib oc2_slirp.c

cc -std=c11 -Wall -Wextra -I/opt/homebrew/opt/libslirp/include \
  -L/opt/homebrew/opt/libslirp/lib -loc2slirp -lslirp \
  -Wl,-rpath,/private/tmp -o /tmp/oc2-slirp-smoke smoke_test.c
/tmp/oc2-slirp-smoke
```

The exported functions are intentionally small enough to bind from JNI or a
platform-specific Java foreign-function layer. The queue is bounded so a
guest cannot grow native memory without limit. The OC2 Java device now loads
this library when the external card is mounted.

## Linux

Install a JDK 21 and `libslirp-dev`, then build:

```sh
cmake -S native/libslirp -B build/native -DCMAKE_BUILD_TYPE=Release
cmake --build build/native
```

This produces `liboc2slirp.so` and the smoke test. To embed it in a mod JAR,
copy it to `linux-x86_64/liboc2slirp.so` under a package directory and pass
that directory as `-PnativeLibDir` to the Gradle platform build.

## Windows

Build from an MSYS2 MinGW64 shell with JDK 21 after installing `mingw-w64-x86_64-libslirp`,
`mingw-w64-x86_64-cmake`, and `mingw-w64-x86_64-ninja`:

```sh
cmake -S native/libslirp -B build/native -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build/native
```

This produces `oc2slirp.dll`. The Windows branch uses `WSAPoll`, Winsock, and
the high-resolution performance counter instead of POSIX polling and clocks.
Copy it to `windows-x86_64/oc2slirp.dll` under a package directory and pass
that directory as `-PnativeLibDir` to the Gradle platform build.
