# BYOD 머더 미스터리 MVP Todo

> 상세 계획: `tasks/plan.md` / 전체 스펙: `SPEC.md`
> 체크포인트 완료 조건은 `tasks/plan.md` §체크포인트 참조.

---

## 🔵 체크포인트 A — A2 Walking Skeleton

- [x] **T-00** 인프라·DB·STOMP 부트스트랩
  - [x] BE: Flyway + JSON Schema 의존성 추가, `application.yml` `ddl-auto: validate` 전환
  - [x] BE: `WebSocketConfig`, `CorsConfig`, `RestExceptionHandler`, STOMP `HandshakeHandler` 스켈레톤
  - [x] FE: `@stomp/stompjs` + `zustand` + `react-router-dom` 설치
  - [x] FE: `useStompClient` 훅 + 4개 Zustand store 스켈레톤
  - [x] FE: `App.tsx` 라우터 (`/` · `/lobby/:inviteCode` · `/play/:sessionId` · `/join`)
  - [x] DB: `V1__init.sql` (Flyway 부트스트랩)
  - [x] 검증: `bootRun` + STOMP/SockJS 엔드포인트 200 응답 + 4 라우트 진입 (App.test.tsx 4/4 그린)

- [x] **T-01** 시나리오 메타 로더 + Schema 검증 + 카탈로그 API
  - [x] BE: `scenario-schema.json` 작성 (JSON Schema + cross-field validator)
  - [x] BE: `ScenarioLoader` 클래스패스 스캔, 검증 실패는 WARN 로그
  - [x] BE: `GET /api/scenarios` 컨트롤러
  - [x] BE: `toy-manor.json` placeholder (3 캐릭터·N=3·location_pool 4개·각 장소 1+ 아이템)
  - [x] BE: `broken-edge.json` fixture (캐릭터 2명, 의도적 실패)
  - [x] FE: `pages/Catalog.tsx` (단일 카드 + "세션 만들기" 버튼)
  - [x] 검증: `ScenarioLoaderTest` 5 실패 fixture + `Catalog.test.tsx` 4 케이스

- [ ] **T-02** 카탈로그 → 세션 만들기 → 초대 번호 + lobby
  - [ ] BE: `Session` 엔티티, `SessionService.createSession`
  - [ ] BE: `POST /api/sessions {scenarioId, hostNickname}` 컨트롤러
  - [ ] FE: 닉네임 인라인 입력 → POST → localStorage → `/lobby/:inviteCode`
  - [ ] FE: `pages/Lobby.tsx` (6자리 코드 + 합류자 목록 placeholder + "게임 시작" 비활성)
  - [ ] DB: `V2__session_player.sql` (`sessions`, `players`, unique `(session_id, nickname)`)
  - [ ] 검증: `SessionServiceTest`

- [ ] **T-03** 닉네임 합류 + 합류자 목록 양방향 동기 (STOMP)
  - [ ] BE: `JoinService.join`, `POST /api/sessions/{inviteCode}/join`
  - [ ] BE: `PLAYER_JOINED` broadcast, `/app/session/{id}/leave` 핸들러
  - [ ] FE: `pages/Join.tsx` + `useSessionWebSocket(sessionId)` 훅
  - [ ] FE: `Lobby.tsx` players 상태 갱신 + 게스트 "나가기" 버튼
  - [ ] 검증: `JoinServiceTest`, `JoinIntegrationTest`, `useSessionWebSocket.test.ts`, 수동 3 탭

- [ ] **T-04** 인원 매칭 + "게임 시작" 게이트 + 사유 인라인
  - [ ] BE: `GET /api/sessions/{sessionId}` 에 `requiredCharacterCount` + `joinedCount`
  - [ ] BE: `LOBBY_COUNT_CHANGED` broadcast
  - [ ] FE: `Lobby.tsx` 호스트 화면 — 사유 인라인 ("X명 더 필요"·"X명 초과"·활성)
  - [ ] 검증: `Lobby.test.tsx` 6 케이스 매트릭스

**✅ 체크포인트 A 완료 조건:** 3 단말 lobby 데모 + `./gradlew test` + `npm run test` 그린

---

## 🔵 체크포인트 B — A3 + 라운드 1 진입

- [ ] **T-05** 게임 시작 → 단계 1·2 + turn_order + 캐릭터 자동 배정
  - [ ] BE: `SessionService.start` (host 검증 + J=C + turn_order 셔플 + 캐릭터 매핑)
  - [ ] BE: `SESSION_STATE_CHANGED` broadcast + 5초 후 `character_assignment` 전이
  - [ ] BE: `/user/queue/.../private`에 본인 캐릭터 카드 발사
  - [ ] FE: `pages/Play.tsx` shell + `useCardStore` 본인 카드 캐싱
  - [ ] DB: `V3__game_state.sql` (`sessions` 컬럼 추가, `players.tutorial_acked_at/mission_checked_at`)
  - [ ] 검증: `SessionServiceTest.start_*`, `StartIntegrationTest` (3 클라 ACL verify), 수동 3 탭

- [ ] **T-06** 단계 3 튜토리얼 + 거짓말 정책 고정 문구 + L1 자동 진입
  - [ ] BE: `POST /api/sessions/{id}/tutorial-ack`, 모두 통과 시 round 전이
  - [ ] FE: `Tutorial.tsx` (고정 문구 + "확인" 버튼 + "X / N 통과" 대기)
  - [ ] 검증: `TutorialServiceTest`

- [ ] **T-07** 라운드 진입 + 프롬프트 + 카운트다운 + k=1 자기소개 + common_hint
  - [ ] BE: `RoundService.startRound`, `SERVER_TIME_SYNC` broadcast, ScheduledExecutor 등록
  - [ ] FE: `RoundPanel.tsx` (프롬프트 + common_hint + 카운트다운 + 5초 경고)
  - [ ] DB: `V3.5__rounds.sql`
  - [ ] 검증: `RoundServiceTest` (k=1 자기소개 트리거, common_hint 선언 라운드만)

- [ ] **T-08** 캐릭터 카드 단일 표면 (E2-01·02·04·05 통합)
  - [ ] FE: `CharacterCard.tsx` (6 영역: 헤더·라운드목표·미션자리표시자·아이템·본문·알리바이)
  - [ ] FE: `LocationLabel` 공통 컴포넌트 (icon fallback `📍`)
  - [ ] BE: 라운드 전환 시 `OBJECTIVE_UPDATED` broadcast (본인 private)
  - [ ] 검증: `CharacterCard.test.tsx` (6 영역 도달 + 말투 미선언 비표시 + 라운드 전환)

**✅ 체크포인트 B 완료 조건:** 시작 → 라운드 1 진입 3 단말 동기 데모 + 본인 카드 ACL verify

---

## 🔵 체크포인트 C — A4 라운드 루프 핵심

- [ ] **T-09** 회전 턴 30초 + 점유 잠금 + 단서 ACL + 랜덤 자동 선택 ⚠️ *최고 위험*
  - [ ] BE: `RoundTurnService` (차례 큐 + `TURN_STARTED` + 30s ScheduledExecutor)
  - [ ] BE: `select-location` 핸들러 (잠금 검증 + ACL + `CLUE_DELIVERED` + `LOCATION_SELECTED`)
  - [ ] BE: 30s 만료 시 남은 후보 무작위 1개 `LOCATION_AUTO_SELECTED`
  - [ ] BE: select 수신 시 `now < deadlineAt + 1s` grace 윈도우
  - [ ] FE: `LocationGrid.tsx` (본인/타인/점유 분기 + 카운트다운)
  - [ ] DB: `V4__round_turn.sql` (`location_occupancy`, `clues`, `clue_acl`)
  - [ ] 검증: `RoundTurnServiceTest` (snake 회전·timeout·동시 탭 거부·단서 ACL), `RoundTurnIntegrationTest`

- [ ] **T-10** monotonic 누적 + 라운드 자동 전환
  - [ ] BE: 라운드 종료 트리거 (모든 차례 완료 + `time_limit_sec` 만료)
  - [ ] BE: `clue_acl` 절대 삭제 금지. k<N → `startRound(k+1)`, k=N → `phase='vote'`
  - [ ] FE: 라운드 전환 애니메이션 + 단서 라운드 꼬리표 그룹
  - [ ] 검증: `RoundLifecycleTest.cluesPersistAcrossRounds`, `_lastRoundTransitionsToVote`

- [ ] **T-11** 아이템 교환·전체·부분 공유 + 전원 공개 배너
  - [ ] BE: `ItemService` (3종 행위 + `clue_acl` 추가 + `BANNER` broadcast)
  - [ ] FE: 단서 long-press → 행위 메뉴 + share_partial multi-select + 배너 토스트 큐
  - [ ] DB: `V5__items.sql` (`item_actions`)
  - [ ] 검증: `ItemServiceTest` (exchange 양측 X·Y 접근권, partial 비대상 ACL 없음)

- [ ] **T-12** 1:1 밀담 신청·수락·거절 + 동시 한 쌍 + 배너
  - [ ] BE: `PrivateTalkService` (신청 private only → 수락 broadcast → 거절·timeout 무반응)
  - [ ] FE: 인라인 신청 카드(모달 아님) + "A, B 밀담 중" 배너 + 밀담 신청 버튼 잠금
  - [ ] DB: `V6__private_talk.sql`
  - [ ] 검증: `PrivateTalkServiceTest` (거절·timeout·미응답 동일 무반응)

**✅ 체크포인트 C 완료 조건:** 라운드 1·2·3 끝까지 + 단서 monotonic verify + 아이템 3행위 + 밀담 데모

---

## 🔵 체크포인트 D — A5 종료 흐름

- [ ] **T-13** 단계 8 투표 + 동점 자동 재투표 + 색출 실패
  - [ ] BE: 투표 UPSERT + 집계 + 단독 1위/동점/재동점 분기
  - [ ] FE: `VotePanel.tsx` (후보 목록 + 결과 + 색출 실패 프레임)
  - [ ] DB: `V7__vote.sql`
  - [ ] 검증: `VoteServiceTest` (3 분기)

- [ ] **T-14** 9-A 자동 → 9-B 미션 자가 체크
  - [ ] BE: `CULPRIT_REVEAL_STARTED` → 일정 시간 후 `MISSION_PHASE_STARTED` + 본인 private `MISSION_REVEALED`
  - [ ] BE: `/app/mission/check-complete` → `mission_checks` 저장 + 모두 완료 시 9-C
  - [ ] FE: `RevealPanel.tsx` + `MissionPanel.tsx` (✓/✗ + "체크 완료" 잠금 + "X / N 완료" 카운트)
  - [ ] DB: `V8__mission.sql`
  - [ ] 검증: `MissionServiceTest`

- [ ] **T-15** 호스트 강제 진행 + NB3 좁은 인계 ⚠️ *위험*
  - [ ] BE: 첫 체크 완료 후 3분 ScheduledExecutor → `FORCE_PROGRESS_AVAILABLE`
  - [ ] BE: NB3 — 호스트 미완료 + 오프라인(last_seen_at < now-30s) 시 모든 단말 broadcast
  - [ ] BE: `/app/host/force-progress` — 미완료자 null + 9-C 트리거
  - [ ] FE: `MissionPanel.tsx`에 "강제 진행" 컨트롤 조건부 노출
  - [ ] 검증: `ForceProgressTest` (가짜 시계, `_NB3_doesNotApplyToOtherHostActions` 가드)

- [ ] **T-16** 9-C 엔딩 + 10 디브리프 자동 표시
  - [ ] BE: `ENDING_STARTED` → `DEBRIEF_STARTED` → `SURVEY_AVAILABLE` 자동 체인
  - [ ] FE: `EndingPanel.tsx` + `DebriefPanel.tsx` (텍스트만)
  - [ ] 검증: 수동

- [ ] **T-17** 인라인 설문 + 종료 화면
  - [ ] BE: `/app/survey` → `survey_responses` 저장 + `SESSION_ENDED`
  - [ ] FE: `EndScreen.tsx` (인라인 설문 카드 + 두 슬라이더(체크박스 분리) + 80자 자유 텍스트 + "응답하기"/"건너뛰기" 동등 + 미션 한 줄 요약)
  - [ ] DB: `V9__survey.sql`
  - [ ] 검증: `SurveyServiceTest._oneResponsePerPlayerPerSession`, `EndScreen.test.tsx`

**✅ 체크포인트 D 완료 조건:** 투표 → 종료 화면 풀 흐름 데모 + 동점·색출 실패 분기 verify + 강제 진행 fast-mode verify

---

## 🔵 체크포인트 E — 견고화

- [ ] **T-18** 재합류 슬롯 재바인딩 + 풀 스테이트 스냅샷 ⚠️ *최고 위험*
  - [ ] BE: `POST /api/sessions/{inviteCode}/join` — 5개 상태 분기 (lobby 신규·충돌, in_progress 기존·신규, ended)
  - [ ] BE: `SESSION_SNAPSHOT` DTO 완전성 (character·clues·accessClues·missionResults·tutorialAcked·currentPrivateTalk·latestBanners)
  - [ ] FE: 부팅 시 `mm:lastSession` 자동 재합류 시도 + stores hydrate + 호스트 권한 복원
  - [ ] 검증: `RejoinServiceTest` 6 분기, `useAutoRejoin.test.ts`, 수동(브라우저 강제 종료 후 재진입)
  - **PR 리뷰 체크리스트:** 새 필드 추가 시 snapshot DTO에 동시 추가 확인

- [ ] **T-19** session_log 텔레메트리
  - [ ] BE: `SessionLogService.log` (화이트리스트 방식) + 모든 이벤트 포인트에 호출 추가
  - [ ] DB: `V10__session_log.sql`
  - [ ] 검증: `SessionLogServiceTest._doesNotPersistL1Content` + 수동 SQL 조회

- [ ] **T-20** lobby 만료 + 오프라인 감지 + 24h 보존
  - [ ] BE: `LobbyCleanupJob` (6h OR 호스트 30분 오프라인+J=0) + `EndedCleanupJob` (24h)
  - [ ] BE: STOMP heartbeat 기반 `players.last_seen_at` 갱신
  - [ ] FE: 만료 lobby 입장 시 안내
  - [ ] DB: `V11__cleanup_indexes.sql`
  - [ ] 검증: `LobbyCleanupJobTest` (가짜 시계) + dev fast-mode 수동

**✅ 체크포인트 E 완료 조건:** 재합류 풀 복원 데모 + 카탈로그 숨김 verify + session_log SQL 확인 + cleanup fast-mode verify

---

## 🏁 골든 패스 E2E

체크포인트 E 완료 후 3 단말(Alice·Bob·Charlie)로 다음 시나리오를 처음부터 끝까지 수동 통과:

- [ ] 카탈로그 → lobby → 시작 → 캐릭터 자동 배정
- [ ] 라운드 1: turn_order 확인 + 무응답 자동 선택 + Bob 강제 종료 → 재합류 verify
- [ ] 아이템 1:1 교환 + 1:1 밀담 + 라운드 종료
- [ ] 라운드 2·3 단서 monotonic 보존
- [ ] 의도적 동점 → 재투표 → 색출 실패 → 9-A·9-B·9-C → 설문 → 종료 화면
- [ ] `SELECT event_type, count(*) FROM session_log WHERE session_id = ? GROUP BY 1` 결과 확인

---

> 시나리오 1편 출시용 컨텐츠: **사용자 별도 트랙**. `toy-manor.json` schema 통과 후 실제 1편 JSON을 `backend/src/main/resources/scenarios/` 에 git PR로 추가.
