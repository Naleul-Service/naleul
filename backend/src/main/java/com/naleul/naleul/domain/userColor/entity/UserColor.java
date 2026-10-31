package com.naleul.naleul.domain.userColor.entity;

// domain/color/entity/UserColor.java

import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_color")
public class UserColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userColorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 7)
    private String colorCode;

    @Column(nullable = false)
    private boolean isDefault;
}