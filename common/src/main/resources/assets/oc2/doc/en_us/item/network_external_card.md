# External Network Card

The External Network Card gives a RISC-V computer an isolated, server-side
Internet connection. It uses user-mode NAT, so normal Linux TCP and UDP
programs, including HTTPS clients and SSH, work without a host TAP device.

The server administrator must set `admin.external_network.externalNetworkEnabled`
to `true` and install the `oc2slirp` native library. The card is isolated from
OC2 network cables and hubs. It is disabled by default and must be installed
before the computer boots.

At boot, the bundled startup scripts probe unconfigured Ethernet interfaces
with DHCP. Leases matching the external NAT (DHCP server and gateway
`10.0.2.2`, DNS `10.0.2.3`) configure the address, default route and
`/etc/resolv.conf` automatically, using public DNS servers `1.1.1.1` and
`8.8.8.8` instead of the advertised NAT DNS proxy. Interfaces with an existing IPv4 address
are left alone. DHCP clients remain running to renew their leases.

Reboot the guest after updating the mod to run the new startup script.
Discovery logs are in `/run/oc2-network/eth*.log`. If discovery times out,
run the bundled `init.d/S30external_network start` script again.
