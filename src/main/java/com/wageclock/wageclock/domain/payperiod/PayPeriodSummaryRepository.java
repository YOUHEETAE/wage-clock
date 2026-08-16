package com.wageclock.wageclock.domain.payperiod;

import com.wageclock.wageclock.domain.worksession.WorkSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PayPeriodSummaryRepository {

    private final JdbcTemplate jdbcTemplate;

    public PayPeriodSummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PayPeriodSummaryResponse> getSummaries(Long workplaceId, Long employerId) {
        String sql = """
                SELECT
                    e.id AS employment_id,
                    w.name AS worker_name,
                    pp.period_start,
                    pp.total_earned_amount + COALESCE(active.current_earned, 0) AS total_earned_amount,
                    pp.total_ewa_amount,
                    (pp.total_earned_amount + COALESCE(active.current_earned, 0)) * 0.3 - pp.total_ewa_amount AS remaining_ewa_limit,
                    active.status AS active_session_status,
                    pp.status AS pay_period_status
                FROM pay_periods pp
                JOIN employments e ON pp.employment_id = e.id
                JOIN workers w ON e.worker_id = w.id
                LEFT JOIN (
                    SELECT
                        employment_id,
                        status,
                        CASE
                            WHEN status = 'WORKING'
                                THEN ROUND(earned_amount + (hourly_wage / 3600 * EXTRACT(EPOCH FROM (NOW() - last_resume_at))), 2)
                            ELSE earned_amount
                        END AS current_earned
                    FROM work_sessions
                    WHERE status IN ('WORKING', 'PAUSED')
                ) active ON active.employment_id = e.id
                WHERE e.workplace_id = ?
                AND e.employer_id = ?
                AND pp.status IN ('ACTIVE', 'SETTLING')
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new PayPeriodSummaryResponse(
                rs.getLong("employment_id"),
                rs.getString("worker_name"),
                rs.getDate("period_start").toLocalDate(),
                rs.getBigDecimal("total_earned_amount"),
                rs.getBigDecimal("total_ewa_amount"),
                rs.getBigDecimal("remaining_ewa_limit"),
                rs.getString("active_session_status") != null
                        ? WorkSession.WorkSessionStatus.valueOf(rs.getString("active_session_status"))
                        : null,
                rs.getString("pay_period_status") != null
                        ? PayPeriod.PayPeriodStatus.valueOf(rs.getString("pay_period_status"))
                        : null
        ), workplaceId, employerId);
    }
}
