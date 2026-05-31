package com.wageclock.wageclock.domain.history;

import java.time.LocalDateTime;

public record HistoryEvent(
        EventType eventType,
        LocalDateTime timestamp,
        HistoryPayload historyPayload
) {
    public enum EventType {
        PAY_PERIOD_START,
        PAY_PERIOD_END,
        WORK_SESSION_START,
        WORK_SESSION_END,
        EWA_REQUEST
    }

}