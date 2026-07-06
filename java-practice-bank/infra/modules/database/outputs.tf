output "aurora_endpoint" { value = aws_rds_cluster.main.endpoint }
output "redis_endpoint"  { value = aws_elasticache_cluster.main.cache_nodes[0].address }
