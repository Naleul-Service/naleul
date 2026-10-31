package com.naleul.naleul.domain.user.repository;

import com.naleul.naleul.domain.user.entity.UserWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWithdrawalRepository extends JpaRepository<UserWithdrawal, Long> {
}