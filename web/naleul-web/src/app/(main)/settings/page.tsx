import type { Metadata } from 'next'
import { cookies } from 'next/headers'
import { CalendarPlus, FileText } from 'lucide-react'
import { BarChart3, Bell, ChevronRight, Clock, Crown, type IconComponent } from '@/components/icons'
import { PageHeader } from '@/components/layout/PageHeader'
import { Card } from '@/components/ui/Card'
import { LogoutButton } from '@/features/account/ui/LogoutButton'
import { ProfileSummaryCard } from '@/features/account/ui/ProfileSummaryCard'
import { isPremiumRole, type SettingsDestination } from '@/features/account/settingsRole'
import { SettingsMenuLink, SettingsPageview } from '@/features/account/ui/SettingsAnalytics'
import { ThemePicker } from '@/features/theme/ThemePicker'
import { UsageCard } from '@/features/usage/ui/UsageCard'
import { AutoCarryToggle } from '@/features/timetable/ui/AutoCarryToggle'
import { AdminPlanSwitch } from '@/features/admin-dashboard/ui/AdminPlanSwitch'
import { COOKIE, type UserRole } from '@/lib/server/session'

export const metadata: Metadata = { title: '설정' }

const ITEMS: {
  href: string
  destination: SettingsDestination
  icon: IconComponent
  title: string
  description: string
}[] = [
  {
    href: '/settings/membership',
    destination: 'membership',
    icon: Crown,
    title: '멤버십',
    description: 'Pro 구독 · 결제 카드 · 결제 내역',
  },
  {
    href: '/settings/life-pattern',
    destination: 'life_pattern',
    icon: Clock,
    title: '기본 생활 패턴',
    description: '수면·점심·저녁·이동시간처럼 매주 반복되는 고정 시간',
  },
  {
    href: '/settings/calendar-import',
    destination: 'calendar_import',
    icon: CalendarPlus,
    title: '외부 캘린더 가져오기',
    description: '구글 · 노션 · 아이폰 캘린더 일정을 골라서 Task로 가져와요',
  },
  {
    href: '/settings/notifications',
    destination: 'notifications',
    icon: Bell,
    title: '알림 설정',
    description: 'Task·미션 푸시 알림을 종류별로 켜고 꺼요 (나를 앱에 적용)',
  },
  {
    href: '/settings/terms',
    destination: 'terms',
    icon: FileText,
    title: '약관 동의',
    description: '서비스 이용약관 · 개인정보 수집 및 이용 · 마케팅 정보 수신',
  },
]

const rowClass =
  'hover:bg-subtle/60 first:rounded-t-card last:rounded-b-card flex w-full items-center gap-3 px-5 py-4 text-left'

export default async function SettingsPage() {
  const store = await cookies()
  const user = {
    name: store.get(COOKIE.userName)?.value || '나를 사용자',
    role: store.get(COOKIE.userRole)?.value as UserRole | undefined,
    adminViewPlan: store.get(COOKIE.adminViewPlan)?.value === 'FREE' ? ('FREE' as const) : ('PRO' as const),
  }
  const isAdmin = user.role === 'ADMIN'
  const isPremium = isPremiumRole(user.role, user.adminViewPlan)

  return (
    <>
      <SettingsPageview isPremium={isPremium} />
      <PageHeader title="설정" />

      <div className="mt-6 max-w-3xl space-y-6">
        <ProfileSummaryCard user={user} />

        <Card className="divide-line divide-y">
          {ITEMS.map(({ href, destination, icon: Icon, title, description }) => (
            <SettingsMenuLink
              key={href}
              href={href}
              destination={destination}
              isPremium={isPremium}
              className={rowClass}
            >
              <span className="bg-brand-soft text-text-brand grid size-10 shrink-0 place-items-center rounded-xl">
                <Icon className="size-5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-[15px] font-bold">{title}</p>
                <p className="text-ink-3 mt-0.5 text-[13px]">{description}</p>
              </div>
              <ChevronRight className="text-ink-4 size-4" />
            </SettingsMenuLink>
          ))}
        </Card>

        {/* 캘린더 — 못 한 일 자동 이월 (2026-10, 캘린더 머리글에도 있어요) */}
        <Card>
          <AutoCarryToggle variant="card" />
        </Card>

        <Card>
          <ThemePicker />
        </Card>

        {/* 운영진(ADMIN)만 — 운영 대시보드 · 플랜 바꿔 보기 (2026-10-24). 주소를 알아도 백엔드가 403 으로 막아요 */}
        {isAdmin && (
          <Card className="divide-line divide-y">
            <AdminPlanSwitch initial={user.adminViewPlan} />
            <SettingsMenuLink
              href="/settings/dashboard"
              destination="dashboard"
              isPremium={isPremium}
              className={rowClass}
            >
              <span className="bg-warning-soft text-warning-ink grid size-10 shrink-0 place-items-center rounded-xl">
                <BarChart3 className="size-5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-[15px] font-bold">
                  운영 대시보드 <span className="text-ink-3 ml-1 text-[11px] font-semibold">운영진</span>
                </p>
                <p className="text-ink-3 mt-0.5 text-[13px]">
                  목표 달성률 · Task/루틴 실천률 · AI 비용 · 매출 · 서버 비용
                </p>
              </div>
              <ChevronRight className="text-ink-4 size-4" />
            </SettingsMenuLink>
          </Card>
        )}

        {/* 하루 횟수가 정해진 기능 (AI 목표 만들기 · AI 계획 초안 · Task 추가) */}
        <UsageCard />

        <Card className="divide-line divide-y">
          <LogoutButton className={rowClass} />
          <SettingsMenuLink href="/settings/withdraw" destination="withdraw" isPremium={isPremium} className={rowClass}>
            <span className="text-danger flex-1 text-[15px] font-semibold">탈퇴하기</span>
          </SettingsMenuLink>
        </Card>
      </div>
    </>
  )
}
