locals {
  batch_steps = {
    MASTER_LOAD      = { next = "INTEGRATION_IN", catch = "AUDIT" }
    INTEGRATION_IN   = { next = "TXN_VALIDATE", catch = "AUDIT" }
    TXN_VALIDATE     = { next = "TXN_SORT_MERGE", catch = "AUDIT" }
    TXN_SORT_MERGE   = { next = "TXN_POST", catch = "AUDIT" }
    TXN_POST         = { next = "INTEREST_ACCRUAL", catch = "AUDIT" }
    INTEREST_ACCRUAL = { next = "INTEREST_POST", catch = "AUDIT" }
    INTEREST_POST    = { next = "AUTODEBIT", catch = "AUDIT" }
    AUTODEBIT        = { next = "FEE", catch = "AUDIT" }
    FEE              = { next = "STATEMENT", catch = "AUDIT" }
    STATEMENT        = { next = "INTEGRATION_OUT", catch = "AUDIT" }
    INTEGRATION_OUT  = { next = "AUDIT", catch = "AUDIT" }
    AUDIT            = { next = "FINALIZE", catch = null }
    FINALIZE         = { next = null, catch = null }
  }

  state_machine_states = { for step_name, cfg in local.batch_steps :
    step_name => merge(
        {
          Type     = "Task"
          Resource = "arn:aws:states:::ecs:runTask.sync"
          Parameters = {
            Cluster        = var.ecs_cluster_arn
            TaskDefinition = var.ecs_task_arn
            LaunchType     = "FARGATE"
            NetworkConfiguration = {
              AwsvpcConfiguration = {
                Subnets        = var.subnet_ids
                SecurityGroups = [var.security_group_id]
                AssignPublicIp = var.assign_public_ip
              }
            }
            Overrides = {
              ContainerOverrides = [{
                Name    = "batch-job"
                Command = ["java", "-jar", "app.jar", "--step=${step_name}"]
              }]
            }
          }
        },
        cfg.next != null ? { Next = cfg.next } : { End = true },
        cfg.catch != null ? { Catch = [{ ErrorEquals = ["States.ALL"], Next = cfg.catch }] } : {}
    )
  }

  state_machine_definition = {
    Comment = "Daily batch pipeline — Japan core banking"
    StartAt = "MASTER_LOAD"
    States  = local.state_machine_states
  }
}

resource "aws_sfn_state_machine" "daily_batch" {
  name       = "${var.project_name}-daily-batch"
  role_arn   = var.role_arn
  definition = jsonencode(local.state_machine_definition)
  type       = "STANDARD"
  tags       = var.tags
}

resource "aws_scheduler_schedule" "daily_batch" {
  name  = "${var.project_name}-daily-batch"
  state = "ENABLED"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression             = "cron(0 23 * * ? *)"
  schedule_expression_timezone    = "Asia/Tokyo"

  target {
    arn      = aws_sfn_state_machine.daily_batch.arn
    role_arn = var.event_role_arn
    input    = jsonencode({ triggerSource = "scheduler-daily-2300" })
  }
}
