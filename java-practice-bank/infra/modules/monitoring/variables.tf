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

variable "state_machine_arn" {
  type = string
}

variable "alert_emails" {
  type    = list(string)
  default = []
}
