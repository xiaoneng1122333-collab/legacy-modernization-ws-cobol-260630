provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

module "network" {
  source             = "../../modules/network"
  project_name       = "practice-bank-dev"
  vpc_cidr           = "10.0.0.0/16"
  availability_zones = ["ap-northeast-1a", "ap-northeast-1c"]
  aws_region         = var.aws_region
  tags               = { Environment = "dev", Project = "practice-bank" }
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
  source                  = "../../modules/database"
  project_name            = "practice-bank-dev"
  db_password             = var.db_password
  db_security_group_id    = aws_security_group.db.id
  cache_security_group_id = aws_security_group.cache.id
  subnet_ids              = module.network.private_subnet_ids
  tags                    = { Environment = "dev", Project = "practice-bank" }
}

module "storage" {
  source       = "../../modules/storage"
  project_name = "practice-bank-dev"
  environment  = "dev"
  tags         = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_ecr_repository" "app" {
  for_each             = toset(var.service_names)
  name                 = each.key
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
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

resource "aws_iam_role" "ecs_task" {
  name = "practice-bank-dev-ecs-task"
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

resource "aws_iam_role_policy" "ecs_task_s3" {
  name = "s3-batch-access"
  role = aws_iam_role.ecs_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["s3:GetObject", "s3:PutObject", "s3:ListBucket"]
      Resource = concat(
        [for b in module.storage.bucket_arns : b],
        [for b in module.storage.bucket_arns : "${b}/*"]
      )
    }]
  })
}

resource "aws_lb" "app" {
  name               = "practice-bank-dev-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [module.network.app_security_group_id]
  subnets            = module.network.public_subnet_ids
  tags               = { Environment = "dev", Project = "practice-bank" }
}

resource "aws_lb_listener" "app" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Not Found"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "services" {
  for_each     = { for s in var.services : s.name => s if s.path_pattern != "" }
  listener_arn = aws_lb_listener.app.arn
  priority     = each.value.priority
  action {
    type             = "forward"
    target_group_arn = module.ecs.target_group_arns[each.key]
  }
  condition {
    path_pattern {
      values = [each.value.path_pattern]
    }
  }
}

module "ecs" {
  source                = "../../modules/ecs"
  project_name          = "practice-bank-dev"
  tags                  = { Environment = "dev", Project = "practice-bank" }
  execution_role_arn    = aws_iam_role.ecs_execution.arn
  task_role_arn         = aws_iam_role.ecs_task.arn
  db_url                = "jdbc:postgresql://${module.database.aurora_endpoint}:5432/banking"
  db_user               = "bankadmin"
  db_password           = var.db_password
  aws_region            = var.aws_region
  subnet_ids            = module.network.private_subnet_ids
  app_security_group_id = module.network.app_security_group_id
  vpc_id                = module.network.vpc_id
  ecr_account_id        = data.aws_caller_identity.current.account_id
  services              = { for s in var.services : s.name => s }
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
        Resource = [aws_iam_role.ecs_execution.arn, aws_iam_role.ecs_task.arn]
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
      Resource = [module.step_functions.state_machine_arn]
    }]
  })
}

module "step_functions" {
  source            = "../../modules/step-functions"
  project_name      = "practice-bank-dev"
  tags              = { Environment = "dev", Project = "practice-bank" }
  role_arn          = aws_iam_role.step_functions.arn
  event_role_arn    = aws_iam_role.eventbridge_invoke.arn
  ecs_cluster_arn   = module.ecs.cluster_id
  ecs_task_arn      = module.ecs.task_definition_arns["batch-job"]
  subnet_ids        = module.network.public_subnet_ids
  security_group_id = module.network.app_security_group_id
  assign_public_ip  = "ENABLED"
}

module "monitoring" {
  source            = "../../modules/monitoring"
  project_name      = "practice-bank-dev"
  tags              = { Environment = "dev", Project = "practice-bank" }
  aws_region        = var.aws_region
  state_machine_arn = module.step_functions.state_machine_arn
  alert_emails      = var.alert_emails
}
