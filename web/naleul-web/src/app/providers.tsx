'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { useState, type ReactNode } from 'react'
import { isApiError } from '@/lib/client/api'
import { Toaster } from '@/components/ui/Toaster'
import { ThemeSync } from '@/features/theme/theme'
import { LoginRequiredDialog } from '@/components/auth/LoginRequiredDialog'
import { LoginResultTracker } from '@/components/analytics/LoginResultTracker'

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // 4xx 는 다시 시도해도 결과가 같으니 재시도하지 않아요. 네트워크/5xx 만 1번.
        retry: (count, error) => {
          if (isApiError(error) && error.httpStatus >= 400 && error.httpStatus < 500) return false
          return count < 1
        },
      },
      mutations: { retry: false },
    },
  })
}

export function Providers({ children }: { children: ReactNode }) {
  // useState 로 만들어야 리렌더링마다 QueryClient 가 새로 생기지 않아요.
  const [client] = useState(makeQueryClient)

  return (
    <QueryClientProvider client={client}>
      {children}
      <Toaster />
      {/* 로그인 모달은 여기 하나만 — 사이드바가 없는 목표 만들기 화면에서도 떠야 해요 */}
      <LoginRequiredDialog />
      <ThemeSync />
      {/* 카카오 · Apple 로그인 성공 후 첫 화면에서 login.complete + setUserId */}
      <LoginResultTracker />
      <ReactQueryDevtools initialIsOpen={false} buttonPosition="bottom-left" />
    </QueryClientProvider>
  )
}
