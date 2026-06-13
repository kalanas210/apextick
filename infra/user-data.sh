#!/bin/bash
set -e

# Install Docker Engine + the Compose plugin (official convenience script)
curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
sh /tmp/get-docker.sh

# Let the default 'ubuntu' user run docker without sudo
usermod -aG docker ubuntu