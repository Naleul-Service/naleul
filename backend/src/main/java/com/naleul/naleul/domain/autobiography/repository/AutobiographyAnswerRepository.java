package com.naleul.naleul.domain.autobiography.repository;

import com.naleul.naleul.domain.autobiography.entity.AutobiographyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutobiographyAnswerRepository extends JpaRepository<AutobiographyAnswer, Long> {

    // 특정 질문의 답변 조회
    List<AutobiographyAnswer> findByQuestion_QuestionId(Long questionId);

    // 답변 단건 + 본인 소유 검증
    Optional<AutobiographyAnswer> findByAnswerIdAndUser_UserId(Long answerId, Long userId);

    /**
     * 유저가 작성한 전체 답변 개수를 셈.
     * DEFAULT 질문 답변이든 CUSTOM 질문 답변이든 구분 없이 전부 카운트.
     * (AutobiographyAnswer 엔티티에는 questionType 필드가 없으므로,
     *  "어떤 질문에 답했는지"와 무관하게 답변 작성 행위 자체를 셈)
     */
    long countByUser_UserId(Long userId);

    // ✅ 추가: 특정 질문에 달린 답변 개수 (Free 플랜 "질문당 답변 2개" 제한용)
    long countByQuestion_QuestionId(Long questionId);
}