import { type ClassValue, clsx } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

/**
 * globals.css 의 디자인 토큰 이름을 tailwind-merge 에 알려줘요.
 * (안 알려주면 text-label-sm 같은 글자 크기를 글자 "색"으로 보고 text-ink-3 와 겹친다며 지워버려요)
 */
const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      text: [
        'display-xl',
        'display-lg',
        'h1',
        'h2',
        'h3',
        'h4',
        'body-lg',
        'body-md',
        'body-sm',
        'label-lg',
        'label-md',
        'label-sm',
        'label-xs',
        'label-2xs',
        'caption-md',
        'caption-sm',
        'input',
      ],
      radius: [
        'card',
        'dialog',
        'btn',
        'btn-sm',
        'input',
        'chip',
        'chip-sm',
        'event',
        'event-fixed',
        'nav',
        'icon-btn',
        'toast',
        'seg',
        'seg-item',
        'checkbox',
        'quick-reply',
        'composer',
        'sheet',
      ],
      shadow: ['card', 'pop', 'primary', 'danger', 'seg', 'glass'],
    },
  },
})

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
