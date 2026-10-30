# Baritone dependency

ApexCraft links to the unmodified official Baritone Fabric 1.20.0 release for
Minecraft 26.3. Baritone is a separate LGPL-3.0 component, not MIT-licensed ApexCraft
code. Its complete corresponding source and build instructions are available at:
https://github.com/cabaletta/baritone/tree/v1.20.0
Release: https://github.com/cabaletta/baritone/releases/tag/v1.20.0
Source commit: 25111da (v1.20.0)
JAR SHA-256: 7836a235f37339897425ec303b5c0d7f3cf9d99dbdaa1952240b5099aafd8776

The distribution contains the original dependency as a separate, replaceable JAR.
ApexCraft adds a scoped runtime mixin that replaces Baritone's block-breaking
helper with normal client mouse-button input while ApexCraft owns the process.
That adaptation's complete source is in src/client/java/dev/apexcraft/client/mixin.
No upstream source is copied into ApexCraft's MIT Java classes.

The unoptimized official release is required so the scoped input mixin can target
the upstream helper by its stable name. Do not install a second Baritone JAR.
