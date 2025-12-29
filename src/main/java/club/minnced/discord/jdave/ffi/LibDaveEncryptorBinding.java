package club.minnced.discord.jdave.ffi;

import static club.minnced.discord.jdave.ffi.LibDave.*;
import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public class LibDaveEncryptorBinding {
    static final MethodHandle daveEncryptorCreate;
    static final MethodHandle daveEncryptorDestroy;
    static final MethodHandle daveEncryptorSetKeyRatchet;
    static final MethodHandle daveEncryptorSetPassthroughMode;
    static final MethodHandle daveEncryptorGetProtocolVersion;
    static final MethodHandle daveEncryptorGetMaxCiphertextByteSize;
    static final MethodHandle daveEncryptorEncrypt;
    static final MethodHandle daveEncryptorAssignSsrcToCodec;

    static {
        try {
            // DAVEEncryptorHandle daveEncryptorCreate(void);
            daveEncryptorCreate = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveEncryptorCreate").orElseThrow(), FunctionDescriptor.of(ADDRESS));

            // void daveEncryptorDestroy(DAVEEncryptorHandle encryptor);
            daveEncryptorDestroy = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveEncryptorDestroy").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));

            // void daveEncryptorSetKeyRatchet(DAVEEncryptorHandle encryptor, DAVEKeyRatchetHandle keyRatchet);
            daveEncryptorSetKeyRatchet = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveEncryptorSetKeyRatchet").orElseThrow(),
                    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

            // void daveEncryptorSetPassthroughMode(DAVEEncryptorHandle encryptor, bool passthroughMode);
            daveEncryptorSetPassthroughMode = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveEncryptorSetPassthroughMode").orElseThrow(),
                    FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));

            // uint16_t daveEncryptorGetProtocolVersion(DAVEEncryptorHandle encryptor);
            daveEncryptorGetProtocolVersion = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveEncryptorGetProtocolVersion").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ADDRESS));

            // size_t daveEncryptorGetMaxCiphertextByteSize(DAVEEncryptorHandle encryptor, DAVEMediaType mediaType,
            // size_t frameSize);
            daveEncryptorGetMaxCiphertextByteSize = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveEncryptorGetMaxCiphertextByteSize").orElseThrow(),
                    FunctionDescriptor.of(C_SIZE, ADDRESS, JAVA_INT, C_SIZE));

            // DAVEEncryptorResultCode daveEncryptorEncrypt(DAVEEncryptorHandle encryptor, DAVEMediaType mediaType,
            // uint32_t ssrc, const uint8_t* frame, size_t frameLength, uint8_t* encryptedFrame, size_t
            // encryptedFrameCapacity, size_t* bytesWritten);
            daveEncryptorEncrypt = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveEncryptorEncrypt").orElseThrow(),
                    FunctionDescriptor.of(
                            JAVA_INT,
                            ADDRESS,
                            JAVA_INT,
                            JAVA_INT,
                            ADDRESS.withTargetLayout(JAVA_BYTE),
                            C_SIZE,
                            ADDRESS.withTargetLayout(JAVA_BYTE),
                            C_SIZE,
                            ADDRESS.withTargetLayout(C_SIZE)));

            // void daveEncryptorAssignSsrcToCodec(DAVEEncryptorHandle encryptor, uint32_t ssrc, DAVECodec codecType);
            daveEncryptorAssignSsrcToCodec = LINKER.downcallHandle(
                    SYMBOL_LOOKUP.find("daveEncryptorAssignSsrcToCodec").orElseThrow(),
                    FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT));
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static MemorySegment createEncryptor() {
        try {
            return (MemorySegment) daveEncryptorCreate.invoke();
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static void destroyEncryptor(MemorySegment encryptor) {
        try {
            daveEncryptorDestroy.invoke(encryptor);
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static void setKeyRatchet(MemorySegment encryptor, MemorySegment keyRatchet) {
        try {
            daveEncryptorSetKeyRatchet.invoke(encryptor, keyRatchet);
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static void setPassthroughMode(MemorySegment encryptor, boolean passthroughMode) {
        try {
            daveEncryptorSetPassthroughMode.invoke(encryptor, passthroughMode);
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static short getProtocolVersion(MemorySegment encryptor) {
        try {
            return (short) daveEncryptorGetProtocolVersion.invoke(encryptor);
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static long getMaxCiphertextByteSize(MemorySegment encryptor, int mediaType, long frameSize) {
        try {
            return sizeToLong(daveEncryptorGetMaxCiphertextByteSize.invoke(encryptor, mediaType, frameSize));
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static int encrypt(
            MemorySegment encryptor,
            int mediaType,
            int ssrc,
            MemorySegment frame,
            MemorySegment encryptedFrame,
            MemorySegment bytesWritten) {
        try {
            return (int) daveEncryptorEncrypt.invoke(
                    encryptor,
                    mediaType,
                    ssrc,
                    frame,
                    frame.byteSize(),
                    encryptedFrame,
                    encryptedFrame.byteSize(),
                    bytesWritten);
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }

    public static void assignSsrcToCodec(MemorySegment encryptor, int ssrc, int codecType) {
        try {
            daveEncryptorAssignSsrcToCodec.invoke(encryptor, ssrc, codecType);
        } catch (Throwable e) {
            throw new LibDaveBindingException(e);
        }
    }
}
