variable "aws_region" {
  type    = string
  default = "ap-northeast-1"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "alert_emails" {
  type    = list(string)
  default = []
}

variable "service_names" {
  type    = list(string)
  default = ["calendar-service", "inquiry-api", "batch-job"]
}

variable "services" {
  type = list(object({
    name          = string
    port          = optional(number, 8080)
    cpu           = optional(number, 256)
    memory        = optional(number, 512)
    desired_count = optional(number, 1)
    is_long_running = optional(bool, true)
    path_pattern  = optional(string, "")
    priority      = optional(number, null)
  }))
  default = [
    {
      name            = "calendar-service"
      path_pattern    = "/calendar*"
      priority        = 10
      is_long_running = true
    },
    {
      name            = "inquiry-api"
      path_pattern    = "/inquiry*"
      priority        = 20
      is_long_running = true
    },
    {
      name            = "batch-job"
      is_long_running = false
      path_pattern    = ""
    }
  ]
}
