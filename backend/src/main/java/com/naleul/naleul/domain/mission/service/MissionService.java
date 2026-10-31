package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.request.MissionCreateRequest;
import com.naleul.naleul.domain.mission.dto.request.MissionJoinRequest;
import com.naleul.naleul.domain.mission.dto.request.MissionPasswordCheckRequest;
import com.naleul.naleul.domain.mission.dto.request.MissionUpdateRequest;
import com.naleul.naleul.domain.mission.dto.response.MissionDetailResponse;
import com.naleul.naleul.domain.mission.dto.response.MissionListResponse;
import com.naleul.naleul.domain.mission.dto.response.MissionPasswordCheckResponse;
import com.naleul.naleul.domain.mission.dto.response.MissionResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionRepeatDay;
import com.naleul.naleul.domain.mission.entity.MissionTimeRange;
import com.naleul.naleul.domain.mission.enums.*;
import com.naleul.naleul.domain.mission.repository.MissionBookmarkRepository;
import com.naleul.naleul.domain.mission.repository.MissionParticipantRepository;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.notification.entity.NotificationType;
import com.naleul.naleul.domain.notification.service.NotificationService;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.user.service.UserFinder;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionService {

    private final MissionRepository missionRepository;
    private final UserRepository userRepository;
    private final UserFinder userFinder;
    private final MissionParticipantService missionParticipantService;
    private final MissionParticipantRepository missionParticipantRepository;
    private final MissionBookmarkRepository missionBookmarkRepository;
    private final TaskRepository taskRepository; // ✅ 추가
    private final NotificationService notificationService;

    public MissionResponse createMission(Long userId, MissionCreateRequest request) {
        User host = userFinder.findUser(userId);

        validateMissionCreateLimit(host);
        validateRecruitDates(request);
        validatePassword(request);
        validateRepeatDays(request);
        validateCategory(request.getCategory(), request.getCategoryItem(),
                request.getCustomCategoryName(), request.getCustomItemName());

        MissionTimeRange timeRange = null;
        if (request.getStartTime() != null || request.getEndTime() != null) {
            timeRange = MissionTimeRange.builder()
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .build();
        }

        Mission mission = Mission.builder()
                .missionTitle(request.getTitle())
                .maxParticipants(request.getMaxParticipants())
                .isPrivate(request.getIsPrivate())
                .missionPassword(request.getPassword())
                .category(request.getCategory())
                .categoryItem(request.getCategoryItem())
                .customCategoryName(request.getCustomCategoryName())
                .customItemName(request.getCustomItemName())
                .missionCondition(request.getMissionCondition())
                .recruitStartDate(request.getRecruitStartDate())
                .recruitEndDate(request.getRecruitEndDate())
                .missionDuration(request.getDuration())
                .successCondition(request.getSuccessCondition())
                .failCondition(request.getFailCondition())
                .participationCondition(request.getParticipationCondition())
                .repeatDayType(request.getRepeatDayType())
                .timeRange(timeRange)
                .host(host)
                .build();

        if (request.getRepeatDayType() == RepeatDayType.CUSTOM && request.getRepeatDays() != null) {
            request.getRepeatDays().forEach(day ->
                    mission.addRepeatDay(MissionRepeatDay.builder()
                            .dayOfWeek(day)
                            .build())
            );
        }

        Mission saved = missionRepository.save(mission);
        missionParticipantService.createHostWithTasks(saved, host);

        return MissionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MissionPasswordCheckResponse checkPassword(Long missionId, MissionPasswordCheckRequest request) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        if (!mission.getIsPrivate()) {
            throw new CustomException(ErrorCode.MISSION_NOT_PRIVATE);
        }

        boolean isValid = mission.getMissionPassword().equals(request.getPassword());
        return MissionPasswordCheckResponse.of(isValid);
    }

    @Transactional(readOnly = true)
    public List<MissionListResponse> getMissions(Long userId, Boolean isPrivate, String keyword) {
        String searchKeyword = (keyword != null && keyword.isBlank()) ? null : keyword;

        List<Mission> missions = missionRepository.searchMissions(
                MissionStatus.RECRUITING,
                isPrivate,
                searchKeyword
        );

        List<Long> missionIds = missions.stream()
                .map(Mission::getMissionId)
                .collect(Collectors.toList());

        Set<Long> bookmarkedIds = missionIds.isEmpty()
                ? Collections.emptySet()
                : missionBookmarkRepository.findBookmarkedMissionIds(userId, missionIds);

        return missions.stream()
                .map(mission -> MissionListResponse.from(
                        mission,
                        bookmarkedIds.contains(mission.getMissionId())
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MissionDetailResponse getMissionDetail(Long userId, Long missionId) {
        Mission mission = missionRepository.findDetailById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        if (mission.getMissionStatus() == MissionStatus.DELETED) {
            throw new CustomException(ErrorCode.MISSION_DELETED);
        }

        boolean isBookmarked = missionBookmarkRepository
                .existsByMission_MissionIdAndUser_UserId(missionId, userId);

        return MissionDetailResponse.from(mission, userId, isBookmarked);
    }

    @Transactional(readOnly = true)
    public boolean canCreateMission(Long userId) {
        User host = userFinder.findUser(userId);

        long activeCount = missionParticipantRepository.countActiveParticipations(host.getUserId());
        int limit = host.isCurrentlyPremium() ? PlanLimits.MISSION_PRO_LIMIT : PlanLimits.MISSION_FREE_LIMIT;

        return activeCount < limit;
    }

    public void joinMission(Long userId, Long missionId, MissionJoinRequest request) {
        User user = userFinder.findUser(userId);

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        validateJoinable(mission);

        missionParticipantService.createParticipantWithTasks(mission, user, request.getIntroduction());

        notificationService.notify(
                mission.getHost(),
                NotificationType.APPLICATION_RECEIVED,
                mission.getMissionId(),
                user.getUserName()
        );
    }

    public MissionResponse updateMission(Long userId, Long missionId, MissionUpdateRequest request) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        validateHost(mission, userId);
        validateRecruiting(mission);
        validateUpdateRequest(request, mission);

        MissionTimeRange timeRange = null;
        if (request.getStartTime() != null || request.getEndTime() != null) {
            timeRange = MissionTimeRange.builder()
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .build();
        }

        mission.update(
                mission.getMissionTitle(),
                mission.getMaxParticipants(),
                request.getIsPrivate(),
                request.getPassword(),
                mission.getCategory(),
                mission.getCategoryItem(),
                mission.getCustomCategoryName(),
                mission.getCustomItemName(),
                request.getMissionCondition(),
                request.getRecruitStartDate(),
                request.getRecruitEndDate(),
                request.getDuration(),
                request.getSuccessCondition(),
                request.getFailCondition(),
                request.getParticipationCondition(),
                request.getRepeatDayType(),
                timeRange
        );

        if (request.getRepeatDayType() == RepeatDayType.CUSTOM) {
            mission.updateRepeatDays(request.getRepeatDays());
        } else {
            mission.updateRepeatDays(null);
        }

        mission.getParticipants().stream()
                .filter(p -> p.getStatus() == MissionParticipantStatus.ACTIVE)
                .filter(p -> !p.getUser().getUserId().equals(userId)) // ✅ 방장 본인 제외
                .forEach(p -> notificationService.notify(
                        p.getUser(), NotificationType.MISSION_CONTENT_EDITED, mission.getMissionId()
                ));

        return MissionResponse.from(mission);
    }

    public void deleteMission(Long userId, Long missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        validateHost(mission, userId);
        validateRecruiting(mission);

        taskRepository.deleteAllByMissionParticipantMissionMissionId(missionId);

        mission.delete();
    }

    // ===== 검증 =====

    private void validateHost(Mission mission, Long userId) {
        if (!mission.getHost().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.MISSION_ACCESS_DENIED);
        }
    }

    private void validateRecruiting(Mission mission) {
        if (mission.getMissionStatus() != MissionStatus.RECRUITING) {
            throw new CustomException(ErrorCode.MISSION_NOT_RECRUITING);
        }
    }

    private void validateJoinable(Mission mission) {
        if (mission.getMissionStatus() != MissionStatus.RECRUITING) {
            throw new CustomException(ErrorCode.MISSION_NOT_RECRUITING);
        }
        if (mission.getParticipants().size() >= mission.getMaxParticipants()) {
            throw new CustomException(ErrorCode.MISSION_FULL);
        }
    }

    public void cancelJoin(Long userId, Long missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        missionParticipantService.cancelPendingParticipation(mission, userId);
    }

    private void validateUpdateRequest(MissionUpdateRequest request, Mission mission) {
        if (!request.getRecruitStartDate().isBefore(request.getRecruitEndDate())) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }

        if (Boolean.TRUE.equals(request.getIsPrivate()) &&
                (request.getPassword() == null || request.getPassword().isBlank())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (request.getRepeatDayType() == RepeatDayType.CUSTOM &&
                (request.getRepeatDays() == null || request.getRepeatDays().isEmpty())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateRecruitDates(MissionCreateRequest request) {
        if (!request.getRecruitStartDate().isBefore(request.getRecruitEndDate())) {
            throw new CustomException(ErrorCode.GOAL_CATEGORY_INVALID_DATE);
        }
    }

    private void validatePassword(MissionCreateRequest request) {
        if (Boolean.TRUE.equals(request.getIsPrivate()) &&
                (request.getPassword() == null || request.getPassword().isBlank())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateRepeatDays(MissionCreateRequest request) {
        if (request.getRepeatDayType() == RepeatDayType.CUSTOM &&
                (request.getRepeatDays() == null || request.getRepeatDays().isEmpty())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateCategory(MissionCategory category, MissionCategoryItem categoryItem,
                                  String customCategoryName, String customItemName) {
        if (category == MissionCategory.ETC) {
            if (customCategoryName == null || customCategoryName.isBlank()) {
                throw new CustomException(ErrorCode.MISSION_CUSTOM_CATEGORY_NAME_REQUIRED);
            }
            return;
        }

        if (categoryItem == null) {
            throw new CustomException(ErrorCode.MISSION_CATEGORY_ITEM_REQUIRED);
        }

        if (!MissionCategoryItem.belongsTo(category, categoryItem)) {
            throw new CustomException(ErrorCode.MISSION_CATEGORY_ITEM_MISMATCH);
        }

        if (categoryItem == MissionCategoryItem.ETC &&
                (customItemName == null || customItemName.isBlank())) {
            throw new CustomException(ErrorCode.MISSION_CUSTOM_ITEM_NAME_REQUIRED);
        }
    }

    private void validateMissionCreateLimit(User host) {
        if (!canCreateMission(host.getUserId())) {
            throw new CustomException(ErrorCode.MISSION_CREATE_LIMIT_EXCEEDED);
        }
    }
}