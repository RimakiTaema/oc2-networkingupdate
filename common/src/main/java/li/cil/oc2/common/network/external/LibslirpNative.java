/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.external;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class LibslirpNative implements AutoCloseable {
    private static final boolean AVAILABLE;

    static {
        boolean available = false;
        try {
            loadBundledLibrary();
            available = true;
        } catch (final UnsatisfiedLinkError ignored) {
            try {
                System.loadLibrary("oc2slirp");
                available = true;
            } catch (final UnsatisfiedLinkError ignoredFallback) {
                // The external card remains unavailable when no native bridge is installed.
            }
        } catch (final IOException ignored) {
            try {
                System.loadLibrary("oc2slirp");
                available = true;
            } catch (final UnsatisfiedLinkError ignoredFallback) {
                // The external card remains unavailable when no native bridge is installed.
            }
        }
        AVAILABLE = available;
    }

    private static void loadBundledLibrary() throws IOException {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        final String platform;
        final String fileName;
        if (os.contains("win")) {
            platform = "windows-";
            fileName = "oc2slirp.dll";
        } else if (os.contains("linux")) {
            platform = "linux-";
            fileName = "liboc2slirp.so";
        } else if (os.contains("mac")) {
            platform = "macos-";
            fileName = "liboc2slirp.dylib";
        } else {
            throw new IOException("Unsupported operating system");
        }

        final String normalizedArchitecture = switch (architecture) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new IOException("Unsupported architecture");
        };
        final String resourceName = "/assets/oc2/native/" + platform + normalizedArchitecture + "/" + fileName;
        try (InputStream input = LibslirpNative.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Bundled native bridge is absent");
            }
            final String suffix = fileName.substring(fileName.lastIndexOf('.'));
            final Path extracted = Files.createTempFile("oc2slirp-", suffix);
            Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            System.load(extracted.toAbsolutePath().toString());
        }
    }

    private long handle;

    private LibslirpNative(final long handle) {
        this.handle = handle;
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    @Nullable
    public static LibslirpNative create() {
        if (!AVAILABLE) {
            return null;
        }
        final long handle = createNative();
        return handle == 0 ? null : new LibslirpNative(handle);
    }

    public void input(final byte[] frame) {
        if (handle != 0) inputNative(handle, frame);
    }

    public void poll(final int timeoutMillis) {
        if (handle != 0) pollNative(handle, timeoutMillis);
    }

    public int nextFrame(final byte[] destination) {
        return handle == 0 ? 0 : nextFrameNative(handle, destination);
    }

    @Override
    public void close() {
        if (handle != 0) {
            destroyNative(handle);
            handle = 0;
        }
    }

    private static native long createNative();
    private static native void destroyNative(long handle);
    private static native void inputNative(long handle, byte[] frame);
    private static native void pollNative(long handle, int timeoutMillis);
    private static native int nextFrameNative(long handle, byte[] destination);

    private LibslirpNative() {
    }
}
