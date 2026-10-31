package com.naleul.naleul.domain.webBilling.repository;

import com.naleul.naleul.domain.webBilling.entity.WebPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebPaymentRepository extends JpaRepository<WebPayment, Long> {

    boolean existsByOrderId(String orderId);

    List<WebPayment> findTop12ByUserIdOrderByCreatedAtDesc(Long userId);
}
