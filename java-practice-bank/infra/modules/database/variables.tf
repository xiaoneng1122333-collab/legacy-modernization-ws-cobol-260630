variable "project_name" {
  type = string
}
variable "db_password" {
  type      = string
  sensitive = true
}
variable "db_security_group_id" {
  type = string
}
variable "cache_security_group_id" {
  type = string
}
variable "subnet_ids" {
  type = list(string)
}
variable "tags" {
  type = map(string)
}
