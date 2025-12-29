package club.minnced.discord.jdave;

import static club.minnced.discord.jdave.ffi.LibDave.C_SIZE;
import static club.minnced.discord.jdave.ffi.LibDave.readSize;

import club.minnced.discord.jdave.ffi.LibDaveEncryptorBinding;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class DaveEncryptor implements AutoCloseable {
    private final Arena arena;
    private final MemorySegment encryptor;
    private final DaveSessionImpl session;

    private DaveEncryptor(Arena arena, MemorySegment encryptor, DaveSessionImpl session) {
        this.arena = arena;
        this.encryptor = encryptor;
        this.session = session;

        LibDaveEncryptorBinding.setPassthroughMode(encryptor, true);
    }

    public static DaveEncryptor create(DaveSessionImpl session) {
        return new DaveEncryptor(Arena.ofConfined(), LibDaveEncryptorBinding.createEncryptor(), session);
    }

    private void destroy() {
        LibDaveEncryptorBinding.destroyEncryptor(encryptor);
    }

    public void initialize(String selfUserId) {
        LibDaveEncryptorBinding.setPassthroughMode(encryptor, false);
        LibDaveEncryptorBinding.setKeyRatchet(encryptor, session.getKeyRatchet(selfUserId));
    }

    public short getProtocolVersion() {
        return LibDaveEncryptorBinding.getProtocolVersion(encryptor);
    }

    public long getMaxCiphertextByteSize(DaveMediaType mediaType, long frameSize) {
        return LibDaveEncryptorBinding.getMaxCiphertextByteSize(encryptor, mediaType.ordinal(), frameSize);
    }

    public void assignSsrcToCodec(DaveCodec codec, int ssrc) {
        LibDaveEncryptorBinding.assignSsrcToCodec(encryptor, ssrc, codec.ordinal());
    }

    public DaveEncryptorResult encrypt(DaveMediaType mediaType, int ssrc, ByteBuffer input, ByteBuffer output) {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment bytesWrittenPtr = local.allocate(C_SIZE);

            int result = LibDaveEncryptorBinding.encrypt(
                    encryptor,
                    mediaType.ordinal(),
                    ssrc,
                    MemorySegment.ofBuffer(input),
                    MemorySegment.ofBuffer(output),
                    bytesWrittenPtr);

            long bytesWritten = readSize(bytesWrittenPtr);
            DaveEncryptResultType resultType = DaveEncryptResultType.fromRaw(result);

            if (resultType == DaveEncryptResultType.SUCCESS && bytesWritten > 0) {
                output.limit(output.position() + (int) bytesWritten);
            }

            return new DaveEncryptorResult(resultType, bytesWritten);
        }
    }

    @Override
    public void close() {
        this.destroy();
        this.arena.close();
    }

    public record DaveEncryptorResult(DaveEncryptResultType type, long bytesWritten) {}

    public enum DaveEncryptResultType {
        SUCCESS,
        FAILURE,
        ;

        public static DaveEncryptResultType fromRaw(int result) {
            return switch (result) {
                case 0 -> SUCCESS;
                default -> FAILURE;
            };
        }
    }
}
