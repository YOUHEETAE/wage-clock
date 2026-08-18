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

    /**
     * 사업장 대시보드 조회. 여러 테이블을 집계해 화면용 DTO로 바로 내리므로 JdbcTemplate을 쓴다.
     * 대응하는 엔티티가 없고, 읽고 버릴 목록을 영속성 컨텍스트에 올릴 이유도 없다.
     */
    public List<DashboardResponse> getDashboard(Long workplaceId) {
        String sql = """
                SELECT
                    e.id AS employment_id,
                    w.id AS worker_id,
                    w.name AS worker_name,
                    COALESCE(ewa.today_ewa_amount, 0) AS today_ewa_amount,
                    active.status,
                    active.hourly_wage,
                    active.earned_amount,
                    active.last_resume_at
                FROM employments e
                JOIN workers w ON e.worker_id = w.id
                LEFT JOIN (
                    SELECT employment_id, status, hourly_wage, earned_amount, last_resume_at
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
                rs.getBigDecimal("today_ewa_amount"),
                rs.getString("status") != null ? WorkSession.WorkSessionStatus.valueOf(rs.getString("status")) : null,
                rs.getBigDecimal("hourly_wage"),
                rs.getBigDecimal("earned_amount"),
                rs.getTimestamp("last_resume_at") != null ? rs.getTimestamp("last_resume_at").toLocalDateTime() : null
        ), workplaceId);
    }
}
