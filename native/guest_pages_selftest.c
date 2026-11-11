#include "guest_pages.h"
#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static void append(char *s, size_t cap, const char *fmt, ...) {
    if (!cap) return;
    size_t used = strlen(s);
    if (used >= cap - 1) return;
    va_list ap; va_start(ap, fmt); vsnprintf(s + used, cap - used, fmt, ap); va_end(ap);
}
#define CHECK(expr) do { if (!(expr)) { append(report, report_size, "FAIL line %d: %s\n", __LINE__, #expr); gpl_destroy(&m); return 1; } } while (0)
int gpl_run_selftests(char *report, size_t report_size) {
    if (!report || !report_size) return 1;
    report[0] = 0;
    gpl_memory m = {0};
    long native_page = sysconf(_SC_PAGESIZE);
    append(report, report_size, "Port Lab: no game included\nSystem page: %ld bytes\n", native_page);
    CHECK(native_page >= (long)GPL_GUEST_PAGE);
    const size_t groups[] = {0, (size_t)native_page > 16384 ? (size_t)native_page : 16384};
    for (unsigned trial = 0; trial < 2; ++trial) {
        CHECK(gpl_init(&m, 131072, groups[trial]) == 0);
        CHECK(!((uintptr_t)m.base % m.host_page));
        CHECK(gpl_commit(&m, 4096, 8192) == 0);
        uint32_t a = 0x12345678, b = 0xdeadbeef, got = 0;
        CHECK(gpl_write(&m, 4097, &a, sizeof a) == 0);
        CHECK(gpl_write(&m, 8193, &b, sizeof b) == 0);
        CHECK(gpl_committed_bytes(&m) == 8192);
        CHECK(gpl_decommit(&m, 4096, 4096) == 0);
        CHECK(gpl_read(&m, 4097, &got, sizeof got) == -EFAULT);
        CHECK(gpl_read(&m, 8193, &got, sizeof got) == 0 && got == b);
        CHECK(gpl_commit(&m, 4096, 4096) == 0);
        got = 1; CHECK(gpl_read(&m, 4097, &got, sizeof got) == 0 && got == 0);
        CHECK(gpl_read(&m, 8193, &got, sizeof got) == 0 && got == b);
        CHECK(gpl_decommit(&m, 4096, 8192) == 0);
        CHECK(gpl_committed_bytes(&m) == 0);
        CHECK(gpl_commit(&m, 1, 4096) == -EINVAL);
        CHECK(gpl_commit(&m, 131072, 4096) == -EINVAL);
        CHECK(gpl_read(&m, SIZE_MAX - 1, &got, sizeof got) == -EFAULT);
        CHECK(gpl_commit(&m, 0, 4096) == 0);
        CHECK(gpl_write(&m, 4094, &got, sizeof got) == -EFAULT);
        append(report, report_size, "PASS: %zu-byte host groups, subpage decommit, neighbor preservation, zero-on-recommit, bounds\n", m.host_page);
        gpl_destroy(&m);
    }
    if (sizeof(size_t) >= 8) {
        CHECK(gpl_init(&m, (size_t)UINT64_C(4294967296), 0) == 0);
        CHECK(gpl_commit(&m, 0xfffff000u, 4096) == 0);
        uint32_t value = 0xaabbccdd, got = 0;
        CHECK(gpl_write(&m, 0xfffffffcu, &value, 4) == 0);
        CHECK(gpl_read(&m, 0xfffffffcu, &got, 4) == 0 && got == value);
        CHECK(gpl_write(&m, 0xfffffffeu, &value, 4) == -EFAULT);
        append(report, report_size, "PASS: 4 GiB virtual window, last guest page, 32-bit boundary\n");
        gpl_destroy(&m);
    } else append(report, report_size, "SKIP: 4 GiB reservation requires 64-bit host\n");
    append(report, report_size, "PASS: resource-free memory probes\nNot a game or graphics compatibility test.\n");
    return 0;
}
