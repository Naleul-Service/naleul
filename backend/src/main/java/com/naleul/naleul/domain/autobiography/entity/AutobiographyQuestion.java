package com.naleul.naleul.domain.autobiography.entity;

import com.naleul.naleul.domain.autobiography.enums.QuestionType;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "autobiography_question")
public class AutobiographyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long questionId;

    // user가 null = 시스템 기본 질문 템플릿
    // user가 있음 = 이 유저에게 복사된 질문 or 직접 만든 질문
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")  // nullable — 기본 질문 템플릿은 user 없음
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    // 답변과 1:1 관계 (질문 하나에 답변 하나)
    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AutobiographyAnswer> answers = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AutobiographyQuestion(User user, String content, QuestionType questionType) {
        this.user = user;
        this.content = content;
        this.questionType = questionType;
    }

    public void updateContent(String content) {
        this.content = content;
    }
}