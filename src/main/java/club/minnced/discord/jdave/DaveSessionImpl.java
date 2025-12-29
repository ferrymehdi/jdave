package club.minnced.discord.jdave;

import static club.minnced.discord.jdave.ffi.LibDave.isNull;

import club.minnced.discord.jdave.ffi.LibDave;
import club.minnced.discord.jdave.ffi.LibDaveSessionBinding;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

public class DaveSessionImpl implements AutoCloseable {
    private final Arena arena;
    private final MemorySegment session;

    private DaveSessionImpl(Arena arena, MemorySegment session) {
        this.arena = arena;
        this.session = session;
    }

    public static DaveSessionImpl create(String authSessionId) {
        Arena arena = Arena.ofConfined();
        try (Arena local = Arena.ofConfined()) {
            MemorySegment authSessionIdSegment =
                    authSessionId != null ? local.allocateFrom(authSessionId) : MemorySegment.NULL;
            MemorySegment session = LibDaveSessionBinding.createSession(MemorySegment.NULL, authSessionIdSegment);
            return new DaveSessionImpl(arena, session);
        } catch (Throwable e) {
            arena.close();
            throw e;
        }
    }

    private void destroy() {
        LibDaveSessionBinding.destroySession(this.session);
    }

    public void initialize(short version, long groupId, String selfUserId) {
        try (Arena local = Arena.ofConfined()) {
            LibDaveSessionBinding.initializeSession(this.session, version, groupId, local.allocateFrom(selfUserId));
        }
    }

    public void reset() {
        LibDaveSessionBinding.resetSession(this.session);
    }

    public void setProtocolVersion(short version) {
        LibDaveSessionBinding.setProtocolVersion(this.session, version);
    }

    public short getProtocolVersion() {
        return LibDaveSessionBinding.getProtocolVersion(this.session);
    }

    public MemorySegment getKeyRatchet(String selfUserId) {
        try (Arena local = Arena.ofConfined()) {
            return LibDaveSessionBinding.getKeyRatchet(this.session, local.allocateFrom(selfUserId));
        }
    }

    public void setExternalSender(ByteBuffer externalSender) {
        LibDaveSessionBinding.setExternalSender(this.session, externalSender);
    }

    public void processProposals(
            ByteBuffer proposals, List<String> userIds, Consumer<ByteBuffer> sendMLSCommitWelcome) {
        MemorySegment welcome = LibDaveSessionBinding.processProposals(session, proposals, userIds);
        try {
            if (!isNull(welcome)) {
                sendMLSCommitWelcome.accept(welcome.asByteBuffer());
            }
        } finally {
            if (!isNull(welcome)) {
                LibDave.free(welcome);
            }
        }
    }

    // Returns whether we joined the group or not
    public boolean processWelcome(ByteBuffer welcome, List<String> userIds) {
        MemorySegment roster = LibDaveSessionBinding.processWelcome(session, welcome, userIds);
        try {
            return !isNull(roster);
        } finally {
            if (!isNull(roster)) {
                LibDaveSessionBinding.destroyWelcomeResult(roster);
            }
        }
    }

    public CommitResult processCommit(ByteBuffer commit) {
        MemorySegment processedCommit = LibDaveSessionBinding.processCommit(session, commit);
        try {
            boolean isIgnored = LibDaveSessionBinding.isCommitIgnored(processedCommit);
            if (isIgnored) {
                return new CommitResult.Ignored();
            } else {
                return new CommitResult.Success(LibDaveSessionBinding.isCommitJoinedGroup(processedCommit));
            }
        } finally {
            LibDaveSessionBinding.destroyCommitResult(processedCommit);
        }
    }

    public void sendMarshalledKeyPackage(Consumer<ByteBuffer> sendPackage) {
        MemorySegment array = LibDaveSessionBinding.getMarshalledKeyPackage(session);
        try {
            sendPackage.accept(array.asByteBuffer());
        } finally {
            LibDave.free(array);
        }
    }

    @Override
    public void close() {
        this.destroy();
        this.arena.close();
    }

    public sealed interface CommitResult {
        record Ignored() implements CommitResult {}

        record Success(boolean joined) implements CommitResult {}
    }
}
