-- ============================================================
-- V1__add_new_domains_and_columns.sql
--
-- 전제: application-prod.yml 의 flyway.baseline-version 을 0 으로 변경했음.
--       (baseline-on-migrate: true 는 유지)
--
-- 이 마이그레이션이 하는 일:
--   1) 기존 8개 테이블(users, user_color, goal_category, general_category,
--      task, task_actual, refresh-token, retrospective)에 새로 추가된
--      컬럼들을 ALTER TABLE 로 추가
--   2) 새로 생긴 13개 테이블을 FK 의존성 순서대로 CREATE TABLE
--
-- 절대 DROP TABLE 을 쓰지 않음 (운영 데이터 보존).
-- ============================================================


-- ------------------------------------------------------------
-- 1. 기존 테이블 컬럼 추가 (ALTER TABLE)
-- ------------------------------------------------------------

-- users
ALTER TABLE `users`
    ADD COLUMN `apple_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `character_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FEMALE',
  ADD COLUMN `experience` int NOT NULL DEFAULT 0,
  ADD COLUMN `level` int NOT NULL DEFAULT 1,
  ADD COLUMN `total_experience` int NOT NULL DEFAULT 0;

ALTER TABLE `users`
    ADD CONSTRAINT `UK9wqfo5u8soacks1r6gihojk1c` UNIQUE (`apple_id`);

-- goal_category
ALTER TABLE `goal_category`
    ADD COLUMN `achievement_image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `motive` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL;

-- general_category
ALTER TABLE `general_category`
    ADD COLUMN `achievement` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `achievement_image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `general_category_end_date` date DEFAULT NULL,
  ADD COLUMN `general_category_start_date` date DEFAULT NULL,
  ADD COLUMN `general_category_status` enum('COMPLETED','DELETED','IN_PROGRESS','NOT_STARTED','STOPPED')
      COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_STARTED';

-- task (routine_id, mission_participant_id 의 FK 는 뒤에서 해당 테이블 생성 후 추가)
ALTER TABLE `task`
    ADD COLUMN `routine_date` date DEFAULT NULL,
  ADD COLUMN `task_status` enum('COMPLETED','DELETED','SKIPPED','TODO')
      COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TODO',
  ADD COLUMN `mission_participant_id` bigint DEFAULT NULL,
  ADD COLUMN `routine_id` bigint DEFAULT NULL;


-- ------------------------------------------------------------
-- 2. 새 테이블 생성 (FK 의존성 순서: 참조 대상이 먼저 와야 함)
-- ------------------------------------------------------------

-- 2-1. routine (general_category, goal_category, users 를 참조 — 모두 이미 존재)
CREATE TABLE `routine` (
                           `routine_id` bigint NOT NULL AUTO_INCREMENT,
                           `repeat_end_date` date NOT NULL,
                           `repeat_end_time` time(6) DEFAULT NULL,
                           `repeat_start_date` date NOT NULL,
                           `repeat_start_time` time(6) DEFAULT NULL,
                           `routine_name` varchar(255) NOT NULL,
                           `routine_status` enum('COMPLETED','DELETED','IN_PROGRESS','NOT_STARTED','STOPPED') NOT NULL,
                           `general_category_id` bigint NOT NULL,
                           `goal_category_id` bigint NOT NULL,
                           `user_id` bigint NOT NULL,
                           PRIMARY KEY (`routine_id`),
                           KEY `FKhda2bj8w6mprukas7dpauwbc2` (`general_category_id`),
                           KEY `FK6o6tfnn3436n65h7b4v2txar8` (`goal_category_id`),
                           KEY `FKgrafrnx4qs7tt81du15xdicvw` (`user_id`),
                           CONSTRAINT `FK6o6tfnn3436n65h7b4v2txar8` FOREIGN KEY (`goal_category_id`) REFERENCES `goal_category` (`goal_category_id`),
                           CONSTRAINT `FKgrafrnx4qs7tt81du15xdicvw` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
                           CONSTRAINT `FKhda2bj8w6mprukas7dpauwbc2` FOREIGN KEY (`general_category_id`) REFERENCES `general_category` (`general_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-2. routine_repeat_day (routine 을 참조)
CREATE TABLE `routine_repeat_day` (
                                      `id` bigint NOT NULL AUTO_INCREMENT,
                                      `day_of_week` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') NOT NULL,
                                      `routine_id` bigint NOT NULL,
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `UKi1tkqfmop38wy1j8h50gvule` (`routine_id`,`day_of_week`),
                                      CONSTRAINT `FK4cuqoh249fmnq30kspq5mhqdp` FOREIGN KEY (`routine_id`) REFERENCES `routine` (`routine_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- task 의 routine_id FK 는 이제 routine 테이블이 존재하므로 추가 가능
ALTER TABLE `task`
    ADD CONSTRAINT `FK19n14np39wle6vdmo8l53w8d9` FOREIGN KEY (`routine_id`) REFERENCES `routine` (`routine_id`);

-- 2-3. missions (users 를 참조)
CREATE TABLE `missions` (
                            `mission_id` bigint NOT NULL AUTO_INCREMENT,
                            `fail_condition` varchar(500) NOT NULL,
                            `is_private` bit(1) NOT NULL,
                            `max_participants` int NOT NULL,
                            `mission_condition` varchar(500) NOT NULL,
                            `mission_duration` enum('ONE_MONTH','ONE_WEEK','THREE_MONTHS','THREE_WEEKS','TWO_MONTHS','TWO_WEEKS') NOT NULL,
                            `mission_password` varchar(255) DEFAULT NULL,
                            `mission_status` enum('COMPLETED','DELETED','IN_PROGRESS','RECRUITING') NOT NULL,
                            `mission_title` varchar(100) NOT NULL,
                            `participation_condition` varchar(500) DEFAULT NULL,
                            `recruit_end_date` date NOT NULL,
                            `recruit_start_date` date NOT NULL,
                            `repeat_day_type` enum('CUSTOM','EVERYDAY','WEEKDAYS') DEFAULT NULL,
                            `success_condition` varchar(500) NOT NULL,
                            `end_time` time(6) DEFAULT NULL,
                            `start_time` time(6) DEFAULT NULL,
                            `user_id` bigint NOT NULL,
                            PRIMARY KEY (`mission_id`),
                            KEY `FKj9uyn01yjyiva0ad87pha31ay` (`user_id`),
                            CONSTRAINT `FKj9uyn01yjyiva0ad87pha31ay` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-4. mission_participants (missions, users 를 참조)
CREATE TABLE `mission_participants` (
                                        `mission_participant_id` bigint NOT NULL AUTO_INCREMENT,
                                        `introduction` varchar(500) DEFAULT NULL,
                                        `status` enum('ACTIVE','COMPLETED','FAILED','PENDING','REJECTED') NOT NULL,
                                        `mission_id` bigint NOT NULL,
                                        `user_id` bigint NOT NULL,
                                        PRIMARY KEY (`mission_participant_id`),
                                        KEY `FK771g8fa8hm5ugweef0hyld97a` (`mission_id`),
                                        KEY `FKgy8s82uusdv7cm7bukac9p18u` (`user_id`),
                                        CONSTRAINT `FK771g8fa8hm5ugweef0hyld97a` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`mission_id`),
                                        CONSTRAINT `FKgy8s82uusdv7cm7bukac9p18u` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- task 의 mission_participant_id FK 는 이제 mission_participants 가 존재하므로 추가 가능
ALTER TABLE `task`
    ADD CONSTRAINT `FK92c06dngctpxwo83f0fjygbb2` FOREIGN KEY (`mission_participant_id`) REFERENCES `mission_participants` (`mission_participant_id`);

-- 2-5. mission_bookmarks (missions, users 를 참조)
CREATE TABLE `mission_bookmarks` (
                                     `mission_bookmark_id` bigint NOT NULL AUTO_INCREMENT,
                                     `mission_id` bigint NOT NULL,
                                     `user_id` bigint NOT NULL,
                                     PRIMARY KEY (`mission_bookmark_id`),
                                     UNIQUE KEY `UKlp0jxfoi9ciar1pl04dt5odly` (`mission_id`,`user_id`),
                                     KEY `FKpbw1dtuv2ejasqbxnl0y70p95` (`user_id`),
                                     CONSTRAINT `FKjuno8amag1xudonnq2f5xtn03` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`mission_id`),
                                     CONSTRAINT `FKpbw1dtuv2ejasqbxnl0y70p95` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-6. mission_repeat_days (missions 를 참조)
CREATE TABLE `mission_repeat_days` (
                                       `mission_repeat_day_id` bigint NOT NULL AUTO_INCREMENT,
                                       `day_of_week` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') NOT NULL,
                                       `mission_id` bigint NOT NULL,
                                       PRIMARY KEY (`mission_repeat_day_id`),
                                       KEY `FKmoswptmf89c58yct1vnepbvi` (`mission_id`),
                                       CONSTRAINT `FKmoswptmf89c58yct1vnepbvi` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`mission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-7. mission_task_completes (task, users 를 참조 — task 는 이미 존재)
CREATE TABLE `mission_task_completes` (
                                          `mission_task_complete_id` bigint NOT NULL AUTO_INCREMENT,
                                          `comment` varchar(500) DEFAULT NULL,
                                          `complete_date` date NOT NULL,
                                          `image_url` varchar(255) NOT NULL,
                                          `task_id` bigint NOT NULL,
                                          `user_id` bigint NOT NULL,
                                          PRIMARY KEY (`mission_task_complete_id`),
                                          UNIQUE KEY `UK3iuuljjnt27v98cxwwm3esfr3` (`task_id`),
                                          KEY `FK1f5sm1i9h6yb7vxw1mt6iow59` (`user_id`),
                                          CONSTRAINT `FK1f5sm1i9h6yb7vxw1mt6iow59` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
                                          CONSTRAINT `FKnfawk7tdn48scloroipvl9y5m` FOREIGN KEY (`task_id`) REFERENCES `task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-8. mission_task_likes (mission_task_completes, users 를 참조)
CREATE TABLE `mission_task_likes` (
                                      `mission_task_like_id` bigint NOT NULL AUTO_INCREMENT,
                                      `mission_task_complete_id` bigint NOT NULL,
                                      `user_id` bigint NOT NULL,
                                      PRIMARY KEY (`mission_task_like_id`),
                                      UNIQUE KEY `UKpue09vc7yn669tucpefae0anc` (`mission_task_complete_id`,`user_id`),
                                      KEY `FKt60ou7xd7yy0g7xyur09opylf` (`user_id`),
                                      CONSTRAINT `FKl0ow0ndiu6c57vayo5vi5ckta` FOREIGN KEY (`mission_task_complete_id`) REFERENCES `mission_task_completes` (`mission_task_complete_id`),
                                      CONSTRAINT `FKt60ou7xd7yy0g7xyur09opylf` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-9. autobiography_question (users 를 참조)
CREATE TABLE `autobiography_question` (
                                          `question_id` bigint NOT NULL AUTO_INCREMENT,
                                          `content` text NOT NULL,
                                          `created_at` datetime(6) NOT NULL,
                                          `question_type` enum('CUSTOM','DEFAULT') NOT NULL,
                                          `user_id` bigint DEFAULT NULL,
                                          PRIMARY KEY (`question_id`),
                                          KEY `FKew8f3it0wfdojagn544tx4flh` (`user_id`),
                                          CONSTRAINT `FKew8f3it0wfdojagn544tx4flh` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-10. autobiography_answer (autobiography_question, users 를 참조)
CREATE TABLE `autobiography_answer` (
                                        `answer_id` bigint NOT NULL AUTO_INCREMENT,
                                        `answered_at` datetime(6) NOT NULL,
                                        `content` text NOT NULL,
                                        `updated_at` datetime(6) NOT NULL,
                                        `question_id` bigint NOT NULL,
                                        `user_id` bigint NOT NULL,
                                        PRIMARY KEY (`answer_id`),
                                        UNIQUE KEY `UKmehb6sij32qob8cbfmuj6oqw6` (`question_id`),
                                        KEY `FKk57iokjleovom4r404k3k7ice` (`user_id`),
                                        CONSTRAINT `FK3kqedgicb4rl4g9usdges8irt` FOREIGN KEY (`question_id`) REFERENCES `autobiography_question` (`question_id`),
                                        CONSTRAINT `FKk57iokjleovom4r404k3k7ice` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-11. device_tokens (users 를 참조)
CREATE TABLE `device_tokens` (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `created_at` datetime(6) DEFAULT NULL,
                                 `fcm_token` varchar(255) NOT NULL,
                                 `platform` enum('ANDROID','IOS') NOT NULL,
                                 `user_id` bigint NOT NULL,
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `UK3shgoehod3h5jkmwsgkxora0u` (`fcm_token`),
                                 KEY `FKhc7d11bnr8x9gs5biohdhnx1c` (`user_id`),
                                 CONSTRAINT `FKhc7d11bnr8x9gs5biohdhnx1c` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-12. dream_board (users 를 참조)
CREATE TABLE `dream_board` (
                               `dream_board_id` bigint NOT NULL AUTO_INCREMENT,
                               `created_at` datetime(6) NOT NULL,
                               `image_url` varchar(255) NOT NULL,
                               `updated_at` datetime(6) NOT NULL,
                               `user_id` bigint NOT NULL,
                               PRIMARY KEY (`dream_board_id`),
                               KEY `FKael4hl6xqb688lxse2ligfpxe` (`user_id`),
                               CONSTRAINT `FKael4hl6xqb688lxse2ligfpxe` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2-13. idea_board (users 를 참조)
CREATE TABLE `idea_board` (
                              `idea_board_id` bigint NOT NULL AUTO_INCREMENT,
                              `created_at` datetime(6) NOT NULL,
                              `idea` text NOT NULL,
                              `idea_status` enum('APPLIED','PENDING') NOT NULL,
                              `image_url` varchar(255) DEFAULT NULL,
                              `updated_at` datetime(6) NOT NULL,
                              `user_id` bigint NOT NULL,
                              PRIMARY KEY (`idea_board_id`),
                              KEY `FK969fo8ratubpurl5wwotmt0mk` (`user_id`),
                              CONSTRAINT `FK969fo8ratubpurl5wwotmt0mk` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;