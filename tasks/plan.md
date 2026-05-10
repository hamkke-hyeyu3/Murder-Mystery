# BYOD 머더 미스터리 MVP 구현 계획 (요약)

> 전체 상세 계획: `/Users/yuwon-u/.claude/plans/spec-md-peppy-yeti.md`

## 핵심 확정 결정

| 항목 | 결정 |
|------|------|
| 시나리오 메타 포맷/위치 | JSON, `backend/src/main/resources/scenarios/*.json` |
| DB 마이그레이션 | Flyway (`V1__init.sql` ~ `V11`), `ddl-auto: validate` |
| FE 상태 관리 | Zustand (4개 store: Session / Card / Timer / Transient) |
| 오디오 자동 재생 | 텍스트만 (`narration_audio_url?` 옵셔널 자리만) |
| 거짓말 정책 | `all_players` 단일 고정 상수 (메타 필드 아님) |
| Player identity / 재합류 키 | `(invite_code, nickname)` 자연키 + localStorage |
| 타이머 권한 | 서버 결정 (`deadlineAt: epoch_ms`) + 클라 카운트다운 |
| 시나리오 1편 컨텐츠 | 사용자 별도 트랙 작성 (placeholder `toy-manor.json` 우리가 작성) |

## 5개 체크포인트

| 체크포인트 | Task 범위 | 데모 가능 행동 |
|---|---|---|
| **A — A2 walking skeleton** | T-00 ~ T-04 | 3 단말 lobby 합류 → "게임 시작" 활성 직전 |
| **B — A3 + 라운드 1 진입** | T-05 ~ T-08 | 캐릭터 자동 배정 → 라운드 1 프롬프트 + 카운트다운 |
| **C — A4 라운드 루프 핵심** | T-09 ~ T-12 | 회전 턴 조사 + 단서 monotonic + 아이템 + 밀담 |
| **D — A5 종료 흐름** | T-13 ~ T-17 | 투표 → 공개 → 미션 → 엔딩 → 설문 → 종료 화면 |
| **E — 견고화** | T-18 ~ T-20 | 재합류 풀 복원 + 텔레메트리 + lobby 만료 |

## 21개 Task 목록

| ID | 제목 | 체크포인트 | User Story |
|---|---|---|---|
| T-00 | 인프라·DB·STOMP 부트스트랩 | A | — |
| T-01 | 시나리오 메타 로더 + Schema 검증 + 카탈로그 API | A | E1-16, E2-10 |
| T-02 | 카탈로그 → 세션 만들기 → 초대 번호 + lobby | A | E1-01 |
| T-03 | 닉네임 합류 + 합류자 목록 양방향 동기 | A | E1-02 |
| T-04 | 인원 매칭 + "게임 시작" 게이트 + 사유 인라인 | A | E1-03, E1-03b |
| T-05 | 게임 시작 → 단계 1·2 + turn_order + 캐릭터 자동 배정 | B | E1-03, E1-04 |
| T-06 | 단계 3 튜토리얼 + 거짓말 정책 + L1 자동 진입 | B | E1-04b |
| T-07 | 라운드 진입 + 프롬프트 + 카운트다운 + k=1 자기소개 + common_hint | B | E1-05 |
| T-08 | 캐릭터 카드 단일 표면 (E2-01·02·04·05 통합) | B | E2-01·02·04·05 |
| T-09 | 회전 턴 30초 + 점유 잠금 + 단서 ACL + 랜덤 자동 선택 | C | E1-10 |
| T-10 | monotonic 누적 + 라운드 자동 전환 | C | E1-11 |
| T-11 | 아이템 교환·전체·부분 공유 + 전원 공개 배너 | C | E1-07·08·09 |
| T-12 | 1:1 밀담 신청·수락·거절 + 동시 한 쌍 + 배너 | C | E1-06, E2-09 |
| T-13 | 단계 8 투표 + 동점 자동 재투표 + 색출 실패 | D | E1-12 |
| T-14 | 9-A 자동 → 9-B 미션 자가 체크 | D | E1-12b, E1-13(체크), E2-03 |
| T-15 | 호스트 강제 진행 + NB3 좁은 인계 | D | E1-13(강제) |
| T-16 | 9-C 엔딩 + 10 디브리프 자동 표시 | D | E1-14 |
| T-17 | 인라인 설문 + 종료 화면 | D | E1-15, E2-11, E2-12 |
| T-18 | 재합류 슬롯 재바인딩 + 풀 스테이트 스냅샷 | E | E1-02b |
| T-19 | session_log 텔레메트리 | E | PRD §5.3 |
| T-20 | lobby 만료 + 오프라인 감지 + 24h 보존 | E | PRD §5.2 AB1 |

## 위험 Top 5

1. **T-09** 회전 턴 30초 서버·클라 시계 race → `deadlineAt` + grace 1s + `SERVER_TIME_SYNC` offset 단위 테스트
2. **T-18** 재합류 풀 스테이트 스냅샷 완전성 → PR 리뷰 체크리스트로 필드 동기 강제
3. **T-15** NB3 좁은 인계가 다른 호스트 액션에 번지면 가드 위반 → 단위 테스트 명시
4. **T-10·T-18** 단서 monotonic 누적의 재합류 복원(꼬리표·발견 장소 라벨) → snapshot DTO 명시
5. **T-01** cross-field 검증(`location_pool ≥ 캐릭터 수` 등) → Java validator + fixture test

## STOMP 토픽 요약

| 방향 | 패턴 | 용도 |
|---|---|---|
| 클라 → 서버 | `/app/session/{id}/...` | 게임 행위 |
| 서버 → 전원 | `/topic/session/{id}/event` | 증분 이벤트 브로드캐스트 |
| 서버 → 전원 | `/topic/session/{id}/state` | 풀 스냅샷 (재합류용) |
| 서버 → 전원 | `/topic/session/{id}/banner` | 전원 공개 배너 |
| 서버 → 개인 | `/user/queue/session/{id}/private` | 본인 카드·단서 |
| 서버 → 개인 | `/user/queue/session/{id}/error` | 에러 인라인 |

## Flyway 마이그레이션 파일 순서

| 파일 | Task | 내용 |
|---|---|---|
| V1__init.sql | T-00 | 부트스트랩 |
| V2__session_player.sql | T-02 | sessions, players |
| V5__game_state.sql | T-05 | sessions state/turn_order 컬럼 추가, players assigned_character_id/tutorial_acked_at/mission_checked_at |
| V3.5__rounds.sql | T-07 | rounds |
| V4__round_turn.sql | T-09 | location_occupancy, clues, clue_acl |
| V5__items.sql | T-11 | item_actions |
| V6__private_talk.sql | T-12 | private_talks |
| V7__vote.sql | T-13 | votes |
| V8__mission.sql | T-14 | mission_checks |
| V9__survey.sql | T-17 | survey_responses |
| V10__session_log.sql | T-19 | session_log |
| V11__cleanup_indexes.sql | T-20 | 청소 인덱스 |

## 골든 패스 E2E (체크포인트 E 완료 후)

Alice(호스트)·Bob·Charlie 3 단말: 카탈로그 → lobby → 시작 → 캐릭터 → 라운드 1 (Bob 강제 종료 → 재합류 verify) → 아이템 교환 + 밀담 → 라운드 2·3 → 동점 투표 → 재투표 → 색출 실패 → 9-A·9-B·9-C → 설문 → 종료 → SQL로 session_log 확인.
