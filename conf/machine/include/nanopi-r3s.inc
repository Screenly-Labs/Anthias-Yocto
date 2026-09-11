# Common bits for the FriendlyELEC NanoPi R3S family (Rockchip RK3566).
MACHINEOVERRIDES =. "nanopi-r3s:"

# --- RAUC A/B + persistent /data -------------------------------------------
# meta-rockchip ships a complete reference integration for Rockchip:
#   * a U-Boot boot script implementing BOOT_ORDER / BOOT_x_LEFT
#   * systemd-repart drop-ins that create rootfsB and data on first boot
#   * a rauc system.conf with rootfs.0 / rootfs.1 slots
# We reuse the mechanism and override the identity bits (compatible string,
# keyring) from meta-anthias, whose layer priority is higher.
RK_RAUC_DEMO = "1"

# The boot script persists BOOT_ORDER / BOOT_x_LEFT back to the uboot_env
# partition, so the environment has to be seeded into the image and
# writable from Linux (rauc's uboot backend uses fw_setenv).
RK_IMAGE_INCLUDES_UBOOT_ENV = "1"
MACHINE_FEATURES += "rk-u-boot-env"

require conf/machine/include/rk3566.inc

UBOOT_MACHINE = "nanopi-r3s-rk3566_defconfig"

MACHINE_EXTRA_RRECOMMENDS += "kernel-modules linux-firmware-rtl8168"

MACHINE_FEATURES += "usbhost pci ext2 alsa vfat"

SERIAL_CONSOLES = "1500000;ttyS2"

# Room for the host-side Anthias checkout and the docker/rauc tooling. The
# container images themselves never land here — they live on /data.
IMAGE_ROOTFS_EXTRA_SPACE ?= "524288"
