package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.MissionBadgeResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionBadge;
import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import com.naleul.naleul.domain.mission.enums.MissionBadgeType;
import com.naleul.naleul.domain.mission.enums.MissionCategoryItem;
import com.naleul.naleul.domain.mission.repository.MissionBadgeRepository;
import com.naleul.naleul.domain.mission.repository.MissionTaskCompleteRepository;
import com.naleul.naleul.domain.notification.entity.NotificationType;
import com.naleul.naleul.domain.notification.service.NotificationService;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MissionBadgeService {

    private final MissionBadgeRepository missionBadgeRepository;
    private final MissionTaskCompleteRepository missionTaskCompleteRepository;
    private final NotificationService notificationService;

    private static final Map<Integer, MissionBadgeType> COUNT_MILESTONES = Map.of(
            10, MissionBadgeType.COUNT_10,
            50, MissionBadgeType.COUNT_50,
            100, MissionBadgeType.COUNT_100
    );

    // ===== 뱃지(업적) — 퍼즐(ITEM_PUZZLE) 제외 =====

    @Transactional(readOnly = true)
    public List<MissionBadgeResponse> getUnconfirmedBadges(Long userId) {
        return missionBadgeRepository.findUnconfirmedBadgesByUserId(userId)
                .stream()
                .map(MissionBadgeResponse::from)
                .collect(Collectors.toList());
    }

    public void confirmBadge(Long userId, Long badgeId) {
        MissionBadge badge = missionBadgeRepository.findById(badgeId)
                .orElseThrow(() -> new EntityNotFoundException("뱃지를 찾을 수 없습니다."));
        if (!badge.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 뱃지만 확인 처리할 수 있습니다.");
        }
        badge.confirm();
    }

    @Transactional(readOnly = true)
    public List<MissionBadgeResponse> getMyBadges(Long userId) {
        return missionBadgeRepository.findAllByUserId(userId)
                .stream()
                .map(MissionBadgeResponse::from)
                .collect(Collectors.toList());
    }

    // ===== 퍼즐(카테고리 항목별) — 신규 분리 API =====

    // ✅ 신규 — categoryItem이 null이면 전체 항목, 지정하면 그 항목의 퍼즐만
    @Transactional(readOnly = true)
    public List<MissionBadgeResponse> getMyPuzzles(Long userId, MissionCategoryItem categoryItem) {
        return missionBadgeRepository.findPuzzlesByUserId(userId, categoryItem)
                .stream()
                .map(MissionBadgeResponse::from)
                .collect(Collectors.toList());
    }

    // ===== 1, 2번: 미션 성공 시 =====

    public void awardBadgesOnMissionSuccess(Mission mission, User user, int achievedDays, int requiredDays) {
        if (missionBadgeRepository.existsByMission_MissionIdAndUser_UserId(
                mission.getMissionId(), user.getUserId())) {
            return;
        }

        // ✅ 대분류가 ETC라 세부 항목이 없는 미션은 항목별 퍼즐/레벨업이 성립하지 않으므로 스킵
        MissionCategoryItem categoryItem = mission.getCategoryItem();
        if (categoryItem == null) {
            log.info("missionId={} - categoryItem 없음(ETC 등), 퍼즐 지급 스킵", mission.getMissionId());
            return;
        }

        double rate = requiredDays == 0 ? 0 :
                Math.round((double) achievedDays / requiredDays * 1000) / 10.0;

        boolean isFirstEver = !missionBadgeRepository
                .existsByUser_UserIdAndBadgeType(user.getUserId(), MissionBadgeType.ITEM_PUZZLE);

        awardOrLevelUpItemPuzzle(mission, user, categoryItem, rate);

        if (isFirstEver) {
            MissionBadge firstBadge = MissionBadge.builder()
                    .badgeName("첫 퍼즐 획득")
                    .badgeType(MissionBadgeType.FIRST_PUZZLE)
                    .achievementRate(rate)
                    .level(1)
                    .mission(mission)
                    .user(user)
                    .build();
            missionBadgeRepository.save(firstBadge);
            log.info("🏅 첫 퍼즐 뱃지 지급: userId={}", user.getUserId());

            notificationService.notify(user, NotificationType.BADGE_ACQUIRED, firstBadge.getMissionBadgeId());
        }
    }

    // ✅ 변경 — 카테고리(대분류) 대신 카테고리 항목 기준으로 신규 지급/레벨업
    private void awardOrLevelUpItemPuzzle(Mission mission, User user, MissionCategoryItem categoryItem, double rate) {
        Optional<MissionBadge> existing = missionBadgeRepository
                .findByUser_UserIdAndBadgeTypeAndCategoryItem(
                        user.getUserId(), MissionBadgeType.ITEM_PUZZLE, categoryItem
                );

        if (existing.isPresent()) {
            existing.get().levelUp();
            log.info("🏅 항목 퍼즐 레벨업: userId={}, categoryItem={}, newLevel={}",
                    user.getUserId(), categoryItem, existing.get().getLevel());
        } else {
            MissionBadge badge = MissionBadge.builder()
                    .badgeName(categoryItem.name() + " 퍼즐")
                    .badgeType(MissionBadgeType.ITEM_PUZZLE)
                    .categoryItem(categoryItem)
                    .achievementRate(rate)
                    .level(1)
                    .mission(mission)
                    .user(user)
                    .build();
            missionBadgeRepository.save(badge);
            log.info("🏅 항목 퍼즐 신규 지급: userId={}, categoryItem={}", user.getUserId(), categoryItem);

            notificationService.notify(user, NotificationType.BADGE_ACQUIRED, badge.getMissionBadgeId());
        }
    }

    // ===== 3, 4번: 인증(Task 완료)할 때마다 — 변경 없음 =====

    public void awardBadgesOnTaskComplete(User user, MissionTaskComplete complete) {
        awardStreakBadgeIfEligible(user, complete);
        awardCountBadgeIfEligible(user, complete);
    }

    private void awardStreakBadgeIfEligible(User user, MissionTaskComplete complete) {
        if (missionBadgeRepository.existsByUser_UserIdAndBadgeType(user.getUserId(), MissionBadgeType.STREAK_3)) {
            return;
        }

        LocalDate today = complete.getCompleteDate();
        boolean yesterday = missionTaskCompleteRepository
                .existsByUser_UserIdAndCompleteDate(user.getUserId(), today.minusDays(1));
        boolean dayBeforeYesterday = missionTaskCompleteRepository
                .existsByUser_UserIdAndCompleteDate(user.getUserId(), today.minusDays(2));

        if (yesterday && dayBeforeYesterday) {
            MissionBadge badge = MissionBadge.builder()
                    .badgeName("3일 연속 인증")
                    .badgeType(MissionBadgeType.STREAK_3)
                    .level(1)
                    .mission(complete.getTask().getMissionParticipant().getMission())
                    .user(user)
                    .build();
            missionBadgeRepository.save(badge);
            log.info("🏅 3일 연속 인증 뱃지 지급: userId={}", user.getUserId());

            notificationService.notify(user, NotificationType.BADGE_ACQUIRED, badge.getMissionBadgeId());
        }
    }

    private void awardCountBadgeIfEligible(User user, MissionTaskComplete complete) {
        long totalCount = missionTaskCompleteRepository.countByUser_UserId(user.getUserId());

        MissionBadgeType type = COUNT_MILESTONES.get((int) totalCount);
        if (type == null) {
            return;
        }
        if (missionBadgeRepository.existsByUser_UserIdAndBadgeType(user.getUserId(), type)) {
            return;
        }

        MissionBadge badge = MissionBadge.builder()
                .badgeName("누적 인증 " + totalCount + "회")
                .badgeType(type)
                .level(1)
                .mission(complete.getTask().getMissionParticipant().getMission())
                .user(user)
                .build();
        missionBadgeRepository.save(badge);
        log.info("🏅 누적 인증 뱃지 지급: userId={}, count={}", user.getUserId(), totalCount);

        notificationService.notify(user, NotificationType.BADGE_ACQUIRED, badge.getMissionBadgeId());
    }
}