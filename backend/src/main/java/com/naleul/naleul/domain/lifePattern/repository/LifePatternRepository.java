package com.naleul.naleul.domain.lifePattern.repository;

import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LifePatternRepository extends JpaRepository<LifePattern, Long> {

    @Query("""
        SELECT p FROM LifePattern p
        WHERE p.user.userId = :userId
        ORDER BY p.sortOrder ASC, p.lifePatternId ASC
    """)
    List<LifePattern> findAllByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT p FROM LifePattern p
        WHERE p.lifePatternId = :id AND p.user.userId = :userId
    """)
    Optional<LifePattern> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT COUNT(p) FROM LifePattern p WHERE p.user.userId = :userId")
    long countByUserId(@Param("userId") Long userId);
}
