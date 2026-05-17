# BYOD 머더 미스터리 MVP Todo

> 상세 계획: `tasks/plan.md` / 전체 스펙: `SPEC.md`
> 체크포인트 완료 조건은 `tasks/plan.md` §체크포인트 참조.

---

## 🔧 개발 환경

- [x] **INFRA-04** dev 전용 2인 시나리오 로딩 인프라
  - `scenario-schema.json` `characters.minItems` 3 → 2 (tooling 검증 완화; 운영 ≥ 3 규칙은 SPEC §6 각주로 유지)
  - `scenarios-dev/dev-duo.json` 추가 (host·guest 2명, dev profile 한정 로딩)
  - `ScenarioRepository` → `app.scenarios.patterns` 설정 주입식으로 전환
  - `application.yml` / `application-dev.yml` 패턴 설정 추가
  - 검증: `ScenarioLoaderTest.dev_classpath_loads_dev_duo` 그린, 전체 `./gradlew test` 그린

- [x] **INFRA-05** FE dev 빌드 한정 `?deviceId=` 쿼리 override (BUG-02 해결)
  - `deviceId.ts` — `import.meta.env.DEV` guard + `URLSearchParams('deviceId')` valid UUID → localStorage set + URL clean
  - `deviceId.test.ts` 신규 4케이스
  - 사용법: 탭마다 `?deviceId=<uuid>` 부여 → 단일 브라우저에서 2-단말 시뮬레이션 가능

- [ ] **INFRA-01** DB 데이터 보존 전환
  - `scripts/dev.sh` `cleanup()` 내 `docker compose down --volumes` → `docker compose stop postgres`
  - 변경 시점: 시드 데이터 또는 지속 테스트 데이터가 필요해지는 시점

- [x] **INFRA-02** 통합 테스트를 Testcontainers PostgreSQL로 전환 (블로킹: 체크포인트 B 진입 전)
  - 문제: `application-test.yml`이 H2 + `ddl-auto: create-drop` + `flyway: disabled` → V4의 `CREATE UNIQUE INDEX ... WHERE device_id IS NOT NULL` (PG partial index)가 검증 안 됨
  - 영향: `JoinService` outer-catch race 회복 코드(`JoinService.java:95-119`)가 보호하려는 무결성 위반이 운영에서만 트리거될 수 있음
  - 작업: `JoinIntegrationTest`만이라도 Testcontainers PG로 이전, Flyway 활성, V1~V4 적용
  - 출처: 체크포인트 A 리뷰

- [x] **INFRA-03** prod profile에서 STOMP/CORS origin 좁히기 (블로킹: 운영 노출 전)
  - 문제: `WebSocketConfig.java:39` `setAllowedOriginPatterns("*")` + `CorsConfig` `allowCredentials(true)` 조합 그대로 운영 시 invite-code만 알면 누구나 STOMP CONNECT 가능
  - 작업: `app.cors.allowed-origins`를 `setAllowedOriginPatterns(...)`에 주입, 기존 `application-prod.yml`에 `app.cors.allowed-origins` 운영 도메인 추가
  - 출처: 체크포인트 A 리뷰

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

- [x] **T-02** 카탈로그 → 세션 만들기 → 초대 번호 + lobby
  - [x] BE: `Session` 엔티티, `SessionService.createSession`
  - [x] BE: `POST /api/sessions {scenarioId, hostNickname}` 컨트롤러
  - [x] FE: 닉네임 인라인 입력 → POST → localStorage → `/lobby/:inviteCode`
  - [x] FE: `pages/Lobby.tsx` (6자리 코드 + 합류자 목록 placeholder + "게임 시작" 비활성)
  - [x] DB: `V2__session_player.sql` (`sessions`, `players`, unique `(session_id, nickname)`)
  - [x] 검증: `SessionServiceTest`

- [x] **T-03** 닉네임 합류 + 합류자 목록 양방향 동기 (STOMP)
  - [x] BE: `JoinService.join`, `POST /api/sessions/{inviteCode}/join`
  - [x] BE: `PLAYER_JOINED` broadcast, `/app/session/{id}/leave` 핸들러
  - [x] FE: `pages/Join.tsx` + `useSessionWebSocket(sessionId)` 훅
  - [x] FE: `Lobby.tsx` players 상태 갱신 + 게스트 "나가기" 버튼
  - [x] 검증: `JoinServiceTest`, `useSessionWebSocket.test.ts`
  - [x] fix(lobby): `PlayerSummary`에 `playerId` 추가, PLAYER_JOINED dedup(playerId 기준), PLAYER_LEFT playerId 필터링, Lobby host-first 정렬

- [x] **T-04** 인원 매칭 + "게임 시작" 게이트 + 사유 인라인 + Device ID 정체성 추적
  - [x] BE: `GET /api/sessions/{sessionId}` 에 `requiredCharacterCount` + `joinedCount`
  - [x] BE: `LOBBY_COUNT_CHANGED` broadcast
  - [x] FE: `Lobby.tsx` 호스트 화면 — 사유 인라인 ("X명 더 필요"·"X명 초과"·활성)
  - [x] 검증: `Lobby.test.tsx` 6 케이스 매트릭스
  - [x] BE: `mm:deviceId` 기반 `X-Device-Id` 헤더, V3/V4 migration, `ResumeService`
  - [x] FE: `lib/deviceId.ts`, `useResumeSession` hook, Catalog/Join 자동 redirect
  - [x] FE: Playwright e2e `device-resume.spec.ts` (4 시나리오)
  - [x] 동시 join race 처리: aborted 트랜잭션 후 outer catch에서 idempotent 복구
  - [x] BE: `JoinIntegrationTest` — 같은 deviceId 순차 합류 idempotent 케이스 추가

- [x] **BUG-01** Lobby 합류자 목록 실시간 갱신 안 됨
  - 원인: useStompClient가 raw WebSocket으로 연결 시도 → SockJS 전용 /ws 엔드포인트 거절 → STOMP 연결 불가
  - 수정: sockjs-client 추가 + webSocketFactory로 전환, stale callback 가드 추가
  - 검증: useStompClient unit test 5케이스 그린, e2e lobby-realtime-join.spec.ts로 통합 확인

- [x] **BUG-03** Lobby "(호스트)" 라벨 wrong player 버그 — REST/WS 레이스 3종 수정
  - 원인 1: `PLAYER_JOINED` WS 핸들러가 playerId 중복 체크 없이 append → REST + WS 동시 도착 시 같은 플레이어가 두 번 삽입되고 React `key={nickname}` 충돌로 잘못된 props 적용
  - 원인 2: `getSession()` players 업데이트가 `requiredCharacterCount === null` guard 안에 묶여 있어, `LOBBY_COUNT_CHANGED`가 먼저 도착하면 host가 players[]에 영구 누락 (host는 `PLAYER_JOINED` WS 없음)
  - 원인 3: REST `joinedCount`가 tombstone 필터와 불일치 — PLAYER_LEFT 후 stale 스냅샷이 떠난 플레이어를 되살리고 count가 맞지 않아 시작 버튼이 잘못 활성화
  - 수정: `PlayerSummary`에 `playerId` 추가 + PLAYER_JOINED upsert by playerId + `key={p.playerId}` / players 업데이트를 guard 바깥으로 분리 + `leftPlayerIds` tombstone 도입 + REST `joinedCount` 제거
  - 검증: `useSessionWebSocket.test.ts` 2 케이스 추가, `Lobby.test.tsx` 3 케이스 추가 (100 FE 테스트 그린)

- [x] **BUG-02** 로컬 다중 플레이어 테스트: 같은 브라우저 탭은 `mm:deviceId` 공유
  - 증상: 새 탭을 열면 `useResumeSession`이 기존 세션으로 redirect → 별도 플레이어 시뮬레이션 불가
  - 원인: 같은 브라우저 origin의 탭은 localStorage 공유 (의도된 동작, 테스트 환경 문제)
  - 로컬 테스트 방법: **Chrome 프로필 여러 개** 또는 **Safari + Chrome** 조합으로 각각 접속
  - [x] 해결 옵션 완료 (INFRA-05): `?deviceId=<uuid>` dev override 구현 — 탭 A에 `?deviceId=...0001`, 탭 B에 `?deviceId=...0002` → dev-duo(2인) 시나리오와 결합 시 단일 브라우저로 호스트+게스트 동시 합류 시뮬레이션 가능

**✅ 리뷰 메모 완료 (체크포인트 A → B 전환 시 처리):**
- [x] `JoinService.join` 분해 (`loadJoinableSession`/`tryJoin`/`recoverFromConflict`/`broadcastJoin`) + `Optional<LobbyCountChangedPayload>` (null sentinel 제거)
- [x] `validateNickname` → `Nicknames.validate` 유틸 일원화 + `SessionEventPublisher` 컴포넌트 추출 (`JoinService`/`LeaveService` 공유)
- [x] `vitest.config.ts` `clearMocks:true` + `setup.ts` 전체 store reset (`transient`/`timer`/`card`)

**✅ 체크포인트 A 완료 조건:** 3 단말 lobby 데모 + `./gradlew test` + `npm run test` 그린

---

## 🔵 체크포인트 B — A3 + 라운드 1 진입

- [x] **T-05** 게임 시작 → 단계 1·2 + turn_order + 캐릭터 자동 배정
  - [x] BE: `StartGameService.start` (host 검증 + J=C + turn_order 셔플 + 캐릭터 매핑)
  - [x] BE: `SESSION_STATE_CHANGED` broadcast + 5초 후 `character_assignment` 전이 (ScheduledExecutorService)
  - [x] BE: `/user/queue/.../private`에 본인 캐릭터 카드 발사 (`SessionEventPublisher.publishToPlayer`)
  - [x] FE: `pages/Play.tsx` shell + `useCardStore` 본인 카드 캐싱
  - [x] DB: `V5__game_state.sql` (`sessions` state/turn_order 컬럼 추가, `players.assigned_character_id/tutorial_acked_at/mission_checked_at`)
  - [x] 검증: `StartGameServiceTest` (13 케이스), `StartIntegrationTest` (3 클라 ACL verify), FE 66 테스트 그린
  - [x] adversarial-review 수정: `Random`→`ThreadLocalRandom`, `@Version` 낙관적 잠금, 스케줄러 비데몬+awaitTermination, scenarioFindAll 루프 외부화, 부분 전송 try-catch, turnOrder null 가드, start 엔드포인트 컨트롤러 테스트 6케이스

- [x] **T-06** 단계 3 튜토리얼 + 거짓말 정책 고정 문구 + L1 자동 진입
  - [x] BE: `POST /api/sessions/{id}/tutorial-ack`, 모두 통과 시 round 전이
  - [x] FE: `Tutorial.tsx` (고정 문구 + "확인" 버튼 + "X / N 통과" 대기)
  - [x] 검증: `TutorialServiceTest`

- [x] **T-07** 라운드 진입 + 프롬프트 + 카운트다운 + k=1 자기소개 + common_hint
  - [x] BE: `RoundService.startRound`, `SERVER_TIME_SYNC` broadcast (ScheduledExecutor는 T-10에서 — 라운드 종료/자동 전환 범위)
  - [x] FE: `RoundPanel.tsx` (프롬프트 + common_hint + 카운트다운 + 5초 경고) + `useCountdown` 훅
  - [x] DB: `V6__rounds.sql` (V3.5는 V5/V5_1 이후 사용 불가 — V6으로 교체)
  - [x] 검증: `RoundServiceTest` 17케이스, `TutorialServiceTest` 2케이스 추가, `RoundPanel.test.tsx` 7케이스, `useCountdown.test.ts` 7케이스, `useSessionWebSocket.test.ts` 4케이스 추가 (총 95 FE 테스트 그린)
  - [x] fix(T-07): `rounds==null` 시나리오 로딩 시점 거부 (`ScenarioCrossFieldValidator`)
  - [x] adversarial-review 수정: `CrossFieldValidatorTest` 4케이스 ROUNDS_2 상수 적용(pool/culprit 경로 복구), PLAYER_LEFT `leftId` guard, PLAYER_JOINED/LEFT functional updater 전환

- [x] **T-08** 캐릭터 카드 단일 표면 (E2-01·02·04·05 통합)
  - [x] FE: `CharacterCard.tsx` (6 영역: 헤더·라운드목표·미션자리표시자·아이템·본문·알리바이)
  - [x] FE: `LocationLabel` 공통 컴포넌트 (icon fallback `📍`)
  - [x] BE: 라운드 전환 시 `OBJECTIVE_UPDATED` broadcast (본인 private)
  - [x] 검증: `CharacterCard.test.tsx` (6 영역 도달 + 말투 미선언 비표시 + 라운드 전환)
  - [x] `CharacterCard.tsx` — `data-character-id` 속성 추가 (e2e ACL 식별용)

**✅ 체크포인트 B 완료:** `e2e/checkpoint-b-three-terminal.spec.ts` 3 context (Alice·Bob·Charlie) 12.4s 통과 — 시작 → 라운드 1 동기 + distinct characterId ACL verify. `./gradlew test` + `npm run test` + `npm run test:e2e` 전체 그린.

---

## 🔵 체크포인트 C — A4 라운드 루프 핵심

- [x] **T-09** 회전 턴 30초 + 점유 잠금 + 단서 ACL + 랜덤 자동 선택 ⚠️ *최고 위험*
  - [x] BE S1: `V7__round_turn.sql` + 엔티티(LocationOccupancy/Clue/ClueAcl) + `TurnQueueCalculator` + `TurnQueueCalculatorTest` (15 케이스)
  - [x] BE S2: `RoundTurnService` (차례 큐 + `TURN_STARTED` + 30s ScheduledExecutor) + `ClockConfig` + `RoundTurnServiceTest` (7 케이스) + Codex adversarial review 3개 fix (#1 future cancel, #4 rounds row guard, #6 bounds validation) 적용
  - [x] BE S3: `select-location` STOMP 핸들러 + `selectLocation` (7 가드 + grace 1s + ACL + `CLUE_DELIVERED` + `LOCATION_SELECTED`) + `RoundTurnServiceTest` selectLocation 7 케이스
  - [x] BE S4: autoSelectTurn 테스트 4 케이스 (random pick, idempotent, AUTO_SELECTED 이벤트, ROUND_TURNS_COMPLETE) 추가 — autoSelectTurn 구현은 S2에서 완료
  - [x] BE S5: `SessionViewResponse` 스냅샷 확장 (`TurnView currentTurn`, `List<OccupancyView> locationOccupancy`, `MeView.myClues`) + `SessionService` 조회 로직 + `RoundTurnService.getTurnDeadline`
  - [x] FE S6: types(TurnView/OccupancyView/ClueView + 5 이벤트), sessionStore(턴 필드 + hydrateFromSnapshot 확장), timerStore(roundDeadlineAt/turnDeadlineAt), cardStore(ClueView 타입), useSessionWebSocket(5 이벤트 dispatch + publishSelectLocation)
  - [x] FE S7: `LocationGrid.tsx` (본인/타인/점유 분기 + 카운트다운 + 마감 비활성, 6 단위 케이스) + `Play.tsx` 통합(round 분기 + myClues hydration) — 119 FE 테스트 그린
  - [x] BE: select 수신 시 `now < deadlineAt + 1s` grace 윈도우 (S3에서 완료)
  - [x] review 적용 (S7 커밋 후): myClues 0-clue rejoin 버그 수정(`?.length`→`!== undefined`) + 타인-readonly 클릭 미호출 검증 + turnDeadlineAt=null 동작 고정 테스트
  - [x] BE: `RoundTurnIntegrationTest` (select→3 broadcast+1 private+DB verify, autoSelect 직접호출→AUTO_SELECTED×3) + `@AfterEach cancelPendingAutoSelectsForTest` timer cleanup — 2/2 그린
  - [x] FE: `checkpoint-c-round-turn.spec.ts` e2e fixtures + `waitAndSelectLocation`/`waitForLocationOccupied`/`waitForRoundTurnsComplete` helpers

- [x] **T-10** monotonic 누적 + 라운드 자동 전환
  - [x] BE: `RoundLifecycleService` 신규 — OR(turns_complete, time_limit) 종료 트리거, `ObjectProvider<RoundService>` 순환 의존 해소
  - [x] BE: `clue_acl` 절대 삭제 금지. k<N → `startRound(k+1)`, k=N → `state='vote'`
  - [x] BE: stale autoSelect future가 vote 상태에서 데이터 변조 못 하도록 `autoSelectTurn` 가드에 `state='round'` 체크 추가
  - [x] FE: `MyCluesPanel.tsx` — 단서 라운드별 그룹, `VotePlaceholder.tsx`, `ROUND_ENDED` 핸들러 (배너 push + turn clear)
  - [x] 검증: `RoundLifecycleIntegrationTest.cluesPersistAcrossRounds`, `lastRoundTransitionsToVote` + 단위 케이스 포함 전 테스트 그린
  - ⚠️ **기술 부채:** `endedRounds` 가드는 JVM 메모리 전용 — JVM crash 후 재기동 시 `round.ended_at` 있으나 다음 라운드/vote 미진입 상태로 남을 수 있음. 단일 인스턴스 MVP에서 수용; 멀티 인스턴스·HA 전환 전에 startup reconciliation 필요.

- [x] **T-11** 아이템 교환·전체·부분 공유 + 전원 공개 배너
  - [x] DB: `V9__items.sql` (`item_actions`, `clues.current_owner_player_id`) — S1 `e6c4233`
  - [x] BE S2: `ItemService.exchange` + STOMP `/item-exchange` + `ItemServiceTest` 11케이스 — `b3e7de6`
  - [x] BE S3: `ItemService.shareFull` + STOMP `/item-share-full` + `ItemServiceTest` +7케이스(중복방지 가드 포함) — `8d1aef9`
  - [x] BE S4: `ItemService.sharePartial` + STOMP `/item-share-partial` + `ItemServiceTest` +6케이스(sanitize-후-empty 포함) — `f3c3913`
  - [x] BE S5: `ItemIntegrationTest` (exchange·shareFull·sharePartial e2e DB verify) — `290cdd3`
  - [x] FE S6: types(ITEM_EXCHANGED/SHARED_FULL/SHARED_PARTIAL) + useSessionWebSocket(3 이벤트 dispatch + 3 publish 헬퍼) + BannerStack(자동 dismiss + × 버튼) — `95c35e8`
  - [x] FE S7–S8: 단서 long-press → 행위 메뉴 + share_partial multi-select + 배너 토스트 큐 (교환 disabled → 후속 sub-task 분리) — `e6c8a56`
  - [x] FE S9(후속): 교환 UX — BE SessionView allOwnedClues 확장 + exchange picker 구현 — `cabcac3`
  - [x] 검증: 체크포인트 C 완료 조건 — 아이템 3행위 데모
  - ⚠️ **기술 부채:** `ItemService.exchange`는 현재 1-step(requester 단독 swap). 스펙은 2-step — 1단계: 교환 제안(picker 열기), 2단계: partner 수락 시 실행. T-12 이후 별도 task로 수정 필요 (`EXCHANGE_REQUESTED` private → `EXCHANGE_ACCEPTED/DECLINED`).
  - [x] **리뷰 R1**: BE 멱등성·@Version·DB unique·DoS 가드·헬퍼 추출·projection — `8f4959c`
  - [x] **리뷰 R2**: BE WS SUBSCRIBE 구독 권한 가드 (C-3) — `acc8b2e`
  - [x] **리뷰 R3**: FE publish 가드·useLongPress 강화·sheet 자동 닫힘·self-exchange 가드·배너 cap — `4f1a414`
  - [x] **리뷰 R4**: FE ClueActionSheet 모드 분리·핸들러 맵·ClueItem memo·fixtures 리네임 — `ae83951`
  - [x] **리뷰 R5**: FE 이벤트 빈 구멍 3건·useLongPress·BE exchange 권한 위반 통합테스트 — `6df97e1`
  - [x] **리뷰 R6**: ItemService.exchange UUID[][] → List<NewAcl> + useSessionWebSocket publish 헬퍼 단순화 — `84224a3`

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
  - [x] BE: `GET /api/sessions/{id}` 스냅샷 확장 — state·currentRoundNumber·turnOrder·round(prompt/commonHint/deadlineAt)·me(character/objective/tutorialAckedAt) (T-08까지 구현; clues·missionResults·banners·currentPrivateTalk는 T-09~T-13 구현 시 점진 추가)
  - [x] FE: `Play.tsx` 마운트 시 store 비면 `GET /api/sessions/{id}` 스냅샷 fetch + stores(session/card/timer) hydrate
  - [x] FE: `useResumeSession` — phase=in_progress이면 `/play/:sessionId`로 직접 navigate (기존 lobby 우회)
  - [ ] FE: 부팅 시 `mm:lastSession` 자동 재합류 시도 + stores hydrate + 호스트 권한 복원 (기존 by-device 경로와 통합 필요)
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
