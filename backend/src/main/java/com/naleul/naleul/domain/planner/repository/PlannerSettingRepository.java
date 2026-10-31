package com.naleul.naleul.domain.planner.repository;

import com.naleul.naleul.domain.planner.entity.PlannerSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlannerSettingRepository extends JpaRepository<PlannerSetting, Long> {

    /** 사용자 단위 직렬화용 행 잠금 (배치 작업이 같은 사용자에게 동시에 돌지 않도록) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PlannerSetting s WHERE s.userId = :userId")
    Optional<PlannerSetting> findForUpdate(@Param("userId") Long userId);

    /**
     * 설정 행이 없으면 기본값으로 만든다.
     * 웹이 첫 화면에서 API 를 동시에 여러 번 불러도 PK 중복 오류가 나지 않도록 INSERT IGNORE 사용.
     * (나머지 컬럼은 DB DEFAULT 로 채워짐)
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO planner_setting (user_id) VALUES (:userId)", nativeQuery = true)
    int insertDefaultIfAbsent(@Param("userId") Long userId);
}
