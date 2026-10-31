// Core/Purchase/PurchaseManager.swift
// StoreKit 2 인앱 결제 (iOS 는 앱 안 결제에 Toss 를 쓸 수 없어서 애플 인앱 결제로 Pro 를 팔아요)
import Foundation
import Combine   // ObservableObject · @Published 는 Combine 에 있어요
import StoreKit

@MainActor
final class PurchaseManager: ObservableObject {
    static let shared = PurchaseManager()

    @Published private(set) var products: [Product] = []
    @Published private(set) var purchasedProductIDs: Set<String> = []
    @Published private(set) var isLoading = false
    @Published private(set) var hasLoadedEntitlements = false
    @Published var errorMessage: String?

    private var updateListenerTask: Task<Void, Error>?

    init() {
        updateListenerTask = listenForTransactions()

        Task {
            await loadProducts()
            await updatePurchasedProducts()
        }
    }

    deinit {
        updateListenerTask?.cancel()
    }

    // MARK: - 상품 목록 불러오기

    func loadProducts() async {
        isLoading = true
        defer { isLoading = false }

        do {
            products = try await Product.products(for: ProductIdentifier.allIDs)
        } catch {
            errorMessage = "상품 정보를 불러오지 못했습니다: \(error.localizedDescription)"
        }
    }

    // MARK: - 구매 실행

    /// 마지막 결제가 승인 대기(가족 공유 · 부모 승인 등)였는지 — membership.purchase_result 의 pending
    private(set) var lastPurchasePending = false

    func purchase(_ product: Product) async throws {
        lastPurchasePending = false
        let result = try await product.purchase()

        switch result {
        case .success(let verification):
            let signedTransaction = verification.jwsRepresentation
            let transaction = try checkVerified(verification)

            try await IapAPI.verifyPurchase(signedTransaction: signedTransaction)
            await transaction.finish()
            await updatePurchasedProducts()
            UserStore.shared.updateUserRole(.pro)
            Invalidation.shared.invalidate(.account, .usage)

        case .userCancelled:
            break

        case .pending:
            lastPurchasePending = true
            errorMessage = "결제 승인 대기 중입니다."

        @unknown default:
            break
        }
    }

    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw PurchaseError.failedVerification
        case .verified(let safe):
            return safe
        }
    }

    private func listenForTransactions() -> Task<Void, Error> {
        Task.detached {
            for await result in Transaction.updates {
                do {
                    let signedTransaction = result.jwsRepresentation
                    let transaction = try await self.checkVerified(result)

                    try await IapAPI.verifyPurchase(signedTransaction: signedTransaction)
                    await transaction.finish()
                    await self.updatePurchasedProducts()
                    await UserStore.shared.updateUserRole(.pro)
                    await Invalidation.shared.invalidate(.account, .usage)
                } catch {
                    print("거래 검증 실패: \(error)")
                }
            }
        }
    }

    func updatePurchasedProducts() async {
        var purchasedIDs: Set<String> = []

        for await result in Transaction.currentEntitlements {
            guard let transaction = try? checkVerified(result) else { continue }
            if transaction.revocationDate == nil {
                purchasedIDs.insert(transaction.productID)
            }
        }

        purchasedProductIDs = purchasedIDs
        hasLoadedEntitlements = true
    }

    // ⭐ 수정 — 로컬 상태만이 아니라 서버에도 다시 검증을 태움
    func restorePurchases() async throws {
        try await AppStore.sync()

        var didRestoreAnyPurchase = false

        for await result in Transaction.currentEntitlements {
            guard let transaction = try? checkVerified(result),
                  transaction.revocationDate == nil else { continue }

            do {
                try await IapAPI.verifyPurchase(signedTransaction: result.jwsRepresentation)
                didRestoreAnyPurchase = true
            } catch {
                // 개별 거래 검증 실패는 로그만 남기고 나머지 거래는 계속 처리
                print("구매 복원 중 검증 실패 (productID: \(transaction.productID)): \(error)")
            }
        }

        await updatePurchasedProducts()

        if didRestoreAnyPurchase {
            UserStore.shared.updateUserRole(.pro)
            Invalidation.shared.invalidate(.account, .usage)
        }
    }

    var isPremiumActive: Bool {
        !purchasedProductIDs.isEmpty
    }
}

enum PurchaseError: LocalizedError {
    case failedVerification

    var errorDescription: String? {
        switch self {
        case .failedVerification:
            return "거래 검증에 실패했습니다."
        }
    }
}
