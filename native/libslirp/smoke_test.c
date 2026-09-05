/* SPDX-License-Identifier: BSD-3-Clause */

#include "oc2_slirp.h"

int main(void) {
    oc2_slirp *instance = oc2_slirp_create();
    if (!instance) return 1;
    oc2_slirp_poll(instance, 0);
    oc2_slirp_destroy(instance);
    return 0;
}
