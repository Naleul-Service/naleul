-- ============================================================
-- V19__create_mission_badges.sql
--
-- 미션 성공(달성률 50% 이상) 시 지급되는 뱃지(퍼즐) 테이블 생성
-- (mission_id, user_id) 유니크 제약으로 중복 지급 방지
-- ============================================================

CREATE TABLE `mission_badges` (
                                  `mission_badge_id` bigint NOT NULL AUTO_INCREMENT,
                                  `badge_name` varchar(100) NOT NULL,
                                  `image_url` varchar(255) DEFAULT NULL,
                                  `achievement_rate` double NOT NULL,
                                  `acquired_at` datetime(6) NOT NULL,
                                  `mission_id` bigint NOT NULL,
                                  `user_id` bigint NOT NULL,
                                  PRIMARY KEY (`mission_badge_id`),
                                  UNIQUE KEY `UK_mission_badge_mission_user` (`mission_id`,`user_id`),
                                  KEY `FK_mission_badge_mission` (`mission_id`),
                                  KEY `FK_mission_badge_user` (`user_id`),
                                  CONSTRAINT `FK_mission_badge_mission` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`mission_id`),
                                  CONSTRAINT `FK_mission_badge_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;