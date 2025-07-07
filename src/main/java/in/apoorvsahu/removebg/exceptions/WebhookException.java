package in.apoorvsahu.removebg.exceptions;

public class WebhookException extends RuntimeException {
    public WebhookException(String message) {
        super(message);
    }

    public WebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
