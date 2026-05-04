import { useParams } from 'react-router-dom'

export default function Play() {
  const { sessionId } = useParams<{ sessionId: string }>()
  return <div data-testid="page-play">Play {sessionId}</div>
}
