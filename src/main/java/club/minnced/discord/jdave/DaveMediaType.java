package club.minnced.discord.jdave;

// typedef enum { DAVE_MEDIA_TYPE_AUDIO = 0, DAVE_MEDIA_TYPE_VIDEO = 1 } DAVEMediaType;
public enum DaveMediaType {
    AUDIO,
    VIDEO,
    UNKNOWN,
    ;

    public static DaveMediaType fromRaw(int type) {
        return switch (type) {
            case 0 -> DaveMediaType.AUDIO;
            case 1 -> DaveMediaType.VIDEO;
            default -> DaveMediaType.UNKNOWN;
        };
    }
}
