locals {
  buckets = {
    input   = "Batch input landing zone"
    output  = "Batch processing results"
    archive = "Long-term batch archive"
  }
}

resource "aws_s3_bucket" "batch" {
  for_each = local.buckets
  bucket   = "${var.project_name}-${each.key}-${var.environment}"
  tags     = merge(var.tags, { Purpose = each.value })
}

resource "aws_s3_bucket_versioning" "batch" {
  for_each = local.buckets
  bucket   = aws_s3_bucket.batch[each.key].id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "batch" {
  for_each = local.buckets
  bucket   = aws_s3_bucket.batch[each.key].id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "batch" {
  for_each = local.buckets
  bucket   = aws_s3_bucket.batch[each.key].id

  rule {
    id     = "glacier-transition"
    status = "Enabled"
    transition {
      days          = 90
      storage_class = "GLACIER"
    }
    expiration {
      days = 365
    }
  }

  depends_on = [aws_s3_bucket_versioning.batch]
}

resource "aws_s3_bucket_notification" "input" {
  bucket = aws_s3_bucket.batch["input"].id
  eventbridge = true
}

resource "aws_s3_bucket_public_access_block" "batch" {
  for_each                = local.buckets
  bucket                  = aws_s3_bucket.batch[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
