package com.naleul.naleul.domain.userColor.repository;


import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.userColor.entity.UserColor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserColorRepository extends JpaRepository<UserColor, Long> {

    List<UserColor> findByUser_UserId(Long userId);

    Optional<UserColor> findByUserColorIdAndUser_UserId(Long userColorId, Long userId);

    // UserColorRepository에 추가
    Optional<UserColor> findByUserColorIdAndUser(Long userColorId, User user);

    boolean existsByUser_UserIdAndColorCode(Long userId, String colorCode);
}