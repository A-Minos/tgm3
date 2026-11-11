#define _POSIX_C_SOURCE 200809L
#include "guest_pages.h"
#include <assert.h>
#include <signal.h>
#include <stdio.h>
#include <sys/wait.h>
#include <unistd.h>
static void fault(int signal_number) { (void)signal_number; _exit(77); }
static int direct_read(gpl_memory *m) {
    pid_t child = fork();
    assert(child >= 0);
    if (!child) {
        struct sigaction action = {0};
        action.sa_handler = fault;
        sigemptyset(&action.sa_mask);
        sigaction(SIGSEGV, &action, NULL);
        sigaction(SIGBUS, &action, NULL);
        volatile unsigned char value = *(volatile unsigned char *)m->base;
        (void)value;
        _exit(0);
    }
    int status;
    assert(waitpid(child, &status, 0) == child);
    assert(WIFEXITED(status));
    return WEXITSTATUS(status);
}
int main(void) {
    size_t actual = (size_t)sysconf(_SC_PAGESIZE);
    size_t groups[] = {actual, actual > 16384 ? actual : 16384};
    for (unsigned i = 0; i < 2; ++i) {
        gpl_memory m;
        assert(gpl_init(&m, 65536, groups[i]) == 0);
        assert(gpl_commit(&m, 0, 4096) == 0);
        assert(direct_read(&m) == 0);
        assert(gpl_decommit(&m, 0, 4096) == 0);
        assert(direct_read(&m) == 77);
        printf("PASS: wholly decommitted host group faults on direct read, grouping=%zu\n", m.host_page);
        gpl_destroy(&m);
    }
    return 0;
}
