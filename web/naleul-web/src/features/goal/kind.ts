/**
 * 목표 카테고리 2단계 (명세 6.A.1.2) — 백엔드 GoalType / GoalSubType 과 같은 값.
 *
 * 1단계: 건강 / 학습·자기개발 / 생활습관 / 기타
 * 2단계: 건강(운동·다이어트·물 마시기·금주·금연·기타) …
 * "기타"는 이름을 직접 적어요 (공백 포함 최대 10자).
 *
 * AI 목표 설계는 고른 2단계에 맞는 가이드로 계획을 짜요 (운동이면 세션별 종목·세트, 다이어트면 운동+식단 원칙…).
 */
export type GoalType = 'HEALTH' | 'LEARNING' | 'HABIT' | 'ETC' | 'WORK'
export type GoalSubType =
  | 'EXERCISE'
  | 'DIET'
  | 'WATER'
  | 'NO_ALCOHOL'
  | 'NO_SMOKING'
  | 'HEALTH_OTHER'
  | 'LANGUAGE'
  | 'CERTIFICATE'
  | 'READING'
  | 'CODING'
  | 'LEARNING_OTHER'
  | 'WAKE_UP'
  | 'SLEEP'
  | 'MEDITATION'
  | 'CLEANING'
  | 'JOURNAL'
  | 'SAVING'
  | 'OTHER'
  | 'COMPANY_WORK'
  | 'SIDE_PROJECT'

export interface GoalKindValue {
  goalType: GoalType
  goalSubType: GoalSubType
  /** "기타"일 때만 (1~10자) */
  goalKindLabel?: string
}

export const KIND_LABEL_MAX = 10

interface SubOption {
  value: GoalSubType
  label: string
  emoji: string
  custom?: boolean
  /** 시작 화면 예시 문장 */
  examples: string[]
}

export const GOAL_TYPES: { value: GoalType; label: string; emoji: string; hint: string; subs: SubOption[] }[] = [
  {
    value: 'HEALTH',
    label: '건강',
    emoji: '💪',
    hint: '운동 · 다이어트 · 금연',
    subs: [
      {
        value: 'EXERCISE',
        label: '운동',
        emoji: '🏋️',
        examples: ['주 3회 헬스로 근력 키우기', '3개월 안에 5km 30분 안에 뛰기'],
      },
      { value: 'DIET', label: '다이어트', emoji: '🥗', examples: ['2주 동안 3kg 감량하기', '3개월에 8kg 빼기'] },
      { value: 'WATER', label: '물 마시기', emoji: '💧', examples: ['하루 물 2L 마시기'] },
      { value: 'NO_ALCOHOL', label: '금주', emoji: '🚱', examples: ['한 달 동안 술 끊기', '술은 주 1회 이하로'] },
      {
        value: 'NO_SMOKING',
        label: '금연',
        emoji: '🚭',
        examples: ['다음 달부터 완전히 금연하기', '하루 10개비에서 0개비로'],
      },
      { value: 'HEALTH_OTHER', label: '기타', emoji: '✏️', custom: true, examples: [] },
    ],
  },
  {
    value: 'LEARNING',
    label: '학습·자기개발',
    emoji: '📚',
    hint: '언어 · 자격증 · 독서 · 코딩',
    subs: [
      {
        value: 'LANGUAGE',
        label: '언어',
        emoji: '🗣️',
        examples: ['토익 900점 받기', '6개월 안에 일본어로 일상 회화하기'],
      },
      { value: 'CERTIFICATE', label: '자격증', emoji: '📜', examples: ['정보처리기사 필기 합격하기', '컴활 1급 따기'] },
      { value: 'READING', label: '독서', emoji: '📖', examples: ['올해 책 12권 읽기', '한 달에 2권 읽기'] },
      {
        value: 'CODING',
        label: '코딩',
        emoji: '💻',
        examples: ['백준 골드 달성하기', '3개월 안에 포트폴리오 앱 완성하기'],
      },
      { value: 'LEARNING_OTHER', label: '기타', emoji: '✏️', custom: true, examples: [] },
    ],
  },
  {
    value: 'HABIT',
    label: '생활습관',
    emoji: '🌱',
    hint: '기상 · 취침 · 명상 · 청소 · 일기',
    subs: [
      { value: 'WAKE_UP', label: '기상', emoji: '🌅', examples: ['매일 아침 6시 30분에 일어나기'] },
      { value: 'SLEEP', label: '취침', emoji: '🌙', examples: ['자정 전에 잠들기'] },
      { value: 'MEDITATION', label: '명상', emoji: '🧘', examples: ['매일 10분 명상하기'] },
      { value: 'CLEANING', label: '청소', emoji: '🧹', examples: ['방을 매일 깔끔하게 유지하기'] },
      { value: 'JOURNAL', label: '일기', emoji: '📝', examples: ['매일 자기 전 일기 쓰기'] },
    ],
  },
  {
    value: 'ETC',
    label: '기타',
    emoji: '✨',
    hint: '돈 모으기 · 직접 적기',
    subs: [
      {
        value: 'SAVING',
        label: '돈 모으기',
        emoji: '💰',
        examples: ['6개월 안에 300만 원 모으기', '한 달 식비 40만 원 이하로'],
      },
      { value: 'OTHER', label: '기타', emoji: '✏️', custom: true, examples: [] },
    ],
  },
]

/**
 * AI 목표형 만들기에서 고를 수 있는 카테고리 (2026-10).
 * 세수 · 물 마시기 · 기상 같은 "기본 생활 패턴"은 생활 습관(HABIT)으로 따로 체크하니 빼고,
 * 이뤄야 하고 수치로 잴 수 있는 것만 남겨요. 개수가 적어서 1·2단계 없이 칩 한 줄로 보여줘요.
 * 2026-10-24: 금주 · 금연 · 코딩 빼고 돈 모으기 추가 (직접 만드는 목표에는 그대로 있어요)
 */
export const AI_KIND_CHIPS: { goalType: GoalType; goalSubType: GoalSubType }[] = [
  { goalType: 'HEALTH', goalSubType: 'EXERCISE' },
  { goalType: 'HEALTH', goalSubType: 'DIET' },
  { goalType: 'LEARNING', goalSubType: 'LANGUAGE' },
  { goalType: 'LEARNING', goalSubType: 'CERTIFICATE' },
  { goalType: 'LEARNING', goalSubType: 'READING' },
  { goalType: 'ETC', goalSubType: 'SAVING' },
  { goalType: 'ETC', goalSubType: 'OTHER' },
]

/**
 * "이 목표를 위해 꼭 하고 싶은 일" 추천 칩 (AI 목표 시작 화면).
 * 고른 일은 계획 AI 가 반드시 루틴으로 넣어요. 기타는 추천 없이 직접 적어요.
 */
export const MUST_DO_SUGGESTIONS: Partial<Record<GoalSubType, string[]>> = {
  EXERCISE: ['헬스', '러닝', '홈트레이닝', '스트레칭', '수영'],
  DIET: ['헬스', '식단 기록', '유산소', '체중 기록', '간헐적 단식'],
  WATER: ['물 마시기 체크', '텀블러 들고 다니기'],
  NO_ALCOHOL: ['음주 기록', '술자리 대신 운동'],
  NO_SMOKING: ['흡연 욕구 기록', '금연 클리닉'],
  LANGUAGE: ['단어 암기', '듣기 연습', '회화 연습', '모의고사'],
  CERTIFICATE: ['이론 공부', '기출 풀이', '오답 정리', '모의고사'],
  READING: ['매일 읽기', '독서 기록'],
  CODING: ['알고리즘 문제 풀이', '강의 듣기', '프로젝트 개발'],
  SAVING: ['가계부 쓰기', '자동 이체', '무지출 데이', '지출 점검'],
  WAKE_UP: ['기상 인증', '아침 스트레칭'],
  SLEEP: ['취침 준비 루틴', '자기 전 휴대폰 끄기'],
  MEDITATION: ['아침 명상', '호흡 연습'],
  CLEANING: ['정리 정돈', '설거지 바로 하기'],
  JOURNAL: ['일기 쓰기', '감사 일기'],
}

/**
 * 업무형(기록형) 목표 전용 종류 — 생활형 카테고리 고르기(GOAL_TYPES)에는 넣지 않아요.
 * 업무형은 AI 설계 없이 만들고, 완료한 Task 로 패턴·업무 일지를 쌓아요.
 */
export const WORK_TYPE: (typeof GOAL_TYPES)[number] = {
  value: 'WORK',
  label: '업무',
  emoji: '💼',
  hint: '회사 업무 · 사이드 프로젝트',
  subs: [
    { value: 'COMPANY_WORK', label: '회사 업무', emoji: '💼', examples: ['OO팀 업무', '나를 업무'] },
    { value: 'SIDE_PROJECT', label: '사이드 프로젝트', emoji: '🚀', examples: ['포트폴리오 앱', '블로그 운영'] },
  ],
}

const ALL_TYPES = [...GOAL_TYPES, WORK_TYPE]

export const typeOption = (t: GoalType) => ALL_TYPES.find((o) => o.value === t)!
export const subOption = (s: GoalSubType) => ALL_TYPES.flatMap((t) => t.subs).find((o) => o.value === s)!

/** 입력이 끝났는지 (기타면 이름 1~10자) */
export function isKindComplete(v: GoalKindValue | null): v is GoalKindValue {
  if (!v) return false
  if (!subOption(v.goalSubType)?.custom) return true
  const label = v.goalKindLabel?.trim() ?? ''
  return label.length > 0 && label.length <= KIND_LABEL_MAX
}

/** "건강 · 다이어트", "기타 · 악기 연습" */
export function kindName(v: Pick<GoalKindValue, 'goalType' | 'goalSubType' | 'goalKindLabel'> | null | undefined) {
  if (!v?.goalType) return null
  const type = typeOption(v.goalType)
  const sub = v.goalSubType ? subOption(v.goalSubType) : null
  const subLabel = sub?.custom ? v.goalKindLabel?.trim() || sub.label : sub?.label
  return subLabel ? `${type.label} · ${subLabel}` : type.label
}

/** API 요청 바디용 (기타가 아니면 이름은 보내지 않아요) */
export function kindBody(v: GoalKindValue | null) {
  if (!v) return {}
  return {
    goalType: v.goalType,
    goalSubType: v.goalSubType,
    ...(subOption(v.goalSubType)?.custom ? { goalKindLabel: v.goalKindLabel?.trim() } : {}),
  }
}

/**
 * 목표 이름으로 카테고리를 짐작해요 (AI 를 부르지 않는 키워드 규칙 — 비용 0원, 2026-10).
 * 예전 목표를 AI 로 다시 설계할 때 시작 화면에 미리 골라 두는 용도라 틀려도 사용자가 바로 바꿀 수 있어요.
 * 못 찾으면 "기타 · (목표 이름 앞 10자)".
 */
const KIND_KEYWORDS: [GoalSubType, RegExp][] = [
  ['LANGUAGE', /토익|토플|오픽|opic|toeic|ielts|jlpt|hsk|영어|일본어|중국어|회화|스피킹|speaking|어학/i],
  ['CERTIFICATE', /자격증|기사|산업기사|컴활|한국사|시험|합격|면허|필기|실기/],
  ['CODING', /코딩|개발|프로그래밍|알고리즘|코테|코딩테스트|백준|프로젝트|앱 만들기/],
  ['READING', /독서|책|읽기|완독/],
  ['DIET', /다이어트|감량|체중|몸무게|kg|살 ?빼|식단/i],
  ['EXERCISE', /운동|헬스|러닝|달리기|조깅|마라톤|요가|필라테스|근력|수영|등산|자전거|홈트|크로스핏|스트레칭/],
  ['WATER', /물 ?마시|수분/],
  ['NO_ALCOHOL', /금주|술/],
  ['NO_SMOKING', /금연|담배/],
  ['WAKE_UP', /기상|일찍 일어|미라클 ?모닝|아침형/],
  ['SLEEP', /수면|취침|일찍 자|잠/],
  ['MEDITATION', /명상|마음챙김/],
  ['CLEANING', /청소|정리정돈|방 정리/],
  ['JOURNAL', /일기|다이어리|감사 ?일기/],
  ['SAVING', /저축|적금|저금|돈 ?모으|재테크|절약/],
]

export function guessKind(name: string): GoalKindValue {
  const text = name.trim()
  const hit = KIND_KEYWORDS.find(([, re]) => re.test(text))
  if (hit) {
    const goalType = GOAL_TYPES.find((t) => t.subs.some((s) => s.value === hit[0]))!.value
    return { goalType, goalSubType: hit[0] }
  }
  return { goalType: 'ETC', goalSubType: 'OTHER', goalKindLabel: text.slice(0, KIND_LABEL_MAX) || '기타' }
}

/** 목표에 저장된 카테고리 → 없으면 이름으로 짐작 (업무형 · 생활 습관 그릇은 목표형 카테고리가 아니라 짐작) */
export function kindOfGoal(goal: {
  goalCategoryName: string
  goalType?: GoalType | null
  goalSubType?: GoalSubType | null
  goalKindLabel?: string | null
}): GoalKindValue {
  if (
    goal.goalType &&
    goal.goalSubType &&
    goal.goalType !== 'WORK' &&
    GOAL_TYPES.some((t) => t.value === goal.goalType)
  ) {
    const value: GoalKindValue = {
      goalType: goal.goalType,
      goalSubType: goal.goalSubType,
      ...(goal.goalKindLabel ? { goalKindLabel: goal.goalKindLabel } : {}),
    }
    if (isKindComplete(value)) return value
  }
  return guessKind(goal.goalCategoryName)
}
