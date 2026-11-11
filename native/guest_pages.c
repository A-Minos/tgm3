#define _GNU_SOURCE
#include "guest_pages.h"
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#ifndef MAP_NORESERVE
#define MAP_NORESERVE 0
#endif

static int bounds(const gpl_memory *m, size_t offset, size_t length) {
    return m && m->base && !m->faulted && offset <= m->size && length <= m->size - offset;
}
static int pages(const gpl_memory *m, size_t offset, size_t length) {
    return bounds(m, offset, length) && length &&
           !(offset % GPL_GUEST_PAGE) && !(length % GPL_GUEST_PAGE);
}
static size_t floor_host(const gpl_memory *m, size_t n) { return n & ~(m->host_page - 1); }
static size_t ceil_host(const gpl_memory *m, size_t n) { return (n + m->host_page - 1) & ~(m->host_page - 1); }

int gpl_init(gpl_memory *m, size_t size, size_t host_page) {
    if (!m) return -EINVAL;
    memset(m, 0, sizeof *m);
    long os_page = sysconf(_SC_PAGESIZE);
    if (os_page <= 0) return -ENOSYS;
    size_t system_page = (size_t)os_page;
    if (!host_page) host_page = system_page;
    if (host_page < GPL_GUEST_PAGE || host_page < system_page ||
        (host_page & (host_page - 1)) || host_page % system_page ||
        !size || size % host_page || size > SIZE_MAX - host_page) return -EINVAL;
    uint8_t *state = calloc(size / GPL_GUEST_PAGE, 1);
    if (!state) return -ENOMEM;
    size_t reserve = size + host_page;
    void *raw = mmap(NULL, reserve, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
    if (raw == MAP_FAILED) { int e = errno; free(state); return -e; }
    uintptr_t first = (uintptr_t)raw;
    uintptr_t aligned = (first + host_page - 1) & ~(uintptr_t)(host_page - 1);
    size_t prefix = (size_t)(aligned - first);
    size_t suffix = reserve - prefix - size;
    if (prefix) munmap(raw, prefix);
    if (suffix) munmap((void *)(aligned + size), suffix);
    *m = (gpl_memory){ (uint8_t *)aligned, state, size, host_page, system_page, 0 };
    return 0;
}

void gpl_destroy(gpl_memory *m) {
    if (!m) return;
    if (m->base) munmap(m->base, m->size);
    free(m->committed);
    memset(m, 0, sizeof *m);
}

int gpl_commit(gpl_memory *m, size_t offset, size_t length) {
    if (m && m->faulted) return -EIO;
    if (!pages(m, offset, length)) return -EINVAL;
    size_t begin = floor_host(m, offset), end = ceil_host(m, offset + length);
    if (mprotect(m->base + begin, end - begin, PROT_READ | PROT_WRITE)) { int e = errno; m->faulted = 1; return -e; }
    for (size_t i = offset / GPL_GUEST_PAGE; i < (offset + length) / GPL_GUEST_PAGE; ++i) {
        if (!m->committed[i]) memset(m->base + i * GPL_GUEST_PAGE, 0, GPL_GUEST_PAGE);
        m->committed[i] = 1;
    }
    return 0;
}

int gpl_decommit(gpl_memory *m, size_t offset, size_t length) {
    if (m && m->faulted) return -EIO;
    if (!pages(m, offset, length)) return -EINVAL;
    for (size_t i = offset / GPL_GUEST_PAGE; i < (offset + length) / GPL_GUEST_PAGE; ++i) {
        if (m->committed[i]) memset(m->base + i * GPL_GUEST_PAGE, 0, GPL_GUEST_PAGE);
        m->committed[i] = 0;
    }
    size_t end = ceil_host(m, offset + length);
    for (size_t host = floor_host(m, offset); host < end; host += m->host_page) {
        int live = 0;
        for (size_t g = host / GPL_GUEST_PAGE; g < (host + m->host_page) / GPL_GUEST_PAGE; ++g) live |= m->committed[g];
        if (!live) {
            if (mprotect(m->base + host, m->host_page, PROT_NONE)) { int e = errno; m->faulted = 1; return -e; }
            if (madvise(m->base + host, m->host_page, MADV_DONTNEED)) { int e = errno; m->faulted = 1; return -e; }
        }
    }
    return 0;
}

int gpl_is_committed(const gpl_memory *m, size_t offset, size_t length) {
    if (!bounds(m, offset, length)) return 0;
    if (!length) return 1;
    size_t last = (offset + length - 1) / GPL_GUEST_PAGE;
    for (size_t i = offset / GPL_GUEST_PAGE; i <= last; ++i) if (!m->committed[i]) return 0;
    return 1;
}
int gpl_read(const gpl_memory *m, size_t offset, void *dst, size_t length) {
    if ((!dst && length) || !gpl_is_committed(m, offset, length)) return -EFAULT;
    if (length) memcpy(dst, m->base + offset, length);
    return 0;
}
int gpl_write(gpl_memory *m, size_t offset, const void *src, size_t length) {
    if ((!src && length) || !gpl_is_committed(m, offset, length)) return -EFAULT;
    if (length) memcpy(m->base + offset, src, length);
    return 0;
}
size_t gpl_committed_bytes(const gpl_memory *m) {
    if (!m || !m->base) return 0;
    size_t count = 0;
    for (size_t i = 0; i < m->size / GPL_GUEST_PAGE; ++i) count += !!m->committed[i];
    return count * GPL_GUEST_PAGE;
}
