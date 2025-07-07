package in.apoorvsahu.removebg.exceptions;

public class InvalidPlanException extends RuntimeException {
    public InvalidPlanException(String message) {
        super(message);
    }

    public InvalidPlanException(String message, Throwable cause) {
        super(message, cause);
    }
}
