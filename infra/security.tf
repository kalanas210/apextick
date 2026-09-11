# Only Caddy (80/443) faces the internet. The app, API and Keycloak talk over
# the docker network, and every admin surface binds to loopback on the host,
# reached through SSH -- which is itself limited to admin_cidr.
resource "aws_security_group" "ec2" {
  # The description is left as it was: changing it would make Terraform
  # replace the whole group instead of updating its rules in place.
  name        = "apextick-ec2-sg"
  description = "SSH plus the public-facing app ports"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH (management)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr]
  }

  ingress {
    description = "HTTP (ACME cert challenge + redirect to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS (Caddy reverse proxy / TLS)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "apextick-ec2-sg" }
}
