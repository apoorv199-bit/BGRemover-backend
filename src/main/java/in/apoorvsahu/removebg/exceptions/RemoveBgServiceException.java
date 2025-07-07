package in.apoorvsahu.removebg.exceptions;

public class RemoveBgServiceException extends RuntimeException {
    public RemoveBgServiceException(String message) {
        super(message);
    }

    public RemoveBgServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
