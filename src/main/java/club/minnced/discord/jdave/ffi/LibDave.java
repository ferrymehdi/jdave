package club.minnced.discord.jdave.ffi;

import static java.lang.foreign.ValueLayout.*;

import club.minnced.discord.jdave.DaveLoggingSeverity;
import club.minnced.discord.jdave.NativeLibraryLoader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

public class LibDave {
    static final Linker LINKER = Linker.nativeLinker();
    static final SymbolLookup SYMBOL_LOOKUP;
    public static final MemoryLayout C_SIZE;

    static {
        SYMBOL_LOOKUP = NativeLibraryLoader.getSymbolLookup();
        C_SIZE = LINKER.canonicalLayouts().get("size_t");
    }

    private LibDave() {}

    static final MethodHandle daveMaxSupportedProtocolVersion;
    static final MethodHandle daveSetLogSinkCallback;
    public static final MethodHandle free;

    static {
        try {
            // uint16_t daveMaxSupportedProtocolVersion(void);
            daveMaxSupportedProtocolVersion = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveMaxSupportedProtocolVersion").orElseThrow(),
                    FunctionDescriptor.of(JAVA_SHORT));

            // void daveSetLogSinkCallback(DAVELogSinkCallback callback);
            daveSetLogSinkCallback = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveSetLogSinkCallback").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));

            // void free(void*);
            free = LINKER.downcallHandle(SYMBOL_LOOKUP.find("free").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void free(MemorySegment segment) {
        try {
            free.invoke(segment);
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static long readSize(MemorySegment segment) {
        if (C_SIZE.byteSize() == 4) {
            return segment.get(JAVA_INT, 0);
        } else {
            return segment.get(JAVA_LONG, 0);
        }
    }

    static long sizeToLong(Object size) {
        return ((Number) size).longValue();
    }

    static Object longToSize(long size) {
        if (C_SIZE.byteSize() == 4) {
            return (int) size;
        } else {
            return size;
        }
    }

    public static short getMaxSupportedProtocolVersion() {
        try {
            return (short) daveMaxSupportedProtocolVersion.invoke();
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static void setLogSinkCallback(Arena arena, LogSinkCallback logSinkCallback) {
        LogSinkCallbackMapper upcallMapper = new LogSinkCallbackMapper(logSinkCallback);

        MemorySegment upcall = LINKER.upcallStub(
                upcallMapper.getMethodHandle(),
                FunctionDescriptor.ofVoid(
                        JAVA_INT, ADDRESS.withTargetLayout(JAVA_BYTE), JAVA_INT, ADDRESS.withTargetLayout(JAVA_BYTE)),
                arena);

        try {
            daveSetLogSinkCallback.invoke(upcall);
        } catch (Throwable e) {
            free(upcall);
            throw new LibDaveBindingException(e);
        }
    }

    public static boolean isNull(MemorySegment segment) {
        return segment == null || MemorySegment.NULL.equals(segment);
    }

    // typedef void (*DAVELogSinkCallback)(DAVELoggingSeverity severity,
    //                                    const char* file,
    //                                    int line,
    //                                    const char* message);
    public interface LogSinkCallback {
        void onLogSink(DaveLoggingSeverity severity, ByteBuffer file, int line, ByteBuffer message);
    }

    private static class LogSinkCallbackMapper {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
        private static final MethodType TYPE =
                MethodType.methodType(Integer.TYPE, MemorySegment.class, Integer.TYPE, MemorySegment.class);

        private final LogSinkCallback logSinkCallback;

        LogSinkCallbackMapper(LogSinkCallback logSinkCallback) {
            this.logSinkCallback = logSinkCallback;
        }

        public void onCallback(int severity, MemorySegment file, int line, MemorySegment message) {
            DaveLoggingSeverity severityEnum =
                    switch (severity) {
                        case 0 -> DaveLoggingSeverity.VERBOSE;
                        case 1 -> DaveLoggingSeverity.INFO;
                        case 2 -> DaveLoggingSeverity.WARNING;
                        case 3 -> DaveLoggingSeverity.ERROR;
                        case 4 -> DaveLoggingSeverity.NONE;
                        default -> DaveLoggingSeverity.UNKNOWN;
                    };

            logSinkCallback.onLogSink(severityEnum, file.asByteBuffer(), line, message.asByteBuffer());
        }

        MethodHandle getMethodHandle() {
            try {
                return LOOKUP.bind(
                        this,
                        "onCallback",
                        MethodType.methodType(Integer.TYPE, MemorySegment.class, Integer.TYPE, MemorySegment.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
