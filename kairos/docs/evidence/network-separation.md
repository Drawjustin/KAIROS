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
