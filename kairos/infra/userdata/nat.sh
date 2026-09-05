#!/bin/bash
set -euxo pipefail

# ---------------------------------------------------------------------------
# NAT 겸 egress 통제 지점
#
# 이 스크립트가 하는 일은 두 가지다.
#   1. app 구간이 외부로 나갈 수 있게 NAT 역할을 한다.
#   2. 단, HTTPS 직결은 막아서 Squid 프록시를 거치지 않고는 나갈 수 없게 만든다.
#
# 2번이 없으면 프록시는 권고사항일 뿐이다. 애플리케이션이 프록시 설정을 무시하면 그냥 나간다.
# 전달 트래픽의 443을 끊어야 "유일한 출구"가 설정이 아니라 구조가 된다.
# ---------------------------------------------------------------------------

dnf install -y iptables-services squid

# 커널이 패킷을 전달하게 한다. 이게 없으면 NAT 역할 자체가 성립하지 않는다.
echo "net.ipv4.ip_forward = 1" > /etc/sysctl.d/99-nat.conf
sysctl -p /etc/sysctl.d/99-nat.conf

PRIMARY_IF="$(ip -o -4 route show to default | awk '{print $5}')"

# app 구간에서 온 패킷을 이 인스턴스의 주소로 바꿔 내보낸다.
iptables -t nat -A POSTROUTING -s ${app_subnet_cidr} -o "$PRIMARY_IF" -j MASQUERADE

# 프록시를 우회한 HTTPS/HTTP 직결을 끊는다.
# Squid는 이 인스턴스에서 자기 이름으로 나가므로 OUTPUT 체인을 타고, 이 규칙에 걸리지 않는다.
iptables -A FORWARD -s ${app_subnet_cidr} -p tcp --dport 443 -j REJECT
iptables -A FORWARD -s ${app_subnet_cidr} -p tcp --dport 80 -j REJECT

# DNS와 그 외 내부 통신은 그대로 둔다.
iptables -A FORWARD -s ${app_subnet_cidr} -j ACCEPT

service iptables save

# ---------------------------------------------------------------------------
# Squid: 도메인 허용 목록
#
# 실무에서는 AWS Network Firewall이나 보안 웹 게이트웨이가 맡는 역할이다.
# 월 30만원을 넘는 비용 때문에 같은 목적을 Squid로 대신한다.
# 한계를 알고 대체한 것이며, 판정 방식(도메인 허용 목록)은 동일하다.
# ---------------------------------------------------------------------------

cat > /etc/squid/allowed_domains.txt <<'ALLOWED'
%{ for domain in allowed_domains ~}
${domain}
%{ endfor ~}
ALLOWED

cat > /etc/squid/squid.conf <<'SQUIDCONF'
http_port 3128

acl allowed_domains dstdomain "/etc/squid/allowed_domains.txt"
acl app_subnet src APP_SUBNET_CIDR
acl SSL_ports port 443
acl CONNECT method CONNECT

# HTTPS는 CONNECT로 들어온다. 목적지 도메인만 보고 판정하며 내용은 복호화하지 않는다.
http_access allow app_subnet allowed_domains
http_access deny CONNECT !SSL_ports
http_access deny all

# 무엇이 통과하고 무엇이 막혔는지가 증거물이 된다.
access_log stdio:/var/log/squid/access.log
SQUIDCONF

sed -i "s|APP_SUBNET_CIDR|${app_subnet_cidr}|" /etc/squid/squid.conf

systemctl enable --now squid
