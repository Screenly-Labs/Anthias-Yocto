SUMMARY = "Anthias digital signage appliance image"
DESCRIPTION = "A minimal systemd host whose only job is to run the stock \
Anthias containers and to be updatable over the air with RAUC. The rootfs is \
one half of an A/B pair; all state lives on the /data partition."

require recipes-core/images/core-image-base.bb

IMAGE_FEATURES += "ssh-server-openssh"

# No package manager on the device: updates are whole-rootfs RAUC bundles.
IMAGE_FEATURES:remove = "package-management"

IMAGE_INSTALL += " \
    anthias-stack \
    docker-compose \
    docker-moby \
    rauc \
    networkmanager \
    networkmanager-nmtui \
    kernel-modules \
    e2fsprogs \
    e2fsprogs-resize2fs \
    util-linux \
    ca-certificates \
    tzdata \
    curl \
    htop \
    nano \
    rng-tools \
"

# Anthias is the product; a login shell is for the operator.
IMAGE_LINGUAS = "en-us"

# The A slot only has to hold the OS plus the Anthias checkout — container
# images go to /data/docker — but leave headroom for logs and a debug session.
IMAGE_OVERHEAD_FACTOR = "1.3"

export IMAGE_BASENAME = "anthias-image"
