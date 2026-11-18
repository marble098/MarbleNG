# Shared architecture gate for the Android Go cores.
assert_elf_machine() {
    local readelf="$1" file="$2" expected="$3"
    local header actual

    # Read the entire header and check readelf's exit status before matching.
    # `readelf | grep -q` under pipefail can reject a valid ELF when readelf
    # writes more output after grep has already closed the pipe.
    if ! header="$(LC_ALL=C "$readelf" -h "$file" 2>&1)"; then
        printf 'Could not read ELF header for %s:\n%s\n' "$file" "$header" >&2
        return 1
    fi

    actual="$(sed -n 's/^[[:space:]]*Machine:[[:space:]]*//p' <<< "$header")"
    if [[ "$actual" != "$expected" ]]; then
        printf 'ELF machine mismatch for %s: expected %s, got %s\n' \
            "$file" "$expected" "${actual:-<missing>}" >&2
        return 1
    fi
}
