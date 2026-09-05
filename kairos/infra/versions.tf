terraform {
  # 1.10부터 S3 backend가 DynamoDB 테이블 없이 자체 잠금을 지원한다.
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      # 마이너 버전까지 고정한다. provider가 올라가면서 계획이 달라지는 일을 막는다.
      version = "~> 5.80"
    }
  }

  backend "s3" {
    # 실제 값은 backend.hcl에 두고 `terraform init -backend-config=backend.hcl`로 넘긴다.
    # 버킷 이름은 계정마다 다르므로 코드에 박지 않는다.
    key          = "kairos/network-separation.tfstate"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    # 태그를 붙여 두어야 비용이 어디서 나는지 나중에 추적할 수 있고,
    # destroy를 놓쳤을 때 남은 리소스를 찾아낼 수 있다.
    tags = {
      Project     = "kairos"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
