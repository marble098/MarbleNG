#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/lib/native-elf.sh"

scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
readelf="$scratch/llvm-readelf"

cat > "$readelf" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
[[ "$#" == 2 && "$1" == -h ]]

case "$2" in
    'x86_64 core') machine='Advanced Micro Devices X86-64' ;;
    'x86 core') machine='Intel 80386' ;;
    'arm64 core') machine='AArch64' ;;
    'arm core') machine='ARM' ;;
    'wrong machine') machine='ARM64' ;;
    'no machine') printf '  NotMachine: ARM\n'; exit 0 ;;
    'read failure')
        printf '  Machine: ARM\n'
        printf 'invalid ELF header\n' >&2
        exit 2
        ;;
    *) exit 3 ;;
esac
printf 'ELF Header:\n  Machine:                         %s\n' "$machine"
if [[ "$2" == 'x86_64 core' ]]; then
    # Keep writing after the Machine line. An early-exit grep under pipefail
    # closes the pipe and makes this otherwise successful producer fail.
    awk 'BEGIN {for (i = 0; i < 4096; i++) print "  Type: DYN (Shared object file)"}'
fi
STUB
chmod +x "$readelf"

assert_elf_machine "$readelf" 'x86_64 core' 'Advanced Micro Devices X86-64'
assert_elf_machine "$readelf" 'x86 core' 'Intel 80386'
assert_elf_machine "$readelf" 'arm64 core' 'AArch64'
assert_elf_machine "$readelf" 'arm core' ARM

expect_failure() {
    local file="$1" diagnostic="$2" output
    if output="$(assert_elf_machine "$readelf" "$file" ARM 2>&1)"; then
        printf 'Expected ELF validation to reject %s\n' "$file" >&2
        exit 1
    fi
    grep -F -- "$diagnostic" <<< "$output"
}

expect_failure 'wrong machine' 'expected ARM, got ARM64'
expect_failure 'no machine' 'expected ARM, got <missing>'
expect_failure 'read failure' 'invalid ELF header'

echo '[OK] Native ELF machine fixtures passed'
