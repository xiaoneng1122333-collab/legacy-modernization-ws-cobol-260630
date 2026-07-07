output "state_machine_arn" {
  value = aws_sfn_state_machine.daily_batch.arn
}

output "state_machine_name" {
  value = aws_sfn_state_machine.daily_batch.name
}

output "scheduler_arn" {
  value = aws_scheduler_schedule.daily_batch.arn
}
