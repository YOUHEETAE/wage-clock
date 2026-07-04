package com.wageclock.wageclock.domain.dashboard;

import com.wageclock.wageclock.domain.worksession.WorkSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DashboardResponse> getDashboard(Long workplaceId) {
        String sql = """
                SELECT
                    e.id AS employment_id,
                    w.id AS worker_id,
                    w.name AS worker_name,
                    COALESCE(today.today_earned, 0) AS earned_amount,
                    COALESCE(ewa.today_ewa_amount, 0) AS today_ewa_amount,
                    active.status
                FROM employments e
                JOIN workers w ON e.worker_id = w.id
                LEFT JOIN (
                    SELECT employment_id, SUM(earned_amount) AS today_earned
                    FROM work_sessions
                    WHERE DATE(clock_in) = CURRENT_DATE
                    GROUP BY employment_id
                ) today ON today.employment_id = e.id
                LEFT JOIN (
                    SELECT employment_id, status
                    FROM work_sessions
                    WHERE status IN ('WORKING', 'PAUSED')
                ) active ON active.employment_id = e.id
                LEFT JOIN (
                    SELECT pp.employment_id, SUM(er.requested_amount) AS today_ewa_amount
                    FROM ewa_requests er
                    JOIN pay_periods pp ON er.pay_period_id = pp.id
                    WHERE DATE(er.created_at) = CURRENT_DATE
                    AND er.status = 'APPROVED'
                    GROUP BY pp.employment_id
                ) ewa ON ewa.employment_id = e.id
                WHERE e.workplace_id = ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new DashboardResponse(
                rs.getLong("employment_id"),
                rs.getLong("worker_id"),
                rs.getString("worker_name"),
                rs.getBigDecimal("earned_amount"),
                rs.getBigDecimal("today_ewa_amount"),
                rs.getString("status") != null ? WorkSession.WorkSessionStatus.valueOf(rs.getString("status")) : null
        ), workplaceId);
    }
}
