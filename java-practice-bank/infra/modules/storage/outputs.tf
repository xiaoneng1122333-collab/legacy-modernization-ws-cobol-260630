output "input_bucket_id" {
  value = aws_s3_bucket.batch["input"].id
}

output "input_bucket_arn" {
  value = aws_s3_bucket.batch["input"].arn
}

output "output_bucket_id" {
  value = aws_s3_bucket.batch["output"].id
}

output "output_bucket_arn" {
  value = aws_s3_bucket.batch["output"].arn
}

output "archive_bucket_id" {
  value = aws_s3_bucket.batch["archive"].id
}

output "archive_bucket_arn" {
  value = aws_s3_bucket.batch["archive"].arn
}

output "bucket_arns" {
  value = [for b in aws_s3_bucket.batch : b.arn]
}

output "bucket_ids" {
  value = { for k, v in aws_s3_bucket.batch : k => v.id }
}
