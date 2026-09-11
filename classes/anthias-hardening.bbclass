# Lockdown post-processing for production Anthias images.
#
# Enabled from kas/production.yml with INHERIT += "anthias-hardening".
# Lives in a class because bitbake conf files only accept assignments -
# shell functions defined in local.conf are a parse error.

# With root locked and no password on the anthias user, an SSH key is the ONLY
# way into a production box. Leave it empty and the device is genuinely
# unreachable: recovery means reflashing.
ANTHIAS_SSH_AUTHORIZED_KEY ?= ""

provision_anthias_ssh_key() {
    if [ -n "${ANTHIAS_SSH_AUTHORIZED_KEY}" ]; then
        install -d -m 0700 ${IMAGE_ROOTFS}/home/anthias/.ssh
        echo "${ANTHIAS_SSH_AUTHORIZED_KEY}" > ${IMAGE_ROOTFS}/home/anthias/.ssh/authorized_keys
        chmod 0600 ${IMAGE_ROOTFS}/home/anthias/.ssh/authorized_keys
        chown -R 1000:1000 ${IMAGE_ROOTFS}/home/anthias/.ssh
    else
        bbwarn "ANTHIAS_SSH_AUTHORIZED_KEY is empty: this image has no login path at all."
    fi
}

# oe-core's read_only_rootfs_hook makes the rootfs read-only by rewriting
# "defaults" to "ro" in /etc/fstab. meta-rockchip's rauc demo ships its own
# fstab whose rootfs line reads "x-systemd.growfs", so that sed matches nothing
# and read-only-rootfs silently does nothing. Force it, and drop growfs -
# systemd-repart sizes the A/B slots, so the rootfs must not grow into them.
force_ro_rootfs() {
    sed -i -e '/^[#[:space:]]*\/dev\/root/s/x-systemd.growfs/ro/' \
        ${IMAGE_ROOTFS}/etc/fstab
    if ! grep -qE '^[[:space:]]*/dev/root[[:space:]].*[[:space:]]ro[[:space:],]' ${IMAGE_ROOTFS}/etc/fstab; then
        bbfatal "read-only-rootfs requested but the /etc/fstab rootfs line is not ro"
    fi
}

ROOTFS_POSTPROCESS_COMMAND += "provision_anthias_ssh_key;"
ROOTFS_POSTPROCESS_COMMAND += "${@bb.utils.contains('IMAGE_FEATURES', 'read-only-rootfs', 'force_ro_rootfs;', '', d)}"
