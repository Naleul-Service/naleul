package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.MissionActiveParticipantResponse;
import com.naleul.naleul.domain.mission.dto.response.PendingParticipantResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.mission.repository.MissionParticipantRepository;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MissionAdminService {

    private final MissionRepository missionRepository;
    private final MissionParticipantRepository missionParticipantRepository;
    private final MissionParticipantService missionParticipantService;
    private final TaskRepository taskRepository;
    // ❌ NotificationService 필드 제거 — 이 클래스에서 더 이상 직접 알림을 보내지 않음
    //    (승인/거절 알림은 missionParticipantService.notifyPendingApplicationResults()로 일원화)

    @Transactional(readOnly = true)
    public List<PendingParticipantResponse> getPendingParticipants(Long hostId, Long missionId) {
        Mission mission = findMission(missionId);
        validateHost(mission, hostId);

        return missionParticipantRepository
                .findByMissionIdAndStatusIn(
                        missionId,
                        List.of(MissionParticipantStatus.PENDING, MissionParticipantStatus.ACTIVE)
                )
                .stream()
                .filter(p -> !p.getUser().getUserId().equals(hostId))   // ✅ 방장 본인 제외
                .map(PendingParticipantResponse::from)
                .collect(Collectors.toList());
    }

    // 신청 승인
    public void approveParticipant(Long hostId, Long missionId, Long participantId) {
        Mission mission = findMission(missionId);
        validateHost(mission, hostId);
        validateBeforeMissionStart(mission);

        MissionParticipant participant = findParticipant(participantId);
        validatePending(participant);
        validateParticipantSlotAvailable(participant);

        participant.approve(); // PENDING → ACTIVE
        // ❌ 즉시발송 제거 — D-1 배치(MissionScheduler) 또는 조기마감(completeRecruiting) 시점에 일괄 발송
    }

    // 승인 취소 — ACTIVE → PENDING
    public void cancelApproval(Long hostId, Long missionId, Long participantId) {
        Mission mission = findMission(missionId);
        validateHost(mission, hostId);
        validateBeforeMissionStart(mission);

        MissionParticipant participant = findParticipant(participantId);
        validateActiveForCancel(participant);

        taskRepository.deleteByMissionParticipant_MissionParticipantId(participantId);
        participant.cancelApproval(); // ACTIVE → PENDING
    }

    // 신청 거절
    public void rejectParticipant(Long hostId, Long missionId, Long participantId) {
        Mission mission = findMission(missionId);
        validateHost(mission, hostId);
        validateBeforeMissionStart(mission);

        MissionParticipant participant = findParticipant(participantId);
        validatePending(participant);

        participant.reject(); // PENDING → REJECTED
        // ❌ 즉시발송 제거 — D-1 배치 또는 조기마감 시점에 일괄 발송
    }


    // ACTIVE, COMPLETED, FAILED 모두 조회 — 방장 본인은 제외
    @Transactional(readOnly = true)
    public List<MissionActiveParticipantResponse> getActiveParticipants(Long hostId, Long missionId) {
        Mission mission = findMission(missionId);
        validateHost(mission, hostId);

        return missionParticipantRepository
                .findActiveParticipantsByMissionId(missionId)
                .stream()
                .filter(p -> !p.getUser().getUserId().equals(hostId))   // ✅ 방장 본인 제외
                .map(MissionActiveParticipantResponse::from)
                .collect(Collectors.toList());
    }

    // ===== private 메서드 =====

    private Mission findMission(Long missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new EntityNotFoundException("미션을 찾을 수 없습니다."));
    }

    private MissionParticipant findParticipant(Long participantId) {
        return missionParticipantRepository.findByMissionParticipantId(participantId)
                .orElseThrow(() -> new EntityNotFoundException("참여자를 찾을 수 없습니다."));
    }

    private void validateHost(Mission mission, Long userId) {
        if (!mission.getHost().getUserId().equals(userId)) {
            throw new IllegalArgumentException("방장만 접근할 수 있습니다.");
        }
    }

    private void validatePending(MissionParticipant participant) {
        if (participant.getStatus() != MissionParticipantStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 신청만 처리할 수 있습니다.");
        }
    }

    private void validateActiveForCancel(MissionParticipant participant) {
        if (participant.getStatus() != MissionParticipantStatus.ACTIVE) {
            throw new IllegalArgumentException("승인된(ACTIVE) 참여자만 승인을 취소할 수 있습니다.");
        }
    }

    // 승인/거절/승인취소 공통 마감 기준 — 모집 종료일 당일까지 허용
    private void validateBeforeMissionStart(Mission mission) {
        if (LocalDate.now().isAfter(mission.getRecruitEndDate())) {
            throw new IllegalArgumentException("진행 시작 하루 전까지만 승인, 거절, 승인 취소가 가능합니다.");
        }
    }

    // ✅ 승인하면 신청자가 ACTIVE로 바뀌므로, 승인 직전에 신청자의 남은 슬롯을 확인해야 함
    private void validateParticipantSlotAvailable(MissionParticipant participant) {
        User applicant = participant.getUser();
        long activeCount = missionParticipantRepository.countActiveParticipations(applicant.getUserId());
        int limit = applicant.isCurrentlyPremium() ? PlanLimits.MISSION_PRO_LIMIT : PlanLimits.MISSION_FREE_LIMIT;

        if (activeCount >= limit) {
            throw new CustomException(ErrorCode.MISSION_PARTICIPATION_LIMIT_EXCEEDED);
        }
    }
}