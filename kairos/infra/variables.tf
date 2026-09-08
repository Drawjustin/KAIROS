variable "region" {
  description = "리소스를 만들 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "environment" {
  description = "환경 이름. 태그와 리소스 이름에 들어간다."
  type        = string
  default     = "demo"
}

variable "vpc_cidr" {
  description = "VPC 전체 대역"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "인터넷 게이트웨이로 나가는 유일한 구간"
  type        = string
  default     = "10.0.1.0/24"
}

variable "app_subnet_cidr" {
  description = "KAIROS가 도는 구간. NAT를 거쳐서만 외부로 나간다."
  type        = string
  default     = "10.0.11.0/24"
}

variable "workload_subnet_cidr" {
  description = "업무망 구간. 기본 경로를 두지 않아 외부로 나갈 방법이 없다."
  type        = string
  default     = "10.0.12.0/24"
}

variable "app_instance_type" {
  description = <<-EOT
    KAIROS 인스턴스 타입.
    절감 구성이라 같은 호스트에서 애플리케이션과 PostgreSQL을 함께 돌린다.
    JVM 힙과 DB와 OS를 합치면 1GB로는 빠듯해 2GB짜리를 쓴다.
  EOT
  type        = string
  default     = "t4g.small"
}

variable "workload_instance_type" {
  description = "업무망 클라이언트 타입. 검증 명령만 실행하므로 최소 사양으로 충분하다."
  type        = string
  default     = "t4g.micro"
}

variable "nat_instance_type" {
  description = "NAT 겸 forward proxy 인스턴스 타입"
  type        = string
  default     = "t4g.nano"
}

variable "enable_aws_api_endpoints" {
  description = <<-EOT
    ECR과 Secrets Manager용 VPC 엔드포인트를 만들지 여부.

    app 구간에는 인터넷 경로가 없고 NAT가 전달 트래픽의 443을 끊는다. AWS API는
    프록시 예외로 잡혀 있어 엔드포인트 없이는 호출이 실패한다. ECR에서 이미지를
    받거나 Secrets Manager를 쓰려면 켜야 한다.

    인터페이스 엔드포인트는 개당 시간당 과금이므로 기본값은 끔이다.
    공개 레지스트리에서 이미지를 받는 구성이면 켤 필요가 없다.
  EOT
  type        = bool
  default     = false
}

variable "app_image_uri" {
  description = <<-EOT
    KAIROS 컨테이너 이미지 주소. 비워 두면 인프라만 세우고 애플리케이션은 올리지 않는다.
    ECR을 쓰는 경우 계정 ID가 들어가므로 코드에 박지 않고 apply 때 넘긴다.
  EOT
  type        = string
  default     = ""
}

variable "allowed_egress_domains" {
  description = <<-EOT
    KAIROS가 나갈 수 있는 도메인 목록. 이 목록에 없는 곳으로는 나가지 못한다.
    실무에서는 AWS Network Firewall이나 보안 웹 게이트웨이가 맡는 역할을
    비용 때문에 Squid로 대체했다.
  EOT
  type        = list(string)
  default = [
    # AI provider
    "api.openai.com",
    "api.anthropic.com",
    "generativelanguage.googleapis.com",

    # 아래는 부팅에 필요한 최소 항목이다. 통제를 느슨하게 한 것이 아니라,
    # 인터넷이 없는 구간에서 OS 패키지와 컨테이너 이미지를 받으려면 어딘가는 열려야 한다.
    # 실무에서도 사내 미러를 두거나 이런 형태의 허용 목록을 유지한다.
    "cdn.amazonlinux.com",
    "registry-1.docker.io",
    "auth.docker.io",
    "production.cloudflare.docker.com",
  ]
}

variable "package_host" {
  description = "부팅 스크립트가 프록시 준비를 확인할 때 두드리는 주소"
  type        = string
  default     = "cdn.amazonlinux.com"
}
