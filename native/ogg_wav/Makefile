CC ?= cc
CFLAGS ?= -O2 -g
WARNINGS = -std=c11 -Wall -Wextra -Wpedantic -Werror
CPPFLAGS ?=
LDFLAGS ?=
LDLIBS = -lm

all: verify_loader
ogg_wav.o: ogg_wav.c ogg_wav.h stb_vorbis.c
	$(CC) $(CPPFLAGS) $(CFLAGS) $(WARNINGS) -c $< -o $@
vorbis_decoder.o: vorbis_decoder.c stb_vorbis.c
	$(CC) $(CPPFLAGS) $(CFLAGS) -std=c11 -c $< -o $@
verify_loader.o: verify_loader.c ogg_wav.h
	$(CC) $(CPPFLAGS) $(CFLAGS) $(WARNINGS) -c $< -o $@
verify_loader: verify_loader.o ogg_wav.o vorbis_decoder.o
	$(CC) $(LDFLAGS) $^ $(LDLIBS) -o $@
test_fd_lifecycle.o: test_fd_lifecycle.c ogg_wav.h
	$(CC) $(CPPFLAGS) $(CFLAGS) $(WARNINGS) -c $< -o $@
test_fd_lifecycle: test_fd_lifecycle.o ogg_wav.o vorbis_decoder.o
	$(CC) $(LDFLAGS) $^ $(LDLIBS) -o $@
test: verify_loader test_fd_lifecycle
	python3 test_loader.py ./verify_loader
clean:
	rm -f verify_loader test_fd_lifecycle *.o
.PHONY: all test clean
