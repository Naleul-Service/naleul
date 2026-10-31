package com.naleul.naleul.domain.pattern.repository;

import com.naleul.naleul.domain.pattern.entity.BrainDumpLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BrainDumpLogRepository extends JpaRepository<BrainDumpLog, Long> {

    @Query("""
        SELECT l FROM BrainDumpLog l
        WHERE l.userId = :userId
          AND l.confirmedAt >= :from AND l.confirmedAt < :to
        ORDER BY l.confirmedAt ASC
    """)
    List<BrainDumpLog> findAllConfirmedBetween(@Param("userId") Long userId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);
}
