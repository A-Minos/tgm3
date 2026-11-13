/* Original Ogg/WAV compatibility loader. See README.md for integration rules. */
#ifndef TGM_OGG_WAV_H
#define TGM_OGG_WAV_H
#ifdef __cplusplus
extern "C" {
#endif
/* Rebuild a seekable, sealed PCM16 WAV from foo.ogg + foo.ogg.wavmeta.
 * Call ONLY after a read-only OPEN_EXISTING open of foo.wav failed with ENOENT.
 * Success: owned CLOEXEC fd at offset zero; caller closes it. Failure: -1/errno.
 * No input is modified. No padding, truncation or guessed metadata is allowed.
 * Linux/Android memfd support is required; other platforms return ENOSYS.
 */
int tgm_ogg_wav_fallback(const char *wav_path);
/* Same fd contract, with separately resolved host paths. Use this variant in
 * Windows-compatibility layers so each candidate undergoes their normal
 * case-insensitive path resolution. Neither path is modified or created. */
int tgm_ogg_wav_from_paths(const char *ogg_path, const char *metadata_path);
#ifdef __cplusplus
}
#endif
#endif
