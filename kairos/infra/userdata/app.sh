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

cat > /etc/profile.d/proxy.sh <<PROFILECONF
export HTTP_PROXY="$PROXY"
export HTTPS_PROXY="$PROXY"
export NO_PROXY="$NO_PROXY_LIST"
PROFILECONF

# ---------------------------------------------------------------------------
# 자격 증명
#
# DB 비밀번호와 JWT 비밀키는 부팅 때 만들어 이 호스트에만 둔다.
# provider API 키는 Secrets Manager에서 받아 온다. 코드에도, 상태 파일에도,
# 사용자 데이터에도 남지 않는다. Secrets Manager는 AWS API라 프록시로 우회되지 않으므로
# VPC 엔드포인트를 통해서만 닿을 수 있다.
# ---------------------------------------------------------------------------

mkdir -p /opt/kairos
POSTGRES_PASSWORD="$(openssl rand -hex 24)"
JWT_SECRET="$(openssl rand -hex 32)"

SECRET_JSON="$(aws secretsmanager get-secret-value \
  --secret-id "${provider_secret_name}" --region "${region}" \
  --query SecretString --output text 2>/dev/null || echo '{}')"

read_key() {
  echo "$SECRET_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null || echo ""
}
OPENAI_KEY="$(read_key KAIROS_AI_OPENAI_API_KEY)"
ANTHROPIC_KEY="$(read_key KAIROS_AI_ANTHROPIC_API_KEY)"
GEMINI_KEY="$(read_key KAIROS_AI_GEMINI_API_KEY)"

# ---------------------------------------------------------------------------
# KAIROS와 데이터베이스
#
# 절감 구성이라 같은 호스트의 컨테이너로 띄운다. 데모용이며 실제 운영에서는
# 데이터 구간을 별도 서브넷의 관리형 DB로 분리한다.
#
# compose 플러그인은 Amazon Linux 2023 저장소에 없고, 컨테이너 이미지는
# Docker Hub CDN이 허용 목록 밖이라 받을 수 없다. 두 이미지 모두 ECR에 올려 두고
# docker run으로 직접 띄운다.
# ---------------------------------------------------------------------------

if [ -n "${image_uri}" ]; then
  if echo "${image_uri}" | grep -q "dkr.ecr"; then
    aws ecr get-login-password --region "${region}" |
      docker login --username AWS --password-stdin "$(echo "${image_uri}" | cut -d/ -f1)"
  fi

  docker network create kairos 2>/dev/null || true
  docker rm -f postgres kairos 2>/dev/null || true

  docker run -d --name postgres --network kairos --restart unless-stopped \
    -e POSTGRES_USER=kairos -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" -e POSTGRES_DB=kairos \
    -v postgres_data:/var/lib/postgresql/data "${postgres_image_uri}"

  for i in $(seq 1 30); do
    docker exec postgres pg_isready -U kairos >/dev/null 2>&1 && break
    sleep 3
  done

  # provider 호출도 프록시를 지나야 한다. 허용 목록 밖으로는 나가지 못한다.
  docker run -d --name kairos --network kairos --restart unless-stopped \
    -p 8080:8080 -p 9090:9090 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e KAIROS_API_DB_URL=jdbc:postgresql://postgres:5432/kairos \
    -e KAIROS_API_DB_USERNAME=kairos \
    -e KAIROS_API_DB_PASSWORD="$POSTGRES_PASSWORD" \
    -e KAIROS_JWT_SECRET="$JWT_SECRET" \
    -e KAIROS_AI_OPENAI_API_KEY="$OPENAI_KEY" \
    -e KAIROS_AI_ANTHROPIC_API_KEY="$ANTHROPIC_KEY" \
    -e KAIROS_AI_GEMINI_API_KEY="$GEMINI_KEY" \
    -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Dhttps.proxyHost=${proxy_host} -Dhttps.proxyPort=3128 -Dhttp.proxyHost=${proxy_host} -Dhttp.proxyPort=3128 -Dhttp.nonProxyHosts=localhost|127.0.0.1|postgres" \
    "${image_uri}"
fi
