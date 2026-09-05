/* SPDX-License-Identifier: BSD-3-Clause */

#include "oc2_slirp.h"

#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
typedef SSIZE_T oc2_slirp_ssize_t;
typedef SOCKET oc2_slirp_socket_t;
#else
#include <arpa/inet.h>
#include <poll.h>
#include <sys/types.h>
#include <time.h>
typedef ssize_t oc2_slirp_ssize_t;
typedef int oc2_slirp_socket_t;
#endif

#include <slirp/libslirp.h>

#define OC2_SLIRP_MAX_FRAMES 256
#define OC2_SLIRP_MAX_FRAME_SIZE 65536
#define OC2_SLIRP_MAX_POLLS 256

struct oc2_frame {
    size_t length;
    uint8_t data[OC2_SLIRP_MAX_FRAME_SIZE];
};

struct oc2_slirp {
    Slirp *slirp;
    struct oc2_frame frames[OC2_SLIRP_MAX_FRAMES];
    size_t read_index;
    size_t write_index;
    size_t count;
    struct pollfd polls[OC2_SLIRP_MAX_POLLS];
    size_t poll_count;
};

struct oc2_timer {
    SlirpTimerCb callback;
    void *opaque;
};

static void guest_error(const char *message, void *opaque) {
    (void) message;
    (void) opaque;
}

static void *timer_new(SlirpTimerCb callback, void *opaque, void *instance) {
    (void) instance;
    struct oc2_timer *timer = calloc(1, sizeof(*timer));
    if (timer) {
        timer->callback = callback;
        timer->opaque = opaque;
    }
    return timer;
}

static void timer_free(void *opaque, void *instance) {
    (void) instance;
    free(opaque);
}

static void timer_mod(void *opaque, int64_t expire_time, void *instance) {
    (void) opaque;
    (void) expire_time;
    (void) instance;
}

static int64_t clock_ns(void *opaque) {
#ifdef _WIN32
    LARGE_INTEGER frequency;
    LARGE_INTEGER counter;
    (void) opaque;
    QueryPerformanceFrequency(&frequency);
    QueryPerformanceCounter(&counter);
    return (int64_t) ((counter.QuadPart * 1000000000LL) / frequency.QuadPart);
#else
    struct timespec time;
    (void) opaque;
    clock_gettime(CLOCK_MONOTONIC, &time);
    return (int64_t) time.tv_sec * 1000000000LL + time.tv_nsec;
#endif
}

static oc2_slirp_ssize_t send_packet(const void *buffer, size_t length, void *opaque) {
    oc2_slirp *instance = opaque;
    if (length > OC2_SLIRP_MAX_FRAME_SIZE || instance->count == OC2_SLIRP_MAX_FRAMES) {
        return (oc2_slirp_ssize_t) length;
    }

    struct oc2_frame *frame = &instance->frames[instance->write_index];
    memcpy(frame->data, buffer, length);
    frame->length = length;
    instance->write_index = (instance->write_index + 1) % OC2_SLIRP_MAX_FRAMES;
    instance->count++;
    return (oc2_slirp_ssize_t) length;
}

static int add_poll_socket(int socket, int events, void *opaque) {
    oc2_slirp *instance = opaque;
    if (instance->poll_count == OC2_SLIRP_MAX_POLLS) {
        return -1;
    }

    struct pollfd *poll = &instance->polls[instance->poll_count];
    poll->fd = (oc2_slirp_socket_t) socket;
    poll->events = 0;
    poll->revents = 0;
    if (events & SLIRP_POLL_IN) poll->events |= POLLIN;
    if (events & SLIRP_POLL_OUT) poll->events |= POLLOUT;
    if (events & SLIRP_POLL_PRI) poll->events |= POLLPRI;
    return (int) instance->poll_count++;
}

static int get_revents(int index, void *opaque) {
    oc2_slirp *instance = opaque;
    if (index < 0 || (size_t) index >= instance->poll_count) return 0;
    const short revents = instance->polls[index].revents;
    int result = 0;
    if (revents & POLLIN) result |= SLIRP_POLL_IN;
    if (revents & POLLOUT) result |= SLIRP_POLL_OUT;
    if (revents & POLLPRI) result |= SLIRP_POLL_PRI;
    if (revents & POLLERR) result |= SLIRP_POLL_ERR;
    if (revents & POLLHUP) result |= SLIRP_POLL_HUP;
    return result;
}

oc2_slirp *oc2_slirp_create(void) {
    oc2_slirp *instance = calloc(1, sizeof(*instance));
    if (!instance) return NULL;

    SlirpConfig config;
    memset(&config, 0, sizeof(config));
    config.version = SLIRP_CONFIG_VERSION_MAX;
    config.restricted = 0;
    config.in_enabled = true;
    inet_pton(AF_INET, "10.0.2.0", &config.vnetwork);
    inet_pton(AF_INET, "255.255.255.0", &config.vnetmask);
    inet_pton(AF_INET, "10.0.2.2", &config.vhost);
    inet_pton(AF_INET, "10.0.2.15", &config.vdhcp_start);
    inet_pton(AF_INET, "10.0.2.3", &config.vnameserver);
    config.if_mtu = 1500;
    config.if_mru = 1500;
    config.disable_host_loopback = true;
    config.in6_enabled = false;

    SlirpCb callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.send_packet = send_packet;
    callbacks.guest_error = guest_error;
    callbacks.clock_get_ns = clock_ns;
    callbacks.timer_new = timer_new;
    callbacks.timer_free = timer_free;
    callbacks.timer_mod = timer_mod;
    /* Use the compatibility polling callback: it includes the event mask. */

    instance->slirp = slirp_new(&config, &callbacks, instance);
    if (!instance->slirp) {
        free(instance);
        return NULL;
    }
    return instance;
}

void oc2_slirp_destroy(oc2_slirp *instance) {
    if (!instance) return;
    if (instance->slirp) slirp_cleanup(instance->slirp);
    free(instance);
}

void oc2_slirp_input(oc2_slirp *instance, const uint8_t *frame, size_t length) {
    if (instance && instance->slirp && frame && length <= OC2_SLIRP_MAX_FRAME_SIZE) {
        slirp_input(instance->slirp, frame, (int) length);
    }
}

void oc2_slirp_poll(oc2_slirp *instance, int timeout_ms) {
    if (!instance || !instance->slirp) return;
    uint32_t timeout = timeout_ms < 0 ? UINT32_MAX : (uint32_t) timeout_ms;
    instance->poll_count = 0;
    slirp_pollfds_fill(instance->slirp, &timeout, add_poll_socket, instance);
    int wait_ms = timeout > 1000 ? 1000 : (int) timeout;
    if (wait_ms < 0) wait_ms = 0;
#ifdef _WIN32
    const int result = WSAPoll(instance->polls, (ULONG) instance->poll_count, wait_ms);
#else
    const int result = poll(instance->polls, instance->poll_count, wait_ms);
#endif
    slirp_pollfds_poll(instance->slirp, result < 0, get_revents, instance);
}

size_t oc2_slirp_next_frame(oc2_slirp *instance, uint8_t *destination, size_t capacity) {
    if (!instance || !destination || instance->count == 0) return 0;
    struct oc2_frame *frame = &instance->frames[instance->read_index];
    if (frame->length > capacity) return 0;
    memcpy(destination, frame->data, frame->length);
    const size_t length = frame->length;
    instance->read_index = (instance->read_index + 1) % OC2_SLIRP_MAX_FRAMES;
    instance->count--;
    return length;
}
