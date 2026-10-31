// core/billing/PlayBilling.kt
//
// Android Google Play 구독 결제 (Play Billing Library 8 — Play 정책상 8 이상 필요)
// iOS Core/Purchase(StoreKit) 와 같은 역할. 웹은 토스페이먼츠 · 토스페이 정기구독이에요.
//
// 흐름: 상품(가격) 불러오기 → launchBillingFlow → 결제되면 purchaseToken 을 서버 POST /v1/play-billing/verify 로 →
//       서버가 Google Play 에 확인 · 확인 처리(acknowledge) · Pro 기간 연장. 갱신 · 해지 · 환불은 서버가 Play 알림으로 맞춰요.
//  - 앱은 확인 처리(acknowledge)를 하지 않아요. 서버가 Pro 를 준 다음에 해요 (3일 안에 안 하면 Google 이 자동 환불).
//  - 결제한 나를 계정 표시(obfuscatedAccountId)를 붙여서, 서버가 다른 계정의 구매 토큰을 막아요.
//
// Play Console 에 구독 상품 두 개를 만들어야 해요: com.naleul.pro.monthly · com.naleul.pro.yearly (iOS 와 같은 id)

package com.naleul.naleul.core.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.naleul.naleul.core.network.API
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import kotlin.coroutines.resume

object PlayBilling {

    /** 결제 결과 */
    sealed class Outcome {
        data class Purchased(val purchase: Purchase) : Outcome()
        /** 편의점 결제 등 승인 대기 — 승인되면 다음에 화면을 열 때 서버로 보내요 */
        data object Pending : Outcome()
        data object Canceled : Outcome()
        /** 이미 구독 중 (다른 기기 · 복원 필요) */
        data object AlreadyOwned : Outcome()
        data class Failed(val reason: String, val message: String) : Outcome()
    }

    private var client: BillingClient? = null
    private var pending: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        pending?.complete(result to purchases)
        pending = null
    }

    private fun client(context: Context): BillingClient = client ?: BillingClient.newBuilder(context.applicationContext)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
        .also { client = it }

    /** Play 결제 서비스 연결 (Play 스토어가 없는 기기면 false) */
    suspend fun connect(context: Context): Boolean {
        val c = client(context)
        if (c.isReady) return true
        return suspendCancellableCoroutine { cont ->
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    // enableAutoServiceReconnection 이라 다음 요청 때 자동으로 다시 연결해요
                }
            })
        }
    }

    /** 구독 상품 (가격은 Play 가 사용자 나라 · 통화로 알려줘요) */
    suspend fun products(context: Context, ids: List<String>): List<ProductDetails> {
        if (!connect(context)) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            })
            .build()
        return suspendCancellableCoroutine { cont ->
            client(context).queryProductDetailsAsync(params) { result, details ->
                if (cont.isActive) {
                    cont.resume(if (result.responseCode == BillingResponseCode.OK) details.productDetailsList else emptyList())
                }
            }
        }
    }

    /** 기본 요금제(할인 오퍼가 아닌 것)를 우선. 없으면 첫 오퍼 */
    fun baseOffer(details: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = details.subscriptionOfferDetails ?: return null
        return offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
    }

    /** 반복 결제 가격 (무료 체험 · 할인 단계가 있어도 마지막 단계가 정가) */
    fun recurringPhase(details: ProductDetails): ProductDetails.PricingPhase? =
        baseOffer(details)?.pricingPhases?.pricingPhaseList?.lastOrNull()

    suspend fun purchase(activity: Activity, details: ProductDetails, userId: Int): Outcome {
        if (!connect(activity)) return Outcome.Failed("billing_unavailable", "Google Play 결제를 쓸 수 없는 기기예요.")
        val offer = baseOffer(details) ?: return Outcome.Failed("no_offer", "구독 상품을 찾을 수 없어요.")
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offer.offerToken)
                    .build()
            ))
            .setObfuscatedAccountId(accountId(userId))
            .build()
        val deferred = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>()
        pending = deferred
        val launch = client(activity).launchBillingFlow(activity, params)
        if (launch.responseCode != BillingResponseCode.OK) {
            pending = null
            return outcomeOf(launch, null, details.productId)
        }
        val (result, purchases) = deferred.await()
        return outcomeOf(result, purchases, details.productId)
    }

    private fun outcomeOf(result: BillingResult, purchases: List<Purchase>?, productId: String): Outcome =
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val p = purchases?.firstOrNull { it.products.contains(productId) } ?: purchases?.firstOrNull()
                when {
                    p == null -> Outcome.Failed("no_purchase", "결제 결과를 받지 못했어요.")
                    p.purchaseState == Purchase.PurchaseState.PENDING -> Outcome.Pending
                    else -> Outcome.Purchased(p)
                }
            }
            BillingResponseCode.USER_CANCELED -> Outcome.Canceled
            BillingResponseCode.ITEM_ALREADY_OWNED -> Outcome.AlreadyOwned
            BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.SERVICE_UNAVAILABLE ->
                Outcome.Failed("billing_unavailable", "Google Play 결제를 쓸 수 없어요. Play 스토어 로그인을 확인해 주세요.")
            else -> Outcome.Failed("play_${result.responseCode}", result.debugMessage.ifBlank { "결제하지 못했어요." })
        }

    /** 이 Google 계정이 가진 구독 (복원 · 결제 후 서버 전송이 끊겼을 때 다시 보내기) */
    suspend fun ownedSubscriptions(context: Context): List<Purchase> {
        if (!connect(context)) return emptyList()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        return suspendCancellableCoroutine { cont ->
            client(context).queryPurchasesAsync(params) { result, list ->
                if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) list else emptyList())
            }
        }
    }

    /** 서버 PlayAccountId.of 와 같은 값: SHA-256("naleul-play:" + userId) 소문자 hex (64자) */
    fun accountId(userId: Int): String =
        MessageDigest.getInstance("SHA-256").digest("naleul-play:$userId".toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** 서버 /v1/play-billing */
object PlayBillingAPI {
    @Serializable
    data class VerifyRequest(val productId: String, val purchaseToken: String)

    /** 백엔드 PlayBillingStatusResponse */
    @Serializable
    data class Status(
        val configured: Boolean = false,
        val premium: Boolean = false,
        val premiumUntil: String? = null,
        val subscription: Subscription? = null,
    ) {
        @Serializable
        data class Subscription(
            val productId: String,
            val expiryTime: String? = null,
            val autoRenewing: Boolean = false,
            val canceled: Boolean = false,
            val inGracePeriod: Boolean = false,
            val testPurchase: Boolean = false,
        )
    }

    suspend fun status(): Status? = API.get<Status>("/v1/play-billing")

    suspend fun verify(productId: String, purchaseToken: String): Status? =
        API.requestEncodable<Status, VerifyRequest>("POST", "/v1/play-billing/verify", VerifyRequest(productId, purchaseToken))
}

/** Compose LocalContext → Activity (결제 창은 Activity 가 있어야 띄울 수 있어요) */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
