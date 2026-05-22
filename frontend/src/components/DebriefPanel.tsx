export function DebriefPanel() {
  return (
    <div data-testid="debrief-panel" className="flex flex-col items-center gap-6 p-6">
      <h2 className="text-2xl font-bold">디브리프</h2>
      <p className="text-muted-foreground text-center">
        게임이 끝났습니다. 잠시 후 설문이 시작됩니다.
      </p>
    </div>
  )
}
