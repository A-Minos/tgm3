#define _GNU_SOURCE
#include "ogg_wav.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

/* Test/diagnostic tool, not a replacement for the game's CreateFileA. */
int main(int argc, char **argv) {
    int fd, seals, descriptor_flags, fallback = 0;
    struct stat st;
    unsigned char block[16384];
    if (argc != 2 && !(argc == 3 && !strcmp(argv[2], "--probe"))) {
        fprintf(stderr, "usage: %s missing.wav [--probe]\n", argv[0]); return 2;
    }
    fd = open(argv[1], O_RDONLY | O_CLOEXEC);
    if (fd < 0 && errno == ENOENT) { fallback = 1; fd = tgm_ogg_wav_fallback(argv[1]); }
    if (fd < 0) { fprintf(stderr, "open failed errno=%d: %s\n", errno, strerror(errno)); return 1; }
    descriptor_flags = fcntl(fd, F_GETFD);
    if (fstat(fd, &st) || lseek(fd, 0, SEEK_SET) != 0 || descriptor_flags < 0 ||
        !(descriptor_flags & FD_CLOEXEC)) {
        fprintf(stderr, "fd contract failed\n"); close(fd); return 1;
    }
    seals = fcntl(fd, F_GET_SEALS);
    if (fallback && (seals < 0 || (seals & 15) != 15 || write(fd, "x", 1) != -1 || errno != EPERM ||
                     ftruncate(fd, 0) != -1 || errno != EPERM)) {
        fprintf(stderr, "sealing failed\n"); close(fd); return 1;
    }
    if (argc == 3) {
        printf("{\"fallback\":%s,\"bytes\":%lld,\"seals\":%d}\n",
               fallback ? "true" : "false", (long long)st.st_size, seals);
    } else {
        ssize_t n;
        while ((n = read(fd, block, sizeof(block))) > 0)
            if (fwrite(block, 1, (size_t)n, stdout) != (size_t)n) { close(fd); return 1; }
        if (n < 0 || fflush(stdout)) { close(fd); return 1; }
    }
    close(fd); return 0;
}
