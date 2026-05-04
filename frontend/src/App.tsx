import { BrowserRouter, Route, Routes } from 'react-router-dom'
import Catalog from '@/pages/Catalog'
import Join from '@/pages/Join'
import Lobby from '@/pages/Lobby'
import Play from '@/pages/Play'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Catalog />} />
        <Route path="/lobby/:inviteCode" element={<Lobby />} />
        <Route path="/play/:sessionId" element={<Play />} />
        <Route path="/join" element={<Join />} />
      </Routes>
    </BrowserRouter>
  )
}
