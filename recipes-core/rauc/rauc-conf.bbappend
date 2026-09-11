# Replace the meta-rockchip demo identity with the Anthias one. meta-anthias
# has a higher BBFILE_PRIORITY, so this FILESEXTRAPATHS lands in front of the
# demo's and our system.conf / ca.cert.pem are the ones that get installed.
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

PACKAGE_ARCH = "${MACHINE_ARCH}"

do_install:prepend() {
    sed -i -e 's!@MACHINE@!${MACHINE}!g' ${UNPACKDIR}/system.conf
}
