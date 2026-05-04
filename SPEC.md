# SPEC: BYOD 머더 미스터리 웹앱

**Date:** 2026-05-04
**Source:** docs/02-prd/03-user-story-map-byod.md

---

## 1. Objective

같은 공간에 모인 친구 3–6명이 앱을 열고 5분 안에 머더 미스터리를 시작해, 한 편을 끝까지 완주한다.
호스트가 GM 역할에 묶이지 않고 본인도 플레이어로 참여한다.

**1차 가치:** 즉흥 시작 (모임 자리에서 바로)
**2차 가치:** 한 편 완주 (전 페이즈에 걸친 몰입 보존)

---

## 2. MVP 범위

user-story-map §4.1 기준. 활동 순서 A2 → A3 → A4 → A5.

| 활동 | 핵심 |
|------|------|
| **A2** 세션 시작 | 6자리 초대 번호, 닉네임 합류, 인원 매칭 시 시작 |
| **A3** 캐릭터 진입 | 자동 배정, 단계 1·2·3 자동 재생, 첫 자기소개 트리거 |
| **A4** 라운드 루프 | 타이머·프롬프트, 장소 조사(turn-order), 아이템 3행위, 밀담, monotonic 단서 누적 |
| **A5** 결말 | 투표·동점 재투표, 범인 공개, 미션 체크, 엔딩, 인라인 설문, 종료 화면 |

**MVP 가드 (기능 제거로 구현):**
- L1 비개입 — 알리바이 검증·모순 감지 없음
- 밀담 내용 채널(채팅·음성) 미도입 — 대화는 대면
- 인게임 NPS / 라운드 사이 설문 미도입
- 온보딩 화면 미도입 — 카탈로그가 첫 화면

---

## 3. Tech Stack

| 계층 | 기술 | 버전 |
|------|------|------|
| Frontend 런타임 | React | 19.2.5 |
| 타입 | TypeScript | 6.0.3 |
| 빌드 | Vite | 8.0.10 |
| UI 컴포넌트 | shadcn/ui | CLI v4 (`npx shadcn@latest`) |
| 스타일 | Tailwind CSS | 4.2.4 |
| Backend | Spring Boot | 4.0.6 |
| JDK | Java | 25 LTS |
| 빌드 도구 | Gradle (Kotlin DSL) | — |
| 실시간 통신 | WebSocket (STOMP over WebSocket) | Spring Boot built-in |
| Database | PostgreSQL | 18.3 (Docker) |
| 컨테이너 | Docker Compose | — |

**실시간 아키텍처:** 세션 내 모든 이벤트(합류·라운드 전환·장소 조사·아이템 공유·밀담·투표)는 WebSocket STOMP 토픽 브로드캐스트로 처리한다. REST는 초기 로드·세션 생성에만 사용한다.

---

## 4. Project Structure

```
murder-mystery/
├── frontend/                  # React + TypeScript
│   ├── src/
│   │   ├── components/        # shadcn/ui 기반 공유 컴포넌트
│   │   ├── pages/             # 라우트별 페이지
│   │   ├── hooks/             # 커스텀 훅 (WebSocket 포함)
│   │   └── lib/               # API 클라이언트, 유틸
│   ├── package.json
│   └── vite.config.ts
├── backend/                   # Spring Boot
│   ├── src/main/java/
│   │   └── com/murdermystery/
│   │       ├── session/       # 세션·플레이어 도메인
│   │       ├── game/          # 라운드·아이템·밀담 도메인
│   │       └── ws/            # WebSocket 설정·핸들러
│   └── build.gradle.kts
├── docker-compose.yml         # PostgreSQL 18.3
└── SPEC.md
```

패키지 최상위: `com.murdermystery`

---

## 5. Code Style

**Frontend**
- 파일명: `PascalCase` (컴포넌트), `camelCase` (훅·유틸)
- 훅은 `use` 접두사
- 컴포넌트는 named export만 사용

**Backend**
- 표준 Java 컨벤션 (패키지 소문자, 클래스 PascalCase)
- Controller → Service → Repository 레이어드 아키텍처
- WebSocket 메시지 DTO에 `record` 사용

**공통**
- 주석은 WHY가 비자명할 때만

---

## 6. Testing

**Frontend:** Vitest (단위) — 핵심 상태 전환 로직 위주
**Backend:** JUnit 5 (Spring Boot 기본 포함) — 게임 규칙 단위 테스트 위주
UI 통합 테스트·E2E는 MVP 이후 추가 검토.

---

## 7. Boundaries

**Always (항상 지킬 것)**
- 실시간 이벤트는 WebSocket STOMP로 처리
- L1 비개입 — 앱은 알리바이를 검증하거나 모순을 감지하지 않음
- Host 비대칭을 늘리는 신규 기능은 도입하지 않음 (A2.5·A5.4 두 곳만 예외)
- 단서는 monotonic 누적 — 이전 라운드 단서 삭제 금지

**Ask First (먼저 물어볼 것)**
- 새로운 외부 라이브러리 추가
- DB 스키마 변경
- WebSocket 외 실시간 통신 방식 변경

**Never (하지 않을 것)**
- 밀담 내용 채널(채팅·음성) 구현
- 게임 진행 중 설문·NPS 노출
- 온보딩/튜토리얼 화면 추가 (카탈로그가 첫 화면)
- L1(알리바이·추리) 영역에 앱 개입 로직 추가
