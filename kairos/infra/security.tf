# 보안 그룹은 라우팅이 만든 경계를 보조한다.
# 라우팅이 "나갈 길이 있는가"를 정한다면, 보안 그룹은 "그 길로 무엇을 보낼 수 있는가"를 정한다.

# ---------- NAT 겸 forward proxy ----------

resource "aws_security_group" "nat" {
  name        = "kairos-${var.environment}-nat"
  description = "NAT instance and egress proxy"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "kairos-${var.environment}-nat" }
}

resource "aws_vpc_security_group_ingress_rule" "nat_from_app" {
  security_group_id = aws_security_group.nat.id
  # app 구간이 프록시를 거쳐 나가는 통로
  description = "Egress proxy port for the app subnet"
  cidr_ipv4   = var.app_subnet_cidr
  from_port   = 3128
  to_port     = 3128
  ip_protocol = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "nat_forwarding" {
  security_group_id = aws_security_group.nat.id
  # NAT 역할로 전달되는 트래픽이다. 실제로 어디까지 나갈 수 있는지는
  # 인스턴스 안의 iptables와 Squid 허용 목록이 결정한다.
  description = "Forwarded traffic from the app subnet"
  cidr_ipv4   = var.app_subnet_cidr
  ip_protocol = "-1"
}

resource "aws_vpc_security_group_egress_rule" "nat_all" {
  security_group_id = aws_security_group.nat.id
  # 허용 도메인 판정은 Squid가 한다.
  description = "Egress allowed, domain decision is made by Squid"
  cidr_ipv4   = "0.0.0.0/0"
  ip_protocol = "-1"
}

# ---------- KAIROS ----------

resource "aws_security_group" "app" {
  name        = "kairos-${var.environment}-app"
  description = "KAIROS gateway"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "kairos-${var.environment}-app" }
}

resource "aws_vpc_security_group_ingress_rule" "app_service_from_workload" {
  security_group_id = aws_security_group.app.id
  # 업무망은 인터넷에 못 나가지만 이 포트로는 KAIROS를 부를 수 있다.
  # "AI는 쓸 수 있다"가 성립하는 지점이다.
  description = "Service calls from the isolated workload subnet"
  cidr_ipv4   = var.workload_subnet_cidr
  from_port   = 8080
  to_port     = 8080
  ip_protocol = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "app_metrics_from_vpc" {
  security_group_id = aws_security_group.app.id
  # 지표 포트는 VPC 안에서만 연다. 애플리케이션이 이 포트에 인증을 걸지 않는 대신
  # 포트 자체를 경계로 삼기로 했고, 그 전제를 여기서 지킨다.
  description = "Metrics port reachable from inside the VPC only"
  cidr_ipv4   = var.vpc_cidr
  from_port   = 9090
  to_port     = 9090
  ip_protocol = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_to_proxy" {
  security_group_id = aws_security_group.app.id
  # 외부로 나가는 유일한 경로다.
  description                  = "The only path out of the app subnet"
  referenced_security_group_id = aws_security_group.nat.id
  from_port                    = 3128
  to_port                      = 3128
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_to_s3_endpoint" {
  security_group_id = aws_security_group.app.id
  # S3 게이트웨이 엔드포인트로 가는 트래픽은 VPC 대역이 아니라 S3 공인 IP로 향한다.
  # 라우팅만 엔드포인트로 바뀔 뿐 목적지 주소는 그대로이므로, 보안 그룹에서 따로 열어야 한다.
  # OS 패키지 저장소가 이 경로를 쓰므로 이 규칙이 없으면 부팅 스크립트가 패키지를 받지 못한다.
  description    = "S3 gateway endpoint for OS package repositories"
  prefix_list_id = aws_vpc_endpoint.s3.prefix_list_id
  ip_protocol    = "-1"
}

resource "aws_vpc_security_group_egress_rule" "app_to_vpc" {
  security_group_id = aws_security_group.app.id
  # VPC 엔드포인트와 내부 통신
  description = "Internal traffic and VPC endpoints"
  cidr_ipv4   = var.vpc_cidr
  ip_protocol = "-1"
}

# ---------- 업무망 ----------

resource "aws_security_group" "workload" {
  name        = "kairos-${var.environment}-workload"
  description = "Internal workload that must not reach the internet"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "kairos-${var.environment}-workload" }
}

resource "aws_vpc_security_group_egress_rule" "workload_to_vpc" {
  security_group_id = aws_security_group.workload.id
  # VPC 밖으로 나가는 규칙을 아예 두지 않는다.
  # 라우팅에 기본 경로가 없으므로 이 규칙이 없어도 못 나가지만,
  # 두 겹으로 막아 두면 둘 중 하나를 실수로 열어도 바로 뚫리지 않는다.
  description = "VPC internal traffic only, no route to the internet"
  cidr_ipv4   = var.vpc_cidr
  ip_protocol = "-1"
}

# ---------- VPC 엔드포인트 ----------

resource "aws_security_group" "vpc_endpoints" {
  name        = "kairos-${var.environment}-endpoints"
  description = "Interface endpoints for SSM"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "kairos-${var.environment}-endpoints" }
}

resource "aws_vpc_security_group_ingress_rule" "endpoints_from_vpc" {
  security_group_id = aws_security_group.vpc_endpoints.id
  # VPC 안에서 오는 HTTPS
  description = "HTTPS from inside the VPC"
  cidr_ipv4   = var.vpc_cidr
  from_port   = 443
  to_port     = 443
  ip_protocol = "tcp"
}
