# 관측 스택 실행

애플리케이션은 호스트에서, Prometheus와 Grafana는 컨테이너로 띄우는 로컬 구성이다.

```bash
docker compose up -d postgres prometheus grafana
./gradlew bootRun
```

| | 주소 | 비고 |
| --- | --- | --- |
| 서비스 API | http://localhost:8080 | 지표 엔드포인트는 열리지 않는다 |
| 지표 | http://localhost:9090/actuator/prometheus | 관리 포트 |
| Prometheus | http://localhost:9091 | 컨테이너 내부는 9090 |
| Grafana | http://localhost:3000 | 로그인 없이 열린다 (로컬 전용) |

대시보드는 `KAIROS / KAIROS Gateway`로 자동 등록된다.

## 포트를 나눈 이유

지표 엔드포인트는 JVM 상태, 커넥션 풀, 서킷 상태까지 내부 구조를 그대로 드러낸다.
서비스 포트와 같은 곳에 두면 API를 여는 순간 함께 열린다.

`management.server.port`로 분리해 두면 Phase 5에서 보안 그룹 규칙 한 줄로
"9090은 VPC 안에서만"이 성립한다. ALB는 8080만 바라본다.

`/actuator` 체인에는 JWT 필터를 태우지 않았다. Prometheus가 15초마다 긁어갈 때마다
토큰을 발급받게 만들 수는 없고, 포트 자체가 경계 역할을 하는 것이 전제다.
그래서 이 포트를 외부에 노출하지 않는 것이 설계의 일부다.

## 대시보드가 답하는 질문

| 패널 | 질문 |
| --- | --- |
| AI 호출량 | 지금 얼마나 들어오고 있고 실패는 늘고 있나 |
| 응답 지연 p95 | 어느 provider가 느려지고 있나 |
| 실패 원인 | **우리가 막은 것인가, 저쪽이 죽은 것인가** |
| 서킷 브레이커 상태 | 어느 provider를 지금 끊어두고 있나 |
| 민감정보 검출 | 어떤 종류가 얼마나 걸리고 있나 |
| 예산 한도 거절 | 한도가 실제 사용량에 비해 낮게 잡혀 있나 |
| 대체 provider 전환 | 장애 대응이 실제로 작동했나 |

세 번째가 이 대시보드의 핵심이다. 실패를 한 덩어리로 세면 통제가 작동한 것과
장애가 난 것을 구분할 수 없다. 에러코드로 나눠 센다.

| 코드 | 의미 |
| --- | --- |
| `AI_011` | 민감정보 차단 |
| `AI_012` | 예산 한도 초과 |
| `AI_013` | 동시 호출 격리 (Bulkhead 포화) |
| `AI_014` | provider 장애 또는 서킷 차단 |
| `COMMON_999` | 그 외 서버 오류 |

## 지표에 project를 넣지 않은 이유

`project`와 `tenant`는 운영 중에 계속 늘어난다. 태그로 넣으면 시계열 수가 사실상
무제한이 되어 Prometheus 메모리가 먼저 무너진다.

- **지표**는 "시스템이 지금 건강한가"만 본다. 태그는 전부 enum이라 종류가 코드에 고정되어 있다.
- **"어느 project가 얼마나 썼는가"** 는 `/api/platform/.../usage` 집계 API가 답한다.

두 질문의 저장소를 나눈 것이고, 실수로 늘어나는 값을 태그에 넣으면
`KairosMetricsTests`가 막는다.

## 대시보드 수정

`allowUiUpdates: false`로 두었다. 화면에서 고친 내용은 저장되지 않는다.
`ops/grafana/dashboards/kairos-gateway.json`이 원본이고, 고친 뒤 Grafana를 다시 띄우면 반영된다.

서버를 내려도 이 파일은 남는다. 재현할 수 없으면 산출물이라고 할 수 없다.
