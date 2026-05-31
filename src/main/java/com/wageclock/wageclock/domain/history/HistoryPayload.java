package com.wageclock.wageclock.domain.history;

public sealed interface HistoryPayload
        permits PayPeriodHistory, WorkSessionHistory, EwaHistory{
}
