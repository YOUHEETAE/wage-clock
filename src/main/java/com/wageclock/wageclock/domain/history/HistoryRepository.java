package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class HistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public HistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HistoryEvent> getHistory(Long employmentId, Timestamp cursor, int size) {
        String sql = """
                SELECT * FROM (
                SELECT 'PAY_PERIOD_START' AS event_type, period_start::timestamp AS event_time,
                       id AS pay_period_id, period_start, period_end,
                       total_earned_amount, total_ewa_amount, status AS pay_period_status,
                       NULL::bigint AS work_session_id, NULL::timestamp AS clock_in, NULL::timestamp AS clock_out,
                       NULL::numeric AS earned_amount,
                       NULL::bigint AS ewa_request_id, NULL::numeric AS requested_amount, NULL::text AS ewa_status
                FROM pay_periods WHERE employment_id = ?
                UNION ALL
                SELECT 'PAY_PERIOD_END' AS event_type, period_end::timestamp AS event_time,
                       id AS pay_period_id, period_start, period_end,
                       total_earned_amount, total_ewa_amount, status AS pay_period_status,
                       NULL::bigint AS work_session_id, NULL::timestamp AS clock_in, NULL::timestamp AS clock_out,
                       NULL::numeric AS earned_amount,
                       NULL::bigint AS ewa_request_id, NULL::numeric AS requested_amount, NULL::text AS ewa_status
                FROM pay_periods WHERE employment_id = ? AND status = 'CLOSED'
                UNION ALL
                SELECT 'WORK_SESSION_START' AS event_type, clock_in AS event_time,
                       NULL::bigint AS pay_period_id, NULL::date AS period_start, NULL::date AS period_end,
                       NULL::numeric AS total_earned_amount, NULL::numeric AS total_ewa_amount, NULL::text AS pay_period_status,
                       id AS work_session_id, clock_in, clock_out, earned_amount,
                       NULL::bigint AS ewa_request_id, NULL::numeric AS requested_amount, NULL::text AS ewa_status
                FROM work_sessions WHERE employment_id = ?
                UNION ALL
                SELECT 'WORK_SESSION_END' AS event_type, clock_out AS event_time,
                       NULL::bigint AS pay_period_id, NULL::date AS period_start, NULL::date AS period_end,
                       NULL::numeric AS total_earned_amount, NULL::numeric AS total_ewa_amount, NULL::text AS pay_period_status,
                       id AS work_session_id, clock_in, clock_out, earned_amount,
                       NULL::bigint AS ewa_request_id, NULL::numeric AS requested_amount, NULL::text AS ewa_status
                FROM work_sessions WHERE employment_id = ? AND clock_out IS NOT NULL
                UNION ALL
                SELECT 'EWA_REQUEST' AS event_type, er.created_at AS event_time,
                       NULL::bigint AS pay_period_id, NULL::date AS period_start, NULL::date AS period_end,
                       NULL::numeric AS total_earned_amount, NULL::numeric AS total_ewa_amount, NULL::text AS pay_period_status,
                       NULL::bigint AS work_session_id, NULL::timestamp AS clock_in, NULL::timestamp AS clock_out,
                       NULL::numeric AS earned_amount,
                       er.id AS ewa_request_id, er.requested_amount, er.status AS ewa_status
                FROM ewa_requests er
                JOIN pay_periods pp ON er.pay_period_id = pp.id
                WHERE pp.employment_id = ?
                ) events
                WHERE event_time > COALESCE(?, '-infinity'::timestamp)
                ORDER BY event_time ASC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String eventType = rs.getString("event_type");
            Timestamp ts = rs.getTimestamp("event_time");
            LocalDateTime timestamp = ts != null ? ts.toLocalDateTime() : null;

            HistoryPayload payload = switch (eventType) {
                case "PAY_PERIOD_START", "PAY_PERIOD_END" -> {
                    Date periodStartDate = rs.getDate("period_start");
                    Date periodEndDate = rs.getDate("period_end");
                    LocalDate periodStart = periodStartDate != null ? periodStartDate.toLocalDate() : null;
                    LocalDate periodEnd = periodEndDate != null ? periodEndDate.toLocalDate() : null;
                    yield new PayPeriodHistory((Long) rs.getObject("pay_period_id"),
                            periodStart, periodEnd,
                            rs.getBigDecimal("total_earned_amount"),
                            rs.getBigDecimal("total_ewa_amount"),
                            PayPeriod.PayPeriodStatus.valueOf(rs.getString("pay_period_status")));
                }
                case "WORK_SESSION_START", "WORK_SESSION_END" -> {
                    Timestamp clockInTs = rs.getTimestamp("clock_in");
                    Timestamp clockOutTs = rs.getTimestamp("clock_out");
                    LocalDateTime clockIn = clockInTs != null ? clockInTs.toLocalDateTime() : null;
                    LocalDateTime clockOut = clockOutTs != null ? clockOutTs.toLocalDateTime() : null;
                    yield new WorkSessionHistory((Long) rs.getObject("work_session_id"),
                            clockIn, clockOut, rs.getBigDecimal("earned_amount"));
                }
                case "EWA_REQUEST" -> new EwaHistory((Long) rs.getObject("ewa_request_id"),
                        rs.getBigDecimal("requested_amount"),
                        EwaRequest.EwaRequestStatus.valueOf(rs.getString("ewa_status")),
                        timestamp);
                default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
            };

            return new HistoryEvent(HistoryEvent.EventType.valueOf(eventType), timestamp, payload);
        }, employmentId, employmentId, employmentId, employmentId, employmentId, cursor, size);
    }
}
