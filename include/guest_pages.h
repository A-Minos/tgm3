#ifndef PORTLAB_GUEST_PAGES_H
#define PORTLAB_GUEST_PAGES_H
#include <stddef.h>
#include <stdint.h>

/* Original experimental allocator. Caller serializes access. No game code. */
#define GPL_GUEST_PAGE 4096u
typedef struct {
    uint8_t *base;
    uint8_t *committed;
    size_t size;
    size_t host_page;
    size_t system_page;
    int faulted;
} gpl_memory;

/* host_page=0 uses sysconf; larger multiples enable host-grouping tests.
 * Initialize only a fresh object or one already passed to gpl_destroy.
 * Calling init on a live object is unsupported and would lose its allocation. */
int gpl_init(gpl_memory *mem, size_t size, size_t host_page);
void gpl_destroy(gpl_memory *mem);
/* Host syscall errors can leave a partial operation; the object is then
 * poisoned. Do not assume rollback or preserved data. Only destroy is allowed.
 * Invalid arguments do not poison an otherwise healthy object. */
int gpl_commit(gpl_memory *mem, size_t offset, size_t length);
int gpl_decommit(gpl_memory *mem, size_t offset, size_t length);
int gpl_is_committed(const gpl_memory *mem, size_t offset, size_t length);
int gpl_read(const gpl_memory *mem, size_t offset, void *dst, size_t length);
int gpl_write(gpl_memory *mem, size_t offset, const void *src, size_t length);
size_t gpl_committed_bytes(const gpl_memory *mem);
int gpl_run_selftests(char *report, size_t report_size);
#endif
