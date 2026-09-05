# External Network Card

The External Network Card gives a RISC-V computer an isolated, server-side
Internet connection. It uses user-mode NAT, so normal Linux TCP and UDP
programs, including HTTPS clients and SSH, work without a host TAP device.

The server administrator must set `admin.external_network.externalNetworkEnabled`
to `true` and install the `oc2slirp` native library. The card is isolated from
OC2 network cables and hubs. It is disabled by default and must be installed
before the computer boots.
