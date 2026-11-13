#define _GNU_SOURCE
#include "ogg_wav.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

#define PCM_LIMIT (64u * 1024u * 1024u)
#define META_LIMIT (1024u * 1024u)
#define OGG_LIMIT (64u * 1024u * 1024u)
#define DECODER_LIMIT (8u * 1024u * 1024u)
#define PAGE_LIMIT (27u + 255u + 255u * 255u)
#define PATH_LIMIT 4096u

struct wav_description {
    uint32_t pcm_bytes, frames;
    unsigned int channels, rate;
    size_t prefix_bytes;
};

static uint16_t le16(const unsigned char *p) {
    return (uint16_t)((uint16_t)p[0] | (uint16_t)p[1] << 8);
}
static uint32_t le32(const unsigned char *p) {
    return (uint32_t)p[0] | (uint32_t)p[1] << 8 |
           (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
static uint64_t le64(const unsigned char *p) {
    return (uint64_t)le32(p) | (uint64_t)le32(p + 4) << 32;
}
static int invalid(void) { errno = EINVAL; return -1; }

/* wavmeta is the exact WAV with only the data payload removed. RIFF and data
 * size fields still describe the original WAV. fmt need not precede data. */
static int parse_metadata(const unsigned char *m, size_t n,
                          struct wav_description *w) {
    size_t p = 12;
    int have_fmt = 0, have_data = 0;
    if (n < 12 || memcmp(m, "RIFF", 4) || memcmp(m + 8, "WAVE", 4))
        return invalid();
    memset(w, 0, sizeof(*w));
    while (p < n) {
        uint32_t size;
        size_t physical;
        if (n - p < 8) return invalid();
        size = le32(m + p + 4);
        p += 8;
        if (!memcmp(m + p - 8, "data", 4)) {
            if (have_data++ || !size || size > PCM_LIMIT) return invalid();
            w->pcm_bytes = size;
            w->prefix_bytes = p;
            physical = size & 1u; /* data padding, if any, is not removed */
        } else {
            if ((uint64_t)size + (size & 1u) > n - p) return invalid();
            physical = (size_t)size + (size & 1u);
            if (!memcmp(m + p - 8, "fmt ", 4)) {
                if (have_fmt++ || size != 16 || le16(m + p) != 1 ||
                    le16(m + p + 14) != 16) return invalid();
                w->channels = le16(m + p + 2);
                w->rate = le32(m + p + 4);
                if ((w->channels != 1 && w->channels != 2) ||
                    w->rate != 44100 || le16(m + p + 12) != w->channels * 2 ||
                    le32(m + p + 8) != w->rate * w->channels * 2)
                    return invalid();
            }
        }
        if (physical > n - p) return invalid();
        p += physical;
    }
    if (!have_fmt || !have_data || w->pcm_bytes % (w->channels * 2) ||
        (uint64_t)le32(m + 4) + 8 != (uint64_t)n + w->pcm_bytes)
        return invalid();
    w->frames = w->pcm_bytes / (w->channels * 2);
    return 0;
}

static FILE *open_regular(const char *path, size_t limit, size_t *size) {
    struct stat st;
    FILE *f;
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NONBLOCK);
    if (fd < 0) return NULL;
    if (fstat(fd, &st) < 0) {
        int e = errno; close(fd); errno = e; return NULL;
    }
    if (!S_ISREG(st.st_mode) || st.st_size <= 0 || (uint64_t)st.st_size > limit) {
        close(fd); errno = EINVAL; return NULL;
    }
    f = fdopen(fd, "rb");
    if (!f) { int e = errno; close(fd); errno = e; return NULL; }
    *size = (size_t)st.st_size;
    return f;
}

/* Validate complete Ogg framing, checksums and one logical stream. This rejects
 * truncation, trailing garbage and chained streams instead of accepting only
 * the initial decodable portion. The decoder separately verifies Vorbis data. */
static int validate_ogg(FILE *f, size_t length, uint32_t frames) {
    uint32_t crc_table[256], serial = 0, sequence = 0;
    uint64_t previous_granule = 0;
    unsigned char *page = malloc(PAGE_LIMIT);
    size_t consumed = 0;
    int first = 1, continuation = 0, eos = 0;
    if (!page) return -1;
    for (unsigned int i = 0; i < 256; ++i) {
        uint32_t r = i << 24;
        for (int bit = 0; bit < 8; ++bit)
            r = (r << 1) ^ ((r & 0x80000000u) ? 0x04c11db7u : 0);
        crc_table[i] = r;
    }
    while (consumed < length) {
        size_t body = 0, size;
        unsigned int segments, flags;
        uint32_t crc = 0, expected_crc;
        uint64_t granule;
        if (eos || length - consumed < 27 || fread(page, 1, 27, f) != 27)
            goto bad;
        if (memcmp(page, "OggS", 4) || page[4] != 0) goto bad;
        flags = page[5]; segments = page[26]; granule = le64(page + 6);
        if ((flags & ~7u) || !!(flags & 1u) != continuation) goto bad;
        if (first) {
            if (!(flags & 2u) || le32(page + 18) != 0) goto bad;
            serial = le32(page + 14);
        } else if ((flags & 2u) || le32(page + 14) != serial) goto bad;
        if (le32(page + 18) != sequence++) goto bad;
        if (length - consumed < 27u + segments ||
            fread(page + 27, 1, segments, f) != segments) goto bad;
        for (unsigned int i = 0; i < segments; ++i) body += page[27 + i];
        size = 27u + segments + body;
        if (size > length - consumed || fread(page + 27 + segments, 1, body, f) != body)
            goto bad;
        continuation = segments ? page[26 + segments] == 255 : continuation;
        expected_crc = le32(page + 22); memset(page + 22, 0, 4);
        for (size_t i = 0; i < size; ++i)
            crc = (crc << 8) ^ crc_table[((crc >> 24) ^ page[i]) & 255];
        if (crc != expected_crc) goto bad;
        if (granule != UINT64_MAX) {
            if (granule < previous_granule || granule > frames) goto bad;
            previous_granule = granule;
        }
        eos = !!(flags & 4u);
        if (eos && (continuation || granule != frames)) goto bad;
        first = 0; consumed += size;
    }
    free(page);
    if (first || !eos || ferror(f) || fseeko(f, 0, SEEK_SET)) return invalid();
    return 0;
bad:
    free(page); return invalid();
}

static int write_all(int fd, const void *data, size_t length) {
    const unsigned char *p = data;
    while (length) {
        ssize_t n = write(fd, p, length);
        if (n < 0) { if (errno == EINTR) continue; return -1; }
        if (!n) { errno = EIO; return -1; }
        p += n; length -= (size_t)n;
    }
    return 0;
}

static int anonymous_wav(void) {
#if defined(__linux__) && defined(SYS_memfd_create)
    return (int)syscall(SYS_memfd_create, "tgm-ogg-wav", 0x0001u | 0x0002u);
#else
    errno = ENOSYS; return -1;
#endif
}

int tgm_ogg_wav_from_paths(const char *ogg_path, const char *meta_path) {
    size_t meta_size = 0, ogg_size = 0;
    FILE *meta = NULL, *ogg = NULL;
    unsigned char *metadata = NULL, *decode_memory = NULL;
    stb_vorbis *decoder = NULL;
    struct wav_description w;
    int fd = -1, error = 0, saved_errno = EINVAL;
    stb_vorbis_alloc arena;
    stb_vorbis_info info;
    short samples[2048];
    unsigned char pcm[4096];
    if (!ogg_path || !meta_path || strnlen(ogg_path, PATH_LIMIT) == PATH_LIMIT ||
        strnlen(meta_path, PATH_LIMIT) == PATH_LIMIT) return invalid();
    meta = open_regular(meta_path, META_LIMIT, &meta_size);
    if (!meta) goto fail;
    metadata = malloc(meta_size);
    if (!metadata) goto fail;
    if (fread(metadata, 1, meta_size, meta) != meta_size || fgetc(meta) != EOF || ferror(meta)) {
        errno = EINVAL; goto fail;
    }
    if (parse_metadata(metadata, meta_size, &w) < 0) goto fail;
    ogg = open_regular(ogg_path, OGG_LIMIT, &ogg_size);
    if (!ogg || validate_ogg(ogg, ogg_size, w.frames) < 0) goto fail;
    decode_memory = malloc(DECODER_LIMIT);
    if (!decode_memory) goto fail;
    arena.alloc_buffer = (char *)decode_memory;
    arena.alloc_buffer_length_in_bytes = (int)DECODER_LIMIT;
    decoder = stb_vorbis_open_file(ogg, 0, &error, &arena);
    if (!decoder) { errno = error == VORBIS_outofmem ? ENOMEM : EINVAL; goto fail; }
    info = stb_vorbis_get_info(decoder);
    if (info.channels != (int)w.channels || info.sample_rate != w.rate ||
        stb_vorbis_stream_length_in_samples(decoder) != w.frames) {
        errno = EINVAL; goto fail;
    }
    fd = anonymous_wav();
    if (fd < 0 || write_all(fd, metadata, w.prefix_bytes) < 0) goto fail;
    for (uint32_t done = 0; done < w.frames;) {
        uint32_t want = w.frames - done;
        int n;
        if (want > 1024) want = 1024;
        n = stb_vorbis_get_samples_short_interleaved(decoder, (int)w.channels,
                                                    samples, (int)(want * w.channels));
        if (n <= 0 || (uint32_t)n > want) { errno = EINVAL; goto fail; }
        for (size_t i = 0; i < (size_t)n * w.channels; ++i) {
            uint16_t sample = (uint16_t)samples[i];
            pcm[i * 2] = (unsigned char)sample;
            pcm[i * 2 + 1] = (unsigned char)(sample >> 8);
        }
        if (write_all(fd, pcm, (size_t)n * w.channels * 2) < 0) goto fail;
        done += (uint32_t)n;
    }
    /* Independently require exact actual output; never pad/trim the decoded stream. */
    if (stb_vorbis_get_samples_short_interleaved(decoder, (int)w.channels,
                                               samples, (int)w.channels) != 0 ||
        stb_vorbis_get_error(decoder) != VORBIS__no_error) {
        errno = EINVAL; goto fail;
    }
    if (write_all(fd, metadata + w.prefix_bytes, meta_size - w.prefix_bytes) < 0 ||
        lseek(fd, 0, SEEK_SET) != 0) goto fail;
#if defined(F_ADD_SEALS) && defined(F_SEAL_WRITE)
    if (fcntl(fd, F_ADD_SEALS, F_SEAL_SEAL | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE) < 0)
        goto fail;
#else
    errno = ENOSYS; goto fail;
#endif
    stb_vorbis_close(decoder); free(decode_memory); fclose(ogg);
    free(metadata); fclose(meta);
    return fd;
fail:
    saved_errno = errno ? errno : EINVAL;
    if (decoder) stb_vorbis_close(decoder);
    free(decode_memory);
    if (ogg) fclose(ogg);
    free(metadata);
    if (meta) fclose(meta);
    if (fd >= 0) close(fd);
    errno = saved_errno;
    return -1;
}

int tgm_ogg_wav_fallback(const char *wav_path) {
    char ogg_path[PATH_LIMIT], meta_path[PATH_LIMIT];
    size_t len;
    if (!wav_path) return invalid();
    len = strnlen(wav_path, PATH_LIMIT);
    if (len < 4 || len > PATH_LIMIT - sizeof(".wavmeta") || wav_path[len-4] != '.' ||
        (wav_path[len-3] != 'w' && wav_path[len-3] != 'W') ||
        (wav_path[len-2] != 'a' && wav_path[len-2] != 'A') ||
        (wav_path[len-1] != 'v' && wav_path[len-1] != 'V')) return invalid();
    memcpy(ogg_path, wav_path, len - 3); memcpy(ogg_path + len - 3, "ogg", 4);
    memcpy(meta_path, ogg_path, len); memcpy(meta_path + len, ".wavmeta", 9);
    return tgm_ogg_wav_from_paths(ogg_path, meta_path);
}
