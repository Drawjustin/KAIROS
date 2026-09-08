# 접속은 SSH가 아니라 SSM Session Manager로만 한다.
#
# 22번 포트를 아예 열지 않으므로 bastion이 필요 없고, 키 파일을 주고받을 일도 없다.
# 무엇보다 누가 언제 어느 인스턴스에 붙었는지가 CloudTrail에 남는다.
# 감사 관점에서는 이 점이 SSH 대비 가장 큰 차이다.

resource "aws_iam_role" "instance" {
  name = "kairos-${var.environment}-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ecr_pull" {
  # ECR에서 이미지를 받으려면 인스턴스가 스스로 토큰을 발급받아야 한다.
  # SSM 권한만으로는 되지 않고, 이 권한이 없으면 부팅 스크립트의 docker login이 막힌다.
  # 읽기 전용이라 이미지를 밀어 넣지는 못한다.
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_instance_profile" "instance" {
  name = "kairos-${var.environment}-instance"
  role = aws_iam_role.instance.name
}
