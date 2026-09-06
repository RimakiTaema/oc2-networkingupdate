#!/bin/sh
# udhcpc event hook. Only configure leases from OC2's isolated libslirp NAT.
case "$1" in
    bound|renew)
        [ "$serverid" = "10.0.2.2" ] || exit 0
        [ "$router" = "10.0.2.2" ] || exit 0
        [ "$dns" = "10.0.2.3" ] || exit 0
        case "$interface" in eth[0-9]*) ;; *) exit 1 ;; esac
        ifconfig "$interface" "$ip" netmask "${subnet:-255.255.255.0}" up || exit 1
        ip route replace default via 10.0.2.2 dev "$interface" || exit 1
        printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf
        ;;
esac
