package com.naleul.naleul.domain.mission.service;

import com.naleul.naleul.domain.mission.dto.response.MissionBookmarkResponse;
import com.naleul.naleul.domain.mission.entity.Mission;
import com.naleul.naleul.domain.mission.entity.MissionBookmark;
import com.naleul.naleul.domain.mission.repository.MissionBookmarkRepository;
import com.naleul.naleul.domain.mission.repository.MissionRepository;
import com.naleul.naleul.domain.user.entity.User;
import com.naleul.naleul.domain.user.repository.UserRepository;
import com.naleul.naleul.domain.user.service.UserFinder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionBookmarkService {

    private final MissionBookmarkRepository missionBookmarkRepository;
    private final MissionRepository missionRepository;
    private final UserFinder userFinder;

    // 찜 토글 (찜 → 취소, 취소 → 찜)
    public boolean toggleBookmark(Long userId, Long missionId) {
        User user = userFinder.findUser(userId);

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new EntityNotFoundException("미션을 찾을 수 없습니다."));

        boolean alreadyBookmarked = missionBookmarkRepository
                .existsByMission_MissionIdAndUser_UserId(missionId, userId);

        if (alreadyBookmarked) {
            // 찜 취소
            MissionBookmark bookmark = missionBookmarkRepository
                    .findByMission_MissionIdAndUser_UserId(missionId, userId)
                    .orElseThrow();
            missionBookmarkRepository.delete(bookmark);
            return false; // 찜 취소됨
        } else {
            // 찜 추가
            MissionBookmark bookmark = MissionBookmark.create(mission, user);
            missionBookmarkRepository.save(bookmark);
            return true;  // 찜 추가됨
        }
    }

    // 내가 찜한 미션 목록 조회
    @Transactional(readOnly = true)
    public List<MissionBookmarkResponse> getMyBookmarks(Long userId) {
        return missionBookmarkRepository.findAllByUserId(userId)
                .stream()
                .map(MissionBookmarkResponse::from)
                .collect(Collectors.toList());
    }
}