package com.naleul.naleul.domain.user.entity;

import com.naleul.naleul.domain.user.enums.UserWithdrawalReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_withdrawal")
public class UserWithdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userWithdrawalId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserWithdrawalReason reason;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false)
    private LocalDateTime withdrawnAt;

    public static UserWithdrawal of(User user, UserWithdrawalReason reason, String detail) {
        UserWithdrawal withdrawal = new UserWithdrawal();
        withdrawal.userId = user.getUserId();
        withdrawal.userEmail = user.getUserEmail();
        withdrawal.reason = reason;
        withdrawal.detail = detail;
        withdrawal.withdrawnAt = LocalDateTime.now();
        return withdrawal;
    }
}