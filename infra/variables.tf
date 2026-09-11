# Where SSH is allowed from. Deliberately no default, so SSH is never opened
# to the whole internet by omission. Pass it on each plan/apply, e.g.
#   terraform apply -var "admin_cidr=$(curl -s https://checkip.amazonaws.com)/32"
# or put it in a terraform.tfvars next to this file.
variable "admin_cidr" {
  description = "IPv4 CIDR allowed to SSH to the server, normally your own public IP as a /32 (e.g. 203.0.113.7/32)."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.admin_cidr)) && !endswith(var.admin_cidr, "/0")
    error_message = "admin_cidr must be an IPv4 CIDR block such as 203.0.113.7/32, and not a /0."
  }
}
