package com.wageclock.wageclock.domain.statement;

import com.wageclock.wageclock.domain.ewarequest.EwaRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class StatementRepository {

    private final JdbcTemplate jdbcTemplate;

    public StatementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PayPeriodStatementResponse getPayPeriodStatement(Long payPeriodId){
        String sql = """
                SELECT pp.id, pp.period_start, pp.period_end,
                pp.total_earned_amount, pp.total_ewa_amount,
                pp.total_earned_amount - pp.total_ewa_amount AS actual_pay_amount,
                w.id As worker_id, w.name As worker_name
                FROM pay_periods pp
                JOIN employments e ON pp.employment_id = e.id
                JOIN workers w ON e.worker_id = w.id
                WHERE pp.id = ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new PayPeriodStatementResponse(
                rs.getLong("id"),
                rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(),
                rs.getBigDecimal("total_earned_amount"),
                rs.getBigDecimal("total_ewa_amount"),
                rs.getBigDecimal("actual_pay_amount"),
                rs.getLong("worker_id"),
                rs.getString("worker_name")
        ), payPeriodId);
    }

    public List<WorkSessionStatementResponse> getWorkSessionStatements(Long payPeriodId){
        String sql = """
                SELECT id, clock_in, clock_out, hourly_wage, earned_amount
                FROM work_sessions WHERE pay_period_id = ?
                ORDER BY clock_in ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp clockOutTs = rs.getTimestamp("clock_out");
            LocalDateTime clockOut = clockOutTs != null ? clockOutTs.toLocalDateTime() : null;
            return new WorkSessionStatementResponse(
                    rs.getLong("id"),
                    rs.getTimestamp("clock_in").toLocalDateTime(),
                    clockOut,
                    rs.getBigDecimal("hourly_wage"),
                    rs.getBigDecimal("earned_amount")
        );}, payPeriodId);
    }

    public List<EwaRequestStatementResponse> getEwaRequestStatements(Long payPeriodId){
        String sql = """
                SELECT id, requested_amount, status, created_at
                FROM ewa_requests WHERE pay_period_id = ?
                ORDER BY created_at ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new EwaRequestStatementResponse(
                rs.getLong("id"),
                rs.getBigDecimal("requested_amount"),
                EwaRequest.EwaRequestStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), payPeriodId);
    }
}
