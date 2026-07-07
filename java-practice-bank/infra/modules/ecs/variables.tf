variable "project_name" {
  type    = string
  default = "practice-bank"
}

variable "tags" {
  type    = map(string)
  default = {}
}

variable "aws_region" {
  type    = string
  default = "ap-northeast-1"
}

variable "execution_role_arn" {
  type = string
}

variable "task_role_arn" {
  type    = string
  default = null
}

variable "db_url" {
  type = string
}

variable "db_user" {
  type    = string
  default = "bankadmin"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "subnet_ids" {
  type = list(string)
}

variable "app_security_group_id" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "ecr_account_id" {
  type = string
}

variable "services" {
  type = map(object({
    port            = optional(number, 8080)
    cpu             = optional(number, 256)
    memory          = optional(number, 512)
    desired_count   = optional(number, 1)
    is_long_running = optional(bool, true)
    path_pattern    = optional(string, "")
  }))
}
