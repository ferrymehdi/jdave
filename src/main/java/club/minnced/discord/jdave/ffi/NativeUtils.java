package club.minnced.discord.jdave.ffi;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

public class NativeUtils {
    public static String asJavaString(MemorySegment nullTerminatedString) {
        return nullTerminatedString.reinterpret(4096).getString(0, StandardCharsets.UTF_8);
    }

    public static boolean isNull(MemorySegment segment) {
        return segment == null || MemorySegment.NULL.equals(segment);
    }

    public static Object toSizeT(long number) {
        return LibDave.C_SIZE.byteSize() == 8 ? number : (int) number;
    }
}
