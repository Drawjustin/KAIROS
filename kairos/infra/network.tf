# 망분리의 실체는 보안 그룹이 아니라 라우팅 테이블에 있다.
#
# 보안 그룹은 규칙을 잘못 고치면 뚫린다. "설정으로 막았다"는 상태다.
# 경로가 없는 서브넷은 나갈 방법 자체가 없다. "구조적으로 못 나간다"는 상태다.
# 이 파일에서 확인해야 할 것은 workload 라우팅 테이블에 0.0.0.0/0 경로가 없다는 사실이다.

resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  # VPC 엔드포인트가 사설 DNS 이름으로 동작하려면 둘 다 켜져 있어야 한다.
  # 업무망은 인터넷이 없어 SSM 엔드포인트로만 접속할 수 있으므로 필수다.
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "kairos-${var.environment}" }
}

data "aws_availability_zones" "available" {
  state = "available"
}

# ---------- 서브넷 ----------

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = false

  tags = { Name = "kairos-${var.environment}-public" }
}

resource "aws_subnet" "app" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.app_subnet_cidr
  availability_zone = data.aws_availability_zones.available.names[0]

  tags = { Name = "kairos-${var.environment}-app" }
}

resource "aws_subnet" "workload" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.workload_subnet_cidr
  availability_zone = data.aws_availability_zones.available.names[0]

  tags = { Name = "kairos-${var.environment}-workload" }
}

# ---------- 인터넷 게이트웨이 ----------

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "kairos-${var.environment}" }
}

# ---------- 라우팅: public ----------
# 외부와 직접 오갈 수 있는 유일한 구간이다. 여기에는 NAT 인스턴스만 둔다.

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "kairos-${var.environment}-public" }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# ---------- 라우팅: app ----------
# KAIROS가 도는 구간이다. 인터넷에서 들어올 수는 없고, 나갈 때는 반드시 NAT를 거친다.
# 그 NAT 인스턴스가 곧 통제 지점이다.

resource "aws_route_table" "app" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "kairos-${var.environment}-app" }
}

resource "aws_route" "app_egress" {
  route_table_id         = aws_route_table.app.id
  destination_cidr_block = "0.0.0.0/0"
  network_interface_id   = aws_instance.nat.primary_network_interface_id
}

resource "aws_route_table_association" "app" {
  subnet_id      = aws_subnet.app.id
  route_table_id = aws_route_table.app.id
}

# ---------- 라우팅: workload (업무망) ----------
#
# 이 라우팅 테이블에는 VPC 내부 통신을 위한 local 경로만 있다.
# 0.0.0.0/0 경로를 만들지 않는 것이 이 프로젝트에서 말하는 망분리다.
#
# 여기에 aws_route를 하나 더 추가하는 순간 망분리가 무너진다.
# 그래서 이 파일에서 가장 중요한 것은 "무엇이 있는가"가 아니라 "무엇이 없는가"다.

resource "aws_route_table" "workload" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "kairos-${var.environment}-workload"
    Note = "no default route on purpose"
  }
}

resource "aws_route_table_association" "workload" {
  subnet_id      = aws_subnet.workload.id
  route_table_id = aws_route_table.workload.id
}
