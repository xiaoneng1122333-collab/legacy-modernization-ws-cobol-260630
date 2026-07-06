resource "aws_cloudwatch_dashboard" "batch" {
  dashboard_name = "${var.project_name}-batch-monitoring"
  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "Batch Execution Time"
          view   = "timeSeries"
          region = var.aws_region
          metrics = [
            ["AWS/States", "ExecutionTime", "StateMachineArn", var.state_machine_arn]
          ]
          period = 3600
          stat   = "Average"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "Batch Error Rate"
          view   = "timeSeries"
          region = var.aws_region
          metrics = [
            ["AWS/States", "ExecutionsFailed", "StateMachineArn", var.state_machine_arn],
            ["AWS/States", "ExecutionsSucceeded", "StateMachineArn", var.state_machine_arn]
          ]
          period = 3600
          stat   = "Sum"
        }
      }
    ]
  })
}

resource "aws_cloudwatch_metric_alarm" "batch_failure" {
  alarm_name          = "${var.project_name}-batch-failure"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsFailed"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "State machine daily batch had at least one failed execution"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  dimensions = {
    StateMachineArn = var.state_machine_arn
  }
  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "batch_duration" {
  alarm_name          = "${var.project_name}-batch-duration-too-long"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionTime"
  namespace           = "AWS/States"
  period              = 3600
  statistic           = "Maximum"
  threshold           = 14400
  alarm_description   = "Daily batch exceeded 4 hour threshold — investigate)"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  dimensions = {
    StateMachineArn = var.state_machine_arn
  }
  tags = var.tags
}

resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-batch-alerts"
  tags = var.tags
}

resource "aws_sns_topic_subscription" "email" {
  count     = length(var.alert_emails)
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_emails[count.index]
}
