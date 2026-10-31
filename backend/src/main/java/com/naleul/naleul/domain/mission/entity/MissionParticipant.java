package com.naleul.naleul.domain.mission.entity;

import com.naleul.naleul.domain.mission.enums.MissionParticipantStatus;
import com.naleul.naleul.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mission_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long missionParticipantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 500)
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionParticipantStatus status;

    @Column(nullable = false)
    private boolean resultConfirmed = false; // 성공/실패 결과를 앱에서 확인(모달 표시)했는지

    // ✅ 추가 — 승인/거절 결과를 유저에게 알렸는지 여부 (D-1 배치 또는 조기마감 시점에 일괄 발송하기 위한 플래그)
    @Column(name = "application_result_notified", nullable = false)
    private boolean applicationResultNotified = false;

    public static MissionParticipant create(Mission mission, User user, String introduction) {
        MissionParticipant participant = new MissionParticipant();
        participant.mission = mission;
        participant.user = user;
        participant.introduction = introduction;
        participant.status = MissionParticipantStatus.PENDING; // 처음엔 대기 상태
        return participant;
    }

    // 방장 자동 참여용 (승인 불필요)
    public static MissionParticipant createHost(Mission mission, User user) {
        MissionParticipant participant = new MissionParticipant();
        participant.mission = mission;
        participant.user = user;
        participant.introduction = null;
        participant.status = MissionParticipantStatus.ACTIVE; // 방장은 바로 ACTIVE
        participant.applicationResultNotified = true; // ✅ 방장은 승인/거절 알림 대상이 아니므로 처음부터 true
        return participant;
    }

    public void approve() {
        this.status = MissionParticipantStatus.ACTIVE;
    }

    public void cancelApproval() {
        this.status = MissionParticipantStatus.PENDING; // ACTIVE → PENDING
    }

    public void reject() {
        this.status = MissionParticipantStatus.REJECTED;
    }

    public void success() {
        this.status = MissionParticipantStatus.COMPLETED;
        this.resultConfirmed = false; // 새로 확정된 결과
    }

    public void fail() {
        this.status = MissionParticipantStatus.FAILED;
        this.resultConfirmed = false;
    }

    public void confirmResult() {
        this.resultConfirmed = true;
    }

    // ✅ 추가 — 승인/거절 결과 알림을 보냈음을 표시
    public void markApplicationResultNotified() {
        this.applicationResultNotified = true;
    }
}