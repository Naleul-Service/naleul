package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.MissionDailyCompleteResponse;
import com.naleul.naleul.domain.mission.entity.MissionTaskComplete;
import com.naleul.naleul.domain.mission.entity.MissionTaskLike;
import com.naleul.naleul.domain.mission.repository.MissionTaskCompleteRepository;
import com.naleul.naleul.domain.mission.repository.MissionTaskLikeRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.user.service.UserFinder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionTaskLikeService {

    private final MissionTaskCompleteRepository missionTaskCompleteRepository;
    private final MissionTaskLikeRepository missionTaskLikeRepository;

    private final UserFinder userFinder;

    // 오늘의 미션 인증 목록 조회
    @Transactional(readOnly = true)
    public List<MissionDailyCompleteResponse> getDailyCompletes(Long userId, Long missionId) {
        List<MissionTaskComplete> completes =
                missionTaskCompleteRepository.findDailyCompletes(missionId, LocalDate.now());

        return completes.stream()
                .map(complete -> {
                    boolean likedByMe = missionTaskLikeRepository
                            .existsByMissionTaskComplete_MissionTaskCompleteIdAndUser_UserId(
                                    complete.getMissionTaskCompleteId(), userId);
                    return MissionDailyCompleteResponse.of(complete, likedByMe);
                })
                .collect(Collectors.toList());
    }

    // 좋아요 토글 (누르면 좋아요, 다시 누르면 취소)
    public void toggleLike(Long userId, Long missionTaskCompleteId) {
        User user = userFinder.findUser(userId);
        MissionTaskComplete complete = missionTaskCompleteRepository
                .findById(missionTaskCompleteId)
                .orElseThrow(() -> new EntityNotFoundException("인증 기록을 찾을 수 없습니다."));

        // 본인 인증에 좋아요 방지
        validateNotSelf(complete, userId);

        boolean alreadyLiked = missionTaskLikeRepository
                .existsByMissionTaskComplete_MissionTaskCompleteIdAndUser_UserId(
                        missionTaskCompleteId, userId);

        if (alreadyLiked) {
            // 좋아요 취소
            MissionTaskLike like = missionTaskLikeRepository
                    .findByMissionTaskComplete_MissionTaskCompleteIdAndUser_UserId(
                            missionTaskCompleteId, userId)
                    .orElseThrow();
            missionTaskLikeRepository.delete(like);
        } else {
            // 좋아요 추가
            MissionTaskLike like = MissionTaskLike.create(complete, user);
            missionTaskLikeRepository.save(like);
        }
    }

    // ===== 검증 =====

    private void validateNotSelf(MissionTaskComplete complete, Long userId) {
        if (complete.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인 인증에는 좋아요를 누를 수 없습니다.");
        }
    }
}