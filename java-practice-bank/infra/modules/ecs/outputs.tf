output "cluster_id" {
  value = aws_ecs_cluster.main.id
}

output "task_definition_arns" {
  value = { for k, v in aws_ecs_task_definition.service : k => v.arn }
}

output "target_group_arns" {
  value = { for k, v in aws_lb_target_group.service : k => v.arn }
}

output "service_names" {
  value = { for k, v in aws_ecs_service.service : k => v.name }
}
