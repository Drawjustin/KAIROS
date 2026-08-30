# 망분리까지 가는 다섯 단계

> KAIROS 실행 계획 · 2026-08-30 작성

민감정보 통제 · 예산 통제 · 장애 격리 · 관측성을 순서대로 붙이고, 마지막에 AWS 위에 실제 분리망을 세워
**"업무망의 인터넷 접근은 0이지만 AI는 쓸 수 있다"** 를 로그로 증명하는 것까지가 이 계획의 범위입니다.

| Phase | 내용 | 기간 | 마이그레이션 |
| --- | --- | --- | --- |
| 01 | 민감정보 통제 | 3일 | V11 |
| 02 | 한도·예산 통제 | 3일 | V12 |
| 03 | 장애 격리 | 2일 | V13 |
| 04 | 관측성 | 2일 | — |
| 05 | AWS 망분리 | 5일 | — |

---

## 00. 출발점

Kotlin 1.9 / Spring Boot 3.5 / Java 21, PostgreSQL + Flyway `V1`~`V10`, Testcontainers 통합 테스트 11종이 있는 상태입니다.

### 구현되어 있음

- JWT 인증 · refresh 세션
- tenant / project / API key 운영 경계
- Unified Chat API `/api/v1/chat/completions`
- OpenAI · Claude · Gemini 어댑터 + `ProviderRouter`
- project별 allowed model 정책
- 사용량 로그 + QueryDSL 집계 API
- Context Source / Search API + 감사 로그
- MCP JSON-RPC endpoint
- k6 부하 테스트 + mock provider

### 아직 없음

- PII 마스킹 — 프롬프트가 그대로 외부로 나감
- 예산·한도 통제 — 무제한 호출 가능
- Resilience4j — 서킷·격리·fallback 없음
- Actuator / Micrometer — 지표 노출 없음
- **`RestClient` 타임아웃 미설정** — 사실상 무한 대기
- Dockerfile
- CI — commitlint 워크플로만 존재
- Secrets 관리 — API 키가 환경변수

### 순서의 근거

Phase 4는 앞선 세 단계가 만들어내는 사건을 지표로 노출하는 일이므로 반드시 뒤에 오고, Phase 5는 앞의 전부를
"운영되는 시스템"으로 세우는 마지막 단계입니다. Phase 1을 맨 앞에 둔 이유는 금융권 평가자에게 단일 최고
임팩트를 내는 항목이기 때문입니다.

---

## 01. 민감정보 통제

> **목표** — 개인신용정보가 외부 LLM으로 *전송되기 전에* 게이트웨이에서 차단한다.

### 검출 대상과 정책

한국 금융 도메인에 맞춰 주민등록번호, 외국인등록번호, 카드번호, 계좌번호, 휴대전화, 이메일, 여권번호를 다룹니다.
오탐을 줄이기 위해 정규식만으로 끝내지 않고 **카드번호는 Luhn 체크섬**, **주민번호는 생년월일 유효성과 검증번호**를
함께 확인합니다. 숫자 13자리가 전부 주민번호로 잡히면 서비스가 못 쓰이게 됩니다.

정책은 project 단위로 PII 타입별 `ALLOW` / `MASK` / `BLOCK` 세 값을 갖습니다. 사내 상담 봇은 전화번호를 MASK로,
여신 관련 project는 주민번호를 BLOCK으로 두는 식의 차등 운영이 가능해집니다.

### 삽입 지점 — 셋 중 하나라도 빠지면 구멍

| | 파일 | 설명 |
| --- | --- | --- |
| A | `ai/service/UnifiedAiService.kt` | `withDefaultSystemPrompt()` 직후, `providerRouter.route()` 직전. 사용자 프롬프트 본류 |
| B | `ai/service/AiToolExecutor.kt` | 내부 문서 검색 결과를 provider에 되돌려 줄 때. **가장 놓치기 쉬운 경로** — 사내 문서에 담긴 고객정보가 여기로 새어 나갑니다 |
| C | `context/service/ContextSearchService.kt` | 검색 질의. 외부로 나가진 않지만 `context_search_log`에 그대로 적재되므로 로그 자체가 유출 경로가 됩니다 |

### 스키마 · V11

- `project_pii_policy` — project_id, pii_type, action
- `pii_detection_log` — project_id, trace_id, pii_type, detected_count, action, created_at

검출 로그에 **원문도, 마스킹 전 값도, 값의 일부도 저장하지 않습니다.** 남기는 것은 "어떤 타입이 몇 건 검출되어
어떻게 처리됐는가"뿐입니다. 기존 `traceId` MDC로 요청 단위 추적은 이미 가능하므로 원문 없이도 감사에 충분합니다.

### 테스트

- 타입별 검출·마스킹 단위 테스트 (유효/무효 카드번호 각각)
- BLOCK 정책 통합 테스트 — `AI_SENSITIVE_DATA_BLOCKED` 반환 및 provider 미호출 검증
- 도구 실행 결과 마스킹 테스트 — 경로 B 회귀 방지

### 면접에서 쓸 지점

- **감사 로그가 유출 경로가 되는 역설** — 검출 로그에 원문을 남기지 않기로 한 결정과 그 대신 traceId로 추적성을 확보한 설계
- **응답이 아니라 요청을 막는 이유** — 규제가 문제 삼는 것은 부정확한 답변이 아니라 정보의 외부 전송 자체입니다
- **Luhn 체크를 넣은 이유** — 보안 기능이 오탐으로 서비스를 못 쓰게 만들면 결국 우회되고, 우회된 통제는 없는 것과 같습니다

---

## 02. 한도·예산 통제

> **목표** — project 단위 호출·토큰 한도를 *동시 요청 상황에서도 정확히* 지킨다.

### 핵심 난점

AI 호출은 **실행 전에 소비량을 알 수 없습니다.** 토큰 수는 응답이 와야 확정됩니다. 그래서 단순히 "호출 전에 확인하고
호출 후에 더한다"로는 동시 요청에서 한도가 뚫립니다. 카드 결제의 **승인(예약) → 매입(정산) → 취소(보상)** 구조를
그대로 가져옵니다.

### 3단계 흐름

1. **예약** — 호출 전, 조건부 원자적 UPDATE로 호출 건수를 선점합니다.
2. **정산** — 응답의 실제 토큰 수로 `consumed_tokens`를 가산합니다.
3. **보상** — provider 호출이 실패하면 예약분을 되돌립니다.

```sql
-- 예약: affected rows가 0이면 한도 초과 → AI_BUDGET_EXCEEDED
UPDATE project_budget
   SET consumed_requests = consumed_requests + 1
 WHERE project_id = :projectId
   AND consumed_requests + 1 <= request_limit
```

조회 후 판단하는 것이 아니라 **조건을 UPDATE 문 안에 넣어 DB의 행 단위 원자성에 판정을 위임**합니다.
애플리케이션은 영향 행 수만 봅니다.

### 왜 락을 쓰지 않는가

- **비관적 락** — AI 호출은 수 초가 걸립니다. 그동안 예산 행을 잡고 있으면 같은 project의 모든 요청이 직렬화되어 처리량이 무너집니다.
- **낙관적 락** — 인기 project의 예산 행은 경합이 심한 단일 행이라 버전 충돌 재시도가 폭증합니다.
- **조건부 UPDATE** — 락을 애플리케이션이 관리하지 않고, 한 문장의 실행 시간이 밀리초 단위라 경합 구간이 짧습니다.

### 기간 롤오버

스케줄러로 일괄 초기화하지 않고 **요청 시점에 lazy reset**합니다. 이것도 `WHERE period_started_at < :periodStart`
조건부 UPDATE라, 자정 직후 동시에 들어온 요청 중 정확히 하나만 초기화에 성공합니다. 스케줄러를 두면 다중
인스턴스에서 중복 실행 문제가 새로 생깁니다.

### 증거물이 될 테스트

`CountDownLatch`로 100개 스레드를 동시에 풀고, 한도가 100일 때 **정확히 100건만 통과하고 나머지는 전부 거부**되는
것을 검증합니다. 이 테스트 하나가 포트폴리오에서 동시성 서사의 전부를 지탱합니다. 반드시 남기세요.

### 면접에서 쓸 지점

- **카드사 지원 시 그대로 연결** — 예약·정산·보상은 결제 승인 로직과 같은 구조입니다. "AI 호출도 결제 승인과 같은 통제 원칙으로 다뤘다"가 됩니다
- **정합성과 처리량의 트레이드오프** — 토큰은 사후 정산이라 한 요청만큼 초과될 수 있습니다. 이 오차를 왜 허용했는지(호출 전 토큰 수를 알 수 없음) 설명할 수 있어야 합니다

---

## 03. 장애 격리

> **목표** — 한 provider의 장애가 KAIROS 전체를 멈추지 않게 하고, 가능하면 다른 모델로 서비스를 이어간다.

### 가장 먼저 할 일

Resilience4j보다 먼저 **`RestClient` 타임아웃 설정**입니다. 지금 세 어댑터 모두 `RestClient.Builder`를 기본값으로
쓰고 있어 provider가 응답하지 않으면 무한정 대기합니다. connect 2초 / read 30초부터 잡고 시작합니다.
타임아웃 없는 서킷 브레이커는 열리지 않습니다.

### 네 겹의 방어

| 장치 | 적용 단위 | 막는 문제 |
| --- | --- | --- |
| Timeout | 어댑터별 RestClient | 무한 대기 |
| Bulkhead | provider별 동시 호출 수 | 느린 provider 하나가 톰캣 스레드 풀 전체를 잠식하는 것 — **이것이 진짜 격리** |
| CircuitBreaker | provider별 인스턴스 | 죽은 provider를 계속 두드리며 지연을 쌓는 것 |
| Retry | 5xx · 타임아웃 한정 | 일시적 오류. **4xx는 재시도 금지** — 인증·정책 오류를 재시도해도 결과는 같고 부하만 늡니다 |

### Fallback 라우팅

`ProviderRouter`를 확장해, 서킷이 열린 provider의 요청을 해당 project의 `allowed_model` 중 **다른 provider에 속한
대체 모델**로 재라우팅합니다. 정책에 없는 모델로는 절대 우회하지 않습니다 — 장애 대응이 권한 통제를 무너뜨리면 안 됩니다.
`ai_usage_log`에 `is_fallback`, `fallback_from_model`을 추가해 대체 호출을 추적합니다.

### 검증

이미 있는 `tools/load-test/mock-openai-provider.mjs`에 오류 모드를 추가하면 됩니다. 500을 연속 반환시켜 서킷을 열고,
fallback이 Claude로 넘어가는지, 사용량 로그에 대체 이력이 남는지 확인합니다. k6 시나리오를 재사용해
**장애 주입 전후 실패율 비교 그래프**를 뽑아두면 그대로 자소서 자료가 됩니다.

### 면접에서 쓸 지점

- **"retry만 넣으면 왜 위험한가"** — 장애 상황에서 재시도는 부하를 증폭시켜 복구를 늦춥니다(retry storm). fail-fast로 빠르게 포기하고, bulkhead로 피해를 가두고, fallback으로 연속성을 확보하는 3단 구성이라는 설명
- **fallback이 정책을 우회하지 않도록 한 설계** — 가용성과 통제 중 통제를 우선한 판단

---

## 04. 관측성

> **목표** — 앞선 세 단계가 만들어내는 사건을 지표로 노출하고, 대시보드 스크린샷을 산출물로 남긴다.

### 노출할 지표

| 메트릭 | 타입 | 태그 |
| --- | --- | --- |
| `kairos_ai_request_total` | Counter | provider, model, status |
| `kairos_ai_latency_seconds` | Timer | provider, model |
| `kairos_ai_token_total` | Counter | provider, type |
| `kairos_pii_detection_total` | Counter | pii_type, action |
| `kairos_budget_rejection_total` | Counter | period |
| `resilience4j_circuitbreaker_state` | Gauge | 자동 노출 |

### 태그에 project를 넣지 않는다

`tenant_id`나 `project_id`를 태그로 넣으면 시계열 수가 project 수만큼 곱해집니다. project는 계속 늘어나므로
카디널리티가 사실상 무제한이 되고, Prometheus 메모리가 먼저 무너집니다.

**메트릭은 "시스템이 건강한가"를 보는 운영 지표로 한정**하고, "어느 project가 얼마나 썼는가"는 이미 만들어 둔
QueryDSL 집계 API가 담당합니다. 역할을 나눈 근거를 설명할 수 있으면 실무 감각으로 읽힙니다.

### 노출 경계

management port를 `9090`으로 분리하고 `/actuator/**`는 서비스 포트에서 접근할 수 없게 합니다. Phase 5에서 이 포트는
VPC 내부에서만 열립니다. 지표 엔드포인트는 내부 구조를 그대로 드러내므로 운영 경계 밖으로 나가면 안 됩니다.

### 산출물

- `docker-compose.yml`에 Prometheus · Grafana 추가 (현재 postgres만 있음)
- 대시보드 JSON을 `ops/grafana/`에 커밋 — 재현 가능해야 산출물입니다
- README에 대시보드 스크린샷. 부하 테스트 중 캡처하면 그래프가 살아 있습니다

---

## 05. AWS 망분리

> **증명할 한 문장** — 업무망의 인터넷 접근은 0이지만 AI는 쓸 수 있다. 그리고 모든 호출은 기록된다.

### 규제 배경

전자금융감독규정은 금융회사의 내부 업무망을 외부 통신망과 분리하도록 요구합니다. 최근 금융당국은 생성형 AI 활용을
위해 이 규제를 단계적으로 완화하는 방향을 제시했고, 완화의 조건은 대체로 **접근 통제 · 전송 데이터 통제 · 로깅**으로
모입니다.

> 규정의 세부 조문과 최신 개정 내용은 지원 직전에 금융위·금감원 원문으로 한 번 확인해 두세요.
> 면접에서 조문 번호를 틀리는 것보다 안 꺼내는 편이 낫습니다.

중요한 것은 이 지점에서 **KAIROS의 존재 이유가 규제 문장과 정확히 맞물린다**는 사실입니다. 업무망은 인터넷에 나갈 수
없고, 통제된 단일 출구 하나만 허용된다. 그 출구가 KAIROS입니다.

### VPC 설계

```
VPC 10.0.0.0/16 · ap-northeast-2
│
├─ Public              10.0.1.0/24    [0.0.0.0/0 → IGW]
│    ALB · NAT instance(t4g.nano)
│
├─ App · Private       10.0.11.0/24   [0.0.0.0/0 → NAT]
│    KAIROS · Squid(egress allowlist) · Prometheus · Grafana
│
├─ Workload · 업무망     10.0.12.0/24   [기본 경로 없음]
│    내부 클라이언트 EC2
│
└─ Data · Isolated     10.0.21.0/24   [기본 경로 없음]
     RDS PostgreSQL (퍼블릭 액세스 off)
```

설계의 핵심은 보안 그룹이 아니라 **라우팅 테이블**에 있습니다. 업무망 서브넷의 라우팅 테이블에는 `0.0.0.0/0` 경로가
아예 없습니다. 보안 그룹은 규칙을 잘못 고치면 뚫리지만, 경로가 없는 서브넷은 나갈 방법 자체가 없습니다.
"설정으로 막았다"와 "구조적으로 못 나간다"는 다른 이야기이고, 후자가 망분리입니다.

### 트래픽 경로

```
ALLOW    업무망 EC2 → KAIROS → Squid → NAT → api.openai.com
DENY     업무망 EC2 ╳ 인터넷                      (라우팅 없음)
DENY     KAIROS → Squid ╳ 허용 목록 밖 도메인       (403)
```

### 통제 장치 여섯

| # | 장치 | 역할 |
| --- | --- | --- |
| 1 | 업무망 라우팅 테이블에 기본 경로 없음 | 구조적 차단. 망분리의 실체 |
| 2 | App 서브넷만 NAT 경유 egress | 외부로 나가는 유일한 구간 |
| 3 | Squid forward proxy 도메인 allowlist | 세 개 provider 도메인만 통과. KAIROS의 `RestClient`에 프록시 지정 |
| 4 | VPC Endpoint (S3 · ECR · Secrets Manager · SSM) | AWS 서비스 트래픽이 인터넷을 타지 않음 |
| 5 | SSM Session Manager 전용 접속 | 22번 포트 전면 폐쇄, bastion 없음, 접속 이력이 CloudTrail에 남음 |
| 6 | VPC Flow Logs | **거부 기록이 곧 증거물** |

3번은 원래 AWS Network Firewall의 도메인 필터링으로 하는 것이 정석이지만 월 30만 원을 넘습니다. 포트폴리오에는
과하므로 `t4g.nano`에 올린 Squid로 대체하고, README에 "실무에서는 Network Firewall이나 보안 웹 게이트웨이로
대체되는 구간"이라고 명시하세요. 한계를 알고 대체한 것과 모르고 빠뜨린 것은 면접에서 완전히 다르게 읽힙니다.

### 검증 시나리오 — 이것이 최종 산출물

| | 시도 | 결과 | 증거 |
| --- | --- | --- | --- |
| A | 업무망 EC2에서 `curl https://api.openai.com` | 차단 | VPC Flow Log REJECT 기록 |
| B | 같은 EC2에서 KAIROS `/api/v1/chat/completions` | 정상 | `ai_usage_log` + `pii_detection_log` |
| C | KAIROS EC2에서 `curl https://www.google.com` | 403 | Squid `access.log` |
| D | 인터넷에서 RDS 5432 접속 | 차단 | 퍼블릭 IP 미할당 · 보안 그룹 |
| E | 업무망 EC2에 SSH 접속 | SSM만 허용 | CloudTrail 세션 기록 |

다섯 시나리오의 터미널 출력과 로그 캡처를 README에 그대로 붙이세요. 아키텍처 다이어그램 열 장보다
**A와 B가 나란히 놓인 캡처 한 장**이 훨씬 강합니다.

### 선행 작업

Phase 5에 들어가기 전에 지금 없는 것들을 만들어야 합니다. Phase 3~4와 병행 가능합니다.

- **Dockerfile** — multi-stage, Temurin JRE 21 런타임
- **GitHub Actions** — build → Testcontainers 테스트 → ECR push (현재 commitlint 워크플로만 있음)
- **Secrets Manager 이전** — provider API 키를 환경변수에서 분리. 망분리 서사와 직결됩니다
- **Terraform `infra/`** — 서버를 내려도 코드는 남습니다. 이쪽이 진짜 산출물입니다

### 비용

| 항목 | 정석 구성 | 절감 구성 |
| --- | --- | --- |
| 진입점 | ALB ≈ ₩25,000 | EC2 직결 + Caddy · ₩0 |
| Egress | NAT Gateway ≈ ₩45,000 | NAT instance ≈ ₩5,000 |
| 앱 · 업무망 EC2 | t4g.small ×2 ≈ ₩30,000 | t4g.micro ×2 ≈ ₩15,000 |
| Squid | t4g.nano ≈ ₩5,000 | t4g.nano ≈ ₩5,000 |
| DB | RDS db.t4g.micro ≈ ₩25,000 | EC2 내 컨테이너 · ₩0 |
| **월 합계** | **≈ ₩130,000** | **≈ ₩25,000** |

서울 리전 대략값이며 데이터 전송량은 제외했습니다. 실제 청구액은 반드시 Cost Explorer로 확인하세요.

다만 **한 달 내내 켜 둘 필요가 없습니다.** Terraform으로 `apply` → 시나리오 실행 및 캡처 → `destroy`를 반복하면
며칠분 시간당 요금만 나갑니다. 남는 것은 캡처와 Terraform 코드이고, 그 둘이 평가 대상입니다.

### 면접에서 쓸 지점

- **보안 그룹이 아니라 라우팅으로 막은 이유** — 설정 실수로 뚫릴 수 있는 통제와 구조적으로 불가능한 통제의 차이
- **단일 출구(choke point) 설계** — 통제 지점을 하나로 모아야 로깅·정책·감사가 성립한다는 논리. 이것이 KAIROS라는 프로젝트 전체의 정당화이기도 합니다
- **비용 때문에 Network Firewall 대신 Squid를 쓴 판단** — 제약 안에서 설계 목표를 지킨 사례로 이야기할 수 있습니다

---

## 06. 먼저 정할 것

Phase 1~4는 이 계획대로 바로 시작할 수 있습니다. Phase 5는 아래 셋이 구성을 바꿉니다.

**AWS 예산 상한은 얼마인가**
ALB와 NAT Gateway 채택 여부가 갈립니다. *권장: 절감 구성으로 시작* — 망분리 서사에 ALB는 기여하지 않습니다.

**Terraform인가 콘솔 수작업인가**
*강력히 Terraform 권장.* 서버를 내리면 콘솔 작업은 아무것도 남지 않지만 Terraform 코드는 레포에 남아 그 자체로
평가 대상이 됩니다. 학습 비용은 이 규모에서 하루면 충분합니다.

**EC2 + docker compose인가 ECS Fargate인가**
*권장: EC2 + docker compose.* 이 프로젝트의 주인공은 망분리이지 오케스트레이션이 아닙니다. Fargate는 비용과
복잡도를 올리면서 서사를 분산시킵니다.
