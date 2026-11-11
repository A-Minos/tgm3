#include "guest_pages.h"
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <sys/mman.h>
static int fail_protect, fail_advise;
int __real_mprotect(void *, size_t, int);
int __real_madvise(void *, size_t, int);
int __wrap_mprotect(void *p, size_t n, int prot) {
    if (fail_protect) { fail_protect = 0; errno = ENOMEM; return -1; }
    return __real_mprotect(p, n, prot);
}
int __wrap_madvise(void *p, size_t n, int advice) {
    if (fail_advise) { fail_advise = 0; errno = EINVAL; return -1; }
    return __real_madvise(p, n, advice);
}
static void assert_poisoned(gpl_memory *m) {
    unsigned char x = 0;
    assert(m->faulted);
    assert(gpl_read(m, 0, &x, 1) == -EFAULT);
    assert(gpl_write(m, 0, &x, 1) == -EFAULT);
    assert(gpl_commit(m, 0, GPL_GUEST_PAGE) == -EIO);
    assert(gpl_decommit(m, 0, GPL_GUEST_PAGE) == -EIO);
    gpl_destroy(m);
    assert(!m->base && !m->faulted);
}
int main(void) {
    gpl_memory m;
    assert(gpl_init(&m, 65536, 0) == 0);
    fail_protect = 1;
    assert(gpl_commit(&m, 0, 4096) == -ENOMEM);
    assert_poisoned(&m);
    assert(gpl_init(&m, 65536, 0) == 0);
    assert(gpl_commit(&m, 0, 4096) == 0);
    fail_protect = 1;
    assert(gpl_decommit(&m, 0, 4096) == -ENOMEM);
    assert_poisoned(&m);
    assert(gpl_init(&m, 65536, 0) == 0);
    assert(gpl_commit(&m, 0, 4096) == 0);
    fail_advise = 1;
    assert(gpl_decommit(&m, 0, 4096) == -EINVAL);
    assert_poisoned(&m);
    puts("PASS: injected host syscall failures poison the object and reject further access");
    return 0;
}
