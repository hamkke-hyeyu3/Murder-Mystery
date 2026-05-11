interface LocationLabelProps {
  icon?: string
  name: string
}

export function LocationLabel({ icon, name }: LocationLabelProps) {
  return (
    <span data-testid="location-label">
      {icon ?? '📍'} {name}
    </span>
  )
}
