package com.krithika.ratelimiter.repository;

import com.krithika.ratelimiter.model.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, String> {

    long countByApiKey(String apiKey);

    long countByApiKeyAndAllowedTrue(String apiKey);

    long countByApiKeyAndAllowedFalse(String apiKey);

    long countByApiKeyAndCreatedAtAfter(String apiKey, LocalDateTime since);

    /** Hourly breakdown for analytics — returns [hour_string, count] pairs */
    @Query(value = """
        SELECT TO_CHAR(created_at, 'YYYY-MM-DD HH24:00') AS hour, COUNT(*) AS count
        FROM api_usage_logs
        WHERE api_key = :apiKey AND created_at >= :since
        GROUP BY hour
        ORDER BY hour
        """, nativeQuery = true)
    List<Object[]> findHourlyUsage(@Param("apiKey") String apiKey,
                                   @Param("since") LocalDateTime since);

    /** Top N clients by request volume */
    @Query(value = """
        SELECT api_key, client_name, COUNT(*) AS total
        FROM api_usage_logs
        WHERE created_at >= :since
        GROUP BY api_key, client_name
        ORDER BY total DESC
        LIMIT :n
        """, nativeQuery = true)
    List<Object[]> findTopClients(@Param("since") LocalDateTime since,
                                  @Param("n") int n);

    List<ApiUsageLog> findTop20ByApiKeyOrderByCreatedAtDesc(String apiKey);
}
