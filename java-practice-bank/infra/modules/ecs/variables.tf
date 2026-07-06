variable "project_name" {
  type    = string
  default = "practice-bank"
}

variable "tags" {
  type    = map(string)
  default = {}
}

variable "task_cpu" {
  type    = number
  default = 256
}

variable "task_memory" {
  type    = number
  default = 512
}

variable "execution_role_arn" {
  type = string
}

variable "ecr_repository_url" {
  type = string
}

variable "db_url" {
  type = string
}

variable "db_user" {
  type    = string
  default = "cobol"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "log_group_name" {
  type = string
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-1"
}

variable "desired_count" {
  type    = number
  default = 2
}

variable "subnet_ids" {
  type = list(string)
}

variable "app_security_group_id" {
  type = string
}

variable "target_group_arn" {
  type = string
}
