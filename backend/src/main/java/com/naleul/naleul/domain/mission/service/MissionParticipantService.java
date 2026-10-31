package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionParticipant;
import com.naleul.naleul.domain.mission.enums.MissionDuration;
import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.notification.entity.NotificationType;
import com.naleul.naleul.domain.notification.service.NotificationService;
import com.naleul.naleul.domain.mission.repository.MissionParticipantRepository;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.repository.TaskRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.global.common.response.ErrorCode;
import com.naleul.naleul.global.exception.CustomException;
import com.naleul.naleul.global.policy.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionParticipantService {

    private final TaskRepository taskRepository;
    private final MissionParticipantRepository missionParticipantRepository;
    private final NotificationService notificationService;

    // MissionService, MissionAdminService 둘 다 여기서 호출
    // 신청 시 (PENDING) — task 생성 안 함
    public void createParticipantWithTasks(Mission mission, User user, String introduction) {
        validateSlotAvailable(user); // ✅ 신청 시점에도 검사
        MissionParticipant participant = MissionParticipant.create(mission, user, introduction);
        missionParticipantRepository.save(participant);
    }

    // 방장 전용 (PENDING 없이 바로 ACTIVE)
    public void createHostWithTasks(Mission mission, User host) {
        MissionParticipant participant = MissionParticipant.createHost(mission, host);

        // ✅ participant 먼저 저장 → id 확정
        MissionParticipant savedParticipant = missionParticipantRepository.save(participant);

        // ✅ 방장 등록 시점은 항상 "모집종료일 다음 날부터" 시작이 맞으므로 기본 시작일 사용
        LocalDate defaultStart = mission.getRecruitEndDate().plusDays(1);
        List<LocalDate> targetDates = calculateTargetDates(mission, defaultStart);

        List<Task> tasks = targetDates.stream()
                .map(date -> Task.fromMission(savedParticipant, date))
                .collect(Collectors.toList());

        taskRepository.saveAll(tasks);
    }

    // 승인 시 Task 생성 (participant가 이미 존재하는 상태) — 기존 호출부 호환용 오버로드
    public void createTasksForParticipant(MissionParticipant participant) {
        LocalDate defaultStart = participant.getMission().getRecruitEndDate().plusDays(1);
        createTasksForParticipant(participant, defaultStart);
    }

    // ✅ 시작일을 명시적으로 받는 버전 — 조기마감(오늘) / 정상흐름(모집종료일+1) 둘 다 여기로 수렴
    public void createTasksForParticipant(MissionParticipant participant, LocalDate startDate) {
        boolean alreadyExists = taskRepository.existsByMissionParticipantId(
                participant.getMissionParticipantId()
        );
        if (alreadyExists) {
            return;
        }

        Mission mission = participant.getMission();
        List<LocalDate> targetDates = calculateTargetDates(mission, startDate);

        List<Task> tasks = targetDates.stream()
                .map(date -> Task.fromMission(participant, date))
                .collect(Collectors.toList());

        taskRepository.saveAll(tasks);
    }

    // 신청 취소 — PENDING 상태의 내 신청만 취소 가능 (승인된 ACTIVE 참여는 이 메서드로 취소 안 됨)
    public void cancelPendingParticipation(Mission mission, Long userId) {
        // ⭐ 변경 — mission.getParticipants() 컬렉션 스트림 대신, 락 걸린 조회 쿼리로 교체
        MissionParticipant participant = missionParticipantRepository
                .findForCancelUpdate(mission.getMissionId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTICIPANT_NOT_FOUND));

        if (participant.getStatus() != MissionParticipantStatus.PENDING) {
            throw new CustomException(ErrorCode.PARTICIPANT_NOT_PENDING);
        }

        User applicant = participant.getUser();
        missionParticipantRepository.delete(participant);
        missionParticipantRepository.flush();   // ⭐ 추가 — 락을 쥔 채로 삭제를 즉시 DB에 반영

        notificationService.notify(
                mission.getHost(),
                NotificationType.APPLICATION_CANCELED,
                mission.getMissionId(),
                applicant.getUserName()
        );
    }

    // ✅ 신규 — "승인/거절됐지만 아직 결과를 안 알린" 참여자들에게 일괄 발송
    // MissionScheduler(D-1 배치)와 MissionAdminService(조기마감) 양쪽에서 재사용
    public void notifyPendingApplicationResults(Mission mission) {
        List<MissionParticipant> targets = missionParticipantRepository.findByMissionIdAndStatusIn(
                mission.getMissionId(),
                List.of(MissionParticipantStatus.ACTIVE, MissionParticipantStatus.REJECTED)
        );

        for (MissionParticipant participant : targets) {
            if (participant.isApplicationResultNotified()) {
                continue; // 이미 알림 보낸 건 스킵
            }

            NotificationType type = participant.getStatus() == MissionParticipantStatus.ACTIVE
                    ? NotificationType.APPLICATION_APPROVED
                    : NotificationType.APPLICATION_REJECTED;

            notificationService.notify(participant.getUser(), type, mission.getMissionId());
            participant.markApplicationResultNotified();
        }
    }

    // ===== 날짜 계산 =====

    // private였던 이 부분을 public으로 승격 + 이름 변경 (다른 서비스에서도 "실제 예정일"을 구해야 하므로)
    public List<LocalDate> resolveScheduledDates(Mission mission, LocalDate start, LocalDate end) {
        Set<DayOfWeek> repeatDays = resolveRepeatDays(mission);
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (repeatDays.isEmpty() || repeatDays.contains(current.getDayOfWeek())) {
                dates.add(current);
            }
            current = current.plusDays(1);
        }
        return dates;
    }

    // 기존 calculateTargetDates()는 이 메서드를 호출하도록 변경
    private List<LocalDate> calculateTargetDates(Mission mission, LocalDate start) {
        LocalDate end = calculateEndDate(mission, start);
        return resolveScheduledDates(mission, start, end);
    }

    // ✅ 수정 — 내부에서 mission.getRecruitEndDate()를 다시 읽지 않고,
    //    실제로 사용된 start를 기준으로 종료일을 계산 (조기마감 시 기간이 밀리던 버그의 원인)
    private LocalDate calculateEndDate(Mission mission, LocalDate start) {
        return switch (mission.getMissionDuration()) {
            case ONE_WEEK     -> start.plusWeeks(1).minusDays(1);
            case TWO_WEEKS    -> start.plusWeeks(2).minusDays(1);
            case THREE_WEEKS  -> start.plusWeeks(3).minusDays(1);
            case ONE_MONTH    -> start.plusMonths(1).minusDays(1);
            case TWO_MONTHS   -> start.plusMonths(2).minusDays(1);
            case THREE_MONTHS -> start.plusMonths(3).minusDays(1);
        };
    }

    private Set<DayOfWeek> resolveRepeatDays(Mission mission) {
        if (mission.getRepeatDayType() == null) {
            return Set.of();
        }
        return switch (mission.getRepeatDayType()) {
            case EVERYDAY -> Set.of();
            case WEEKDAYS -> Set.of(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            );
            case CUSTOM -> mission.getRepeatDays().stream()
                    .map(d -> DayOfWeek.valueOf(d.getDayOfWeek().name()))
                    .collect(Collectors.toSet());
        };
    }

    // ✅ 기존 호출부(스케줄러 등) 호환용 오버로드 — 기본 동작(모집종료일+1)은 그대로 유지
    public void createTasksForActiveParticipants(Mission mission) {
        LocalDate defaultStart = mission.getRecruitEndDate().plusDays(1);
        createTasksForActiveParticipants(mission, defaultStart);
    }

    // ✅ 신규 — 시작일을 명시적으로 받는 버전 (조기마감 등에서 사용)
    // MissionAdminService(즉시 시작, 조기 마감)와 MissionScheduler(자정 배치) 양쪽에서 재사용
    public void createTasksForActiveParticipants(Mission mission, LocalDate startDate) {
        mission.getParticipants().stream()
                .filter(p -> p.getStatus() == MissionParticipantStatus.ACTIVE)
                .forEach(p -> createTasksForParticipant(p, startDate));
    }

    // MissionParticipantService.java 에 추가
    public void validateSlotAvailable(User applicant) {
        long activeCount = missionParticipantRepository.countActiveParticipations(applicant.getUserId());
        int limit = applicant.isCurrentlyPremium()
                ? PlanLimits.MISSION_PRO_LIMIT
                : PlanLimits.MISSION_FREE_LIMIT;

        if (activeCount >= limit) {
            throw new CustomException(ErrorCode.MISSION_PARTICIPATION_LIMIT_EXCEEDED);
        }
    }
}