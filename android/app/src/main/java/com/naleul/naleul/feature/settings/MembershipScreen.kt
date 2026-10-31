// feature/settings/MembershipScreen.kt
//
// iOS Features/Settings/MembershipScreen.swift (+ Core/Purchase/ProductIdentifier.swift)
// 웹 원본: app/(main)/settings/membership/page.tsx · features/billing/ui/MembershipView.tsx · features/billing/format.ts
//
// ⚠️ 결제하는 곳: iOS 는 애플 인앱 결제(StoreKit), 웹은 토스페이먼츠 · 토스페이 정기구독, Android 는 Google Play 구독이에요.
//    Android: 가격은 Play 상품 정보(사용자 나라 통화)로 보여주고, 결제되면 서버(/v1/play-billing/verify)가 확인 후 Pro.
//    구독 관리 · 해지는 Google Play 정기 결제 화면에서 해요 ("구독 관리" 버튼).
//    Play 결제 코드는 core/billing/PlayBilling.kt

package com.naleul.naleul.feature.settings

import com.naleul.naleul.app.*
import com.naleul.naleul.core.analytics.*
import com.naleul.naleul.core.network.*
import com.naleul.naleul.core.state.*
import com.naleul.naleul.core.util.*
import com.naleul.naleul.designsystem.*
import com.naleul.naleul.model.*
import com.naleul.naleul.feature.auth.*
import com.naleul.naleul.feature.calendar.*
import com.naleul.naleul.feature.calendarimport.*
import com.naleul.naleul.feature.goal.*
import com.naleul.naleul.feature.goalcreation.*
import com.naleul.naleul.feature.habit.*
import com.naleul.naleul.feature.home.*
import com.naleul.naleul.feature.notifications.*
import com.naleul.naleul.feature.pattern.*
import com.naleul.naleul.feature.record.*
import com.naleul.naleul.feature.settings.*
import com.naleul.naleul.feature.taskadd.*
import com.naleul.naleul.feature.work.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.android.billingclient.api.ProductDetails
import com.naleul.naleul.core.billing.PlayBilling
import com.naleul.naleul.core.billing.PlayBillingAPI
import com.naleul.naleul.core.billing.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

// MARK: - 상품 id (iOS Core/Purchase/ProductIdentifier.swift)

/** iOS App Store 상품 id. 웹 WebPlan(PRO_MONTHLY · PRO_YEARLY)과 같은 혜택이라 화면에서 플랜을 구분하는 id 로도 써요 */
enum class ProductIdentifier(val rawValue: String) {
    premiumMonthly("com.naleul.pro.monthly"),
    premiumYearly("com.naleul.pro.yearly");

    companion object {
        val allIDs: List<String> get() = entries.map { it.rawValue }
        fun fromRaw(raw: String?): ProductIdentifier? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** Play 결제를 쓸 수 없을 때 (Play 스토어가 없는 기기 등) */
const val membershipAndroidBillingNotice = "Google Play 결제를 쓸 수 없어요. 웹에서 결제할 수도 있어요."

/** 화면에 보여줄 요금제 한 개 (웹 결제 상품에서 만들거나, 미리보기에서 직접 만들어요) */
data class MembershipPlanOption(
    /** 상품 id (ProductIdentifier.rawValue) */
    val id: String,
    /** "월간" | "연간" (웹 PLAN_LABEL) */
    val label: String,
    val months: Int,
    /** "₩3,900" */
    val displayPrice: String,
    /** 연간일 때 "₩3,250" */
    val perMonthPrice: String?,
    /** 월간 대비 할인율 (0 이면 표시 안 함) */
    val savePercent: Int,
) {
    companion object {}
}

// MARK: - 가격 불러오기 (GET /v1/web-billing 의 plans)

object MembershipAPI {
    /** 백엔드 WebBillingStatusResponse.Plan — plan: "PRO_MONTHLY" | "PRO_YEARLY" */
    @Serializable
    data class WebPlan(
        val plan: String,
        val name: String = "",
        val amount: Long = 0,
        val months: Int = 1,
    ) {
        companion object {}
    }

    /** 백엔드 WebBillingStatusResponse 중 화면에 필요한 것만 */
    @Serializable
    data class WebBillingStatus(
        val configured: Boolean = false,
        val premium: Boolean = false,
        val otherPremium: Boolean = false,
        val plans: List<WebPlan> = emptyList(),
    ) {
        companion object {}
    }

    suspend fun status(): WebBillingStatus? = API.get<WebBillingStatus>("/v1/web-billing")

    /** "₩3,900" */
    fun won(amount: Long): String = "₩" + NumberFormat.getNumberInstance(Locale.KOREA).format(amount)

    /** 웹 상품 → 화면용 (월간 · 연간 순서, 연간은 월 환산 · 할인율) */
    fun planOptions(plans: List<WebPlan>): List<MembershipPlanOption> {
        val monthly = plans.firstOrNull { it.plan == "PRO_MONTHLY" }
        val ordered = listOf(
            Triple("PRO_MONTHLY", ProductIdentifier.premiumMonthly, 1),
            Triple("PRO_YEARLY", ProductIdentifier.premiumYearly, 12),
        )
        return ordered.mapNotNull { (key, pid, fallbackMonths) ->
            val p = plans.firstOrNull { it.plan == key } ?: return@mapNotNull null
            val months = if (p.months > 0) p.months else fallbackMonths
            var perMonth: String? = null
            var save = 0
            if (months > 1) {
                perMonth = won(p.amount / months)
                if (monthly != null) {
                    val full = (monthly.amount * months).toDouble()
                    if (full > 0) save = ((1 - p.amount.toDouble() / full) * 100).roundToInt()
                }
            }
            MembershipPlanOption(
                id = pid.rawValue,
                label = if (months == 12) "연간" else "월간",
                months = months,
                displayPrice = won(p.amount),
                perMonthPrice = perMonth,
                savePercent = save,
            )
        }
    }
}

/** Play 상품 → 화면용 (월간 · 연간 순서). 가격은 Play 가 알려준 통화 · 금액 그대로 */
fun membershipPlayPlanOptions(details: List<ProductDetails>): List<MembershipPlanOption> {
    val byId = details.associateBy { it.productId }
    val monthlyMicros = byId[ProductIdentifier.premiumMonthly.rawValue]?.let { PlayBilling.recurringPhase(it)?.priceAmountMicros }
    return listOf(ProductIdentifier.premiumMonthly to 1, ProductIdentifier.premiumYearly to 12).mapNotNull { (pid, months) ->
        val d = byId[pid.rawValue] ?: return@mapNotNull null
        val phase = PlayBilling.recurringPhase(d) ?: return@mapNotNull null
        var perMonth: String? = null
        var save = 0
        if (months > 1) {
            if (phase.priceCurrencyCode == "KRW") perMonth = MembershipAPI.won(phase.priceAmountMicros / 1_000_000 / months)
            if (monthlyMicros != null && monthlyMicros > 0) {
                save = ((1 - phase.priceAmountMicros.toDouble() / (monthlyMicros * months)) * 100).roundToInt()
            }
        }
        MembershipPlanOption(
            id = pid.rawValue,
            label = if (months == 12) "연간" else "월간",
            months = months,
            displayPrice = phase.formattedPrice,
            perMonthPrice = perMonth,
            savePercent = save,
        )
    }
}

/** Google Play 정기 결제 관리 화면 (해지 · 결제 수단 변경) */
fun membershipPlayManageUri(productId: String?, packageName: String): Uri =
    Uri.parse("https://play.google.com/store/account/subscriptions" +
        (if (productId != null) "?sku=$productId&package=$packageName" else ""))

/** plan 값 (monthly · yearly) */
fun membershipPlanKey(productId: String): String =
    if (productId == ProductIdentifier.premiumYearly.rawValue) "yearly" else "monthly"

// MARK: - 화면

@Composable
fun MembershipScreen() {
    var plans by remember { mutableStateOf<List<MembershipPlanOption>>(emptyList()) }
    var products by remember { mutableStateOf<Map<String, ProductDetails>>(emptyMap()) }
    var playSubscription by remember { mutableStateOf<PlayBillingAPI.Status.Subscription?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val preview = androidx.compose.ui.platform.LocalInspectionMode.current

    /** 서버 결과로 화면 · 역할 맞추기 (운영진은 그대로) */
    fun applyStatus(status: PlayBillingAPI.Status?) {
        if (status == null) return
        playSubscription = status.subscription
        val role = UserStore.user?.userRole
        if (role != UserRole.admin && role != UserRole.guest) {
            UserStore.updateUserRole(if (status.premium) UserRole.pro else UserRole.free)
        }
    }

    /** 이 Google 계정의 구독을 서버로 보내 맞춰요. 결제 직후 서버 전송이 끊겼어도 여기서 다시 이어져요 */
    suspend fun syncOwned(onlyUnacknowledged: Boolean): Int {
        var count = 0
        for (p in PlayBilling.ownedSubscriptions(context)) {
            if (p.purchaseState != com.android.billingclient.api.Purchase.PurchaseState.PURCHASED) continue
            if (onlyUnacknowledged && p.isAcknowledged) continue
            val productId = p.products.firstOrNull() ?: continue
            applyStatus(PlayBillingAPI.verify(productId, p.purchaseToken))
            count++
        }
        return count
    }

    suspend fun loadProducts() {
        isLoading = true
        var failReason: String? = null
        try {
            val details = PlayBilling.products(context, ProductIdentifier.allIDs)
            products = details.associateBy { it.productId }
            plans = membershipPlayPlanOptions(details)
            if (plans.isEmpty()) failReason = "products_empty"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 상품을 못 불러오면 "다시 시도" 카드가 보여요
            failReason = "billing_${e::class.simpleName ?: "error"}"
        }
        isLoading = false
        // 무료일 때만 "멤버십 정보를 불러오지 못했어요" 칸이 보여요
        if (failReason != null && UserStore.user?.userRole?.isPaid != true) {
            ST.track("membership.load_fail", mapOf("fail_reason" to failReason), preview)
        }
    }

    LaunchedEffect(Unit) {
        ST.track("membership.pageview", mapOf(
            "membership_state" to ST.roleState,
            "plan" to null,
            "entry_source" to (ST.pendingMembershipEntry ?: "other"),
        ), preview)
        ST.pendingMembershipEntry = null
        if (preview) {
            isLoading = false
            return@LaunchedEffect
        }
        loadProducts()
        // 지금 Play 구독 (구독 관리 버튼) · 확인 처리가 안 된 결제가 있으면 서버로 다시 보내기
        try {
            applyStatus(PlayBillingAPI.status())
            syncOwned(onlyUnacknowledged = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 조용히 — 다음에 화면을 열 때 다시
        }
    }

    MembershipContent(
        role = UserStore.user?.userRole ?: UserRole.free,
        plans = plans,
        isLoadingProducts = isLoading,
        playSubscription = playSubscription,
        onPurchase = onPurchase@{ id ->
            val plan = membershipPlanKey(id)
            fun result(r: String, reason: String? = null) = ST.track("membership.purchase_result", mapOf(
                "plan" to plan, "result" to r, "fail_reason" to reason,
            ))
            val activity = context.findActivity()
            val details = products[id]
            val userId = UserStore.user?.userId
            if (activity == null || details == null || userId == null) {
                result("fail", if (details == null) "product_missing" else "no_activity")
                Toast.show(membershipAndroidBillingNotice)
                return@onPurchase false
            }
            when (val outcome = PlayBilling.purchase(activity, details, userId)) {
                is PlayBilling.Outcome.Purchased -> try {
                    applyStatus(PlayBillingAPI.verify(id, outcome.purchase.purchaseToken))
                    result("success")
                    Toast.show("Pro 구독을 시작했어요")
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 결제는 됐는데 서버 확인만 실패 → 다음에 화면을 열면 다시 보내요 (3일 안에 확인 처리)
                    result("fail", "verify_${ST.status(e)}")
                    Toast.show(errorMessage(e, fallback = "결제는 됐지만 확인하지 못했어요. 잠시 후 멤버십 화면을 다시 열어 주세요."))
                    false
                }
                PlayBilling.Outcome.Pending -> {
                    result("pending")
                    Toast.show("결제 승인을 기다리고 있어요. 승인되면 Pro가 시작돼요.")
                    false
                }
                PlayBilling.Outcome.Canceled -> {
                    result("cancel")
                    false
                }
                PlayBilling.Outcome.AlreadyOwned -> {
                    // 이미 이 Google 계정으로 구독 중 → 서버에 다시 맞추기
                    val n = runCatching { syncOwned(onlyUnacknowledged = false) }.getOrDefault(0)
                    result(if (n > 0) "success" else "fail", "already_owned")
                    Toast.show(if (n > 0) "이미 구독 중이라 Pro를 다시 연결했어요" else "이미 구독 중인 상품이에요")
                    n > 0
                }
                is PlayBilling.Outcome.Failed -> {
                    result("fail", outcome.reason)
                    Toast.show(outcome.message)
                    false
                }
            }
        },
        onRestore = {
            ST.track("membership.restore_click", mapOf("membership_state" to ST.roleState))
            try {
                val n = syncOwned(onlyUnacknowledged = false)
                if (n > 0 && UserStore.user?.userRole?.isPaid == true) {
                    ST.track("membership.restore_result", mapOf("result" to "restored"))
                    Toast.show("구입 내역을 복원했어요")
                } else {
                    ST.track("membership.restore_result", mapOf("result" to "none"))
                    Toast.show("복원할 구입 내역이 없어요")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ST.track("membership.restore_result", mapOf("result" to "fail", "fail_reason" to "http_${ST.status(e)}"))
                Toast.show(errorMessage(e, fallback = "구입 내역을 복원하지 못했어요."))
            }
        },
        onManage = { productId ->
            ST.track("membership.manage_click", mapOf("plan" to productId?.let { membershipPlanKey(it) }))
            val intent = Intent(Intent.ACTION_VIEW, membershipPlayManageUri(productId, context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
        onReload = {
            ST.track("membership.retry_click", mapOf("fail_reason" to "products_empty"))
            loadProducts()
        },
    )
}

/**
 * 화면 (결제 없이도 그려지게 값만 받아요)
 * iOS 의 appleProductIDs(애플 구독 중인 상품)는 Android 에 없어서 뺐어요.
 */
@Composable
fun MembershipContent(
    role: UserRole,
    plans: List<MembershipPlanOption>,
    isLoadingProducts: Boolean,
    onPurchase: suspend (String) -> Boolean,
    onRestore: suspend () -> Unit,
    onReload: suspend () -> Unit,
    /** 지금 Pro 근거인 Google Play 구독 (없으면 웹 · 아이폰 · 운영진 Pro) */
    playSubscription: PlayBillingAPI.Status.Subscription? = null,
    onManage: (String?) -> Unit = {},
) {
    PushedScreen(title = "멤버십") {
        ScreenColumn(spacing = 24.dp) {
            if (playSubscription != null) {
                MembershipPlayCard(sub = playSubscription, onManage = onManage)
            } else if (role.isPaid) {
                MembershipOtherPremiumCard(role = role)
            } else {
                MembershipPlanPicker(plans = plans, isLoading = isLoadingProducts, onPurchase = onPurchase, onReload = onReload)
            }
            MembershipFooter(onRestore = onRestore)
        }
    }
}

/** BM(2026-10): 목표 · 루틴 · Task · 회고 만들기와 알림은 Free 도 제한 없이 써요. Pro 는 AI 를 더 많이 + 광고 없이 (웹과 같은 문구) */
private val membershipBenefits = listOf(
    "AI 목표 만들기 주 10개 · 월 20개 (Free: 주 2개 · 월 8개)",
    "AI 계획 초안 주 10번 · 월 20번 (Free: 주 2번 · 월 8번)",
    "AI로 목표 고치기 · AI로 일정 조정 · 브레인 덤프 AI 정리 하루 30번 (Free: 각 3번)",
    "광고 없이",
    "웹과 앱 어디서든 같은 Pro",
)

/** 요금제 고르기 + 구독 (웹 PlanPicker) */
@Composable
private fun MembershipPlanPicker(
    plans: List<MembershipPlanOption>,
    isLoading: Boolean,
    onPurchase: suspend (String) -> Boolean,
    onReload: suspend () -> Unit,
) {
    var selectedId by remember { mutableStateOf(ProductIdentifier.premiumYearly.rawValue) }
    var agreed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val chosen = plans.firstOrNull { it.id == selectedId } ?: plans.firstOrNull()

    val preview = androidx.compose.ui.platform.LocalInspectionMode.current

    fun start() {
        val c = chosen ?: return
        if (busy) return
        ST.track("membership.subscribe_click", mapOf("plan" to membershipPlanKey(c.id)), preview)
        busy = true
        scope.launch {
            onPurchase(c.id)
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
        Text("나를 Pro", style = NType.h4, color = NC.ink)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (b in membershipBenefits) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SfImage("checkmark", size = 14.dp, tint = NC.textBrand)
                    Text(b, style = pt(14), color = NC.ink2)
                }
            }
        }

        // 요금제
        Box(Modifier.padding(top = 20.dp)) {
            if (plans.isEmpty()) {
                if (isLoading) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        CircularProgressIndicator(color = NC.ink3, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        Text("상품 정보를 불러오는 중...", style = pt(13), color = NC.ink3)
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    ) {
                        Text("멤버십 정보를 불러오지 못했어요", style = pt(15, PTWeight.bold), color = NC.ink)
                        NButton("다시 시도", size = NButtonSize.sm) { scope.launch { onReload() } }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (p in plans) {
                        MembershipPlanCard(plan = p, selected = chosen?.id == p.id) {
                            if (chosen?.id != p.id) ST.track("membership.plan_select", mapOf("plan" to membershipPlanKey(p.id)), preview)
                            selectedId = p.id
                        }
                    }
                }
            }
        }

        // 자동 결제 동의
        val agreeText = run {
            val head = if (chosen != null) "${chosen.displayPrice}이 ${if (chosen.months == 12) "매년" else "매달"} " else ""
            // 웹 문구 그대로 (iOS 는 "아이폰 설정 → Apple ID → 구독" 으로 바꿨던 부분)
            // Android 는 Google Play 정기 결제라 해지하는 곳이 달라요
            head + "Google Play로 자동으로 결제되는 데 동의해요. 결제 전까지 Google Play → 결제 및 정기 결제 → 정기 결제에서 언제든 해지할 수 있고, 해지해도 남은 기간까지 Pro를 쓸 수 있어요."
        }
        val boxShape = RoundedCornerShape(4.dp)
        Row(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .clickable(role = Role.Checkbox) {
                    agreed = !agreed
                    ST.track("membership.agree_toggle", mapOf("checked" to agreed, "plan" to chosen?.let { membershipPlanKey(it.id) }), preview)
                }
                .semantics { selected = agreed },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
                    .background(if (agreed) NC.brand else NC.surface, boxShape)
                    .border(if (agreed) 1.5.dp else 1.dp, if (agreed) NC.borderFocus else NC.borderSubtle, boxShape),
                contentAlignment = Alignment.Center,
            ) {
                if (agreed) SfImage("checkmark", size = 11.dp, tint = Color.White)
            }
            Text(agreeText, style = pt(13).copy(lineHeight = 19.sp), color = NC.ink2)
        }

        NButton(
            "구독하기",
            variant = NButtonVariant.brand,
            size = NButtonSize.lg,
            icon = "creditcard",
            loading = busy,
            fullWidth = true,
            enabled = agreed && chosen != null,
            modifier = Modifier.padding(top = 16.dp),
        ) { start() }

        // Android 결제 안내
        Text(
            "Google Play로 결제해요. 웹 · 아이폰에서 구독한 Pro도 같은 계정이면 그대로 쓸 수 있어요.",
            style = pt(12),
            color = NC.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

@Composable
private fun MembershipPlanCard(plan: MembershipPlanOption, selected: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(Radius.xxl)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) NC.brandSoft else NC.surface, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) NC.borderFocus else NC.borderSubtle, shape)
            .clickable(role = Role.RadioButton) { onTap() }
            .semantics { this.selected = selected }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.label, style = pt(15, PTWeight.bold), color = NC.ink)
            if (plan.savePercent > 0) NBadge("${plan.savePercent}% 할인", tone = BadgeTone.success)
        }
        val ink = NC.ink
        val ink3 = NC.ink3
        Text(
            buildAnnotatedString {
                withStyle(ptMono(22, PTWeight.bold).toSpanStyle().copy(color = ink)) { append(plan.displayPrice) }
                withStyle(pt(13, PTWeight.medium).toSpanStyle().copy(color = ink3)) {
                    append(" / ${if (plan.months == 12) "년" else "월"}")
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        val per = plan.perMonthPrice
        if (per != null) {
            Text("월 $per 꼴", style = pt(12), color = NC.ink3, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Google Play 로 구독 중 — 해지 · 결제 수단 변경은 Play 정기 결제 화면에서 (iOS '구독 관리' 버튼 자리) */
@Composable
private fun MembershipPlayCard(sub: PlayBillingAPI.Status.Subscription, onManage: (String?) -> Unit) {
    val until = sub.expiryTime?.take(10)?.split("-")?.let { if (it.size == 3) "${it[0]}년 ${it[1].toInt()}월 ${it[2].toInt()}일" else null }
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "나를 Pro ${if (sub.productId == ProductIdentifier.premiumYearly.rawValue) "연간" else "월간"} · Google Play",
            style = pt(17, PTWeight.bold),
            color = NC.ink,
        )
        Text(
            when {
                sub.inGracePeriod -> "결제에 실패했어요. Google Play에서 결제 수단을 확인해 주세요."
                sub.canceled -> "${until ?: ""}까지 이용하고 그 뒤로는 결제되지 않아요 (해지 예정)"
                else -> "${until ?: ""}까지 이용 · 자동으로 갱신돼요"
            },
            style = pt(13),
            color = if (sub.inGracePeriod) NC.danger else NC.ink3,
        )
        if (sub.testPurchase) Text("테스트 결제예요 (실제 돈이 나가지 않아요)", style = pt(12), color = NC.ink3)
        NButton(
            "구독 관리",
            variant = NButtonVariant.secondary,
            fullWidth = true,
            modifier = Modifier.padding(top = 10.dp),
        ) { onManage(sub.productId) }
    }
}

/** 웹 · 아이폰 등 다른 경로(또는 운영진)로 Pro (웹 otherPremium 카드를 반대로) */
@Composable
private fun MembershipOtherPremiumCard(role: UserRole) {
    Row(
        Modifier.fillMaxWidth().card().padding(20.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(NC.brandSoft, RoundedCornerShape(Radius.xl)),
            contentAlignment = Alignment.Center,
        ) { SfImage("globe", size = 20.dp, tint = NC.textBrand) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (role == UserRole.admin) "운영진 계정이라 Pro를 이용 중이에요" else "웹 또는 아이폰 앱에서 Pro를 이용 중이에요",
                style = pt(15, PTWeight.bold),
                color = NC.ink,
            )
            Text(
                "구독 관리 · 해지는 구독한 곳(웹의 설정 → 멤버십 또는 아이폰 구독 관리)에서 할 수 있어요. Android 앱에서도 같은 Pro 혜택을 그대로 쓸 수 있어요.",
                style = pt(13).copy(lineHeight = 19.sp),
                color = NC.ink3,
            )
        }
    }
}

/** 구입 내역 복원 · 안내 · 약관 */
@Composable
private fun MembershipFooter(onRestore: suspend () -> Unit) {
    var restoring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // iOS 의 "환불요청시 서비스 이용 내역이 Apple에 제공" 문구는 애플 결제 전용이라 뺐어요
        Text("언제든 해지할 수 있어요.", style = pt(12), color = NC.ink3, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (t in listOf(TermsType.service, TermsType.privacy)) {
                Text(
                    t.detailTitle,
                    style = pt(12, PTWeight.medium).copy(textDecoration = TextDecoration.Underline),
                    color = NC.ink2,
                    modifier = Modifier.clickable(role = Role.Button) {
                        ST.track("terms.item_click", mapOf("terms_type" to t.rawValue, "entry_point" to "membership"))
                        AppRouter.push(AppRoute.Settings(SettingsRoute.TermsDetail(t.rawValue)))
                    },
                )
            }
        }
        NButton("구입 내역 복원하기", variant = NButtonVariant.ghost, size = NButtonSize.sm, loading = restoring) {
            restoring = true
            scope.launch {
                onRestore()
                restoring = false
            }
        }
    }
}

// MARK: - 미리보기

private val membershipPreviewPlans = listOf(
    MembershipPlanOption(id = ProductIdentifier.premiumMonthly.rawValue, label = "월간", months = 1,
        displayPrice = "₩3,900", perMonthPrice = null, savePercent = 0),
    MembershipPlanOption(id = ProductIdentifier.premiumYearly.rawValue, label = "연간", months = 12,
        displayPrice = "₩39,000", perMonthPrice = "₩3,250", savePercent = 17),
)

@Preview(showBackground = true, heightDp = 1100, name = "무료 — 요금제 고르기")
@Composable
private fun MembershipFreePreview() {
    NaleulTheme(dark = false) {
        MembershipContent(role = UserRole.free, plans = membershipPreviewPlans, isLoadingProducts = false,
            onPurchase = { false }, onRestore = {}, onReload = {})
    }
}

@Preview(showBackground = true, heightDp = 700, name = "다른 곳에서 구독 중")
@Composable
private fun MembershipProPreview() {
    NaleulTheme(dark = false) {
        MembershipContent(role = UserRole.pro, plans = emptyList(), isLoadingProducts = false,
            onPurchase = { false }, onRestore = {}, onReload = {})
    }
}
