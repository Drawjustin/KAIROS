# Terraform 상태 파일을 둘 S3 버킷을 만든다.
#
# 상태 파일에는 리소스 ID와 일부 속성이 그대로 담긴다. 로컬에 두면 노트북이 바뀌는 순간
# 인프라를 관리할 수 없게 되고, 여러 곳에서 동시에 apply하면 상태가 깨진다.
# 그래서 상태는 원격에 두고 잠금을 건다.
#
# 상태를 둘 곳 자체는 Terraform 상태로 관리할 수 없다(닭과 달걀). 그래서 이 구성만
# 따로 떼어 로컬 상태로 한 번 apply하고, 이후 본 구성이 이 버킷을 backend로 쓴다.
#
#   cd infra/bootstrap && terraform init && terraform apply

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }
}

provider "aws" {
  region = var.region
}

variable "region" {
  description = "리소스를 만들 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  description = "상태 파일을 둘 S3 버킷 이름. 전 세계에서 유일해야 한다."
  type        = string
}

resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket_name

  # 실수로 지우면 인프라 전체를 추적할 수 없게 된다.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  # 잘못된 apply로 상태가 망가져도 이전 버전으로 되돌릴 수 있어야 한다.
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  # 상태 파일이 공개되면 인프라 구조가 통째로 노출된다.
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

output "state_bucket" {
  value       = aws_s3_bucket.state.id
  description = "본 구성의 backend에 적어 넣을 버킷 이름"
}
