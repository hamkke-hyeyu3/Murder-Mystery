import { useParams } from 'react-router-dom'

export default function Lobby() {
  const { inviteCode } = useParams<{ inviteCode: string }>()
  return <div data-testid="page-lobby">Lobby {inviteCode}</div>
}
