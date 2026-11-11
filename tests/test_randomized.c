#include "guest_pages.h"
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define NPAGES 64u
static uint32_t rng = 0x13579bdfu;
static uint32_t next(void) { rng = rng * 1664525u + 1013904223u; return rng; }
int main(void) {
    size_t actual = (size_t)sysconf(_SC_PAGESIZE);
    size_t modes[2] = {actual, actual > 16384 ? actual : 16384};
    for (unsigned mode = 0; mode < 2; ++mode) {
        gpl_memory m;
        if (gpl_init(&m, NPAGES * GPL_GUEST_PAGE, modes[mode])) return 2;
        unsigned char states[NPAGES] = {0};
        unsigned char *expected = calloc(NPAGES, GPL_GUEST_PAGE);
        unsigned char scratch[GPL_GUEST_PAGE];
        if (!expected) return 2;
        for (unsigned step = 0; step < 3000; ++step) {
            unsigned start = next() % NPAGES;
            unsigned count = 1 + next() % 4;
            if (count > NPAGES - start) count = NPAGES - start;
            int commit = !!(next() & 0x100);
            int status = commit ? gpl_commit(&m, start * GPL_GUEST_PAGE, count * GPL_GUEST_PAGE) :
                                  gpl_decommit(&m, start * GPL_GUEST_PAGE, count * GPL_GUEST_PAGE);
            if (status) { fprintf(stderr, "operation failed: %d\n", status); return 1; }
            for (unsigned p = start; p < start + count; ++p) {
                if (!commit || !states[p]) memset(expected + p * GPL_GUEST_PAGE, 0, GPL_GUEST_PAGE);
                states[p] = (unsigned char)commit;
            }
            unsigned changed = next() % NPAGES;
            if (states[changed]) {
                memset(expected + changed * GPL_GUEST_PAGE, (int)(next() & 255), GPL_GUEST_PAGE);
                if (gpl_write(&m, changed * GPL_GUEST_PAGE, expected + changed * GPL_GUEST_PAGE, GPL_GUEST_PAGE)) return 1;
            }
            size_t active = 0;
            for (unsigned p = 0; p < NPAGES; ++p) {
                int r = gpl_read(&m, p * GPL_GUEST_PAGE, scratch, sizeof scratch);
                if ((states[p] && (r || memcmp(scratch, expected + p * GPL_GUEST_PAGE, sizeof scratch))) ||
                    (!states[p] && r != -EFAULT)) {
                    fprintf(stderr, "model mismatch: host=%zu step=%u page=%u\n", m.host_page, step, p);
                    return 1;
                }
                active += states[p] * GPL_GUEST_PAGE;
            }
            if (active != gpl_committed_bytes(&m)) return 1;
        }
        printf("PASS: 3000 deterministic randomized operations, host grouping=%zu\n", m.host_page);
        free(expected);
        gpl_destroy(&m);
    }
    return 0;
}
