package com.naleul.naleul.domain.chart.dto;

// domain/report/dto/RoutineHeatmapRowDto.java
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class RoutineHeatmapRowDto {
    private Long routineId;
    private String routineName;
    private List<Boolean> completedByWeekday; // 길이 7, [월,화,수,목,금,토,일]
    private List<Boolean> scheduledByWeekday; // 길이 7, [월,화,수,목,금,토,일]

    // ✅ 옛날 리포트(JSON에 이 필드가 없어 역직렬화 시 null이 됨)를 위한 방어 처리
    // API 응답으로 나갈 때는 null 대신 "전부 스케줄됨(true)"으로 채워서 내려줌
    // → 새로 생성된 리포트든 옛날 리포트든, iOS는 항상 길이 7짜리 배열을 받는다는 걸 보장받음
    public List<Boolean> getScheduledByWeekday() {
        if (scheduledByWeekday == null) {
            return Collections.nCopies(7, true);
        }
        return scheduledByWeekday;
    }
}