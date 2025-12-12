package com.pip.ingest.pubsub;

public class PubSubPushEnvelope {

    private PubSubMessage message;
    private String subscription;

    public PubSubPushEnvelope() {
    }

    public PubSubMessage getMessage() {
        return message;
    }

    public void setMessage(PubSubMessage message) {
        this.message = message;
    }

    public String getSubscription() {
        return subscription;
    }

    public void setSubscription(String subscription) {
        this.subscription = subscription;
    }
}
