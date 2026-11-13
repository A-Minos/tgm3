# Optional verified resource bootstrap

The default public app remains an external ZIP importer. Its LauncherActivity,
application ID, manifest, native library requirements and asset policy are not
changed. This source export contains no bundle or native runtime.

`BundledResources` is an optional, inactive helper for an authorized private
build. It reads `bundled-game.manifest` and `bundled-game.zip` from assets only
when explicitly called by that build. The manifest is UTF-8 TSV with one
`sha256<TAB>byte-length<TAB>relative-path` line per regular resource file.
Only `game.exe` and files below `data/` are accepted. Paths, byte limits,
duplicates, hashes, missing/extra files and resource symlinks are checked.
It is a consistency check against the packaged manifest, not a license or
signature verification service.

The importer now offers a staged verifier callback, run before the existing
save-link and atomic directory replacement steps. The two-argument method
used by the public launcher delegates with no extra callback, preserving its
behavior. Saves remain outside the replaceable resource directory. A separate
application ID is required for a new signing identity to avoid breaking old
installations; this helper does not migrate another app's private saves.

Run `ANDROID_HOME=... JAVA_HOME=... ./run_resource_checks.sh` for synthetic
checks requiring no original game files. SDK platform 35 supplies compilation
stubs only; the tests execute file operations on the host JVM. Optionally pass
a locally authorized 226-file private bundle ZIP and its manifest for the
full import/save integration checks. That optional integration test does
require matching original game files, which are not included here.

These checks are not Android lifecycle, audio, gameplay or physical-device
verification. Adding this helper alone does not create a complete APK or
reconstruct the omitted native game implementation.
