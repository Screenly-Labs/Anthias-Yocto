FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# meta-rockchip opts each board into linux-yocto explicitly; do the same for
# ours. The regex matches both nanopi-r3s and nanopi-r3s-lts.
COMPATIBLE_MACHINE:nanopi-r3s = "nanopi-r3s"

SRC_URI:append:nanopi-r3s = " \
    file://rk3566-nanopi-r3s-lts.dts \
    file://anthias.cfg \
"

# The NanoPi R3S LTS has no device tree upstream (only the non-LTS R3S, which
# has no video output at all). Drop ours into the kernel tree and register it
# with the rockchip dts Makefile so KERNEL_DEVICETREE and the fitImage pick it
# up like any in-tree dtb.
do_configure:prepend:nanopi-r3s() {
    install -m 0644 "${UNPACKDIR}/rk3566-nanopi-r3s-lts.dts" \
        "${S}/arch/arm64/boot/dts/rockchip/"
    if ! grep -q 'rk3566-nanopi-r3s-lts.dtb' "${S}/arch/arm64/boot/dts/rockchip/Makefile"; then
        echo 'dtb-$(CONFIG_ARCH_ROCKCHIP) += rk3566-nanopi-r3s-lts.dtb' \
            >> "${S}/arch/arm64/boot/dts/rockchip/Makefile"
    fi
}
