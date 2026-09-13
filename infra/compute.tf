# Look up the latest Ubuntu 24.04 image, published by Canonical
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Upload our public key so the instance trusts our private key
resource "aws_key_pair" "main" {
  key_name   = "apextick-key"
  public_key = file("${path.module}/apextick-key.pub")
}

# The server
resource "aws_instance" "app" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = "t3.large"
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.ec2.id]
  key_name                    = aws_key_pair.main.key_name
  user_data                   = file("${path.module}/user-data.sh")
  user_data_replace_on_change = false

  root_block_device {
    volume_size = 30
  }

  # IMDSv2 only: the metadata service answers only callers that first PUT
  # for a session token, which a server-side request forgery (a plain GET
  # to 169.254.169.254) can't do. Updates in place, no replacement.
  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = { Name = "apextick-server" }

  lifecycle {
    # data.aws_ami.ubuntu tracks whatever Canonical most recently published,
    # which drifts constantly -- without this, every `terraform plan` wants to
    # destroy and recreate the instance (AMI changes can't apply in-place) the
    # moment a newer Ubuntu 24.04 build ships, wiping every docker volume on
    # its disk (Postgres/Keycloak/MinIO/... -- nothing here is on separate,
    # persistent storage). The AMI this instance actually boots from stays
    # pinned to whatever it was created with; bump it deliberately (remove
    # this line for one apply) if a rebuild is ever genuinely wanted.
    #
    # user_data is ignored for the same reason. cloud-init only runs it on
    # the first boot, so an edit to user-data.sh can't reach this server
    # anyway: all a diff could do is stop/start it or, with
    # user_data_replace_on_change, rebuild it and lose those same volumes.
    # Apply bootstrap changes to the running box over SSH; the script still
    # seeds any future instance.
    ignore_changes = [ami, user_data]
  }
}

# A static (Elastic) IP pinned to the instance, so the public IP no longer
# changes when the instance is stopped/started. Without this, a reboot hands
# the box a new IP and breaks both the <ip>.nip.io hostname and the TLS cert.
resource "aws_eip" "app" {
  instance   = aws_instance.app.id
  domain     = "vpc"
  tags       = { Name = "apextick-eip" }
  depends_on = [aws_internet_gateway.main]
}

output "server_ip" {
  value = aws_eip.app.public_ip
}

output "ssh_command" {
  value = "ssh -i apextick-key ubuntu@${aws_eip.app.public_ip}"
}