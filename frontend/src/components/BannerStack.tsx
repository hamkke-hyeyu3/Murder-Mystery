import { useEffect } from 'react'
import { useTransientStore, type Banner } from '@/stores/transientStore'

function BannerItem({ banner }: { banner: Banner }) {
  const dismissBanner = useTransientStore((s) => s.dismissBanner)

  useEffect(() => {
    const id = setTimeout(() => dismissBanner(banner.id), 4000)
    return () => clearTimeout(id)
  }, [banner.id, dismissBanner])

  return (
    <div
      data-testid={`banner-${banner.id}`}
      className="flex items-center justify-between rounded-lg border p-3 text-sm bg-background"
      role="status"
      aria-live="polite"
    >
      <span>{banner.message}</span>
      <button
        data-testid={`banner-${banner.id}-dismiss`}
        onClick={() => dismissBanner(banner.id)}
        className="ml-3 text-muted-foreground hover:text-foreground"
        aria-label="닫기"
      >
        ×
      </button>
    </div>
  )
}

export function BannerStack() {
  const banners = useTransientStore((s) => s.banners)

  if (banners.length === 0) return null

  return (
    <div data-testid="banner-stack" className="flex flex-col gap-2">
      {banners.map((b) => (
        <BannerItem key={b.id} banner={b} />
      ))}
    </div>
  )
}
