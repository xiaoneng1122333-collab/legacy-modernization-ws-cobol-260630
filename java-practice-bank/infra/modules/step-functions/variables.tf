variable "project_name" {
  type    = string
  default = "practice-bank"
}

variable "tags" {
  type    = map(string)
  default = {}
}

variable "role_arn" {
  type = string
}

variable "event_role_arn" {
  type = string
}

variable "ecs_cluster_arn" {
  type = string
}

variable "ecs_task_arn" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "assign_public_ip" {
  type    = string
  default = "ENABLED"
}
