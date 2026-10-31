// feature/settings/terms/TermsContent.kt
//
// iOS Features/Settings/Terms/TermsContent.swift
// 웹 원본: features/terms/content.ts
// 약관 본문 — 예전 iOS TermsDetailView.swift 의 문구를 웹이 그대로 옮긴 것을 다시 그대로 가져왔어요.
// ⚠️ 약관을 고칠 때는 iOS · Android · 웹 세 곳을 같이 바꿔야 해요.

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

/** 약관 종류 (웹 /settings/terms/service | privacy | marketing). SettingsRoute.TermsDetail(String) 의 값 */
enum class TermsType {
    service, privacy, marketing;

    val rawValue: String get() = name
    val id: String get() = rawValue

    /** 목록에 보이는 이름 */
    val listTitle: String
        get() = when (this) {
            service -> "서비스 이용약관 동의(필수)"
            privacy -> "개인 정보 수집 및 이용 동의(필수)"
            marketing -> "마케팅 정보 수신 동의(선택)"
        }

    /** 상세 화면 제목 */
    val detailTitle: String
        get() = when (this) {
            service -> "서비스 이용약관"
            privacy -> "개인정보 수집 및 이용"
            marketing -> "마케팅 정보 수신"
        }

    val body: String
        get() = when (this) {
            service -> TermsText.service
            privacy -> TermsText.privacy
            marketing -> TermsText.marketingIntro
        }

    companion object {
        fun fromRaw(raw: String?): TermsType? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** 마케팅 동의 상세의 "수집 정보" 표 한 줄 */
data class TermsInfoRow(
    val label: String,
    val content: String,
) {
    companion object {}
}

object TermsText {
    val marketingRows: List<TermsInfoRow> = listOf(
        TermsInfoRow(label = "이용목적", content = "신기능 안내, 이벤트 정보, 서비스 관련 공지사항, 프로모션 및 마케팅"),
        TermsInfoRow(label = "이용 정보", content = "사용자 성명, 사용자 활동 통계(목표·루틴 달성 통계 등), 앱 내 알림"),
        TermsInfoRow(label = "제공 수단", content = "앱 내 알림, 이메일(해당하는 경우), 푸시 알림"),
        TermsInfoRow(label = "보유 기간", content = "동의 철회 시까지"),
    )

    val privacy: String = """
    나를 서비스는 소셜 로그인을 통해 아래의 개인정보를 수집합니다.
    아래 정보의 수집 및 이용에 동의하셔야 서비스 가입 및 이용이 가능합니다.

    선택한 로그인 서비스별 수집 정보

    ① 카카오(카카오 로그인)로 가입하는 경우
    필수 수집 정보:
    - 성명: 회원 식별, 서비스 제공 (회원 탈퇴 시까지 보유)
    - 프로필 이미지: 프로필 표시 (회원 탈퇴 시까지 보유)
    - 소셜 고유 아이디: 중복 가입 방지, 회원 관리 (회원 탈퇴 시 즉시 파기)
    - 서비스 이용 기록: 서비스 개선, 통계 분석 (회원 탈퇴 시까지 보유)

    선택 수집 정보:
    - 이메일 주소*: 서비스 제공, 연락처 (회원 탈퇴 시까지 보유)
      * 카카오 비즈니스 앱 인증 후 수집 가능합니다.
      * 현재 나를은 카카오 비즈니스 앱 미인증 상태이므로 이메일 수집이 불가능합니다.
      * 추후 비즈니스 앱 인증 후 이메일 수집을 추가할 계획입니다.

    ② 애플(Sign in with Apple)로 가입하는 경우
    필수 수집 정보:
    - 성명: 회원 식별, 서비스 제공 (회원 탈퇴 시까지 보유)
    - 소셜 고유 아이디: 중복 가입 방지, 회원 관리 (회원 탈퇴 시 즉시 파기)
    - 이메일 주소*: 서비스 제공, 연락처 (회원 탈퇴 시까지 보유)
    - 서비스 이용 기록: 서비스 개선, 통계 분석 (회원 탈퇴 시까지 보유)

    주의사항:
    * 애플 로그인은 프로필 이미지를 제공하지 않습니다.
    * 사용자가 이메일을 비공개로 설정한 경우, 애플의 Privacy Relay 서비스를 통해
      임시 이메일(xxxxxXXXX@privaterelay.appleid.com)이 제공됩니다.
    * 비공개 이메일은 사용자가 애플의 개인정보 보호 기능을 사용했음을 의미합니다.

    ③ 구글 캘린더 일정 가져오기 (선택)
    "외부 캘린더 가져오기"에서 구글 계정을 직접 연결한 경우에만 아래 정보를 이용합니다. 연결하지 않으면 구글 정보는 수집하지 않습니다.
    - 요청 권한: 구글 캘린더 목록 보기(https://www.googleapis.com/auth/calendar.calendarlist.readonly)와 일정 보기(https://www.googleapis.com/auth/calendar.events.readonly), 두 가지 읽기 전용 권한만 요청합니다. 나를은 구글 캘린더에 일정을 만들거나 고치거나 지우지 않습니다.
    - 읽는 정보: 캘린더 목록(이름, 색상), 사용자가 고른 캘린더·기간의 일정(제목, 시작·종료 시각, 종일 여부, 내 참석 응답, 일정 고유 아이디)
    - 이용 목적: 사용자가 고른 일정을 나를의 Task로 만들고, 같은 일정을 두 번 가져오지 않기 위함
    - 저장 정보 및 보유 기간: 사용자가 가져오기로 고른 일정의 제목, 시작·종료 시각, 종일 여부, 일정 고유 아이디만 Task로 저장하며, 해당 Task를 지우거나 회원 탈퇴 시 파기합니다. 고르지 않은 일정과 캘린더 목록은 저장하지 않습니다.
    - 접근 토큰: 구글이 발급하는 1시간짜리 접근 토큰은 가져오는 동안 사용자의 브라우저·기기에서만 쓰고 나를 서버에 저장하지 않습니다. 나를은 구글 계정 비밀번호를 알 수 없습니다.
    - AI 기능: 사용자가 AI로 일정 옮기기 등 AI 기능을 쓰면, 가져온 일정으로 만든 Task의 제목·시간이 그 요청을 처리하기 위해 AI 서비스 제공사(Anthropic)에 전달될 수 있습니다. 이 정보는 요청 처리에만 쓰이며 AI 모델 학습에 쓰이지 않습니다.
    - 제3자 제공: 위 경우를 빼고 구글 캘린더에서 받은 정보를 다른 회사나 사용자에게 제공·판매하지 않으며, 광고에 쓰지 않습니다. 운영자는 사용자 동의, 보안 문제 대응, 법령 준수에 필요한 경우를 빼고 이 정보를 열람하지 않습니다.
    - 연결 해제: 구글 계정 설정(https://myaccount.google.com/permissions)에서 언제든 나를의 접근 권한을 해제할 수 있습니다.
    - 나를이 Google API로 받은 정보를 이용하고 다른 앱으로 전송하는 방식은 제한적 사용(Limited Use) 요건을 포함한 Google API 서비스 사용자 데이터 정책을 따릅니다.
      Naleul's use and transfer to any other app of information received from Google APIs will adhere to the Google API Services User Data Policy, including the Limited Use requirements.
      https://developers.google.com/terms/api-services-user-data-policy

    ④ 광고 (Pro 가 아닌 사용자)
    - 나를은 Pro 구독이 아닌 사용자(Free · 둘러보기)에게 Google이 제공하는 광고(웹: Google AdSense, 앱: Google AdMob)를 보여줍니다. Pro 구독 중에는 광고가 표시되지 않습니다.
    - Google 등 광고 제공업체는 쿠키 또는 기기의 광고 식별자(iOS 광고 식별자 · Android 광고 ID)를 사용해 이 서비스와 다른 웹사이트 · 앱 방문 기록을 바탕으로 광고를 게재하고 광고 성과를 측정할 수 있습니다.
    - 나를은 이름, 이메일, 목표, Task 등 회원 정보를 광고 제공업체에 제공하지 않습니다.
    - 맞춤 광고는 Google 광고 설정(https://adssettings.google.com)에서 끌 수 있고, https://www.aboutads.info 에서 다른 광고 업체의 맞춤 광고도 거부할 수 있습니다. 앱에서는 기기 설정에서 광고 식별자를 재설정하거나 맞춤 광고를 끌 수 있습니다.
    - Google이 광고 데이터를 사용하는 방식: https://policies.google.com/technologies/partner-sites
    
    회원 탈퇴 시 처리 방침:
    회원 탈퇴 시 위 개인정보는 즉시 파기되며, 별도의 보관 기간 없이 재가입이 가능합니다.
    단, 탈퇴 사유는 통계 목적으로 개인을 식별할 수 없는 형태로 별도 보관될 수 있습니다.

    목표 달성 통계 활용
    목표가 끝났을 때의 결과(달성 여부, 목표 기간, 루틴 실천률)는 개인을 식별할 수 없는 형태로 모아,
    같은 종류의 목표를 만드는 사용자에게 현실적인 계획을 추천하는 데 활용됩니다.
    같은 종류의 달성 사례가 30건 이상 모인 경우에만 중앙값 같은 통계로 쓰이며, 개별 기록은 다른 사용자에게 보이지 않습니다.

    개인정보 보호책임자
    - 성명: 황유림
    - 직책: 대표
    - 이메일: hske3602@naver.com

    본 방침은 2026년 10월 28일부터 시행합니다. (이전 방침: 2026년 9월 7일 시행)
    """.trimIndent()

    val service: String = """
    제1조 (목적)
    본 약관은 나를(이하 "서비스")이 제공하는 목표 관리 및 자기계발 지원 플랫폼 서비스를 이용함에 있어 회사와 사용자 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.

    제2조 (약관의 효력 및 변경)
    본 약관은 사용자가 서비스에 가입하고 서비스를 이용할 때부터 효력이 발생합니다.
    서비스 제공자는 필요한 경우 약관을 변경할 수 있으며, 변경된 약관은 공지한 날로부터 14일 후 효력이 발생합니다.
    사용자가 변경된 약관에 동의하지 않는 경우 서비스 이용을 중단하고 탈퇴할 수 있습니다.

    제3조 (서비스의 이용 및 제공 중단)
    서비스 제공자는 다음의 경우 사전 통지 없이 서비스 제공을 중단할 수 있습니다.
    - 컴퓨터 등 정보통신설비의 보수, 점검, 교체 및 고장
    - 서비스 이용의 폭증으로 정상적인 서비스 제공이 불가능한 경우
    - 법률에 의거한 강제 조치가 필요한 경우

    사용자는 다음의 경우 즉시 서비스 이용이 제한될 수 있습니다.
    - 타인의 개인정보를 도용하거나 부정하게 사용한 경우
    - 서비스를 이용하여 불법적인 활동을 하는 경우
    - 타인의 명예를 훼손하거나 권리를 침해하는 행위

    제4조 (사용자의 책임)
    사용자는 서비스 이용 시 관련 법령, 본 약관을 준수해야 합니다.
    사용자는 자신의 계정 정보(로그인 정보)에 대한 관리책임을 지며, 이로 인한 모든 결과에 대해 책임을 집니다.
    사용자가 게시한 콘텐츠(목표, 루틴, 회고, 미션 인증 등)는 자신의 책임하에 제공되는 것이며, 이로 인한 모든 책임은 사용자에게 있습니다.

    제5조 (회원 탈퇴)
    사용자는 언제든지 서비스 내 설정 메뉴를 통해 회원 탈퇴를 신청할 수 있습니다.
    회원 탈퇴 시 계정 및 저장된 모든 개인정보는 즉시 삭제되며, 삭제된 정보는 복구할 수 없습니다.
    탈퇴 후에는 별도의 제한 기간 없이 동일한 소셜 계정으로 재가입할 수 있습니다.

    제6조 (개인정보의 보호)
    서비스 제공자는 관련 법령에 따라 사용자의 개인정보를 보호하며, 개인정보 취급에 관한 상세한 내용은 별도의 개인정보처리방침에서 정합니다.

    제7조 (면책 조항)
    서비스 제공자는 다음의 경우에 대해 책임을 지지 않습니다.
    - 사용자의 귀책사유로 인한 서비스 이용의 중단 또는 손해
    - 사용자 간의 분쟁
    - 사용자가 게시한 정보의 신뢰성, 정확성 또는 유용성

    제8조 (유료 서비스와 환불)
    ① Pro 구독은 월간(3,300원) · 연간(33,000원) 정기결제 상품이며, 해지하지 않으면 결제 주기마다 같은 결제수단으로 자동 결제됩니다. 웹은 토스페이먼츠 · 토스페이, iOS 앱은 Apple App Store, Android 앱은 Google Play로 결제됩니다.
    ② 사용자는 언제든 구독을 해지할 수 있습니다(웹: 설정 → 멤버십 / iOS: 아이폰 설정 → Apple ID → 구독 / Android: Play 스토어 → 결제 및 정기 결제). 해지하면 다음 결제부터 청구되지 않으며, 이미 결제한 기간이 끝날 때까지 Pro를 이용할 수 있습니다.
    ③ 결제일로부터 7일 이내이고 결제 후 Pro 전용 기능을 이용하지 않은 경우, 문의 이메일로 요청하면 전액 환불합니다.
    ④ ③에 해당하지 않는 경우 월간 구독은 이미 결제한 달의 요금을 환불하지 않습니다. 연간 구독을 중도에 해지하고 환불을 요청하면, 결제일부터 요청일까지 이용한 개월 수(1개월 미만은 1개월)에 월간 요금(3,300원)을 곱한 금액을 뺀 나머지를 환불합니다.
    ⑤ Apple App Store 또는 Google Play로 결제한 구독의 환불은 각 스토어의 환불 정책과 절차를 따릅니다.
    ⑥ 미성년자가 법정대리인의 동의 없이 결제한 경우 본인 또는 법정대리인이 결제를 취소할 수 있습니다.
    ⑦ 환불은 원래 결제수단으로 진행하며, 요청을 확인한 날로부터 3영업일 이내에 처리합니다.

    부칙
    본 약관은 2026년 10월 28일부터 시행합니다. (이전 약관: 2026년 9월 7일 시행)

    사업자 정보
    상호: 나를 · 대표자: 황유림 · 사업자등록번호: 495-02-03664
    통신판매업 신고번호: 제2026-경기이천-0730호
    주소: 경기도 이천시 애련정로 180, 202동 1203호(증포동, 설봉2차 푸르지오)
    전화: 010-7557-9217 · 문의: hske3602@naver.com
    """.trimIndent()

    val marketingIntro: String = """
    나를은 서비스 개선, 신기능 안내, 이벤트 정보, 맞춤형 콘텐츠 추천 등의 마케팅 목적으로 개인정보를 이용할 수 있습니다.
    해당 동의는 서비스 이용에 필수가 아니며, 언제든 철회할 수 있습니다.

    주요 안내사항:
    - 본 동의는 선택 사항이며, 동의하지 않아도 기본 서비스 이용에는 영향이 없습니다.
    - 마케팅 정보 수신 동의는 언제든 앱 내 설정에서 철회할 수 있습니다.
    - 철회 후 즉시 마케팅 정보 수신이 중단됩니다.

    수집 정보
    """.trimIndent()
}
