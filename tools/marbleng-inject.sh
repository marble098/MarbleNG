#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
say(){ printf '\033[1;36m[MarbleNG]\033[0m %s\n' "$*"; }
die(){ printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }
ZIP="${1:-}"; [[ -n "$ZIP" ]] || ZIP="$(find /sdcard/Download "$HOME/storage/downloads" -maxdepth 1 -type f -name 'MarbleNG-complete-source.zip' 2>/dev/null | head -1 || true)"; [[ -f "$ZIP" ]] || die 'Pass the MarbleNG-complete-source.zip path as argument.'
command -v pkg >/dev/null || die 'Run this in Termux.'
say 'Installing Termux prerequisites'; pkg update -y; pkg install -y git gh unzip zip jq curl openssl openjdk-17 coreutils
if ! gh auth status >/dev/null 2>&1; then say 'GitHub login is required once.'; gh auth login --hostname github.com --git-protocol https --web; fi
OWNER="$(gh api user -q .login)"; REPO="$OWNER/MarbleNG"; WORK="$HOME/.marbleng/inject"; SIGN="$HOME/.marbleng/signing"; rm -rf "$WORK"; mkdir -p "$WORK" "$SIGN"
unzip -q "$ZIP" -d "$WORK/unpacked"; SRC="$(find "$WORK/unpacked" -maxdepth 2 -type f -name settings.gradle.kts -printf '%h\n' | head -1)"; [[ -n "$SRC" ]] || die 'Source root not found in zip.'
if ! gh repo view "$REPO" >/dev/null 2>&1; then say "Creating public repository $REPO"; gh repo create "$REPO" --public --description 'MarbleNG — Xray Genius Android' --clone=false; fi
# Stable signer: repository secrets survive content wipes. Never rotate them automatically.
mapfile -t present < <(gh secret list -R "$REPO" --json name -q '.[].name' 2>/dev/null || true)
has(){ printf '%s\n' "${present[@]:-}" | grep -qx "$1"; }
count=0; for n in ANDROID_KEYSTORE_B64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do has "$n" && count=$((count+1)); done
if (( count==4 )); then
  say 'Existing signing secrets found — preserving Android signature identity.'
elif (( count>0 )); then
  die 'Partial Android signing secrets detected. Refusing to rotate the key/signature. Restore the missing signing secrets first.'
else
  KS="$SIGN/marbleng-release.jks"; META="$SIGN/signing.env"
  if [[ ! -s "$KS" || ! -s "$META" ]]; then
    PASS="$(openssl rand -base64 36 | tr -d '\n/=+' | head -c 32)"; KPASS="$(openssl rand -base64 36 | tr -d '\n/=+' | head -c 32)"; ALIAS=marbleng
    keytool -genkeypair -v -keystore "$KS" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 -storepass "$PASS" -keypass "$KPASS" -dname 'CN=MarbleNG, OU=Android, O=MarbleNG, L=Internet, C=XX'
    cat > "$META" <<EOF
KS_PASS='$PASS'
KEY_PASS='$KPASS'
KS_ALIAS='$ALIAS'
EOF
    chmod 600 "$KS" "$META"
  fi
  # shellcheck disable=SC1090
  source "$META"; gh secret set ANDROID_KEYSTORE_B64 -R "$REPO" --body "$(base64 -w0 "$KS")"; gh secret set ANDROID_KEYSTORE_PASSWORD -R "$REPO" --body "$KS_PASS"; gh secret set ANDROID_KEY_ALIAS -R "$REPO" --body "$KS_ALIAS"; gh secret set ANDROID_KEY_PASSWORD -R "$REPO" --body "$KEY_PASS"
  say 'Stable signing identity stored as GitHub Actions secrets.'
  if [[ -d "$HOME/storage/downloads" ]]; then mkdir -p "$HOME/storage/downloads/MarbleNG-SIGNING-BACKUP"; cp -f "$KS" "$META" "$HOME/storage/downloads/MarbleNG-SIGNING-BACKUP/"; chmod 600 "$HOME/storage/downloads/MarbleNG-SIGNING-BACKUP/"* 2>/dev/null || true; say 'A local signing backup was copied to Downloads/MarbleNG-SIGNING-BACKUP. Keep it private.'; fi
fi
say 'Cloning repository and removing ALL previous repository contents'; rm -rf "$WORK/repo"; gh repo clone "$REPO" "$WORK/repo" -- --depth 1 || { git clone "https://github.com/$REPO.git" "$WORK/repo"; }
cd "$WORK/repo"; git checkout -B main; find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +; cp -a "$SRC"/. .
rm -rf .gitmodules app/src/main/jni/hev app/src/main/jniLibs .cores signing.properties release.jks 2>/dev/null || true
./scripts/update-core-lock.sh || say 'Live core discovery failed; keeping the pinned verified core lock.'
bash -n scripts/*.sh; jq -e '.xray.tag and .hev.tag' core-lock.json >/dev/null
say 'Enabling GitHub Actions and write permission before push'; gh api -X PUT "repos/$REPO/actions/permissions" -F enabled=true -f allowed_actions=all >/dev/null || true; gh api -X PUT "repos/$REPO/actions/permissions/workflow" -f default_workflow_permissions=write -F can_approve_pull_request_reviews=false >/dev/null || true
git add -A; git config user.name "${OWNER}-termux"; git config user.email "$OWNER@users.noreply.github.com"; git commit -m "MarbleNG Android source injection $(date -u +%Y-%m-%dT%H:%M:%SZ)" || true
git remote set-url origin "https://github.com/$REPO.git"; git push -u origin main --force
say 'Push completed. The main-branch push automatically triggers the signed multi-ABI build.'
say "Done: https://github.com/$REPO/actions"
say 'Future injections keep the existing repository signing secrets, so APK signatures stay identical.'
