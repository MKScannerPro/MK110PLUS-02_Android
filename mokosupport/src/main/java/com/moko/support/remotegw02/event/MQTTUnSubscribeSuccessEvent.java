package com.moko.support.remotegw02.event;

public class MQTTUnSubscribeSuccessEvent {
    private String topic;

    public MQTTUnSubscribeSuccessEvent(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
