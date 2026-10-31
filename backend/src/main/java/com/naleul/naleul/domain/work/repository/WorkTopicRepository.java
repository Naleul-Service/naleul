package com.naleul.naleul.domain.work.repository;

import com.naleul.naleul.domain.work.entity.WorkTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkTopicRepository extends JpaRepository<WorkTopic, Long> {

    @Query("""
        SELECT t FROM WorkTopic t
        WHERE t.goalCategory.goalCategoryId = :goalCategoryId
          AND t.isDeleted = false
        ORDER BY t.sortOrder ASC, t.workTopicId ASC
    """)
    List<WorkTopic> findAllOfGoal(@Param("goalCategoryId") Long goalCategoryId);

    @Query("""
        SELECT t FROM WorkTopic t
        WHERE t.workTopicId = :topicId
          AND t.goalCategory.goalCategoryId = :goalCategoryId
          AND t.isDeleted = false
    """)
    Optional<WorkTopic> findInGoal(@Param("topicId") Long topicId, @Param("goalCategoryId") Long goalCategoryId);
}
