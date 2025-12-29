package club.minnced.discord.jdave.interop;

import club.minnced.discord.jdave.*;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.audio.dave.DaveProtocolCallbacks;
import net.dv8tion.jda.api.audio.dave.DaveSession;

public class JDaveSession implements DaveSession {
    private final long selfUserId;
    private final long channelId;

    private final DaveProtocolCallbacks callbacks;
    private final DaveSessionImpl session;
    private final DaveEncryptor encryptor;
    private final Map<Long, DaveDecryptor> decryptors = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> preparedTransitions = new ConcurrentHashMap<>();

    public JDaveSession(long selfUserId, long channelId, DaveProtocolCallbacks callbacks, DaveSessionImpl session) {
        this.selfUserId = selfUserId;
        this.channelId = channelId;
        this.callbacks = callbacks;
        this.session = session;
        this.encryptor = DaveEncryptor.create(session);
    }

    @Override
    public int getMaxProtocolVersion() {
        return session.getProtocolVersion();
    }

    @Override
    public void assignSsrcToCodec(Codec codec, int ssrc) {
        if (codec == Codec.OPUS) {
            encryptor.assignSsrcToCodec(DaveCodec.OPUS, ssrc);
        }
    }

    @Override
    public int getMaxEncryptedFrameSize(MediaType type, int frameSize) {
        if (type != MediaType.AUDIO) {
            return frameSize * 2;
        }

        return (int) encryptor.getMaxCiphertextByteSize(DaveMediaType.AUDIO, frameSize);
    }

    @Override
    public int getMaxDecryptedFrameSize(MediaType type, long userId, int frameSize) {
        DaveDecryptor decryptor = this.decryptors.get(userId);
        if (decryptor == null || type != MediaType.AUDIO) {
            return frameSize * 2;
        }

        return (int) decryptor.getMaxPlaintextByteSize(DaveMediaType.AUDIO, frameSize);
    }

    @Override
    public void encryptOpus(int ssrc, ByteBuffer audio, ByteBuffer encrypted) {
        encryptor.encrypt(DaveMediaType.AUDIO, ssrc, audio, encrypted);
    }

    @Override
    public void decryptOpus(long userId, ByteBuffer encrypted, ByteBuffer decrypted) {
        DaveDecryptor decryptor = decryptors.get(userId);

        decryptor.decrypt(DaveMediaType.AUDIO, encrypted, decrypted);
    }

    @Override
    public void addUser(long userId) {
        decryptors.put(userId, DaveDecryptor.create());
    }

    @Override
    public void removeUser(long userId) {
        DaveDecryptor decryptor = decryptors.remove(userId);
        if (decryptor != null) {
            decryptor.close();
        }
    }

    @Override
    public void initialize() {}

    @Override
    public void destroy() {
        encryptor.close();
        decryptors.values().forEach(DaveDecryptor::close);
        decryptors.clear();
        session.close();
    }

    @Override
    public void onSelectProtocolAck(int protocolVersion) {
        session.initialize((short) protocolVersion, channelId, Long.toUnsignedString(selfUserId));
    }

    @Override
    public void onDaveProtocolPrepareTransition(int transitionId, int protocolVersion) {
        preparedTransitions.put(transitionId, protocolVersion);
    }

    @Override
    public void onDaveProtocolExecuteTransition(int transitionId) {
        Integer protocolVersion = preparedTransitions.remove(transitionId);
        if (protocolVersion == null) {
            return;
        }

        int oldVersion = session.getProtocolVersion();
        if (oldVersion == protocolVersion) {
            return;
        }

        if (protocolVersion == DaveConstants.DISABLED_PROTOCOL_VERSION) {
            session.reset();
        } else {
            encryptor.initialize(Long.toUnsignedString(selfUserId));
        }
    }

    @Override
    public void onDaveProtocolPrepareEpoch(String epoch, int protocolVersion) {
        if ("1".equals(epoch)) {
            session.reset();
            session.setProtocolVersion((short) protocolVersion);
            encryptor.initialize(Long.toUnsignedString(selfUserId));
            decryptors.forEach(
                    (key, value) -> value.updateKeyRatchet(session.getKeyRatchet(Long.toUnsignedString(key))));
        }
    }

    @Override
    public void onDaveProtocolMLSExternalSenderPackage(ByteBuffer externalSenderPackage) {
        session.setExternalSender(externalSenderPackage);
    }

    @Override
    public void onMLSProposals(ByteBuffer proposals) {
        session.processProposals(proposals, getUserIds(), callbacks::sendMLSCommitWelcome);
    }

    @Override
    public void onMLSPrepareCommitTransition(int transitionId, ByteBuffer commit) {
        DaveSessionImpl.CommitResult result = session.processCommit(commit);
        switch (result) {
            case DaveSessionImpl.CommitResult.Ignored ignored -> {
                preparedTransitions.remove(transitionId);
            }
            case DaveSessionImpl.CommitResult.Success success -> {
                if (success.joined()) {
                    prepareTransition(transitionId);
                } else {
                    sendInvalidCommitWelcome(transitionId);
                }
            }
        }
    }

    @Override
    public void onMLSWelcome(int transitionId, ByteBuffer welcome) {
        boolean joinedGroup = session.processWelcome(welcome, getUserIds());

        if (joinedGroup) {
            prepareTransition(transitionId);
        } else {
            sendInvalidCommitWelcome(transitionId);
        }
    }

    private List<String> getUserIds() {
        return decryptors.keySet().stream().map(Long::toUnsignedString).toList();
    }

    private void prepareTransition(int transitionId) {
        decryptors.forEach((userId, decryptor) -> {
            if (userId == selfUserId) {
                return;
            }

            MemorySegment keyRatchet = session.getKeyRatchet(Long.toUnsignedString(userId));
            decryptor.updateKeyRatchet(keyRatchet);
        });

        if (transitionId == DaveConstants.INIT_TRANSITION_ID) {
            encryptor.initialize(Long.toUnsignedString(selfUserId));
        } else {
            Integer preparedProtocolVersion = preparedTransitions.remove(transitionId);
            session.setProtocolVersion(preparedProtocolVersion.shortValue());
        }

        if (transitionId != DaveConstants.INIT_TRANSITION_ID) {
            preparedTransitions.put(transitionId, (int) session.getProtocolVersion());
            callbacks.sendDaveProtocolReadyForTransition(transitionId);
        }
    }

    private void sendInvalidCommitWelcome(int transitionId) {
        callbacks.sendMLSInvalidCommitWelcome(transitionId);
        session.sendMarshalledKeyPackage(callbacks::sendMLSKeyPackage);
    }
}
