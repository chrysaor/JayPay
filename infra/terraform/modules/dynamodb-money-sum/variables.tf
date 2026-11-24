variable "table_name" {
  description = "Table read and written by money-query-service."
  type        = string
  default     = "MoneyIncreaseEventByRegion"
}

variable "point_in_time_recovery" {
  type    = bool
  default = true
}

variable "tags" {
  type    = map(string)
  default = {}
}
