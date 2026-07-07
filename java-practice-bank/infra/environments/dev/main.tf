provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

module "network" {
  source              = "../../modules/network"
  project_name        = "practice-bank-dev"
  vpc_cidr            = "10.0.0.0/16"
  availability_zones  = ["ap-northeast-1a", "ap-northeast-1c"]
  aws_region          = var.aws_region
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

module "database" {
  source                 = "../../modules/database"
  project_name           = "practice-bank-dev"
  db_password            = var.db_password
  db_security_group_id   = aws_security_group.db.id
  cache_security_group_id = aws_security_group.cache.id
  subnet_ids             = module.network.private_subnet_ids
  tags = { Environment = "dev", Project = "practice-bank" }
}

module "storage" {
  source      = "../../modules/storage"
  project_name = "practice-bank-dev"
  environment  = "dev"
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_ecr_repository" "app" {
  name                 = "practice-bank-app"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/practice-bank-dev"
  retention_in_days = 30
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_iam_role" "ecs_execution" {
  name = "practice-bank-dev-ecs-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_lb" "app" {
  name               = "practice-bank-dev-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [module.network.app_security_group_id]
  subnets            = module.network.public_subnet_ids
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_lb_target_group" "app" {
  name        = "practice-bank-dev-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = module.network.vpc_id
  target_type = "ip"
  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 10
  }
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_lb_listener" "app" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

module "ecs" {
  source              = "../../modules/ecs"
  project_name        = "practice-bank-dev"
  tags                = { Environment = "dev", Project = "practice-bank" }
  task_cpu            = 256
  task_memory         = 512
  execution_role_arn  = aws_iam_role.ecs_execution.arn
  ecr_repository_url  = aws_ecr_repository.app.repository_url
  db_url              = "jdbc:postgresql://${module.database.aurora_endpoint}:5432/banking"
  db_user             = "bankadmin"
  db_password         = var.db_password
  log_group_name      = aws_cloudwatch_log_group.app.name
  aws_region          = var.aws_region
  desired_count       = 1
  subnet_ids          = module.network.private_subnet_ids
  app_security_group_id = module.network.app_security_group_id
  target_group_arn    = aws_lb_target_group.app.arn
}

resource "aws_iam_role" "step_functions" {
  name = "practice-bank-dev-step-functions"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "states.amazonaws.com" }
    }]
  })
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_iam_role_policy" "step_functions_ecs" {
  name = "ecs-run-task"
  role = aws_iam_role.step_functions.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ecs:RunTask", "ecs:StopTask", "ecs:DescribeTasks"]
        Resource = ["*"]
      },
      {
        Effect   = "Allow"
        Action   = "iam:PassRole"
        Resource = [aws_iam_role.ecs_execution.arn]
      },
      {
        Effect   = "Allow"
        Action   = ["events:PutTargets", "events:PutRule", "events:DescribeRule"]
        Resource = ["arn:aws:events:${var.aws_region}:${data.aws_caller_identity.current.account_id}:rule/StepFunctionsGetEventsForECSTaskRule"]
      }
    ]
  })
}

resource "aws_iam_role" "eventbridge_invoke" {
  name = "practice-bank-dev-eventbridge"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
    }]
  })
  tags = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_iam_role_policy" "eventbridge_invoke" {
  name = "invoke-step-functions"
  role = aws_iam_role.eventbridge_invoke.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "states:StartExecution"
      Resource = ["*"]
    }]
  })
}

module "step_functions" {
  source          = "../../modules/step-functions"
  project_name    = "practice-bank-dev"
  tags            = { Environment = "dev", Project = "practice-bank" }
  role_arn        = aws_iam_role.step_functions.arn
  event_role_arn  = aws_iam_role.eventbridge_invoke.arn
  ecs_cluster_arn = module.ecs.cluster_id
  ecs_task_arn    = module.ecs.task_definition_arn
}

module "monitoring" {
  source             = "../../modules/monitoring"
  project_name       = "practice-bank-dev"
  tags               = { Environment = "dev", Project = "practice-bank" }
  aws_region         = var.aws_region
  state_machine_arn  = module.step_functions.state_machine_arn
  alert_emails       = var.alert_emails
}
