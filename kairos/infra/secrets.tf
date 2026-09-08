# provider API 키는 코드에도 상태 파일에도 사용자 데이터에도 남기지 않는다.
# Secrets Manager에 미리 넣어 두고 인스턴스가 부팅 때 받아 간다.
#
# 이 시크릿은 Terraform이 만들지 않는다. 값을 Terraform이 알게 되는 순간
# 상태 파일에 평문으로 남기 때문이다. 값 없이 참조만 한다.
data "aws_secretsmanager_secret" "provider_keys" {
  name = var.provider_keys_secret_name
}

resource "aws_iam_role_policy" "read_provider_keys" {
  name = "read-provider-keys"
  role = aws_iam_role.instance.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      # 이 시크릿 하나만 읽을 수 있게 좁힌다.
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = data.aws_secretsmanager_secret.provider_keys.arn
    }]
  })
}
