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

variable "instance_type" {
  description = "KAIROS와 업무망 인스턴스 타입"
  type        = string
  default     = "t4g.micro"
}

variable "nat_instance_type" {
  description = "NAT 겸 forward proxy 인스턴스 타입"
  type        = string
  default     = "t4g.nano"
}

variable "allowed_egress_domains" {
  description = <<-EOT
    KAIROS가 나갈 수 있는 도메인 목록. 이 목록에 없는 곳으로는 나가지 못한다.
    실무에서는 AWS Network Firewall이나 보안 웹 게이트웨이가 맡는 역할을
    비용 때문에 Squid로 대체했다.
  EOT
  type        = list(string)
  default = [
    "api.openai.com",
    "api.anthropic.com",
    "generativelanguage.googleapis.com",
  ]
}
