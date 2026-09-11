SUMMARY = "Anthias OS self-update client"
DESCRIPTION = "A systemd timer that polls a static JSON manifest over HTTPS \
and installs the RAUC bundle it points at. No update server and no inbound \
network access are required."
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = " \
    file://anthias-update \
    file://anthias-update.service \
    file://anthias-update.timer \
    file://anthias-update.conf \
"

inherit systemd allarch

# Everything comes from file:// URIs, so nothing is unpacked into ${BP}.
S = "${UNPACKDIR}"

# Where the device looks for its manifest. Overridden per channel, e.g.
#   ANTHIAS_UPDATE_URL = "https://screenly.github.io/anthias-os"
ANTHIAS_UPDATE_URL ?= ""

# The version string this image reports as "currently running". CI sets this to
# the release tag, and the published manifest carries the same value, so the
# comparison is an exact string match with no parsing anywhere.
ANTHIAS_VERSION ?= "${DISTRO_VERSION}"

do_install() {
    install -d ${D}${libexecdir}/anthias
    install -m 0755 ${UNPACKDIR}/anthias-update ${D}${libexecdir}/anthias/

    install -d ${D}${sysconfdir}
    echo "${ANTHIAS_VERSION}" > ${D}${sysconfdir}/anthias-version
    chmod 0644 ${D}${sysconfdir}/anthias-version
    install -m 0644 ${UNPACKDIR}/anthias-update.conf ${D}${sysconfdir}/
    sed -i -e 's!@ANTHIAS_UPDATE_URL@!${ANTHIAS_UPDATE_URL}!' \
        ${D}${sysconfdir}/anthias-update.conf

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/anthias-update.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/anthias-update.timer   ${D}${systemd_system_unitdir}/
}

# The timer is what gets enabled; the service is pulled in by it.
SYSTEMD_SERVICE:${PN} = "anthias-update.timer"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

CONFFILES:${PN} = "${sysconfdir}/anthias-update.conf"
FILES:${PN} += "${sysconfdir}/anthias-version"

RDEPENDS:${PN} = "rauc curl coreutils sed gawk systemd"

FILES:${PN} += "${libexecdir}/anthias ${systemd_system_unitdir}"
