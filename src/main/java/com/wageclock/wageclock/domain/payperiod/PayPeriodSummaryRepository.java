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

    /**
     * 사업장의 근로자별 정산 요약 목록. 프로젝트 대부분은 JPA를 쓰지만 여기는 JdbcTemplate이다.
     * <p>
     * 근무 중인 세션의 실시간 적립액을 SQL 안에서 계산하기 때문이다. JPA로 하면 세션을 전부
     * 로딩해 자바에서 더해야 하고, 근로자 수만큼 그 비용이 붙는다.
     * <p>
     * 조회 전용 목록이라는 점도 이유다. 읽고 바로 응답에 실을 데이터를 엔티티로 매핑하면
     * 영속성 컨텍스트 등록과 스냅샷 생성 비용만 들고 얻는 게 없다.
     * <p>
     * ACTIVE와 SETTLING을 함께 조회한다. 정산 중이라고 목록에서 빠지면 사장 화면이 비어버린다.
     */
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
