provider "aws" {
  region = var.aws_region
}

module "network" {
  source              = "../../modules/network"
  project_name        = "practice-bank-dev"
  vpc_cidr            = "10.0.0.0/16"
  availability_zones  = ["ap-northeast-1a", "ap-northeast-1c"]
  tags = { Environment = "dev", Project = "practice-bank" }
}

module "database" {
  source                 = "../../modules/database"
  project_name           = "practice-bank-dev"
  db_password            = var.db_password
  db_security_group_id   = aws_security_group.db.id
  cache_security_group_id = aws_security_group.cache.id
  subnet_ids             = module.network.private_subnet_ids
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_security_group" "db" {
  name_prefix = "practice-bank-dev-db-"
  vpc_id      = module.network.vpc_id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [module.network.app_security_group_id]
  }
}

resource "aws_security_group" "cache" {
  name_prefix = "practice-bank-dev-cache-"
  vpc_id      = module.network.vpc_id
  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [module.network.app_security_group_id]
  }
}
