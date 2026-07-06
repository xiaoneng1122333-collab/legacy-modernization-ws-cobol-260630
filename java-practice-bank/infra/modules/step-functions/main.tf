locals {
  state_machine_definition = {
    Comment = "Daily batch pipeline — Japan core banking"
    StartAt = "MasterLoad"
    States = {
      MasterLoad = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=MASTER_LOAD"]
            }]
          }
        }
        Next = "IntegrationIn"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      IntegrationIn = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=INTEGRATION_IN"]
            }]
          }
        }
        Next = "TxnValidate"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      TxnValidate = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=TXN_VALIDATE"]
            }]
          }
        }
        Next = "TxnSortMerge"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      TxnSortMerge = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=TXN_SORT_MERGE"]
            }]
          }
        }
        Next = "TxnPost"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      TxnPost = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=TXN_POST"]
            }]
          }
        }
        Next = "InterestAccrual"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      InterestAccrual = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=INTEREST_ACCRUAL"]
            }]
          }
        }
        Next = "InterestPost"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      InterestPost = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=INTEREST_POST"]
            }]
          }
        }
        Next = "Autodebit"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      Autodebit = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=AUTODEBIT"]
            }]
          }
        }
        Next = "Fee"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      Fee = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=FEE"]
            }]
          }
        }
        Next = "Statement"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      Statement = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=STATEMENT"]
            }]
          }
        }
        Next = "IntegrationOut"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      IntegrationOut = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=INTEGRATION_OUT"]
            }]
          }
        }
        Next = "Audit"
        Catch = [{ ErrorEquals = ["States.ALL"], Next = "Audit" }]
      }
      Audit = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=AUDIT"]
            }]
          }
        }
        Next = "Finalize"
      }
      Finalize = {
        Type     = "Task"
        Resource = var.ecs_task_arn
        Parameters = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = var.ecs_task_arn
          LaunchType     = "FARGATE"
          Overrides = {
            ContainerOverrides = [{
              Name = "app"
              Command = ["java", "-jar", "app.jar", "--step=FINALIZE"]
            }]
          }
        }
        End = true
      }
    }
  }
}

resource "aws_sfn_state_machine" "daily_batch" {
  name     = "${var.project_name}-daily-batch"
  role_arn = var.role_arn
  definition = jsonencode(local.state_machine_definition)
  type     = "STANDARD"
  logging_configuration {
    level           = "ERROR"
    include_execution_data = false
  }
  tags = var.tags
}

resource "aws_cloudwatch_event_rule" "daily_trigger" {
  name                = "${var.project_name}-daily-batch-trigger"
  description         = "Triggers daily batch pipeline at 23:00 Asia/Tokyo"
  schedule_expression = "cron(0 23 * * ? *)"
  tags                = var.tags
}

resource "aws_cloudwatch_event_target" "daily_trigger" {
  rule     = aws_cloudwatch_event_rule.daily_trigger.name
  arn      = aws_sfn_state_machine.daily_batch.arn
  role_arn = var.event_role_arn
  input    = jsonencode({ triggerSource = "eventbridge-cron-daily" })
}

resource "aws_scheduler_schedule" "daily_batch" {
  name  = "${var.project_name}-daily-batch"
  state = "ENABLED"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "cron(0 23 * * ? *)"
  schedule_expression_timezone = "Asia/Tokyo"

  target {
    arn      = aws_sfn_state_machine.daily_batch.arn
    role_arn = var.event_role_arn
    input    = jsonencode({ triggerSource = "scheduler-daily-2300" })
  }
}
