#!/bin/bash
set -euxo pipefail

# ---------------------------------------------------------------------------
# KAIROS가 도는 인스턴스
#
# 이 구간은 NAT를 거쳐야만 외부로 나갈 수 있고, NAT는 전달 트래픽의 443/80을 끊어 두었다.
# 따라서 여기서는 패키지를 받는 것조차 프록시를 지나야 한다.
# 프록시 설정이 다른 무엇보다 먼저 와야 하는 이유다.
#
# 이 설정 자체는 편의가 아니라 필수다. 통제는 NAT 쪽 iptables가 하고,
# 여기서는 그 통제를 지나는 유일한 방법을 지정할 뿐이다.
# ---------------------------------------------------------------------------

PROXY="http://${proxy_host}:3128"
NO_PROXY_LIST="localhost,127.0.0.1,169.254.169.254,.amazonaws.com,${app_subnet_cidr}"

export HTTP_PROXY="$PROXY"
export HTTPS_PROXY="$PROXY"
export NO_PROXY="$NO_PROXY_LIST"

# dnf는 환경변수를 보지 않는다. 설정 파일에 적어야 프록시를 지난다.
echo "proxy=$PROXY" >> /etc/dnf/dnf.conf

# 프록시가 뜨기 전에 이 스크립트가 먼저 도는 경우가 있다.
# 두 인스턴스가 동시에 만들어지므로 몇 초에서 몇 분까지 벌어질 수 있다.
for attempt in $(seq 1 60); do
  if curl -sS --max-time 5 --proxy "$PROXY" -o /dev/null "https://${package_host}/"; then
    break
  fi
  echo "waiting for the egress proxy (attempt $attempt)"
  sleep 10
done

dnf install -y docker
systemctl enable --now docker

# 컨테이너 런타임이 이미지를 받을 때도 같은 경로를 지난다.
mkdir -p /etc/systemd/system/docker.service.d
cat > /etc/systemd/system/docker.service.d/proxy.conf <<PROXYCONF
[Service]
Environment="HTTP_PROXY=$PROXY"
Environment="HTTPS_PROXY=$PROXY"
Environment="NO_PROXY=$NO_PROXY_LIST"
PROXYCONF

systemctl daemon-reload
systemctl restart docker

# 셸에서 확인할 때도 같은 경로를 쓰도록 맞춰 둔다.
cat > /etc/profile.d/proxy.sh <<PROFILECONF
export HTTP_PROXY="$PROXY"
export HTTPS_PROXY="$PROXY"
export NO_PROXY="$NO_PROXY_LIST"
PROFILECONF

# ---------------------------------------------------------------------------
# KAIROS와 데이터베이스
#
# 절감 구성이라 RDS를 쓰지 않고 같은 호스트의 컨테이너로 띄운다.
# 데모용이며, 실제 운영에서는 데이터 구간을 별도 서브넷의 관리형 DB로 분리한다.
# ---------------------------------------------------------------------------

dnf install -y docker-compose-plugin || true

mkdir -p /opt/kairos
cat > /opt/kairos/compose.yaml <<'COMPOSE'
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: kairos
      POSTGRES_PASSWORD: $${POSTGRES_PASSWORD}
      POSTGRES_DB: kairos
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U kairos"]
      interval: 10s
      timeout: 5s
      retries: 10

  kairos:
    image: $${KAIROS_IMAGE}
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      KAIROS_API_DB_URL: jdbc:postgresql://postgres:5432/kairos
      KAIROS_API_DB_USERNAME: kairos
      KAIROS_API_DB_PASSWORD: $${POSTGRES_PASSWORD}
      KAIROS_JWT_SECRET: $${JWT_SECRET}
      # 외부 provider 호출도 프록시를 지나야 한다.
      JAVA_TOOL_OPTIONS: >-
        -XX:MaxRAMPercentage=70
        -Dhttp.proxyHost=$${PROXY_HOST} -Dhttp.proxyPort=3128
        -Dhttps.proxyHost=$${PROXY_HOST} -Dhttps.proxyPort=3128
        -Dhttp.nonProxyHosts=localhost|127.0.0.1|postgres
    ports:
      # 업무망이 부를 서비스 포트와, VPC 안에서만 여는 지표 포트.
      - "8080:8080"
      - "9090:9090"

volumes:
  postgres_data:
COMPOSE

# 비밀번호는 코드에도 상태 파일에도 남기지 않는다. 부팅 때 만들어 이 호스트에만 둔다.
cat > /opt/kairos/.env <<ENVFILE
POSTGRES_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -hex 32)
KAIROS_IMAGE=${image_uri}
PROXY_HOST=${proxy_host}
ENVFILE
chmod 600 /opt/kairos/.env

# 이미지 주소를 넘기지 않으면 인프라만 세우고 애플리케이션은 나중에 올린다.
if [ -n "${image_uri}" ]; then
  cd /opt/kairos
  # ECR에서 받는 경우 로그인이 필요하다.
  # 단 이 호출은 AWS API라 프록시로 우회되지 않으므로 enable_aws_api_endpoints가 켜져 있어야 한다.
  # 공개 레지스트리에서 받는 구성이면 이 단계는 조용히 넘어간다.
  if echo "${image_uri}" | grep -q "dkr.ecr"; then
    aws ecr get-login-password --region "${region}" |
      docker login --username AWS --password-stdin "$(echo "${image_uri}" | cut -d/ -f1)"
  fi
  docker compose up -d
fi
