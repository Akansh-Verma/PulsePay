package com.pulsepay.common.event;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public abstract class BaseEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String correlationId;
}
