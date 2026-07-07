resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"
  setting {
    name  = "containerInsights"
    value = "disabled"
  }
  tags = var.tags
}

resource "aws_cloudwatch_log_group" "service" {
  for_each          = var.services
  name              = substr("/ecs/${var.project_name}-${each.key}", 0, 512)
  retention_in_days = 30
  tags              = var.tags
}

resource "aws_ecs_task_definition" "service" {
  for_each                 = var.services
  family                   = substr("${var.project_name}-${each.key}", 0, 255)
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn
  container_definitions = jsonencode([{
    name  = each.key
    image = "${var.ecr_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${each.key}:latest"
    portMappings = [{ containerPort = each.value.port, protocol = "tcp" }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
      { name = "DB_URL", value = var.db_url },
      { name = "DB_USER", value = var.db_user },
      { name = "DB_PASSWORD", value = var.db_password }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.service[each.key].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_lb_target_group" "service" {
  for_each             = { for k, v in var.services : k => v if v.path_pattern != "" }
  name                 = substr("${var.project_name}-${each.key}", 0, 32)
  port                 = each.value.port
  protocol             = "HTTP"
  vpc_id               = var.vpc_id
  target_type          = "ip"
  deregistration_delay = 30
  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 10
    timeout             = 5
    interval            = 30
  }
  tags = var.tags
}

resource "aws_ecs_service" "service" {
  for_each        = { for k, v in var.services : k => v if v.is_long_running }
  name            = substr("${var.project_name}-${each.key}", 0, 255)
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  launch_type     = "FARGATE"
  desired_count   = each.value.desired_count
  network_configuration {
    subnets         = var.subnet_ids
    security_groups = [var.app_security_group_id]
  }
  dynamic "load_balancer" {
    for_each = each.value.path_pattern != "" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.service[each.key].arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }
  health_check_grace_period_seconds = 60
}
