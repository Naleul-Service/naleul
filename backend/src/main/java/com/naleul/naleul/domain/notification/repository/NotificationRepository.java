package com.naleul.naleul.domain.notification.repository;

import com.naleul.naleul.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUser_UserIdAndReadFalse(Long userId);
    // ✅ 신규 — 특정 유저의 안 읽은 알림 전체를 한 번에 읽음 처리
    //    엔티티를 하나씩 조회해서 setter 호출하는 대신 벌크 UPDATE로 처리 (N개 알림 → 쿼리 1번)
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.read = true
        WHERE n.user.userId = :userId
        AND n.read = false
        """)
    int markAllAsReadByUserId(@Param("userId") Long userId);
}