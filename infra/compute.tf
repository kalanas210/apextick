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
  instance_type               = "t3.medium"
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.ec2.id]
  key_name                    = aws_key_pair.main.key_name
  user_data                   = file("${path.module}/user-data.sh")
  user_data_replace_on_change = true

  root_block_device {
    volume_size = 30
  }

  tags = { Name = "apextick-server" }
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