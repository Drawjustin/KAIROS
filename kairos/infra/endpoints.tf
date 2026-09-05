# 업무망에는 인터넷으로 나가는 경로가 없다. 그런데도 SSM으로 접속하려면
# AWS 서비스에 닿을 길이 필요하고, 그 길이 VPC 엔드포인트다.
#
# 인터페이스 엔드포인트는 시간당 과금이라 이 구성에서 비용의 대부분을 차지한다.
# 데모 기간에만 띄우고 끝나면 destroy하는 것을 전제로 한다.

data "aws_region" "current" {}

locals {
  # 세 개가 모두 있어야 Session Manager가 동작한다.
  interface_endpoints = toset(["ssm", "ssmmessages", "ec2messages"])
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoints

  vpc_id             = aws_vpc.main.id
  service_name       = "com.amazonaws.${data.aws_region.current.name}.${each.value}"
  vpc_endpoint_type  = "Interface"
  subnet_ids         = [aws_subnet.app.id, aws_subnet.workload.id]
  security_group_ids = [aws_security_group.vpc_endpoints.id]
  # 사설 DNS를 켜야 인스턴스가 평소 쓰던 주소 그대로 엔드포인트로 붙는다.
  private_dns_enabled = true

  tags = { Name = "kairos-${var.environment}-${each.value}" }
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.app.id, aws_route_table.workload.id]

  # 게이트웨이 엔드포인트는 무료다. 이게 있어야 인터넷 없이도 OS 패키지를 받을 수 있다.
  tags = { Name = "kairos-${var.environment}-s3" }
}
