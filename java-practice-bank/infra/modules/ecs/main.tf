resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"
  setting { name = "containerInsights" value = "enabled" }
  tags = var.tags
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project_name}-app"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = var.execution_role_arn
  container_definitions    = jsonencode([{
    name  = "app"
    image = "${var.ecr_repository_url}:latest"
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
      { name = "DB_URL", value = var.db_url },
      { name = "DB_USER", value = var.db_user },
      { name = "DB_PASSWORD", value = var.db_password }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = var.log_group_name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "app" {
  name            = "${var.project_name}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  launch_type     = "FARGATE"
  desired_count   = var.desired_count
  network_configuration {
    subnets         = var.subnet_ids
    security_groups = [var.app_security_group_id]
  }
  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = "app"
    container_port   = 8080
  }
}
