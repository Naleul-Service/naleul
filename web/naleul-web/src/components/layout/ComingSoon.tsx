import { Card } from '@/components/ui/Card'
import { PageHeader } from './PageHeader'

/** 아직 만들지 않은 화면의 자리 표시 */
export function ComingSoon({ title, description }: { title: string; description?: string }) {
  return (
    <>
      <PageHeader title={title} />
      <Card className="mt-6 grid min-h-[320px] place-items-center p-10 text-center">
        <div>
          <p className="text-h3 text-text-primary">곧 만들어질 화면이에요</p>
          <p className="text-body-md text-text-secondary mt-1.5">
            {description ?? 'AI 목표 만들기를 먼저 완성한 뒤 이어서 만들어요.'}
          </p>
        </div>
      </Card>
    </>
  )
}
