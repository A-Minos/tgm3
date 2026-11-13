#define _GNU_SOURCE
#include "ogg_wav.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
static int descriptor_count(void) {
    DIR *d = opendir("/proc/self/fd");
    struct dirent *e;
    int n = 0;
    if (!d) return -1;
    while ((e = readdir(d))) if (e->d_name[0] != '.') ++n;
    closedir(d);
    return n;
}
int main(int argc, char **argv) {
    if (argc == 4 && !strcmp(argv[1], "--paths")) {
        int fd = tgm_ogg_wav_from_paths(argv[2], argv[3]);
        if (fd < 0) return 1;
        int flags = fcntl(fd, F_GETFD), seals = fcntl(fd, F_GET_SEALS);
        int valid = flags >= 0 && (flags & FD_CLOEXEC) && seals >= 0 && (seals & 15) == 15 && lseek(fd, 0, SEEK_CUR) == 0;
        close(fd);
        return valid ? 0 : 1;
    }
    if (argc != 3) return 2;
    if (tgm_ogg_wav_fallback(NULL) != -1 || errno != EINVAL ||
        tgm_ogg_wav_from_paths(NULL, argv[2]) != -1 || errno != EINVAL ||
        tgm_ogg_wav_from_paths(argv[1], NULL) != -1 || errno != EINVAL) return 1;
    int baseline = descriptor_count();
    if (baseline < 0) return 2;
    for (int i = 0; i < 200; ++i) {
        int fd = tgm_ogg_wav_fallback(argv[1]);
        if (fd < 0) { perror("valid open"); return 1; }
        char bytes[12];
        if (read(fd, bytes, sizeof(bytes)) != sizeof(bytes) ||
            lseek(fd, -1, SEEK_END) < 0 || read(fd, bytes, 1) != 1 ||
            lseek(fd, 0, SEEK_SET) != 0) return 1;
        if (close(fd)) return 1;
        fd = tgm_ogg_wav_fallback(argv[2]);
        if (fd >= 0) { close(fd); return 1; }
        if (descriptor_count() != baseline) { fprintf(stderr,"fd leak at iteration %d\n",i); return 1; }
    }
    printf("200 valid + 200 rejected opens: no fd growth; read/seek/close passed\n");
    return 0;
}
