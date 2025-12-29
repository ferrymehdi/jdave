package club.minnced.discord.jdave.ffi;

public class LibDaveBindingException extends RuntimeException {
    public LibDaveBindingException(String message) {
        super(message);
    }

    public LibDaveBindingException(Throwable cause) {
        super(cause);
    }
}
