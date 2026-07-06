output "state_machine_arn" {
  value = aws_sfn_state_machine.daily_batch.arn
}

output "state_machine_name" {
  value = aws_sfn_state_machine.daily_batch.name
}

output "trigger_arn" {
  value = aws_cloudwatch_event_rule.daily_trigger.arn
}

output "scheduler_arn" {
  value = aws_scheduler_schedule.daily_batch.arn
}
