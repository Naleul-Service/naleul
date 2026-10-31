// Features/Settings/MembershipScreen.swift
//
// 웹 원본: app/(main)/settings/membership/page.tsx · features/billing/ui/MembershipView.tsx · features/billing/format.ts
//          (+ 결과 화면 features/billing/ui/BillingResultView.tsx 의 문구)
//
// ⚠️ 웹은 토스페이먼츠 자동결제지만, iOS 앱 안에서는 애플 인앱 결제만 쓸 수 있어요.
//    그래서 화면 구성 · 문구는 웹을 따르고, 결제는 Core/Purchase/PurchaseManager(StoreKit 2)로 해요.
//    카드 등록 · 결제 내역 · 웹 해지/해지 취소는 애플 구독 관리(manageSubscriptionsSheet)로 바뀌었어요.

import SwiftUI
import StoreKit

/// 화면에 보여줄 요금제 한 개 (StoreKit Product 에서 만들거나, 미리보기에서 직접 만들어요)
struct MembershipPlanOption: Identifiable, Hashable {
    /// 상품 id (ProductIdentifier)
    let id: String
    /// "월간" | "연간" (웹 PLAN_LABEL)
    let label: String
    let months: Int
    /// "₩3,900"
    let displayPrice: String
    /// 연간일 때 "월 ₩3,250"
    let perMonthPrice: String?
    /// 월간 대비 할인율 (0 이면 표시 안 함)
    let savePercent: Int
}

struct MembershipScreen: View {
    @EnvironmentObject private var purchaseManager: PurchaseManager
    @Environment(UserStore.self) private var userStore

    init() {}

    var body: some View {
        MembershipContent(
            role: userStore.user?.userRole ?? .free,
            appleProductIDs: purchaseManager.purchasedProductIDs,
            plans: Self.planOptions(purchaseManager.products),
            isLoadingProducts: purchaseManager.isLoading,
            onPurchase: { id in await purchase(id) },
            onRestore: { await restore() },
            onReload: { await reload() }
        )
        .task {
            let entry = ST.membershipEntry ?? "other"
            ST.membershipEntry = nil
            if purchaseManager.products.isEmpty { await purchaseManager.loadProducts() }
            await purchaseManager.updatePurchasedProducts()
            let role = userStore.user?.userRole ?? .free
            let ids = purchaseManager.purchasedProductIDs
            ST.track("membership.pageview", [
                "membership_state": ST.membershipState(role: role, appleIds: ids),
                "plan": ST.plan(of: ids),
                "entry_source": entry,
            ])
            trackLoadFailIfNeeded()
        }
        // PurchaseManager 가 남긴 오류 문구(결제 승인 대기 등)는 토스트로 보여주고 비워요
        .onChange(of: purchaseManager.errorMessage) { _, message in
            guard let message else { return }
            Toast.error(message)
            purchaseManager.errorMessage = nil
        }
    }

    // MARK: StoreKit Product → 화면용

    static func planOptions(_ products: [Product]) -> [MembershipPlanOption] {
        let monthly = products.first { $0.id == ProductIdentifier.premiumMonthly.rawValue }
        let ordered: [(ProductIdentifier, Int)] = [(.premiumMonthly, 1), (.premiumYearly, 12)]
        return ordered.compactMap { entry -> MembershipPlanOption? in
            let (pid, months) = entry
            guard let p = products.first(where: { $0.id == pid.rawValue }) else { return nil }
            var perMonth: String? = nil
            var save = 0
            if months > 1 {
                perMonth = (p.price / Decimal(months)).formatted(p.priceFormatStyle)
                if let monthly {
                    let full = NSDecimalNumber(decimal: monthly.price * Decimal(months)).doubleValue
                    let actual = NSDecimalNumber(decimal: p.price).doubleValue
                    if full > 0 { save = Int(((1 - actual / full) * 100).rounded()) }
                }
            }
            return MembershipPlanOption(id: p.id, label: months == 12 ? "연간" : "월간", months: months,
                                        displayPrice: p.displayPrice, perMonthPrice: perMonth, savePercent: save)
        }
    }

    // MARK: 결제 · 복원

    /// 성공하면 true
    /// 실패 칸 '다시 시도'
    @MainActor
    private func reload() async {
        ST.track("membership.retry_click", ["fail_reason": "products_unavailable"])
        await purchaseManager.loadProducts()
        trackLoadFailIfNeeded()
    }

    /// 앱스토어 상품을 못 받아 '멤버십 정보를 불러오지 못했어요'가 보일 때 (구독 중이 아니고 무료일 때만 보여요)
    @MainActor
    private func trackLoadFailIfNeeded() {
        let role = userStore.user?.userRole ?? .free
        guard purchaseManager.products.isEmpty, !purchaseManager.isLoading,
              purchaseManager.purchasedProductIDs.isEmpty, !role.isPaid else { return }
        ST.track("membership.load_fail", ["fail_reason": "products_unavailable"])
    }

    @MainActor
    private func purchase(_ productId: String) async -> Bool {
        let plan = ST.plan(productId)
        ST.track("membership.subscribe_click", ["plan": plan])
        guard let product = purchaseManager.products.first(where: { $0.id == productId }) else {
            ST.track("membership.purchase_result", ["plan": plan, "result": "fail", "fail_reason": "product_missing"])
            Toast.error("상품 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.")
            return false
        }
        do {
            try await purchaseManager.purchase(product)
            if purchaseManager.purchasedProductIDs.contains(product.id) {
                ST.track("membership.purchase_result", ["plan": plan, "result": "success"])
                Toast.success("Pro 구독을 시작했어요")
                return true
            }
            if purchaseManager.lastPurchasePending {
                ST.track("membership.purchase_result", ["plan": plan, "result": "pending"])
                return false
            }
            // 오류 없이 끝났는데 구매 목록에 없으면 사용자가 취소한 경우가 대부분이에요
            ST.track("membership.purchase_result", ["plan": plan, "result": "cancel"])
            return false
        } catch {
            let ns = error as NSError
            ST.track("membership.purchase_result", ["plan": plan, "result": "fail", "fail_reason": "\(ns.domain):\(ns.code)"])
            Toast.error(error.localizedDescription)
            return false
        }
    }

    @MainActor
    private func restore() async {
        ST.track("membership.restore_click", [
            "membership_state": ST.membershipState(role: userStore.user?.userRole ?? .free, appleIds: purchaseManager.purchasedProductIDs),
        ])
        do {
            try await purchaseManager.restorePurchases()
            if purchaseManager.isPremiumActive {
                ST.track("membership.restore_result", ["result": "restored"])
                Toast.success("구입 내역을 복원했어요.")
            } else {
                ST.track("membership.restore_result", ["result": "none"])
                Toast.show("복원할 구입 내역이 없어요.")
            }
        } catch {
            let ns = error as NSError
            ST.track("membership.restore_result", ["result": "fail", "fail_reason": "\(ns.domain):\(ns.code)"])
            Toast.error(error.localizedDescription)
        }
    }
}

// MARK: - 화면 (StoreKit 없이도 그려지게 값만 받아요)

struct MembershipContent: View {
    let role: UserRole
    /// 이 애플 ID 로 구독 중인 상품 id
    let appleProductIDs: Set<String>
    let plans: [MembershipPlanOption]
    let isLoadingProducts: Bool
    let onPurchase: (String) async -> Bool
    let onRestore: () async -> Void
    let onReload: () async -> Void

    private var appleActive: Bool { !appleProductIDs.isEmpty }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if appleActive {
                    MembershipAppleSubscriptionCard(productIDs: appleProductIDs)
                } else if role.isPaid {
                    MembershipOtherPremiumCard(role: role)
                } else {
                    MembershipPlanPicker(plans: plans, isLoading: isLoadingProducts,
                                         onPurchase: onPurchase, onReload: onReload)
                }
                MembershipFooter(onRestore: onRestore)
            }
            .padding(16)
        }
        .canvasBackground()
        .navigationTitle("멤버십")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// BM(2026-10): 목표 · 루틴 · Task · 회고 만들기와 알림은 Free 도 제한 없이 써요. Pro 는 AI 를 더 많이 + 광고 없이 (웹과 같은 문구)
private let membershipBenefits = [
    "AI 목표 만들기 주 10개 · 월 20개 (Free: 주 2개 · 월 8개)",
    "AI 계획 초안 주 10번 · 월 20번 (Free: 주 2번 · 월 8번)",
    "AI로 목표 고치기 · AI로 일정 조정 · 브레인 덤프 AI 정리 하루 30번 (Free: 각 3번)",
    "광고 없이",
    "웹과 iOS 앱 어디서든 같은 Pro",
]

/// 요금제 고르기 + 구독 (웹 PlanPicker)
private struct MembershipPlanPicker: View {
    let plans: [MembershipPlanOption]
    let isLoading: Bool
    let onPurchase: (String) async -> Bool
    let onReload: () async -> Void

    @State private var selectedId: String = ProductIdentifier.premiumYearly.rawValue
    @State private var agreed = false
    @State private var busy = false

    // private @State 때문에 자동 init 이 private 이 되어 MembershipContent 에서 못 불러요 → 직접 만들어요
    init(plans: [MembershipPlanOption], isLoading: Bool,
         onPurchase: @escaping (String) async -> Bool, onReload: @escaping () async -> Void) {
        self.plans = plans
        self.isLoading = isLoading
        self.onPurchase = onPurchase
        self.onReload = onReload
    }

    private var chosen: MembershipPlanOption? { plans.first { $0.id == selectedId } ?? plans.first }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("나를 Pro").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
            VStack(alignment: .leading, spacing: 8) {
                ForEach(membershipBenefits, id: \.self) { b in
                    HStack(spacing: 8) {
                        NSymbol("checkmark", size: 13, weight: .bold).foregroundStyle(Color.textBrand)
                        Text(b).font(.pt(14)).foregroundStyle(Color.ink2)
                    }
                }
            }
            .padding(.top, 12)

            plansSection.padding(.top, 20)

            agreeRow.padding(.top, 20)

            NButton("구독하기", variant: .brand, size: .lg, icon: "creditcard", loading: busy, fullWidth: true) {
                start()
            }
            .disabled(!agreed || chosen == nil)
            .padding(.top, 16)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    @ViewBuilder private var plansSection: some View {
        if plans.isEmpty {
            if isLoading {
                VStack(spacing: 8) {
                    ProgressView().tint(Color.ink3)
                    Text("상품 정보를 불러오는 중...").font(.pt(13)).foregroundStyle(Color.ink3)
                }
                .frame(maxWidth: .infinity, minHeight: 120)
            } else {
                VStack(spacing: 12) {
                    Text("멤버십 정보를 불러오지 못했어요").font(.pt(15, .bold)).foregroundStyle(Color.ink)
                    NButton("다시 시도", size: .sm) { Task { await onReload() } }
                }
                .frame(maxWidth: .infinity, minHeight: 120)
            }
        } else {
            VStack(spacing: 12) {
                ForEach(plans) { p in
                    MembershipPlanCard(plan: p, selected: chosen?.id == p.id) {
                        if selectedId != p.id { ST.track("membership.plan_select", ["plan": ST.plan(p.id)]) }
                        selectedId = p.id
                    }
                }
            }
        }
    }

    private var agreeRow: some View {
        Button {
            agreed.toggle()
            ST.track("membership.agree_toggle", ["checked": agreed, "plan": ST.plan(chosen?.id)])
        } label: {
            HStack(alignment: .top, spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 4).fill(agreed ? Color.brand : Color.surface)
                    RoundedRectangle(cornerRadius: 4).stroke(agreed ? Color.brand : Color.borderSubtle, lineWidth: 1)
                    if agreed {
                        NSymbol("checkmark", size: 10, weight: .bold).foregroundStyle(.white)
                    }
                }
                .frame(width: 16, height: 16)
                .padding(.top, 2)
                Text(agreeText)
                    .font(.pt(13))
                    .foregroundStyle(Color.ink2)
                    .lineSpacing(3)
                    .multilineTextAlignment(.leading)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(agreed ? .isSelected : [])
    }

    /// 웹 문구에서 "설정 → 멤버십에서 해지"만 애플 구독 해지 방법으로 바꿨어요
    private var agreeText: String {
        let head: String
        if let c = chosen {
            head = "\(c.displayPrice)이 \(c.months == 12 ? "매년" : "매달") "
        } else {
            head = ""
        }
        return head + "자동으로 결제되는 데 동의해요. 결제 하루 전까지 아이폰 설정 → Apple ID → 구독에서 언제든 해지할 수 있고, 해지해도 남은 기간까지 Pro를 쓸 수 있어요."
    }

    private func start() {
        guard let c = chosen, !busy else { return }
        busy = true
        Task { @MainActor in
            _ = await onPurchase(c.id)
            busy = false
        }
    }
}

private struct MembershipPlanCard: View {
    let plan: MembershipPlanOption
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text(plan.label).font(.pt(15, .bold)).foregroundStyle(Color.ink)
                    if plan.savePercent > 0 { Badge("\(plan.savePercent)% 할인", tone: .success) }
                }
                (Text(plan.displayPrice).font(.ptMono(22, .bold)).foregroundColor(Color.ink)
                 + Text(" / \(plan.months == 12 ? "년" : "월")").font(.pt(13, .medium)).foregroundColor(Color.ink3))
                    .padding(.top, 8)
                if let per = plan.perMonthPrice {
                    Text("월 \(per) 꼴").font(.pt(12)).foregroundStyle(Color.ink3).padding(.top, 2)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(selected ? Color.brandSoft : Color.surface, in: RoundedRectangle(cornerRadius: Radius.xxl))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xxl)
                    .stroke(selected ? Color.brand : Color.borderSubtle, lineWidth: selected ? 1.5 : 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: Radius.xxl))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 이 애플 ID 로 구독 중 (웹 SubscriptionCard 자리)
private struct MembershipAppleSubscriptionCard: View {
    let productIDs: Set<String>
    @State private var showManage = false

    init(productIDs: Set<String>) { self.productIDs = productIDs }

    private var planLabel: String {
        productIDs.contains(ProductIdentifier.premiumYearly.rawValue) ? "연간" : "월간"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text("나를 Pro \(planLabel)").font(.pt(16, .semibold)).foregroundStyle(Color.ink)
                Badge("이용 중", tone: .brand)
            }
            Text("구독 관리 · 해지는 아이폰 설정 → Apple ID → 구독에서 할 수 있어요. 웹에서도 같은 Pro 혜택을 그대로 쓸 수 있어요.")
                .font(.pt(13))
                .foregroundStyle(Color.ink3)
                .lineSpacing(3)
                .padding(.top, 12)
            NButton("구독 관리", variant: .secondary) {
                ST.track("membership.manage_click", ["plan": ST.plan(of: productIDs)])
                showManage = true
            }
                .padding(.top, 20)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
        .manageSubscriptionsSheet(isPresented: $showManage)
    }
}

/// 애플 결제가 아닌 경로(웹 토스 구독 · 운영진)로 Pro (웹 otherPremium 카드를 반대로)
private struct MembershipOtherPremiumCard: View {
    let role: UserRole

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: "globe")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.textBrand)
                .frame(width: 40, height: 40)
                .background(Color.brandSoft, in: RoundedRectangle(cornerRadius: Radius.xl))
            VStack(alignment: .leading, spacing: 4) {
                Text(role == .admin ? "운영진 계정이라 Pro를 이용 중이에요" : "웹에서 Pro를 이용 중이에요")
                    .font(.pt(15, .bold))
                    .foregroundStyle(Color.ink)
                Text("구독 관리 · 해지는 웹의 설정 → 멤버십에서 할 수 있어요. 앱에서도 같은 Pro 혜택을 그대로 쓸 수 있어요.")
                    .font(.pt(13))
                    .foregroundStyle(Color.ink3)
                    .lineSpacing(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(20)
        .card()
    }
}

/// 구입 내역 복원 · 안내 · 약관
private struct MembershipFooter: View {
    let onRestore: () async -> Void
    @State private var restoring = false

    init(onRestore: @escaping () async -> Void) { self.onRestore = onRestore }

    var body: some View {
        VStack(spacing: 16) {
            Text("구매를 진행함으로써, 환불요청시\n서비스 이용 내역이 Apple에 제공됨에 동의하게 됩니다.\n언제든 해지할 수 있어요.")
                .font(.pt(12))
                .foregroundStyle(Color.ink3)
                .multilineTextAlignment(.center)
            HStack(spacing: 16) {
                NavigationLink(value: AppRoute.settings(.termsDetail(TermsType.service.rawValue))) {
                    Text(TermsType.service.detailTitle).underline()
                }
                .simultaneousGesture(TapGesture().onEnded {
                    ST.track("terms.item_click", ["terms_type": TermsType.service.rawValue, "entry_point": "membership"])
                })
                NavigationLink(value: AppRoute.settings(.termsDetail(TermsType.privacy.rawValue))) {
                    Text(TermsType.privacy.detailTitle).underline()
                }
                .simultaneousGesture(TapGesture().onEnded {
                    ST.track("terms.item_click", ["terms_type": TermsType.privacy.rawValue, "entry_point": "membership"])
                })
            }
            .font(.pt(12, .medium))
            .foregroundStyle(Color.ink2)
            .buttonStyle(.plain)

            NButton("구입 내역 복원하기", variant: .ghost, size: .sm, loading: restoring) {
                restoring = true
                Task { @MainActor in
                    await onRestore()
                    restoring = false
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - 미리보기

private let previewPlans = [
    MembershipPlanOption(id: ProductIdentifier.premiumMonthly.rawValue, label: "월간", months: 1,
                         displayPrice: "₩3,900", perMonthPrice: nil, savePercent: 0),
    MembershipPlanOption(id: ProductIdentifier.premiumYearly.rawValue, label: "연간", months: 12,
                         displayPrice: "₩39,000", perMonthPrice: "₩3,250", savePercent: 17),
]

#Preview("무료 — 요금제 고르기") {
    NavigationStack {
        MembershipContent(role: .free, appleProductIDs: [], plans: previewPlans, isLoadingProducts: false,
                          onPurchase: { _ in false }, onRestore: {}, onReload: {})
    }
}

#Preview("애플 구독 중") {
    NavigationStack {
        MembershipContent(role: .pro, appleProductIDs: [ProductIdentifier.premiumYearly.rawValue], plans: previewPlans,
                          isLoadingProducts: false, onPurchase: { _ in false }, onRestore: {}, onReload: {})
    }
}

#Preview("웹에서 구독 중") {
    NavigationStack {
        MembershipContent(role: .pro, appleProductIDs: [], plans: [], isLoadingProducts: false,
                          onPurchase: { _ in false }, onRestore: {}, onReload: {})
    }
}
