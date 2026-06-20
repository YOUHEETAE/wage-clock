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

    public List<DashboardResponse> getDashboards(Long employerId){
        String sql = """
                SELECT DISTINCT ON (e.id)
                w.id AS worker_id, w.name AS worker_name, e.id AS employment_id,
                ws.status, ws.clock_in, ws.earned_amount, ewa.today_ewa_amount
                FROM employments e
                JOIN workers w ON e.worker_id = w.id
                LEFT JOIN work_sessions ws ON ws.employment_id = e.id
                LEFT JOIN(
                SELECT pp.employment_id, SUM(er.requested_amount) AS today_ewa_amount
                FROM ewa_requests er
                JOIN pay_periods pp ON er.pay_period_id = pp.id
                WHERE DATE(er.created_at) = CURRENT_DATE
                AND er.status = 'APPROVED'
                GROUP BY pp.employment_id)
                ewa ON ewa.employment_id = e.id
                WHERE e.employer_id = ?
                ORDER BY e.id, ws.clock_in DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new DashboardResponse(
                rs.getLong("employment_id"),
                rs.getLong("worker_id"),
                rs.getString("worker_name"),
                rs.getBigDecimal("earned_amount"),
                rs.getBigDecimal("today_ewa_amount"),
                rs.getString("status") != null ? WorkSession.WorkSessionStatus.valueOf(rs.getString("status")) : null

        ), employerId);
    }

}
