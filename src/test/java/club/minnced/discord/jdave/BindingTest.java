package club.minnced.discord.jdave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import club.minnced.discord.jdave.ffi.LibDave;
import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BindingTest {
    @Test
    void testMaxVersion() {
        assertEquals(1, LibDave.getMaxSupportedProtocolVersion());
    }

    @Test
    void testSessionCreateDestroy() {
        Random random = new Random(42);
        try (DaveSessionImpl session = DaveSessionImpl.create(null)) {
            session.initialize((short) 1, random.nextLong(), Long.toUnsignedString(random.nextLong()));
            assertEquals(1, session.getProtocolVersion());
        }
    }

    @Test
    void testEncryptor() {
        Random random = new Random(42);
        long channelId = random.nextLong();
        String selfUserId = Long.toUnsignedString(random.nextLong());

        try (DaveSessionImpl session = DaveSessionImpl.create(null)) {
            session.initialize((short) 1, channelId, selfUserId);
            assertEquals(1, session.getProtocolVersion());
            session.sendMarshalledKeyPackage(session::setExternalSender);

            try (DaveEncryptor encryptor = DaveEncryptor.create(session)) {
                encryptor.initialize(selfUserId);

                int ssrc = random.nextInt();
                encryptor.assignSsrcToCodec(DaveCodec.OPUS, ssrc);

                byte[] plaintext = new byte[512];
                random.nextBytes(plaintext);

                ByteBuffer output = ByteBuffer.allocateDirect(587);
                ByteBuffer input = ByteBuffer.allocateDirect(plaintext.length);
                input.put(plaintext);

                assertEquals(
                        output.capacity(), encryptor.getMaxCiphertextByteSize(DaveMediaType.AUDIO, input.capacity()));

                DaveEncryptor.DaveEncryptorResult result = encryptor.encrypt(DaveMediaType.AUDIO, ssrc, input, output);

                assertEquals(DaveEncryptor.DaveEncryptResultType.FAILURE, result.type());
            }
        }
    }
}
