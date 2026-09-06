/* SPDX-License-Identifier: BSD-3-Clause */

#include "oc2_slirp.h"

/* Ethernet + IPv4 + ICMP echo to exercise socket registration, not just idle polling. */
static const uint8_t echo[] = {
    0x52,0x55,0x0a,0x00,0x02,0x02, 0x02,0,0,0,0,1, 0x08,0x00,
    0x45,0,0,28,0,0,0,0,64,1,0x5e,0xc3,10,0,2,15,8,8,8,8,
    8,0,0xf7,0xfd,0,1,0,1
};

int main(void) {
    oc2_slirp *instance = oc2_slirp_create();
    if (!instance) return 1;
    oc2_slirp_input(instance, echo, sizeof(echo));
    for (int i = 0; i < 10000; i++) {
        oc2_slirp_poll(instance, 0);
    }
    oc2_slirp_destroy(instance);
    return 0;
}
