#!/bin/bash
set -euxo pipefail

# ---------------------------------------------------------------------------
# KAIROS가 도는 인스턴스
#
# 이 구간은 NAT를 거쳐야만 외부로 나갈 수 있고, NAT는 HTTPS 직결을 끊어 두었다.
# 따라서 여기서 외부를 부르려면 프록시를 지나는 수밖에 없다.
# 아래 설정은 편의를 위한 것이지 통제 수단이 아니다. 통제는 NAT 쪽 iptables가 한다.
# ---------------------------------------------------------------------------

dnf install -y docker
systemctl enable --now docker

PROXY="http://${proxy_host}:3128"

# 컨테이너 런타임이 이미지를 받을 때도 프록시를 지난다.
mkdir -p /etc/systemd/system/docker.service.d
cat > /etc/systemd/system/docker.service.d/proxy.conf <<PROXYCONF
[Service]
Environment="HTTP_PROXY=$PROXY"
Environment="HTTPS_PROXY=$PROXY"
Environment="NO_PROXY=localhost,127.0.0.1,169.254.169.254,.amazonaws.com"
PROXYCONF

systemctl daemon-reload
systemctl restart docker

# 셸에서 확인할 때도 같은 경로를 쓰도록 맞춰 둔다.
cat > /etc/profile.d/proxy.sh <<PROFILECONF
export HTTP_PROXY="$PROXY"
export HTTPS_PROXY="$PROXY"
export NO_PROXY="localhost,127.0.0.1,169.254.169.254,.amazonaws.com"
PROFILECONF
