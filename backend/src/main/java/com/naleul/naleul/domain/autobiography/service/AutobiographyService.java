package com.naleul.naleul.domain.autobiography.service;

import com.naleul.naleul.domain.autobiography.dto.request.*;
import com.naleul.naleul.domain.autobiography.dto.response.*;
import com.naleul.naleul.domain.autobiography.entity.AutobiographyAnswer;
import com.naleul.naleul.domain.autobiography.entity.AutobiographyQuestion;
import com.naleul.naleul.domain.autobiography.enums.QuestionType;
import com.naleul.naleul.domain.autobiography.repository.AutobiographyAnswerRepository;
import com.naleul.naleul.domain.autobiography.repository.AutobiographyQuestionRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutobiographyService {

    private final AutobiographyQuestionRepository questionRepository;
    private final AutobiographyAnswerRepository answerRepository;
    private final UserFinder userFinder;

    // ── 회원가입 시 기본 질문 복사 ────────────────────────────
    // ⚠️ 중요: 여기는 시스템이 자동으로 호출하는 로직이라
    //          요금제 제한 체크를 절대 넣으면 안 됨.
    //          넣으면 회원가입 자체가 막혀버리는 심각한 버그가 생김.
    @Transactional
    public void createDefaultQuestions(User user) {
        List<AutobiographyQuestion> defaults =
                questionRepository.findByUserIsNullAndQuestionType(QuestionType.DEFAULT);

        List<AutobiographyQuestion> userQuestions = defaults.stream()
                .map(template -> AutobiographyQuestion.builder()
                        .user(user)
                        .content(template.getContent())
                        .questionType(QuestionType.DEFAULT)
                        .build())
                .toList();

        questionRepository.saveAll(userQuestions);
    }

    // ── QUESTION CREATE ────────────────────────────────────
    // 사용자 직접 질문 생성 (CUSTOM 타입만 여기로 만들어짐)
    @Transactional
    public QuestionWithAnswerResponse createQuestion(QuestionCreateRequest request, Long userId) {
        User user = userFinder.findUser(userId);

        validateQuestionLimit(user);

        AutobiographyQuestion question = AutobiographyQuestion.builder()
                .user(user)
                .content(request.getContent())
                .questionType(QuestionType.CUSTOM)
                .build();

        AutobiographyQuestion saved = questionRepository.save(question);
        return QuestionWithAnswerResponse.from(saved);
    }

    // ── READ ───────────────────────────────────────────────
    public List<QuestionWithAnswerResponse> getAllMyQuestions(Long userId) {
        userFinder.validateUserExists(userId);
        return questionRepository.findAllByUserIdWithAnswer(userId)
                .stream()
                .map(QuestionWithAnswerResponse::from)
                .toList();
    }

    public QuestionWithAnswerResponse getQuestion(Long questionId, Long userId) {
        AutobiographyQuestion question = questionRepository
                .findByQuestionIdAndUserId(questionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTOBIOGRAPHY_QUESTION_NOT_FOUND));

        return QuestionWithAnswerResponse.from(question);
    }

    // ── QUESTION UPDATE ────────────────────────────────────
    @Transactional
    public QuestionWithAnswerResponse updateQuestion(Long questionId, Long userId,
                                                     QuestionUpdateRequest request) {
        AutobiographyQuestion question = questionRepository
                .findByQuestionIdAndUserId(questionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTOBIOGRAPHY_QUESTION_NOT_FOUND));

        // 기본 질문은 수정 불가 (커스텀 질문만 수정 가능)
        if (question.getQuestionType() == QuestionType.DEFAULT) {
            throw new CustomException(ErrorCode.AUTOBIOGRAPHY_DEFAULT_QUESTION_CANNOT_MODIFY);
        }

        question.updateContent(request.getContent());
        return QuestionWithAnswerResponse.from(question);
    }

    // ── QUESTION DELETE ────────────────────────────────────
    @Transactional
    public void deleteQuestion(Long questionId, Long userId) {
        AutobiographyQuestion question = questionRepository
                .findByQuestionIdAndUserId(questionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTOBIOGRAPHY_QUESTION_NOT_FOUND));

        // 기본 질문은 삭제 불가
        if (question.getQuestionType() == QuestionType.DEFAULT) {
            throw new CustomException(ErrorCode.AUTOBIOGRAPHY_DEFAULT_QUESTION_CANNOT_DELETE);
        }

        // CascadeType.ALL + orphanRemoval = true 덕분에
        // 질문 삭제 시 연결된 답변도 자동 삭제
        questionRepository.delete(question);
    }

    @Transactional
    public QuestionWithAnswerResponse createAnswer(Long questionId, Long userId,
                                                   AnswerCreateRequest request) {
        AutobiographyQuestion question = questionRepository
                .findByQuestionIdAndUserId(questionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTOBIOGRAPHY_QUESTION_NOT_FOUND));

        User user = userFinder.findUser(userId);

        validateAnswerLimit(user, questionId);

        AutobiographyAnswer answer = AutobiographyAnswer.builder()
                .question(question)
                .user(user)
                .content(request.getContent())
                .build();

        answerRepository.save(answer);

        AutobiographyQuestion updated = questionRepository
                .findByQuestionIdAndUserId(questionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTOBIOGRAPHY_QUESTION_NOT_FOUND));

        return QuestionWithAnswerResponse.from(updated);
    }

    // ── ANSWER UPDATE ──────────────────────────────────────
    @Transactional
    public AnswerResponse updateAnswer(Long answerId, Long userId,
                                       AnswerUpdateRequest request) {
        AutobiographyAnswer answer = answerRepository
                .findByAnswerIdAndUser_UserId(answerId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTOBIOGRAPHY_ANSWER_NOT_FOUND));

        answer.updateContent(request.getContent());
        return AnswerResponse.from(answer);
    }

    // ── ANSWER DELETE ──────────────────────────────────────
    @Transactional
    public void deleteAnswer(Long answerId, Long userId) {
        AutobiographyAnswer answer = answerRepository
                .findByAnswerIdAndUser_UserId(answerId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTOBIOGRAPHY_ANSWER_NOT_FOUND));

        answerRepository.delete(answer);
    }

    // ── private 헬퍼 ────────────────────────────────────────

    /**
     * Free 플랜 유저의 "자문자답 질문(CUSTOM 타입) 생성 개수" 제한을 체크.
     * DEFAULT 타입(시스템 자동 생성)은 카운트하지 않음.
     * Pro 유저는 무제한이므로 바로 통과.
     */
    private void validateQuestionLimit(User user) {
        if (user.isCurrentlyPremium()) {
            return;
        }

        long currentCount = questionRepository.countByUser_UserIdAndQuestionType(
                user.getUserId(), QuestionType.CUSTOM
        );

        if (currentCount >= PlanLimits.AUTOBIOGRAPHY_QUESTION_FREE_LIMIT) {
            throw new CustomException(ErrorCode.AUTOBIOGRAPHY_QUESTION_LIMIT_EXCEEDED);
        }
    }

    /**
     * Free 플랜 유저의 "질문 하나당 답변 작성 개수" 제한을 체크.
     * DEFAULT 질문이든 CUSTOM 질문이든 동일하게 질문당 최대 2개까지.
     * Pro 유저는 무제한이므로 바로 통과.
     */
    private void validateAnswerLimit(User user, Long questionId) {
        if (user.isCurrentlyPremium()) {
            return;
        }

        long currentCount = answerRepository.countByQuestion_QuestionId(questionId);

        if (currentCount >= PlanLimits.AUTOBIOGRAPHY_ANSWER_PER_QUESTION_FREE_LIMIT) {
            throw new CustomException(ErrorCode.AUTOBIOGRAPHY_ANSWER_LIMIT_EXCEEDED);
        }
    }
}