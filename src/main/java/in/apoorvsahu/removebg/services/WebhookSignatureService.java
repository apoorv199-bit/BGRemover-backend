package in.apoorvsahu.removebg.services;

public interface WebhookSignatureService {
    boolean verifyWebhookSignature(String svixId, String svixTimestamp, String svixSignature, String payload);
}
