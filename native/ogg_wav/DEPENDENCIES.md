# Decoder dependency

`stb_vorbis.c` is the unmodified upstream Ogg Vorbis decoder v1.22 from:

- Repository: https://github.com/nothings/stb
- Commit: `f58f558c120e9b32c217290b80bad1a0729fbb2c`
- File: https://github.com/nothings/stb/blob/f58f558c120e9b32c217290b80bad1a0729fbb2c/stb_vorbis.c
- SHA-256: `4c7cb2ff1f7011e9d67950446b7eb9ca044f2e464d76bfbb0b84dd2e23e65636`
- Exact Git blob: `3e5c2504c08f76b9d04e4adfbce80818631e835e` (192,790 bytes).

The complete upstream dual MIT/public-domain notice is retained at the end of
that file. The MIT option can be used. `vorbis_decoder.c` supplies build options
without changing the vendored source. No upstream TGM3 runtime or game-derived
code is present in this directory.

The other files here are the new compatibility loader, test tooling and
documentation written for this project. The repository has not selected a
blanket license; the decoder's license does not license unrelated project code.
