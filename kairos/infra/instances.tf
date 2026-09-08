data "aws_ssm_parameter" "al2023_arm64" {
  # AMI ID를 코드에 박으면 리전이 바뀌거나 이미지가 갱신될 때마다 고쳐야 한다.
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-6.1-arm64"
}

# ---------- NAT 겸 egress 통제 지점 ----------
#
# 이 인스턴스가 이 구성의 핵심이다. app 구간이 외부로 나가는 유일한 길이고,
# 그 길 위에서 Squid가 도메인 허용 목록을 판정한다.
#
# 프록시를 KAIROS와 같은 호스트에 두지 않은 이유가 있다.
# 같은 호스트에 있으면 프록시 설정을 지우는 것만으로 우회된다.
# 별도 인스턴스에 두고 전달 트래픽의 443을 막으면, 프록시를 거치지 않고는 나갈 수 없다.

resource "aws_instance" "nat" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.nat_instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.nat.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  # NAT 역할을 하려면 자기 앞으로 온 것이 아닌 패킷도 처리할 수 있어야 한다.
  source_dest_check = false

  # 부팅 스크립트가 패키지를 받으려면 이 시점에 이미 주소가 있어야 한다.
  associate_public_ip_address = true

  user_data = templatefile("${path.module}/userdata/nat.sh", {
    app_subnet_cidr = var.app_subnet_cidr
    allowed_domains = var.allowed_egress_domains
  })

  # user_data가 바뀌면 인스턴스를 다시 만든다. 기동 스크립트는 부팅 때만 도므로
  # 내용만 바꿔서는 반영되지 않고, 그 사실을 잊으면 바꾼 줄 알고 넘어가게 된다.
  user_data_replace_on_change = true

  metadata_options {
    # IMDSv2를 강제한다. v1은 SSRF 한 번으로 인스턴스 자격 증명이 새어나간다.
    http_tokens = "required"
  }

  root_block_device {
    volume_size = 8
    encrypted   = true
  }

  tags = { Name = "kairos-${var.environment}-nat" }
}

resource "aws_eip" "nat" {
  instance = aws_instance.nat.id
  domain   = "vpc"

  tags = { Name = "kairos-${var.environment}-nat" }
}

# ---------- KAIROS ----------

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.app_instance_type
  subnet_id              = aws_subnet.app.id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  user_data = templatefile("${path.module}/userdata/app.sh", {
    proxy_host           = aws_instance.nat.private_ip
    image_uri            = var.app_image_uri
    postgres_image_uri   = var.postgres_image_uri
    app_subnet_cidr      = var.app_subnet_cidr
    package_host         = var.package_host
    region               = var.region
    provider_secret_name = var.provider_keys_secret_name
  })
  user_data_replace_on_change = true

  # 이 인스턴스는 NAT를 거치지 않으면 아무것도 받을 수 없다.
  # 라우팅이 먼저 생기지 않으면 부팅 스크립트가 통째로 실패한다.
  depends_on = [aws_route.app_egress]

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size = 16
    encrypted   = true
  }

  tags = { Name = "kairos-${var.environment}-app" }
}

# ---------- 업무망 ----------
#
# 인터넷으로 나가는 경로가 없는 구간에 놓인 내부 클라이언트다.
# 검증 시나리오에서 "인터넷은 막히는데 KAIROS는 부를 수 있다"를 보여주는 자리다.

resource "aws_instance" "workload" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.workload_instance_type
  subnet_id              = aws_subnet.workload.id
  vpc_security_group_ids = [aws_security_group.workload.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size = 8
    encrypted   = true
  }

  tags = { Name = "kairos-${var.environment}-workload" }
}
