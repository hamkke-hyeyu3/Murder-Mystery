export type CreateSessionRequest = {
  scenarioId: string
  hostNickname: string
}

export type CreateSessionResponse = {
  sessionId: string
  inviteCode: string
  scenarioId: string
  hostNickname: string
  phase: string
  playerId: string
}

export type LastSession = {
  sessionId: string
  inviteCode: string
  nickname: string
  isHost: boolean
  playerId: string
  scenarioId: string
  savedAt: number
}

export const LAST_SESSION_KEY = 'mm:lastSession'

export type PlayerSummaryDto = {
  playerId: string
  nickname: string
  isHost: boolean
}

export type JoinSessionResponse = {
  sessionId: string
  inviteCode: string
  scenarioId: string
  phase: string
  nickname: string
  playerId: string
  players: PlayerSummaryDto[]
}

export type RoundView = {
  roundNumber: number
  prompt: string
  commonHint: string | null
  startedAt: number
  deadlineAt: number
}

export type ObjectiveView = {
  roundNumber: number
  totalRounds: number
  objective: string | null
}

export type TurnView = {
  turnIndex: number
  playerId: string
  characterId: string
  deadlineAt: number
  candidateLocationIds: string[]
}

export type OccupancyView = {
  locationId: string
  playerId: string
  characterId: string
  autoSelected: boolean
}

export type ClueView = {
  id: string
  itemId: string
  title: string
  originLocationId: string
  roundNumberDiscovered: number
  discoveredAt: number
  source: string
}

export type OwnedClueView = {
  id: string
  itemId: string
  title: string
  ownerPlayerId: string
  roundNumberDiscovered: number
}

export type MeView = {
  playerId: string
  nickname: string
  isHost: boolean
  assignedCharacterId: string | null
  character: CharacterCardPayload | null
  objective: ObjectiveView | null
  tutorialAckedAt: number | null
  myClues: ClueView[]
  allOwnedClues?: OwnedClueView[]
}

export type ScenarioLocationView = {
  id: string
  name: string
  icon?: string
}

export type SessionViewResponse = {
  sessionId: string
  inviteCode: string
  scenarioId: string
  phase: string
  requiredCharacterCount: number
  joinedCount: number
  players: PlayerSummaryDto[]
  state?: string | null
  currentRoundNumber?: number | null
  turnOrder?: string[] | null
  locations?: ScenarioLocationView[]
  round?: RoundView | null
  currentTurn?: TurnView | null
  locationOccupancy?: OccupancyView[]
  me?: MeView | null
}

export type ResumeResponse = {
  sessionId: string
  inviteCode: string
  scenarioId: string
  phase: string
  nickname: string
  playerId: string
  isHost: boolean
  players: PlayerSummaryDto[]
}

export type LocationRef = {
  id: string
  name: string
  icon?: string
}

export type ItemRef = {
  id: string
  title: string
  originLocation?: LocationRef
}

export type CharacterCardPayload = {
  characterId: string
  name: string
  turnOrderIndex: number
  speechStyle?: string
  background?: string
  motive?: string
  alibi?: string
  secret?: string
  relationships?: string
  alibiLocation?: LocationRef
  items?: ItemRef[]
}

export type ObjectiveUpdatedPayload = {
  roundNumber: number
  totalRounds: number
  objective: string | null
}

export type TutorialAckResponse = {
  acked: number
  total: number
  state: string
}

export type SessionEvent =
  | {
      type: 'PLAYER_JOINED'
      sessionId: string
      occurredAt: string
      payload: { playerId: string; nickname: string; isHost: boolean }
    }
  | {
      type: 'PLAYER_LEFT'
      sessionId: string
      occurredAt: string
      payload: { playerId: string; nickname: string }
    }
  | {
      type: 'LOBBY_COUNT_CHANGED'
      sessionId: string
      occurredAt: string
      payload: { joined: number; required: number }
    }
  | {
      type: 'SESSION_STATE_CHANGED'
      sessionId: string
      occurredAt: string
      payload: { state: string; turnOrder: string[] | null }
    }
  | {
      type: 'CHARACTER_CARD_DEALT'
      sessionId: string
      occurredAt: string
      payload: CharacterCardPayload
    }
  | {
      type: 'TUTORIAL_ACKED'
      sessionId: string
      occurredAt: string
      payload: { playerId: string; nickname: string; acked: number; total: number }
    }
  | {
      type: 'SERVER_TIME_SYNC'
      sessionId: string
      occurredAt: string
      payload: { serverNow: number }
    }
  | {
      type: 'ROUND_STARTED'
      sessionId: string
      occurredAt: string
      payload: {
        roundNumber: number
        prompt: string
        commonHint: string | null
        deadlineAt: number
        startedAt: number
      }
    }
  | {
      type: 'OBJECTIVE_UPDATED'
      sessionId: string
      occurredAt: string
      payload: ObjectiveUpdatedPayload
    }
  | {
      type: 'TURN_STARTED'
      sessionId: string
      occurredAt: string
      payload: {
        roundNumber: number
        turnIndex: number
        playerId: string
        characterId: string
        deadlineAt: number
        candidateLocationIds: string[]
      }
    }
  | {
      type: 'LOCATION_SELECTED'
      sessionId: string
      occurredAt: string
      payload: {
        roundNumber: number
        turnIndex: number
        playerId: string
        characterId: string
        locationId: string
      }
    }
  | {
      type: 'LOCATION_AUTO_SELECTED'
      sessionId: string
      occurredAt: string
      payload: {
        roundNumber: number
        turnIndex: number
        playerId: string
        characterId: string
        locationId: string
      }
    }
  | {
      type: 'CLUE_DELIVERED'
      sessionId: string
      occurredAt: string
      payload: ClueView
    }
  | {
      type: 'ROUND_TURNS_COMPLETE'
      sessionId: string
      occurredAt: string
      payload: { roundNumber: number }
    }
  | {
      type: 'ROUND_ENDED'
      sessionId: string
      occurredAt: string
      payload: { roundNumber: number; reason: string; endedAt: number }
    }
  | {
      type: 'ITEM_EXCHANGED'
      sessionId: string
      occurredAt: string
      payload: {
        actorPlayerId: string
        actorNickname: string
        partnerPlayerId: string
        partnerNickname: string
        roundNumber: number
        actorClueId: string
        partnerClueId: string
        actionId: string
        occurredAt: number
      }
    }
  | {
      type: 'ITEM_SHARED_FULL'
      sessionId: string
      occurredAt: string
      payload: {
        actorPlayerId: string
        actorNickname: string
        clueId: string
        roundNumber: number
        actionId: string
        occurredAt: number
      }
    }
  | {
      type: 'ITEM_SHARED_PARTIAL'
      sessionId: string
      occurredAt: string
      payload: {
        actorPlayerId: string
        actorNickname: string
        clueId: string
        roundNumber: number
        recipients: { playerId: string; nickname: string }[]
        actionId: string
        occurredAt: number
      }
    }
