output "dashboard_name" {
  value = aws_cloudwatch_dashboard.batch.dashboard_name
}

output "dashboard_arn" {
  value = aws_cloudwatch_dashboard.batch.dashboard_arn
}

output "batch_failure_alarm_arn" {
  value = aws_cloudwatch_metric_alarm.batch_failure.arn
}

output "batch_duration_alarm_arn" {
  value = aws_cloudwatch_metric_alarm.batch_duration.arn
}

output "sns_topic_arn" {
  value = aws_sns_topic.alerts.arn
}
