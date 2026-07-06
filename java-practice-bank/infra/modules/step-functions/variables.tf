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
