package com.naleul.naleul.domain.lifePattern.service;

import com.naleul.naleul.domain.lifePattern.entity.LifePattern;
import com.naleul.naleul.domain.lifePattern.entity.LifePatternOverride;
import com.naleul.naleul.domain.lifePattern.repository.LifePatternOverrideRepository;
import com.naleul.naleul.domain.lifePattern.repository.LifePatternRepository;
import com.naleul.naleul.domain.lifePattern.support.DayFixedBlocks;
import com.naleul.naleul.domain.lifePattern.support.FixedBlockCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DB 에서 패턴·예외를 읽어 날짜별 고정 블록을 계산한다.
 * TimeTable 조회(BE-4), 배치 엔진(BE-3) 이 공통으로 쓴다.
 *
 * ⚠️ 기본 패턴 초기화는 하지 않는다 (읽기 전용 트랜잭션에서도 쓰이므로).
 *    초기화는 LifePatternService.ensureInitialized 가 담당.
 */
@Component
@RequiredArgsConstructor
public class FixedBlockResolver {

    private final LifePatternRepository lifePatternRepository;
    private final LifePatternOverrideRepository overrideRepository;

    public Map<LocalDate, DayFixedBlocks> resolve(Long userId, LocalDate startDate, LocalDate endDate) {
        List<LifePattern> patterns = lifePatternRepository.findAllByUserId(userId);
        List<LifePatternOverride> overrides = overrideRepository.findAllInRange(
                userId, startDate.minusDays(FixedBlockCalculator.SPAN), endDate.plusDays(FixedBlockCalculator.SPAN));
        return FixedBlockCalculator.calculate(patterns, overrides, startDate, endDate);
    }

    public DayFixedBlocks resolveDay(Long userId, LocalDate date) {
        return resolve(userId, date, date).get(date);
    }
}
