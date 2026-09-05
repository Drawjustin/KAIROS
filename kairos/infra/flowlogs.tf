# 망분리가 실제로 동작했다는 증거는 이 로그에 남는다.
#
# 검증 시나리오에서 업무망 인스턴스가 인터넷으로 나가려 시도하면
# 여기에 REJECT로 기록된다. 아키텍처 다이어그램보다 이 한 줄이 강하다.

resource "aws_cloudwatch_log_group" "flow_logs" {
  name = "/kairos/${var.environment}/vpc-flow-logs"

  # 데모용이라 짧게 둔다. 실제 금융권 요건은 이보다 훨씬 길다.
  retention_in_days = 7
}

resource "aws_iam_role" "flow_logs" {
  name = "kairos-${var.environment}-flow-logs"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "vpc-flow-logs.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "flow_logs" {
  name = "write-flow-logs"
  role = aws_iam_role.flow_logs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # 기록은 이 로그 그룹에만 할 수 있게 좁힌다.
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "${aws_cloudwatch_log_group.flow_logs.arn}:*"
      },
      {
        # Describe 계열은 목록 조회라 특정 로그 그룹 ARN으로 제한하면 호출 자체가 거부된다.
        # 읽기 전용이고 내용이 아니라 존재 여부만 보는 권한이라 분리해서 허용한다.
        Effect   = "Allow"
        Action   = ["logs:DescribeLogGroups", "logs:DescribeLogStreams"]
        Resource = "*"
      },
    ]
  })
}

resource "aws_flow_log" "main" {
  vpc_id = aws_vpc.main.id
  # 허용된 트래픽만 남기면 "막혔다"를 증명할 수 없다. 거부 기록이 핵심이라 ALL로 둔다.
  traffic_type    = "ALL"
  log_destination = aws_cloudwatch_log_group.flow_logs.arn
  iam_role_arn    = aws_iam_role.flow_logs.arn

  tags = { Name = "kairos-${var.environment}" }
}
