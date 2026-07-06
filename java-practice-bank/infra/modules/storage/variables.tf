variable "project_name" {
  type    = string
  default = "practice-bank"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "tags" {
  type    = map(string)
  default = {}
}
