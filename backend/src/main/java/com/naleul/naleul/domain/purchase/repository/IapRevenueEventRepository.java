package com.naleul.naleul.domain.purchase.repository;

import com.naleul.naleul.domain.purchase.entity.IapRevenueEvent;
import com.naleul.naleul.domain.purchase.enums.RevenueEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface IapRevenueEventRepository extends JpaRepository<IapRevenueEvent, Long> {

    /** 앱의 결제 확인 요청과 Apple 웹훅이 같은 결제를 두 번 알려줄 수 있어서 중복 확인용 */
    boolean existsByTransactionIdAndEventTypeIn(String transactionId, Collection<RevenueEventType> eventTypes);
}
