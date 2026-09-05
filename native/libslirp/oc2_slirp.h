/* SPDX-License-Identifier: BSD-3-Clause */

#ifndef OC2_SLIRP_H
#define OC2_SLIRP_H

#include <stddef.h>
#include <stdint.h>

typedef struct oc2_slirp oc2_slirp;

oc2_slirp *oc2_slirp_create(void);
void oc2_slirp_destroy(oc2_slirp *instance);
void oc2_slirp_input(oc2_slirp *instance, const uint8_t *frame, size_t length);
void oc2_slirp_poll(oc2_slirp *instance, int timeout_ms);
size_t oc2_slirp_next_frame(oc2_slirp *instance, uint8_t *destination, size_t capacity);

#endif
