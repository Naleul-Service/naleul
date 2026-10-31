package com.naleul.naleul.domain.autobiography.repository;

import com.naleul.naleul.domain.autobiography.entity.AutobiographyQuestion;
import com.naleul.naleul.domain.autobiography.enums.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AutobiographyQuestionRepository extends JpaRepository<AutobiographyQuestion, Long> {

    // 내 질문 전체 조회 (답변까지 한 번에 fetch — N+1 방지)
    @Query("""
            SELECT q FROM AutobiographyQuestion q
            LEFT JOIN FETCH q.answers
            WHERE q.user.userId = :userId
            ORDER BY q.createdAt ASC
            """)
    List<AutobiographyQuestion> findAllByUserIdWithAnswer(@Param("userId") Long userId);

    // 단건 조회 + 본인 소유 검증
    @Query("""
            SELECT q FROM AutobiographyQuestion q
            LEFT JOIN FETCH q.answers
            WHERE q.questionId = :questionId AND q.user.userId = :userId
            """)
    Optional<AutobiographyQuestion> findByQuestionIdAndUserId(
            @Param("questionId") Long questionId,
            @Param("userId") Long userId
    );

    // 시스템 기본 질문 템플릿 전체 조회 (회원가입 시 복사용)
    List<AutobiographyQuestion> findByUserIsNullAndQuestionType(QuestionType questionType);

    long countByUser_UserIdAndQuestionType(Long userId, QuestionType questionType);
}