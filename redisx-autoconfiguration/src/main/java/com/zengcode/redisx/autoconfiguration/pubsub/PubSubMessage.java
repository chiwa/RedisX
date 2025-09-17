package com.zengcode.redisx.autoconfiguration.pubsub;

public class PubSubMessage<T> {
    private String event;
    private String source; // service/node id (optional)
    private T payload;

    public PubSubMessage() {}
    public PubSubMessage(String event, String source, T payload) {
        this.event = event;
        this.source = source;
        this.payload = payload;
    }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }
}