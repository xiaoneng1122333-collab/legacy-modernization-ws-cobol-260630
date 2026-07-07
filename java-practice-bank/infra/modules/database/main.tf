resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet"
  subnet_ids = var.subnet_ids
  tags       = var.tags
}

resource "aws_rds_cluster" "main" {
  cluster_identifier = "${var.project_name}-aurora"
  engine             = "aurora-postgresql"
  engine_version     = "16.4"
  database_name      = "banking"
  master_username    = "bankadmin"
  master_password    = var.db_password
  vpc_security_group_ids = [var.db_security_group_id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  skip_final_snapshot    = true
  tags = var.tags
}

resource "aws_rds_cluster_instance" "main" {
  count              = 2
  identifier         = "${var.project_name}-aurora-${count.index}"
  cluster_identifier = aws_rds_cluster.main.id
  instance_class     = "db.r6g.large"
  engine             = aws_rds_cluster.main.engine
}

resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project_name}-cache-subnet"
  subnet_ids = var.subnet_ids
}

resource "aws_elasticache_cluster" "main" {
  cluster_id           = "${var.project_name}-redis"
  engine               = "redis"
  node_type            = "cache.r6g.large"
  num_cache_nodes      = 1
  engine_version       = "7.1"
  security_group_ids   = [var.cache_security_group_id]
  subnet_group_name    = aws_elasticache_subnet_group.main.name
}
