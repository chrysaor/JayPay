terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "jaypay"
      Environment = "dev"
      ManagedBy   = "terraform"
    }
  }
}

module "money_sum" {
  source = "../../modules/dynamodb-money-sum"
}

output "table_name" {
  value = module.money_sum.table_name
}
