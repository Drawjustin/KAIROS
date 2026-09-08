# 망분리 검증 결과

실행일: 2026-09-08 · AWS ap-northeast-2 · 계정 676206941757

```
Public    10.0.1.0/24    NAT + Squid   i-0c4d2ad5580818352   3.36.135.181 / 10.0.1.152
App       10.0.11.0/24   KAIROS        i-0feb92214b4e37f27   10.0.11.42   (공인 IP 없음)
Workload  10.0.12.0/24   업무망         i-0411d9756429e7250   10.0.12.82   (공인 IP 없음)
```

---

## A · 업무망에서 인터넷 (차단)

```
$ curl https://api.openai.com
curl: (28) Connection timed out after 8001 milliseconds
```

## B · 같은 서버에서 KAIROS 호출 (성공)

```
$ curl -X POST http://10.0.11.42:8080/api/v1/chat/completions -d '{}'
{"errorCode":"COMMON_999","errorMessage":"Internal server error"}

$ curl http://10.0.11.42:9090/actuator/health
{"status":"UP"}
```

같은 서버에서 인터넷은 끊기고 KAIROS는 응답한다.
게이트웨이가 요청을 받아 처리하고 자신의 에러 형식으로 답한 것이므로,
네트워크가 아니라 애플리케이션이 살아 있다는 증거다.

## C · KAIROS에서 허용 목록 밖 (차단)

```
$ curl --noproxy '*' https://api.openai.com          # 프록시 우회 시도
000                                                   # NAT의 FORWARD REJECT

$ curl --proxy http://10.0.1.152:3128 https://www.google.com
000                                                   # Squid가 CONNECT 거부
```

## D · KAIROS에서 허용 도메인 (통과)

```
$ curl --proxy http://10.0.1.152:3128 https://api.openai.com/v1/models
HTTP 401                                              # provider가 응답 (인증만 실패)

$ curl --proxy http://10.0.1.152:3128 https://api.anthropic.com/v1/messages
HTTP 405                                              # provider가 응답
```

401과 405는 **외부 provider가 직접 돌려준 응답**이다. 연결 자체는 성립했다는 뜻이다.

## Squid 판정 기록

```
TCP_DENIED/403     www.google.com:443                 ← 허용 목록 밖
TCP_TUNNEL/200     api.openai.com:443                 ← 허용
TCP_TUNNEL/200     api.anthropic.com:443              ← 허용
TCP_TUNNEL/200     registry-1.docker.io:443           ← 허용
TCP_DENIED/403     production.cloudfront.docker.com   ← 목록에 없어 차단
```

## E · 업무망 격리 — VPC Flow Log

```
업무망(10.0.12.82) 발신 기록 119건, 전부 VPC 내부

  -> 10.0.11.219 :443    ACCEPT  x60     VPC 엔드포인트 (SSM)
  -> 10.0.11.206 :443    ACCEPT  x46     VPC 엔드포인트
  -> 10.0.11.45  :443    ACCEPT  x9      VPC 엔드포인트
  -> 10.0.11.42  :8080   ACCEPT  x2      KAIROS 서비스
  -> 10.0.11.42  :9090   ACCEPT  x2      KAIROS 지표

외부로 향하는 기록: 0건
```

**인터넷으로 나가려는 시도가 REJECT로 기록되지도 않았다.**
보안 그룹으로 막으면 패킷이 인터페이스에 도달한 뒤 거부되어 REJECT가 남지만,
경로가 없으면 라우팅 단계에서 사라져 기록조차 생기지 않는다.

이것이 "설정으로 막았다"와 "구조적으로 못 나간다"의 차이다.

## 업무망 라우팅 테이블

```
10.0.0.0/16   -> local                     active
(prefix list) -> vpce-0a4bfb8c3d52140f7    active     S3 게이트웨이 엔드포인트

0.0.0.0/0     경로 없음
```

## 접속 경로

3대 모두 SSH 포트를 열지 않았다. SSM Session Manager로만 접속하며,
인터넷이 없는 업무망도 VPC 엔드포인트를 통해 붙는다.

```
i-0feb92214b4e37f27   Online   Amazon Linux   SSM Agent 3.3.4624.0
i-0411d9756429e7250   Online   Amazon Linux   SSM Agent 3.3.4624.0
i-0c4d2ad5580818352   Online   Amazon Linux   SSM Agent 3.3.4624.0
```

---

# 종단 검증 — 인터넷은 막힌 채로 AI 답변 받기

2차 실행 (2026-09-09). provider API 키를 Secrets Manager에 넣고
업무망에서 KAIROS를 통해 실제 AI 답변을 받는 데까지 확인했다.

```
App       10.0.11.217   i-0855314582c4d5c62
Workload  10.0.12.x     i-0319bfa030143e753   (공인 IP 없음, 기본 경로 없음)
```

## 같은 서버에서

```console
$ curl https://api.openai.com/v1/models
curl: (28) Connection timed out after 8000 milliseconds

$ curl -X POST http://10.0.11.217:8080/api/v1/chat/completions \
    -H "Authorization: Bearer kairos_sk_..." \
    -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"망분리가 무엇인지 한 문장으로 설명해줘."}]}'

model  : gpt-4o-mini-2024-07-18
answer : 망분리는 외부 네트워크와 내부 네트워크를 물리적으로 또는 논리적으로
         분리하여 보안과 데이터 보호를 강화하는 기술입니다.
tokens : {'prompt_tokens': 59, 'completion_tokens': 35, 'total_tokens': 94}
```

인터넷으로는 나갈 수 없는 서버에서 AI 답변을 받았다.
경로는 `업무망 → KAIROS → Squid(허용 목록) → NAT → api.openai.com` 하나뿐이다.

## 민감정보 차단

```console
$ curl ... -d '{"messages":[{"content":"고객 홍길동 주민번호 900101-1234567 조회해줘"}]}'
{"errorCode":"AI_011","errorMessage":"주민등록번호 이(가) 포함되어 요청을 차단했습니다"}
HTTP 403
```

provider까지 가지 않고 게이트웨이에서 끊었다.

## 민감정보 마스킹

```console
$ curl ... -d '{"messages":[{"content":"연락처 010-1234-5678 로 안내문 초안을 한 문장으로 써줘"}]}'
answer: 연락처 010-****-5678로 문의해 주시면 친절히 안내해 드리겠습니다.
```

**모델이 마스킹된 값을 그대로 되받았다.** 원본 번호가 아니라 가려진 값이
실제로 provider까지 갔다는 뜻이다. 마스킹이 전송 전에 일어났음을 모델의 답변이 증명한다.

## 원장 기록

```
ai_usage_log
provider | model       | status  | error_code | tokens | latency | trace
OPENAI   | gpt-4o-mini | SUCCESS |            |     94 |  3068ms | 653efd2bca8c
OPENAI   | gpt-4o-mini | FAILED  | AI_011     |      0 |    27ms | b8954c6461d0
OPENAI   | gpt-4o-mini | SUCCESS |            |     92 |  1483ms | 9ea1dea42a48

pii_detection_log
source      | pii_type                     | count | action | trace
CHAT_PROMPT | RESIDENT_REGISTRATION_NUMBER |     1 | BLOCK  | b8954c6461d0
CHAT_PROMPT | PHONE_NUMBER                 |     1 | MASK   | 9ea1dea42a48
```

`traceId`가 두 로그를 잇는다. `b8954c6461d0`은 주민번호 때문에 차단된 요청이고,
`9ea1dea42a48`은 전화번호를 가린 뒤 성공한 요청이다.

**검출 로그에는 값이 없다.** 어떤 종류가 몇 건 걸려 어떻게 처리됐는지만 남고,
원문을 보려면 어디를 뒤져도 없다. 차단이 27ms에 끝난 것은 provider를 부르지 않았기 때문이다.

## API 키 관리

provider API 키는 Secrets Manager에 두고 인스턴스가 부팅 때 받아 간다.
코드에도, Terraform 상태 파일에도, 사용자 데이터에도 남지 않는다.

Secrets Manager는 AWS API라 프록시로 우회되지 않으므로 VPC 엔드포인트를 통해서만 닿는다.
인터넷 없는 구간에서 AWS 서비스를 쓰려면 엔드포인트가 유일한 길이라는 제약이 여기서도 드러난다.
