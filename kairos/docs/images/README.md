# 대시보드 캡처

`ops/grafana/dashboards/kairos-gateway.json`을 실제 데이터로 렌더링한 결과다.
화면을 손으로 캡처하지 않고 Grafana 렌더링 서버로 뽑았으므로 같은 그림을 다시 만들 수 있다.

| 파일 | 내용 |
| --- | --- |
| `grafana-dashboard.png` | 대시보드 전체 (패널 7개) |
| `panel-ai-requests.png` | AI 호출량 (성공/실패) |
| `panel-failure-reasons.png` | 실패 원인 (에러코드별) |
| `panel-pii-detection.png` | 민감정보 검출 (종류/처리) |
| `panel-fallback.png` | 대체 provider 전환 |
| `panel-latency-p95.png` | 응답 지연 p95 |

## 어떻게 만들었나

로컬 스택을 띄우고 성공·차단·한도초과·대체를 섞은 트래픽을 흘린 뒤,
Grafana 렌더링 서버에 PNG를 요청했다.

```bash
docker compose up -d postgres prometheus grafana
./gradlew bootRun
# 트래픽 생성 후
curl -o grafana-dashboard.png \
  "http://localhost:3000/render/d/kairos-gateway/kairos-gateway?kiosk&from=now-45m&to=now&width=1400&height=1080&scale=2"
```

렌더링 서버는 캡처할 때만 붙였다 뗀다. 평소 구성에는 필요 없다.

```yaml
# docker-compose.render.yml (임시)
services:
  renderer:
    image: grafana/grafana-image-renderer:latest
  grafana:
    environment:
      GF_RENDERING_SERVER_URL: http://renderer:8081/render
      GF_RENDERING_CALLBACK_URL: http://grafana:3000/
```

## 대체 provider 전환은 장애를 주입해 만들었다

평소에는 이 패널이 비어 있다. OpenAI가 죽어야 나타나기 때문이다.
기동 시 base-url을 닿을 수 없는 주소로 돌려 장애를 만들었다.

```bash
KAIROS_AI_OPENAI_BASE_URL="http://127.0.0.1:9" ./gradlew bootRun
```

그 상태에서 `gpt-4o-mini`를 요청하면 Claude가 답한다.
서킷 브레이커 패널에 `openai / open`이 서는 것과, 대체 전환이 19건 쌓이는 것이 같은 시각에 보인다.
