package dev.bluehouse.enablevolte

/** Generated for the module's boot stage. No parent directory or partition is mounted. */
internal fun regionalSingleFileBootScript(
    targetPath: String = "/vendor/firmware/carrierconfig/cfg.db",
): String {
    val quotedTarget = "'" + targetPath.replace("'", "'\\''") + "'"
    return """
        #!/system/bin/sh
        MODDIR="${'$'}{0%/*}"
        TARGET=$quotedTarget
        SOURCE="${'$'}MODDIR/files/cfg.db"
        STATUS="${'$'}MODDIR/bind.status"
        fail() { printf '%s\n' "${'$'}1" > "${'$'}STATUS"; exit 0; }
        [ -f "${'$'}MODDIR/disable" ] && exit 0
        [ -f "${'$'}MODDIR/remove" ] && exit 0
        rm -f "${'$'}MODDIR/.pending_reboot"
        [ "${'$'}KSU_LATE_LOAD" = 1 ] && fail 'late-load unsupported; reboot with normal KernelSU boot integration'
        [ -f "${'$'}SOURCE" ] && [ ! -L "${'$'}SOURCE" ] || fail 'staged database missing or symlinked'
        [ -f "${'$'}TARGET" ] && [ ! -L "${'$'}TARGET" ] || fail 'live database missing or symlinked'
        SELF_NS="${'$'}(readlink /proc/self/ns/mnt)"
        INIT_NS="${'$'}(readlink /proc/1/ns/mnt)"
        [ -n "${'$'}SELF_NS" ] && [ -n "${'$'}INIT_NS" ] || fail 'mount namespace unreadable'
        if [ "${'$'}SELF_NS" != "${'$'}INIT_NS" ]; then
            [ "${'$'}1" = --init-mount ] && fail 'init mount namespace was not entered'
            nsenter -t 1 -m -- /data/adb/ksu/bin/busybox sh "${'$'}0" --init-mount || fail 'init mount namespace unavailable'
            exit 0
        fi
        hash_file() { sha256sum "${'$'}1" 2>/dev/null | awk '{print ${'$'}1}'; }
        STOCK="${'$'}(cat "${'$'}MODDIR/source.sha256")"
        PATCHED="${'$'}(cat "${'$'}MODDIR/patched.sha256")"
        [ "${'$'}{#STOCK}" = 64 ] && [ "${'$'}{#PATCHED}" = 64 ] || fail 'hash metadata invalid'
        [ "${'$'}(hash_file "${'$'}SOURCE")" = "${'$'}PATCHED" ] || fail 'staged database checksum mismatch'
        LIVE="${'$'}(hash_file "${'$'}TARGET")"
        if [ "${'$'}LIVE" = "${'$'}PATCHED" ]; then
            printf '%s\n' active > "${'$'}STATUS"
            exit 0
        fi
        [ "${'$'}LIVE" = "${'$'}STOCK" ] || fail 'firmware database changed; validate a fresh patch'
        LABEL="${'$'}(ls -Zd "${'$'}TARGET" | awk '{print ${'$'}1}')"
        case "${'$'}LABEL" in u:object_r:*:s0*) ;; *) fail 'live file SELinux label unreadable';; esac
        chcon "${'$'}LABEL" "${'$'}SOURCE" || fail 'staged file SELinux label could not be set'
        [ "${'$'}(ls -Zd "${'$'}SOURCE" | awk '{print ${'$'}1}')" = "${'$'}LABEL" ] || fail 'staged file label verification failed'
        mount --bind "${'$'}SOURCE" "${'$'}TARGET" || fail 'single-file mount failed'
        rollback() {
            umount "${'$'}TARGET" || fail "${'$'}1; rollback unmount failed"
            [ "${'$'}(hash_file "${'$'}TARGET")" = "${'$'}STOCK" ] || fail "${'$'}1; stock restore verification failed"
            fail "${'$'}1"
        }
        # Supplying both paths avoids BusyBox's one-path mount-table lookup.
        if REMOUNT_ERROR="${'$'}(mount -o remount,bind,ro "${'$'}SOURCE" "${'$'}TARGET" 2>&1)"; then
            :
        else
            rollback "read-only bind remount failed: ${'$'}REMOUNT_ERROR"
        fi
        if [ "${'$'}(hash_file "${'$'}TARGET")" != "${'$'}PATCHED" ]; then
            rollback 'live database verification failed'
        fi
        printf '%s\n' active > "${'$'}STATUS"
    """.trimIndent() + "\n"
}
