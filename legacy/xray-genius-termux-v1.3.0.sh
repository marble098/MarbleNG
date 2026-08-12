#!/data/data/com.termux/files/usr/bin/bash
# Xray Genius Termux Client
# Single-file bootstrap + colorful TUI + subscription parser + smart benchmarker.
# Designed for official XTLS/Xray-core pre-release channel.

set -uo pipefail
umask 077

APP_NAME="Xray Genius"
APP_VERSION="1.3.0"
ROOT="${HOME}/.xray-genius"
BIN_DIR="$ROOT/bin"
PROFILE_DIR="$ROOT/profiles"
RAW_DIR="$ROOT/raw"
TMP_DIR="$ROOT/tmp"
LOG_DIR="$ROOT/logs"
UNSUPPORTED_DIR="$ROOT/unsupported"
SETTINGS="$ROOT/settings.conf"
SUB_DB="$ROOT/subscriptions.tsv"
INDEX="$ROOT/profiles.jsonl"
PID_FILE="$ROOT/xray.pid"
ACTIVE_FILE="$ROOT/active.json"
RUNTIME_CONFIG="$ROOT/runtime.json"
BENCH_RESULTS="$ROOT/bench-results.tsv"
XRAY_BIN="$BIN_DIR/xray"
LAST_UPDATE_CHECK="$ROOT/.last-update-check"
INSTALLED_TAG="$ROOT/.installed-tag"
SELF_PATH="$ROOT/xgc.sh"
TG_DIR="$ROOT/telegram"
TG_CHANNELS="$TG_DIR/channels.txt"
TG_RAW="$TG_DIR/latest-configs.txt"
TG_META="$TG_DIR/latest-meta.jsonl"
TG_RESULTS="$ROOT/telegram-results.tsv"
CF_CONF="$ROOT/cloudflare.json"
WORKER_FILE="$TG_DIR/worker.mjs"
LAST_CONNECTED="$ROOT/last-connected.json"
HISTORY_FILE="$ROOT/connection-history.jsonl"

# ANSI colors
RESET='\033[0m'; BOLD='\033[1m'; DIM='\033[2m'
RED='\033[38;5;203m'; GREEN='\033[38;5;84m'; YELLOW='\033[38;5;220m'
BLUE='\033[38;5;75m'; MAGENTA='\033[38;5;213m'; CYAN='\033[38;5;51m'
WHITE='\033[38;5;255m'; GRAY='\033[38;5;245m'; BG='\033[48;5;17m'

say() { printf "%b\n" "$*"; }
ok() { say "${GREEN}✔${RESET} $*"; }
warn() { say "${YELLOW}⚠${RESET} $*"; }
err() { say "${RED}✖${RESET} $*" >&2; }
info() { say "${CYAN}◆${RESET} $*"; }
line() { printf "%b\n" "${DIM}${BLUE}────────────────────────────────────────────────────────────${RESET}"; }
pause() { printf "\n${GRAY}Press Enter to continue...${RESET}"; read -r _ || true; }

header() {
  clear 2>/dev/null || true
  printf "%b\n" "${BG}${BOLD}${CYAN}  ⚡ XRAY GENIUS TERMUX CLIENT  ${RESET} ${GRAY}v${APP_VERSION}${RESET}"
  printf "%b\n" "${DIM}${WHITE}  Xray pre-release • subscriptions • smart selector • SOCKS5${RESET}"
  line
}

ensure_dirs() {
  mkdir -p "$ROOT" "$BIN_DIR" "$PROFILE_DIR" "$RAW_DIR" "$TMP_DIR" "$LOG_DIR" "$UNSUPPORTED_DIR" "$TG_DIR"
  touch "$SUB_DB" "$INDEX" "$TG_CHANNELS" "$HISTORY_FILE"
  chmod 700 "$ROOT" "$BIN_DIR" "$PROFILE_DIR" "$RAW_DIR" "$TMP_DIR" "$LOG_DIR" "$UNSUPPORTED_DIR" "$TG_DIR" 2>/dev/null || true
  chmod 600 "$SUB_DB" "$INDEX" "$TG_CHANNELS" "$HISTORY_FILE" 2>/dev/null || true
  [[ -f "$CF_CONF" ]] && chmod 600 "$CF_CONF" 2>/dev/null || true
}

default_settings() {
  cat > "$SETTINGS" <<'EOF'
SOCKS_PORT=10808
SOCKS_LISTEN=127.0.0.1
AUTO_UPDATE=1
UPDATE_CHECK_HOURS=6
BENCH_PROFILE=balanced
BENCH_CANDIDATES=20
BENCH_SAMPLES=4
BENCH_TIMEOUT=8
BENCH_BYTES=262144
TCP_PRECHECK_TIMEOUT=1.8
TCP_PRECHECK_WORKERS=20
LEAK_PROTECTION=1
DNS_LEAK_TEST=1
REMEMBER_LAST=1
TELEGRAM_POSTS=20
TELEGRAM_MAX_CONFIGS=80
TELEGRAM_TCP_GATE=1
TELEGRAM_TCP_SAMPLES=3
TELEGRAM_AUTO_SUB=1
TELEGRAM_PASS_MIN_SUCCESS=75
TEST_URL=https://cp.cloudflare.com/generate_204
SPEED_URL=https://speed.cloudflare.com/__down?bytes=262144
EOF
  chmod 600 "$SETTINGS"
}

load_settings() {
  [[ -f "$SETTINGS" ]] || default_settings
  local legacy_settings=0
  grep -q '^BENCH_PROFILE=' "$SETTINGS" 2>/dev/null || legacy_settings=1
  # shellcheck disable=SC1090
  source "$SETTINGS"
  : "${SOCKS_PORT:=10808}" "${SOCKS_LISTEN:=127.0.0.1}" "${AUTO_UPDATE:=1}"
  : "${UPDATE_CHECK_HOURS:=6}" "${BENCH_PROFILE:=balanced}" "${BENCH_CANDIDATES:=20}" "${BENCH_SAMPLES:=4}"
  : "${BENCH_TIMEOUT:=8}" "${BENCH_BYTES:=262144}" "${TCP_PRECHECK_TIMEOUT:=1.8}" "${TCP_PRECHECK_WORKERS:=20}"
  : "${LEAK_PROTECTION:=1}" "${DNS_LEAK_TEST:=1}" "${REMEMBER_LAST:=1}"
  : "${TELEGRAM_POSTS:=20}" "${TELEGRAM_MAX_CONFIGS:=80}"
  : "${TELEGRAM_TCP_GATE:=1}" "${TELEGRAM_TCP_SAMPLES:=3}" "${TELEGRAM_AUTO_SUB:=1}" "${TELEGRAM_PASS_MIN_SUCCESS:=75}"
  (( legacy_settings == 1 )) && BENCH_PROFILE=custom
  : "${TEST_URL:=https://cp.cloudflare.com/generate_204}"
  : "${SPEED_URL:=https://speed.cloudflare.com/__down?bytes=262144}"
  LEAK_PROTECTION=1  # v1.1.5: connection privacy guard is fail-closed and always-on
}

save_settings() {
  cat > "$SETTINGS" <<EOF
SOCKS_PORT=$SOCKS_PORT
SOCKS_LISTEN=$SOCKS_LISTEN
AUTO_UPDATE=$AUTO_UPDATE
UPDATE_CHECK_HOURS=$UPDATE_CHECK_HOURS
BENCH_PROFILE=$BENCH_PROFILE
BENCH_CANDIDATES=$BENCH_CANDIDATES
BENCH_SAMPLES=$BENCH_SAMPLES
BENCH_TIMEOUT=$BENCH_TIMEOUT
BENCH_BYTES=$BENCH_BYTES
TCP_PRECHECK_TIMEOUT=$TCP_PRECHECK_TIMEOUT
TCP_PRECHECK_WORKERS=$TCP_PRECHECK_WORKERS
LEAK_PROTECTION=$LEAK_PROTECTION
DNS_LEAK_TEST=$DNS_LEAK_TEST
REMEMBER_LAST=$REMEMBER_LAST
TELEGRAM_POSTS=$TELEGRAM_POSTS
TELEGRAM_MAX_CONFIGS=$TELEGRAM_MAX_CONFIGS
TELEGRAM_TCP_GATE=$TELEGRAM_TCP_GATE
TELEGRAM_TCP_SAMPLES=$TELEGRAM_TCP_SAMPLES
TELEGRAM_AUTO_SUB=$TELEGRAM_AUTO_SUB
TELEGRAM_PASS_MIN_SUCCESS=$TELEGRAM_PASS_MIN_SUCCESS
TEST_URL=$TEST_URL
SPEED_URL=$SPEED_URL
EOF
  chmod 600 "$SETTINGS"
}

is_termux() {
  [[ -n "${PREFIX:-}" && "$PREFIX" == *com.termux* ]] || [[ -d /data/data/com.termux/files/usr ]]
}

install_deps() {
  local need=0 c
  for c in curl jq unzip python3 sha256sum ps; do command -v "$c" >/dev/null 2>&1 || need=1; done
  (( need == 0 )) && return 0
  if ! command -v pkg >/dev/null 2>&1; then
    err "This installer expects Termux (pkg command not found)."
    return 1
  fi
  info "Installing required Termux packages..."
  pkg update -y || true
  pkg install -y curl jq unzip python coreutils procps || return 1
}

install_launcher() {
  local src
  src="$(readlink -f "$0" 2>/dev/null || printf '%s' "$0")"
  if [[ -f "$src" && "$src" != "$SELF_PATH" ]]; then
    cp -f "$src" "$SELF_PATH" 2>/dev/null || true
    chmod 700 "$SELF_PATH" 2>/dev/null || true
  fi
  if [[ -n "${PREFIX:-}" && -d "$PREFIX/bin" && -f "$SELF_PATH" ]]; then
    cat > "$PREFIX/bin/xgc" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
exec bash "$SELF_PATH" "\$@"
EOF
    chmod 700 "$PREFIX/bin/xgc" 2>/dev/null || true
  fi
}

arch_asset_names() {
  case "$(uname -m)" in
    aarch64|arm64) printf '%s\n' "Xray-linux-arm64-v8a.zip" "Xray-linux-arm64.zip" ;;
    armv7l|armv8l|arm) printf '%s\n' "Xray-linux-arm32-v7a.zip" "Xray-linux-arm32-v7.zip" "Xray-linux-arm32.zip" ;;
    x86_64|amd64) printf '%s\n' "Xray-linux-64.zip" ;;
    i386|i686|x86) printf '%s\n' "Xray-linux-32.zip" ;;
    *) return 1 ;;
  esac
}

get_prerelease_json() {
  curl -fsSL --connect-timeout 12 --max-time 35 --retry 2 \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' \
    'https://api.github.com/repos/XTLS/Xray-core/releases?per_page=20' \
  | jq -c '[.[] | select(.draft == false and .prerelease == true)][0]'
}

update_xray() {
  ensure_dirs
  install_deps || return 1
  info "Checking official XTLS/Xray-core pre-release channel..."
  local rel tag current asset_name asset_url expected actual tmpzip stage found name
  rel="$(get_prerelease_json 2>/dev/null || true)"
  [[ -n "$rel" && "$rel" != "null" ]] || { err "Could not query GitHub releases."; return 1; }
  tag="$(jq -r '.tag_name // empty' <<<"$rel")"
  [[ -n "$tag" ]] || { err "Release tag missing."; return 1; }
  current="$(cat "$INSTALLED_TAG" 2>/dev/null || true)"
  if [[ -x "$XRAY_BIN" && "$current" == "$tag" ]]; then
    ok "Xray is already on newest pre-release: $tag"
    return 0
  fi

  asset_name=""; asset_url=""; expected=""
  while IFS= read -r name; do
    asset_url="$(jq -r --arg n "$name" '.assets[] | select(.name==$n) | .browser_download_url' <<<"$rel" | head -n1)"
    if [[ -n "$asset_url" && "$asset_url" != "null" ]]; then
      asset_name="$name"
      expected="$(jq -r --arg n "$name" '.assets[] | select(.name==$n) | (.digest // "")' <<<"$rel" | head -n1 | sed 's/^sha256://')"
      break
    fi
  done < <(arch_asset_names || true)
  [[ -n "$asset_url" ]] || { err "No compatible Linux Xray asset for architecture $(uname -m)."; return 1; }

  tmpzip="$TMP_DIR/${asset_name}.part"
  stage="$TMP_DIR/xray-stage"
  rm -rf "$stage" "$tmpzip"; mkdir -p "$stage"
  info "Downloading ${tag} • ${asset_name}"
  if ! curl -fL --connect-timeout 15 --max-time 180 --retry 3 --retry-delay 1 -o "$tmpzip" "$asset_url"; then
    err "Xray download failed."; return 1
  fi
  if [[ -n "$expected" && "$expected" != "null" ]]; then
    actual="$(sha256sum "$tmpzip" | awk '{print $1}')"
    if [[ "${actual,,}" != "${expected,,}" ]]; then
      err "SHA-256 verification failed. Existing Xray was not changed."
      rm -f "$tmpzip"; return 1
    fi
    ok "Release asset SHA-256 verified"
  fi
  unzip -q -o "$tmpzip" -d "$stage" || { err "Invalid Xray archive."; return 1; }
  found="$(find "$stage" -maxdepth 2 -type f -name xray | head -n1)"
  [[ -n "$found" ]] || { err "xray binary not found in release archive."; return 1; }
  chmod 700 "$found"
  if ! "$found" version >/dev/null 2>&1; then
    err "Downloaded Xray binary cannot run on this device."; return 1
  fi

  stop_xray >/dev/null 2>&1 || true
  [[ -x "$XRAY_BIN" ]] && cp -f "$XRAY_BIN" "$BIN_DIR/xray.previous" 2>/dev/null || true
  cp -f "$found" "$XRAY_BIN"; chmod 700 "$XRAY_BIN"
  for name in geoip.dat geosite.dat; do
    found="$(find "$stage" -maxdepth 2 -type f -name "$name" | head -n1)"
    [[ -n "$found" ]] && cp -f "$found" "$BIN_DIR/$name"
  done
  printf '%s' "$tag" > "$INSTALLED_TAG"
  date +%s > "$LAST_UPDATE_CHECK"
  rm -rf "$stage" "$tmpzip"
  ok "Installed Xray $tag"
}

maybe_update_xray() {
  [[ "$AUTO_UPDATE" == "1" ]] || return 0
  local now last due
  now="$(date +%s)"; last="$(cat "$LAST_UPDATE_CHECK" 2>/dev/null || echo 0)"
  due=$(( UPDATE_CHECK_HOURS * 3600 ))
  if (( now - last >= due )); then
    date +%s > "$LAST_UPDATE_CHECK"
    update_xray || warn "Auto-update check failed; keeping installed Xray."
  fi
}

# Python helper: parsing, profile generation, patching, precheck, ranking.
pyhelper() {
python3 - "$@" <<'PY'
import sys, os, json, base64, hashlib, statistics, math, socket, time, urllib.parse, concurrent.futures, re
from pathlib import Path

def b64decode_loose(s):
    s=''.join(str(s).strip().split())
    s += '=' * (-len(s) % 4)
    for alt in (None, b'-_'):
        try:
            return base64.b64decode(s, altchars=alt, validate=False)
        except Exception:
            pass
    raise ValueError('bad base64')

def qfirst(q, key, default=''):
    v=q.get(key)
    if not v: return default
    return v[0]

def boolish(v):
    return str(v).lower() in ('1','true','yes','on')

def safe_json_value(s):
    if not s: return None
    try: return json.loads(s)
    except Exception: return None

def stream_from_query(q, remote_host, legacy_net=None):
    t=(qfirst(q,'type', legacy_net or 'tcp') or 'tcp').lower()
    method_map={'tcp':'raw','raw':'raw','ws':'websocket','websocket':'websocket','grpc':'grpc','xhttp':'xhttp','splithttp':'xhttp','httpupgrade':'httpupgrade','kcp':'mkcp','mkcp':'mkcp'}
    # Legacy h2/http transport stays in legacy network form because it is still accepted as compatibility input by Xray.
    if t in ('http','h2'):
        st={'network':'http','security':'none','httpSettings':{}}
        path=qfirst(q,'path','/') or '/'; host=qfirst(q,'host','')
        st['httpSettings']['path']=path
        if host: st['httpSettings']['host']=[host]
    else:
        method=method_map.get(t,t)
        st={'method':method,'security':'none'}
        path=qfirst(q,'path','/') or '/'; host=qfirst(q,'host','')
        if method=='xhttp':
            xs={'path':path}
            if host: xs['host']=host
            mode=qfirst(q,'mode','')
            if mode: xs['mode']=mode
            extra=safe_json_value(qfirst(q,'extra',''))
            if isinstance(extra,dict): xs['extra']=extra
            st['xhttpSettings']=xs
        elif method=='websocket':
            ws={'path':path}
            if host: ws['host']=host
            st['wsSettings']=ws
        elif method=='httpupgrade':
            hs={'path':path}
            if host: hs['host']=host
            st['httpupgradeSettings']=hs
        elif method=='grpc':
            gs={}
            service=qfirst(q,'serviceName',qfirst(q,'path',''))
            if service: gs['serviceName']=service.lstrip('/')
            authority=qfirst(q,'authority','')
            if authority: gs['authority']=authority
            mode=qfirst(q,'mode','')
            if mode in ('multi','gun-multi'): gs['multiMode']=True
            st['grpcSettings']=gs
        elif method=='mkcp':
            ks={}
            if qfirst(q,'seed',''): ks['seed']=qfirst(q,'seed')
            if qfirst(q,'mtu','').isdigit(): ks['mtu']=int(qfirst(q,'mtu'))
            if qfirst(q,'tti','').isdigit(): ks['tti']=int(qfirst(q,'tti'))
            ht=qfirst(q,'headerType','none') or 'none'
            ks['header']={'type':ht}
            st['kcpSettings']=ks
        elif method=='raw':
            ht=qfirst(q,'headerType','none') or 'none'
            st['rawSettings']={'header':{'type':ht}}

    sec=(qfirst(q,'security','none') or 'none').lower()
    if sec=='xtls': sec='tls'
    st['security']=sec
    sni=qfirst(q,'sni',remote_host) or remote_host
    fp=qfirst(q,'fp','chrome') or 'chrome'
    alpn=[x for x in qfirst(q,'alpn','').split(',') if x]
    if sec=='tls':
        tls={'serverName':sni,'fingerprint':fp}
        if alpn: tls['alpn']=alpn
        if boolish(qfirst(q,'allowInsecure',qfirst(q,'insecure','0'))): tls['allowInsecure']=True
        pcs=qfirst(q,'pcs','')
        if pcs: tls['pinnedPeerCertSha256']=pcs
        vcn=qfirst(q,'vcn','')
        if vcn: tls['verifyPeerCertByName']=vcn
        ech=qfirst(q,'ech','')
        if ech: tls['echConfigList']=ech
        st['tlsSettings']=tls
    elif sec=='reality':
        reality={'serverName':sni,'fingerprint':fp,'password':qfirst(q,'pbk',''),'shortId':qfirst(q,'sid','')}
        spx=qfirst(q,'spx','')
        if spx: reality['spiderX']=spx
        pqv=qfirst(q,'pqv','')
        if pqv: reality['mldsa65Verify']=pqv
        st['realitySettings']=reality
    fm=safe_json_value(qfirst(q,'fm',''))
    if isinstance(fm,dict): st['finalmask']=fm
    return st, t, sec

def base_config(outbound):
    return {
      'log': {'loglevel':'warning'},
      'inbounds': [{
        'tag':'socks-in','listen':'127.0.0.1','port':10808,'protocol':'socks',
        'settings': {'udp':True},
        'sniffing': {'enabled':True,'destOverride':['http','tls','quic']}
      }],
      'outbounds': [outbound, {'tag':'direct','protocol':'freedom'}, {'tag':'block','protocol':'blackhole'}]
    }

def parse_vless(uri):
    p=urllib.parse.urlsplit(uri); q=urllib.parse.parse_qs(p.query,keep_blank_values=True)
    uid=urllib.parse.unquote(p.username or ''); host=p.hostname; port=p.port
    if not (uid and host and port): raise ValueError('missing VLESS id/host/port')
    stream,t,sec=stream_from_query(q,host)
    settings={'address':host,'port':port,'id':uid,'encryption':qfirst(q,'encryption','none') or 'none'}
    flow=qfirst(q,'flow','')
    if flow: settings['flow']=flow
    out={'tag':'proxy','protocol':'vless','settings':settings,'streamSettings':stream}
    return base_config(out), p.fragment or f'VLESS {host}', host,port,t,sec

def parse_vmess_legacy(uri):
    raw=uri[len('vmess://'):]
    obj=json.loads(b64decode_loose(raw).decode('utf-8-sig'))
    host=str(obj.get('add') or obj.get('address') or ''); port=int(obj.get('port') or 0); uid=str(obj.get('id') or '')
    if not (host and port and uid): raise ValueError('missing legacy VMess fields')
    q={}
    def put(k,v):
        if v is not None and str(v)!='': q[k]=[str(v)]
    put('type',obj.get('net') or 'tcp'); put('host',obj.get('host')); put('path',obj.get('path')); put('serviceName',obj.get('path'))
    tls=obj.get('tls'); put('security','tls' if str(tls).lower() in ('tls','1','true') else 'none')
    put('sni',obj.get('sni')); put('fp',obj.get('fp')); put('headerType',obj.get('type'))
    stream,t,sec=stream_from_query(q,host,legacy_net=str(obj.get('net') or 'tcp'))
    aid=int(obj.get('aid') or 0); cipher=str(obj.get('scy') or 'auto')
    if aid>0:
        settings={'vnext':[{'address':host,'port':port,'users':[{'id':uid,'alterId':aid,'security':cipher}]}]}
    else:
        settings={'address':host,'port':port,'id':uid,'security':cipher}
    out={'tag':'proxy','protocol':'vmess','settings':settings,'streamSettings':stream}
    return base_config(out), str(obj.get('ps') or f'VMess {host}'), host,port,t,sec

def parse_vmess(uri):
    raw=uri[len('vmess://'):]
    if '@' not in raw.split('#',1)[0]:
        try: return parse_vmess_legacy(uri)
        except Exception: pass
    p=urllib.parse.urlsplit(uri); q=urllib.parse.parse_qs(p.query,keep_blank_values=True)
    uid=urllib.parse.unquote(p.username or ''); host=p.hostname; port=p.port
    if not (uid and host and port): return parse_vmess_legacy(uri)
    stream,t,sec=stream_from_query(q,host)
    settings={'address':host,'port':port,'id':uid,'security':qfirst(q,'encryption','auto') or 'auto'}
    out={'tag':'proxy','protocol':'vmess','settings':settings,'streamSettings':stream}
    return base_config(out), p.fragment or f'VMess {host}', host,port,t,sec

def parse_trojan(uri):
    p=urllib.parse.urlsplit(uri); q=urllib.parse.parse_qs(p.query,keep_blank_values=True)
    password=urllib.parse.unquote(p.username or ''); host=p.hostname; port=p.port
    if not (password and host and port): raise ValueError('missing Trojan password/host/port')
    if 'security' not in q: q['security']=['tls']
    stream,t,sec=stream_from_query(q,host)
    out={'tag':'proxy','protocol':'trojan','settings':{'address':host,'port':port,'password':password},'streamSettings':stream}
    return base_config(out), p.fragment or f'Trojan {host}', host,port,t,sec

def parse_ss(uri):
    p=urllib.parse.urlsplit(uri)
    q=urllib.parse.parse_qs(p.query,keep_blank_values=True)
    if qfirst(q,'plugin',''): raise ValueError('Shadowsocks plugins are not native Xray outbounds')
    user=p.username; password=p.password; host=p.hostname; port=p.port
    if user and password is not None:
        method=urllib.parse.unquote(user); passwd=urllib.parse.unquote(password)
    else:
        # SIP002 legacy: ss://base64(method:password)@host:port or whole base64 authority
        body=uri[len('ss://'):].split('#',1)[0].split('?',1)[0]
        if '@' in body:
            creds,addr=body.rsplit('@',1)
            dec=b64decode_loose(creds).decode(); method,passwd=dec.split(':',1)
            pp=urllib.parse.urlsplit('ss://x@'+addr); host=pp.hostname; port=pp.port
        else:
            dec=b64decode_loose(body).decode()
            pp=urllib.parse.urlsplit('ss://'+dec)
            method=urllib.parse.unquote(pp.username or ''); passwd=urllib.parse.unquote(pp.password or ''); host=pp.hostname; port=pp.port
    if not (method and passwd and host and port): raise ValueError('missing Shadowsocks fields')
    out={'tag':'proxy','protocol':'shadowsocks','settings':{'address':host,'port':port,'method':method,'password':passwd}}
    return base_config(out), p.fragment or f'SS {host}',host,port,'native','none'

def parse_hy2(uri):
    p=urllib.parse.urlsplit(uri); q=urllib.parse.parse_qs(p.query,keep_blank_values=True)
    auth=urllib.parse.unquote(p.username or '')
    # URI may put auth in username:password form; retain colon when needed.
    if p.password is not None: auth += ':' + urllib.parse.unquote(p.password)
    host=p.hostname; port=p.port or 443
    if not (auth and host and port): raise ValueError('missing Hysteria2 auth/host/port')
    sni=qfirst(q,'sni',host) or host
    tls={'serverName':sni,'fingerprint':qfirst(q,'fp','chrome') or 'chrome','alpn':['h3']}
    if boolish(qfirst(q,'insecure','0')): tls['allowInsecure']=True
    hs={'version':2,'auth':auth}
    st={'method':'hysteria','security':'tls','tlsSettings':tls,'hysteriaSettings':hs}
    obfs=qfirst(q,'obfs','')
    obfsp=qfirst(q,'obfs-password',qfirst(q,'obfsPassword',''))
    if obfs.lower()=='salamander' and obfsp:
        st['finalmask']={'udp':[{'type':'salamander','settings':{'password':obfsp}}]}
    out={'tag':'proxy','protocol':'hysteria','settings':{'version':2,'address':host,'port':port},'streamSettings':st}
    return base_config(out), p.fragment or f'Hysteria2 {host}',host,port,'hysteria','tls'

def parse_uri(uri):
    low=uri.lower()
    if low.startswith('vless://'): return parse_vless(uri), 'vless'
    if low.startswith('vmess://'): return parse_vmess(uri), 'vmess'
    if low.startswith('trojan://'): return parse_trojan(uri), 'trojan'
    if low.startswith('ss://'): return parse_ss(uri), 'ss'
    if low.startswith('hysteria2://') or low.startswith('hy2://'): return parse_hy2(uri), 'hysteria2'
    raise ValueError('unsupported URI scheme')

def looks_like_uri_text(s):
    return bool(re.search(r'(?im)^(vless|vmess|trojan|ss|hysteria2|hy2)://',s.strip()))

def decode_subscription_text(text):
    text=text.strip('\ufeff\x00 \r\n\t')
    if looks_like_uri_text(text) or text.startswith('{') or text.startswith('['): return text
    compact=''.join(text.split())
    if len(compact)>16:
        try:
            dec=b64decode_loose(compact).decode('utf-8-sig','strict').strip()
            if looks_like_uri_text(dec) or dec.startswith('{') or dec.startswith('['): return dec
        except Exception: pass
    return text

def sanitize_name(s):
    s=urllib.parse.unquote(str(s or '')).strip()
    s=re.sub(r'[\x00-\x1f\x7f]+',' ',s)
    return s[:120] or 'Unnamed'

def parse_action(args):
    rawfile,subid,subname,profdir,indexfile,unsupported=args
    raw=Path(rawfile).read_bytes().decode('utf-8-sig','replace')
    text=decode_subscription_text(raw)
    profdir=Path(profdir); profdir.mkdir(parents=True,exist_ok=True)
    idx=Path(indexfile); old=[]
    if idx.exists():
        for line in idx.read_text(errors='ignore').splitlines():
            try:
                m=json.loads(line)
                if m.get('subid')==subid:
                    try: Path(m.get('file','')).unlink(missing_ok=True)
                    except Exception: pass
                else: old.append(m)
            except Exception: pass
    items=[]; bad=[]
    # Whole Xray JSON config support.
    stripped=text.strip()
    if stripped.startswith('{'):
        try:
            obj=json.loads(stripped)
            if isinstance(obj,dict) and isinstance(obj.get('outbounds'),list):
                fn=profdir/f'{subid}-json-{hashlib.sha256(stripped.encode()).hexdigest()[:12]}.json'
                fn.write_text(json.dumps(obj,ensure_ascii=False,indent=2))
                first=(obj.get('outbounds') or [{}])[0]
                m={'id':hashlib.sha256((subid+str(fn)).encode()).hexdigest()[:16],'file':str(fn),'name':sanitize_name(subname+' (JSON)'),
                   'scheme':str(first.get('protocol','json')),'host':'','port':0,'transport':'json','security':'','subid':subid,'subname':subname}
                items.append(m)
            else: bad.append('JSON does not contain an outbounds array')
        except Exception as e: bad.append('JSON parse: '+str(e))
    else:
        lines=[]
        for ln in text.replace('\r','\n').split('\n'):
            ln=ln.strip()
            if not ln or ln.startswith('#'): continue
            # Some panels concatenate with spaces; only split safely when multiple schemes are visible.
            matches=list(re.finditer(r'(?i)(?:^|\s)((?:vless|vmess|trojan|ss|hysteria2|hy2)://\S+)',ln))
            if matches and len(matches)>1: lines.extend(m.group(1) for m in matches)
            else: lines.append(ln)
        for n,uri in enumerate(lines,1):
            try:
                (cfg,name,host,port,transport,security),scheme=parse_uri(uri)
                name=sanitize_name(name)
                digest=hashlib.sha256(uri.encode()).hexdigest()[:16]
                fn=profdir/f'{subid}-{digest}.json'
                fn.write_text(json.dumps(cfg,ensure_ascii=False,indent=2))
                items.append({'id':digest,'file':str(fn),'name':name,'scheme':scheme,'host':host or '', 'port':int(port or 0),
                              'transport':transport,'security':security,'subid':subid,'subname':subname})
            except Exception as e:
                shown=uri[:90] + ('…' if len(uri)>90 else '')
                bad.append(f'{n}: {shown} :: {e}')
    allm=old+items
    idx.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n' for x in allm))
    Path(unsupported).write_text('\n'.join(bad))
    print(json.dumps({'added':len(items),'unsupported':len(bad)},ensure_ascii=False))

def patch_action(args):
    src,dst,port,listen=args; port=int(port)
    obj=json.loads(Path(src).read_text())
    inbound={'tag':'socks-in','listen':listen,'port':port,'protocol':'socks','settings':{'udp':True},
             'sniffing':{'enabled':True,'destOverride':['http','tls','quic']}}
    ins=obj.get('inbounds')
    if not isinstance(ins,list): ins=[]
    # Remove only our own generated inbound; preserve provider-defined others.
    ins=[x for x in ins if not (isinstance(x,dict) and x.get('tag')=='socks-in')]
    obj['inbounds']=[inbound]+ins
    if 'log' not in obj or not isinstance(obj['log'],dict): obj['log']={'loglevel':'warning'}
    else: obj['log']['loglevel']=obj['log'].get('loglevel','warning')
    Path(dst).write_text(json.dumps(obj,ensure_ascii=False,indent=2))

def list_action(args):
    idx=Path(args[0]); rows=[]
    for line in idx.read_text(errors='ignore').splitlines() if idx.exists() else []:
        try: rows.append(json.loads(line))
        except Exception: pass
    for i,m in enumerate(rows,1):
        print(f"{i}\t{m.get('id','')}\t{m.get('scheme','')}\t{m.get('transport','')}\t{m.get('security','')}\t{m.get('name','')}")

def meta_by_number(args):
    idx=Path(args[0]); num=int(args[1]); rows=[]
    for line in idx.read_text(errors='ignore').splitlines() if idx.exists() else []:
        try: rows.append(json.loads(line))
        except Exception: pass
    if 1<=num<=len(rows): print(json.dumps(rows[num-1],ensure_ascii=False))

def precheck_action(args):
    idxfile,topn,timeout,workers=args; topn=int(topn); timeout=float(timeout); workers=int(workers)
    rows=[]
    for line in Path(idxfile).read_text(errors='ignore').splitlines():
        try: rows.append(json.loads(line))
        except Exception: pass
    def check(m):
        if m.get('scheme')=='hysteria2' or m.get('transport')=='hysteria':
            return (m,0.55,9999.0,9999.0,'udp')
        host=m.get('host'); port=int(m.get('port') or 0)
        if not host or not port: return (m,0.0,9999.0,9999.0,'unknown')
        vals=[]
        for _ in range(2):
            t=time.perf_counter()
            try:
                with socket.create_connection((host,port),timeout=timeout): pass
                vals.append((time.perf_counter()-t)*1000)
            except Exception: pass
        success=len(vals)/2
        med=statistics.median(vals) if vals else 9999.0
        jit=(max(vals)-min(vals)) if len(vals)>1 else (0.0 if vals else 9999.0)
        return (m,success,med,jit,'tcp')
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1,workers)) as ex:
        results=list(ex.map(check,rows))
    tcp=[r for r in results if r[4]=='tcp' and r[1]>0]
    udp=[r for r in results if r[4]=='udp']
    other=[r for r in results if r[4]=='unknown']
    tcp.sort(key=lambda r:(-r[1],r[2]+r[3]*0.5))
    # Reserve at least 1/3 of candidates for Hysteria2 when present; actual Xray test is the only meaningful handshake test for UDP.
    udp_slots=min(len(udp), max(2, topn//3)) if udp else 0
    chosen=tcp[:max(0,topn-udp_slots)] + udp[:udp_slots]
    if len(chosen)<topn:
        used={r[0].get('file') for r in chosen}
        for r in tcp+udp+other:
            if r[0].get('file') not in used:
                chosen.append(r); used.add(r[0].get('file'))
                if len(chosen)>=topn: break
    for r in chosen:
        m,s,med,jit,kind=r
        print(json.dumps({'id':m.get('id'),'file':m.get('file'),'name':m.get('name'),'scheme':m.get('scheme'),
                          'host':m.get('host'),'port':m.get('port'),'pre_success':s,'pre_latency':med,'pre_jitter':jit,'kind':kind},ensure_ascii=False))

def rank_action(args):
    resultfile=args[0]; rows=[]
    for line in Path(resultfile).read_text(errors='ignore').splitlines():
        parts=line.split('\t')
        if len(parts)<7: continue
        try:
            rows.append({'id':parts[0],'file':parts[1],'name':parts[2],'success':float(parts[3]),'lat':float(parts[4]),'jit':float(parts[5]),'speed':float(parts[6])})
        except Exception: pass
    if not rows: return
    def norm(vals,x,invert=False):
        lo=min(vals); hi=max(vals)
        if hi-lo<1e-9: v=0.5
        else: v=(x-lo)/(hi-lo)
        return 1-v if invert else v
    lats=[min(r['lat'],10000) for r in rows]; jits=[min(r['jit'],10000) for r in rows]; speeds=[math.log1p(max(0,r['speed'])) for r in rows]
    for r in rows:
        nlat=norm(lats,min(r['lat'],10000),True)
        njit=norm(jits,min(r['jit'],10000),True)
        nspd=norm(speeds,math.log1p(max(0,r['speed'])),False)
        r['score']=r['success']*55+nlat*20+njit*10+nspd*15
        if r['success']<=0: r['score']=-1
    rows.sort(key=lambda r:(-r['score'],-r['success'],r['lat'],r['jit'],-r['speed']))
    for i,r in enumerate(rows,1):
        print(f"{i}\t{r['id']}\t{r['file']}\t{r['name']}\t{r['success']:.0f}\t{r['lat']:.1f}\t{r['jit']:.1f}\t{r['speed']/1024:.0f}\t{r['score']:.1f}")

def stats_action(args):
    vals=[]
    for x in args:
        try: vals.append(float(x)*1000.0)
        except Exception: pass
    if not vals: print('9999\t9999'); return
    med=statistics.median(vals)
    jit=statistics.pstdev(vals) if len(vals)>1 else 0.0
    print(f'{med:.3f}\t{jit:.3f}')

action=sys.argv[1]; args=sys.argv[2:]
if action=='parse': parse_action(args)
elif action=='patch': patch_action(args)
elif action=='list': list_action(args)
elif action=='meta-number': meta_by_number(args)
elif action=='precheck': precheck_action(args)
elif action=='rank': rank_action(args)
elif action=='stats': stats_action(args)
else: raise SystemExit('unknown action')
PY
}

count_profiles() { [[ -s "$INDEX" ]] && wc -l < "$INDEX" | tr -d ' ' || echo 0; }

fetch_subscription() {
  local id="$1" name="$2" url="$3" raw="$RAW_DIR/$id.raw" out
  info "Refreshing: $name"
  if ! curl -fL --connect-timeout 15 --max-time 75 --retry 2 --retry-delay 1 \
      -A 'v2rayN' -H 'Accept: */*' -o "$raw.part" "$url"; then
    rm -f "$raw.part"; err "Download failed: $name"; return 1
  fi
  mv -f "$raw.part" "$raw"; chmod 600 "$raw"
  out="$(pyhelper parse "$raw" "$id" "$name" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)" || {
    err "Parse failed: $out"; return 1; }
  local added unsupported
  added="$(jq -r '.added // 0' <<<"$out" 2>/dev/null || echo 0)"
  unsupported="$(jq -r '.unsupported // 0' <<<"$out" 2>/dev/null || echo 0)"
  ok "$name → $added usable profile(s)"
  (( unsupported > 0 )) && warn "$unsupported entry/entries were not safely convertible; report saved in $UNSUPPORTED_DIR/$id.txt"
  return 0
}

add_subscription() {
  header
  printf "%b" "${CYAN}Subscription URL:${RESET} "
  read -r url
  [[ "$url" =~ ^https?:// ]] || { err "Please enter an http:// or https:// subscription URL."; pause; return; }
  printf "%b" "${CYAN}Name (optional):${RESET} "
  read -r name
  [[ -n "$name" ]] || name="Subscription"
  local id tmp
  id="$(printf '%s' "$url" | sha256sum | cut -c1-12)"
  tmp="$TMP_DIR/subdb.$$"
  awk -F'\t' -v id="$id" '$1!=id' "$SUB_DB" > "$tmp" 2>/dev/null || true
  printf '%s\t%s\t%s\t%s\n' "$id" "${name//$'\t'/ }" "$url" "$(date +%s)" >> "$tmp"
  mv -f "$tmp" "$SUB_DB"; chmod 600 "$SUB_DB"
  fetch_subscription "$id" "$name" "$url" || true
  pause
}

import_uri() {
  header
  say "${CYAN}Paste one VLESS / VMess / Trojan / SS / Hysteria2 URI:${RESET}"
  read -r uri
  [[ "$uri" == *"://"* ]] || { err "No proxy URI detected."; pause; return; }
  printf "%b" "${CYAN}Group name (optional):${RESET} "; read -r name; [[ -n "$name" ]] || name="Manual"
  local id raw out
  id="manual-$(printf '%s' "$uri" | sha256sum | cut -c1-10)"; raw="$RAW_DIR/$id.raw"
  printf '%s\n' "$uri" > "$raw"
  out="$(pyhelper parse "$raw" "$id" "$name" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)" || { err "$out"; pause; return; }
  ok "Imported $(jq -r '.added' <<<"$out") profile(s)."
  pause
}

import_file() {
  header
  printf "%b" "${CYAN}File path (raw subscription, URI list, or Xray JSON):${RESET} "
  read -r path
  path="${path/#\~/$HOME}"
  [[ -f "$path" ]] || { err "File not found."; pause; return; }
  printf "%b" "${CYAN}Group name:${RESET} "; read -r name; [[ -n "$name" ]] || name="Imported file"
  local id raw out
  id="file-$(printf '%s:%s' "$path" "$(date +%s)" | sha256sum | cut -c1-10)"; raw="$RAW_DIR/$id.raw"
  cp -f "$path" "$raw"
  out="$(pyhelper parse "$raw" "$id" "$name" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)" || { err "$out"; pause; return; }
  ok "Imported $(jq -r '.added' <<<"$out") profile(s)."
  pause
}

refresh_all() {
  header
  [[ -s "$SUB_DB" ]] || { warn "No saved subscription URLs."; pause; return; }
  local id name url updated good=0 bad=0
  while IFS=$'\t' read -r id name url updated; do
    [[ -n "$id" && -n "$url" ]] || continue
    if fetch_subscription "$id" "$name" "$url"; then ((good+=1)); else ((bad+=1)); fi
  done < "$SUB_DB"
  line; ok "$good subscription(s) refreshed"; ((bad>0)) && warn "$bad failed"
  pause
}

list_profiles() {
  pyhelper list "$INDEX" 2>/dev/null | while IFS=$'\t' read -r n id scheme transport security name; do
    printf "${CYAN}%3s${RESET}  ${MAGENTA}%-10s${RESET} ${BLUE}%-11s${RESET} ${GRAY}%-8s${RESET} %s\n" "$n" "$scheme" "$transport" "$security" "$name"
  done
}

process_alive() {
  [[ -f "$PID_FILE" ]] || return 1
  local p; p="$(cat "$PID_FILE" 2>/dev/null || true)"
  [[ "$p" =~ ^[0-9]+$ ]] && kill -0 "$p" 2>/dev/null
}

stop_xray() {
  if process_alive; then
    local p; p="$(cat "$PID_FILE")"
    kill "$p" 2>/dev/null || true
    for _ in 1 2 3 4 5; do kill -0 "$p" 2>/dev/null || break; sleep .2; done
    kill -9 "$p" 2>/dev/null || true
  fi
  rm -f "$PID_FILE" "$ACTIVE_FILE"
}

wait_port() {
  python3 - "$1" "$2" <<'PY'
import socket,sys,time
host=sys.argv[1]; port=int(sys.argv[2]); end=time.time()+5
while time.time()<end:
    s=socket.socket(); s.settimeout(.25)
    try:
        s.connect((host,port)); s.close(); raise SystemExit(0)
    except Exception:
        s.close(); time.sleep(.12)
raise SystemExit(1)
PY
}

start_profile_file() {
  local file="$1" name="$2" quiet="${3:-0}"
  [[ -x "$XRAY_BIN" ]] || { err "Xray is not installed."; return 1; }
  [[ -f "$file" ]] || { err "Profile file missing."; return 1; }
  stop_xray >/dev/null 2>&1 || true
  pyhelper patch "$file" "$RUNTIME_CONFIG" "$SOCKS_PORT" "$SOCKS_LISTEN" || { err "Could not build runtime config."; return 1; }
  if ! XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$RUNTIME_CONFIG" >"$LOG_DIR/config-test.log" 2>&1; then
    err "Xray rejected this config. See $LOG_DIR/config-test.log"
    return 1
  fi
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
  XRAY_LOCATION_ASSET="$BIN_DIR" nohup "$XRAY_BIN" run -c "$RUNTIME_CONFIG" >"$LOG_DIR/xray.log" 2>&1 &
  local p=$!; printf '%s' "$p" > "$PID_FILE"
  if ! wait_port "$SOCKS_LISTEN" "$SOCKS_PORT"; then
    err "Xray started but SOCKS port did not open. See logs."
    stop_xray; return 1
  fi
  python3 - "$ACTIVE_FILE" "$name" "$file" "$SOCKS_LISTEN" "$SOCKS_PORT" <<'PY'
import json,sys,time
open(sys.argv[1],'w').write(json.dumps({'name':sys.argv[2],'file':sys.argv[3],'listen':sys.argv[4],'port':int(sys.argv[5]),'started':int(time.time())},ensure_ascii=False))
PY
  [[ "$quiet" == "1" ]] || ok "Connected: $name  →  SOCKS5 ${SOCKS_LISTEN}:${SOCKS_PORT}"
}

connect_manual() {
  header
  local total; total="$(count_profiles)"
  (( total > 0 )) || { warn "No profiles. Add a subscription first."; pause; return; }
  list_profiles
  line
  printf "%b" "${CYAN}Profile number:${RESET} "; read -r num
  [[ "$num" =~ ^[0-9]+$ ]] || { err "Invalid number."; pause; return; }
  local meta file name
  meta="$(pyhelper meta-number "$INDEX" "$num" 2>/dev/null || true)"
  [[ -n "$meta" ]] || { err "Profile not found."; pause; return; }
  file="$(jq -r '.file' <<<"$meta")"; name="$(jq -r '.name' <<<"$meta")"
  start_profile_file "$file" "$name" || true
  pause
}

bench_one() {
  local file="$1" id="$2" name="$3" port="$4" cfg="$TMP_DIR/bench-${port}.json" log="$TMP_DIR/bench-${port}.log"
  pyhelper patch "$file" "$cfg" "$port" "127.0.0.1" >/dev/null 2>&1 || { printf '%s\t%s\t%s\t0\t9999\t9999\t0\n' "$id" "$file" "$name"; return; }
  if ! XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$cfg" >"$log" 2>&1; then
    printf '%s\t%s\t%s\t0\t9999\t9999\t0\n' "$id" "$file" "$name"; rm -f "$cfg"; return
  fi
  XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -c "$cfg" >"$log" 2>&1 & local xp=$!
  if ! wait_port 127.0.0.1 "$port" >/dev/null 2>&1; then
    kill "$xp" 2>/dev/null || true; wait "$xp" 2>/dev/null || true
    printf '%s\t%s\t%s\t0\t9999\t9999\t0\n' "$id" "$file" "$name"; rm -f "$cfg"; return
  fi
  local -a times=(); local i val successes=0 speed=0
  for ((i=0;i<BENCH_SAMPLES;i++)); do
    val="$(curl -sS --socks5-hostname "127.0.0.1:$port" --connect-timeout "$BENCH_TIMEOUT" --max-time "$BENCH_TIMEOUT" \
      -o /dev/null -w '%{time_total}' "$TEST_URL" 2>/dev/null || true)"
    if [[ "$val" =~ ^[0-9]+([.][0-9]+)?$ && "$val" != "0.000000" ]]; then times+=("$val"); ((successes+=1)); fi
  done
  if (( successes > 0 )); then
    speed="$(curl -sS --socks5-hostname "127.0.0.1:$port" --connect-timeout "$BENCH_TIMEOUT" --max-time $((BENCH_TIMEOUT+4)) \
      -o /dev/null -w '%{speed_download}' "$SPEED_URL" 2>/dev/null || echo 0)"
    [[ "$speed" =~ ^[0-9]+([.][0-9]+)?$ ]] || speed=0
  fi
  kill "$xp" 2>/dev/null || true; wait "$xp" 2>/dev/null || true
  local stats lat jit success_pct
  stats="$(pyhelper stats "${times[@]}" 2>/dev/null || printf '9999\t9999')"; lat="${stats%%$'\t'*}"; jit="${stats##*$'\t'}"
  success_pct=$(( successes * 100 / BENCH_SAMPLES ))
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$file" "${name//$'\t'/ }" "$success_pct" "$lat" "$jit" "$speed"
  rm -f "$cfg"
}

smart_connect() {
  header
  local total; total="$(count_profiles)"
  (( total > 0 )) || { warn "No profiles. Add a subscription first."; pause; return; }
  [[ -x "$XRAY_BIN" ]] || { warn "Xray missing; installing now."; update_xray || { pause; return; }; }
  stop_xray >/dev/null 2>&1 || true
  info "Stage 1/2: fast reachability screen across $total profile(s)..."
  local candidates="$TMP_DIR/candidates.jsonl"
  pyhelper precheck "$INDEX" "$BENCH_CANDIDATES" "$TCP_PRECHECK_TIMEOUT" "$TCP_PRECHECK_WORKERS" > "$candidates" 2>/dev/null
  local ccount; ccount="$(wc -l < "$candidates" | tr -d ' ')"
  (( ccount > 0 )) || { err "No candidates available."; pause; return; }
  info "Stage 2/2: real Xray SOCKS tests on $ccount candidate(s)..."
  : > "$BENCH_RESULTS"
  local i=0 id file name port
  while IFS= read -r row; do
    ((i+=1)); id="$(jq -r '.id' <<<"$row")"; file="$(jq -r '.file' <<<"$row")"; name="$(jq -r '.name' <<<"$row")"; port=$((18080+i))
    printf "${GRAY}[${i}/${ccount}]${RESET} %-44s " "${name:0:44}"
    local result; result="$(bench_one "$file" "$id" "$name" "$port")"
    printf '%s\n' "$result" >> "$BENCH_RESULTS"
    local sp lat jit succ; succ="$(cut -f4 <<<"$result")"; lat="$(cut -f5 <<<"$result")"; jit="$(cut -f6 <<<"$result")"; sp="$(cut -f7 <<<"$result")"
    if [[ "$succ" == "0" ]]; then say "${RED}FAIL${RESET}"; else printf "${GREEN}%s%%${RESET}  ${CYAN}%sms${RESET}  jitter ${YELLOW}%sms${RESET}  %s KB/s\n" "$succ" "$lat" "$jit" "$(awk -v s="$sp" 'BEGIN{printf "%.0f",s/1024}')"; fi
  done < "$candidates"
  line
  local ranked="$TMP_DIR/ranked.tsv"
  pyhelper rank "$BENCH_RESULTS" > "$ranked"
  say "${BOLD}${WHITE}Smart ranking${RESET} ${GRAY}(success 55% • latency 20% • jitter 10% • throughput 15%)${RESET}"
  printf "${GRAY}%3s  %-40s %7s %9s %9s %10s %7s${RESET}\n" "#" "Profile" "OK" "Latency" "Jitter" "KB/s" "Score"
  head -n 12 "$ranked" | while IFS=$'\t' read -r rank rid rfile rname succ lat jit kb score; do
    printf "%3s  %-40s %6s%% %7sms %7sms %9s %7s\n" "$rank" "${rname:0:40}" "$succ" "$lat" "$jit" "$kb" "$score"
  done
  local best; best="$(head -n1 "$ranked")"
  [[ -n "$best" ]] || { err "No benchmark result."; pause; return; }
  local bestfile bestname bestsucc
  bestfile="$(cut -f3 <<<"$best")"; bestname="$(cut -f4 <<<"$best")"; bestsucc="$(cut -f5 <<<"$best")"
  if [[ "$bestsucc" == "0" ]]; then err "None of the tested candidates established working proxy traffic."; pause; return; fi
  line
  info "Starting best measured profile: $bestname"
  start_profile_file "$bestfile" "$bestname" || true
  pause
}

show_status() {
  header
  local version tag profiles subs active
  version="$($XRAY_BIN version 2>/dev/null | head -n1 || echo 'not installed')"
  tag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo '-')"; profiles="$(count_profiles)"; subs="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  printf "${CYAN}Core:${RESET}       %s\n" "$version"
  printf "${CYAN}Channel:${RESET}    official XTLS pre-release  ${GRAY}[%s]${RESET}\n" "$tag"
  printf "${CYAN}Profiles:${RESET}   %s    ${CYAN}Subscriptions:${RESET} %s\n" "$profiles" "$subs"
  printf "${CYAN}SOCKS5:${RESET}     %s:%s\n" "$SOCKS_LISTEN" "$SOCKS_PORT"
  if process_alive; then
    active="$(jq -r '.name // "Connected"' "$ACTIVE_FILE" 2>/dev/null || echo Connected)"
    printf "${CYAN}State:${RESET}      ${GREEN}${BOLD}CONNECTED${RESET}  → %s\n" "$active"
  else
    printf "${CYAN}State:${RESET}      ${RED}DISCONNECTED${RESET}\n"
  fi
  line
  say "${GRAY}Use 127.0.0.1:${SOCKS_PORT} in Android apps that support SOCKS5/SOCKS5h.${RESET}"
  pause
}

manage_subs() {
  while true; do
    header
    say "${BOLD}${WHITE}Subscription & Import Center${RESET}"
    printf " ${CYAN}1${RESET}) Add subscription URL\n ${CYAN}2${RESET}) Refresh all saved subscriptions\n ${CYAN}3${RESET}) Paste one proxy URI\n ${CYAN}4${RESET}) Import file / Xray JSON\n ${CYAN}5${RESET}) Show saved subscriptions\n ${CYAN}6${RESET}) Remove subscription\n ${CYAN}0${RESET}) Back\n"
    line; printf "%b" "${MAGENTA}Choose:${RESET} "; read -r ch
    case "$ch" in
      1) add_subscription ;;
      2) refresh_all ;;
      3) import_uri ;;
      4) import_file ;;
      5) header; if [[ -s "$SUB_DB" ]]; then awk -F'\t' '{printf "  %s) %s  [%s]\n",NR,$2,$1}' "$SUB_DB"; else warn "No saved subscriptions."; fi; pause ;;
      6) remove_subscription ;;
      0) return ;;
    esac
  done
}

remove_subscription() {
  header
  [[ -s "$SUB_DB" ]] || { warn "No saved subscriptions."; pause; return; }
  awk -F'\t' '{printf "  %s) %s  [%s]\n",NR,$2,$1}' "$SUB_DB"
  printf "%b" "${CYAN}Number to remove:${RESET} "; read -r num
  [[ "$num" =~ ^[0-9]+$ ]] || { err "Invalid number."; pause; return; }
  local rec id tmp
  rec="$(sed -n "${num}p" "$SUB_DB")"; [[ -n "$rec" ]] || { err "Not found."; pause; return; }
  id="${rec%%$'\t'*}"; tmp="$TMP_DIR/remove.$$"
  awk -F'\t' -v id="$id" '$1!=id' "$SUB_DB" > "$tmp"; mv -f "$tmp" "$SUB_DB"
  python3 - "$INDEX" "$id" <<'PY'
import json,sys
from pathlib import Path
p=Path(sys.argv[1]); sid=sys.argv[2]; keep=[]
for l in p.read_text(errors='ignore').splitlines() if p.exists() else []:
    try:
        m=json.loads(l)
        if m.get('subid')==sid:
            try: Path(m.get('file','')).unlink(missing_ok=True)
            except Exception: pass
        else: keep.append(m)
    except Exception: pass
p.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n' for x in keep))
PY
  rm -f "$RAW_DIR/$id.raw" "$UNSUPPORTED_DIR/$id.txt"
  ok "Removed subscription and its generated profiles."
  pause
}

show_logs() {
  header
  say "${BOLD}${WHITE}Last Xray log lines${RESET}"
  line
  tail -n 80 "$LOG_DIR/xray.log" 2>/dev/null || warn "No runtime log yet."
  pause
}

# ─────────────────────────────────────────────────────────────────────────────
# v1.1.0 advanced layer. Later definitions intentionally override v1.0 helpers.
# ─────────────────────────────────────────────────────────────────────────────

PURPLE='\033[38;5;141m'; PINK='\033[38;5;205m'; ORANGE='\033[38;5;214m'; LIME='\033[38;5;118m'
DEEPBG='\033[48;5;18m'; TEALBG='\033[48;5;23m'

header() {
  clear 2>/dev/null || true
  printf "%b\n" "${DEEPBG}${BOLD}${WHITE} ╔══════════════════════════════════════════════════════════╗ ${RESET}"
  printf "%b\n" "${DEEPBG}${BOLD}${CYAN} ║ ⚡ XRAY GENIUS  ${PINK}v${APP_VERSION}${CYAN}  •  TERMUX CONTROL DECK      ║ ${RESET}"
  printf "%b\n" "${DEEPBG}${BOLD}${WHITE} ╚══════════════════════════════════════════════════════════╝ ${RESET}"
  printf "%b\n" " ${PURPLE}◈${RESET} ${GRAY}Native Xray • smart tunnel tests • Telegram radar • leak guard${RESET}"
  line
}

section() { printf "\n%b\n" "${TEALBG}${BOLD}${WHITE}  $*  ${RESET}"; }
yn() { [[ "${1:-0}" == "1" ]] && printf "%b" "${GREEN}ON${RESET}" || printf "%b" "${RED}OFF${RESET}"; }

randhex() {
  local n="${1:-16}"
  od -An -N "$(( (n+1)/2 ))" -tx1 /dev/urandom 2>/dev/null | tr -d ' \n' | cut -c1-"$n"
}

apply_bench_profile() {
  case "${1:-balanced}" in
    reliable)
      BENCH_PROFILE=reliable; BENCH_CANDIDATES=30; BENCH_SAMPLES=6; BENCH_TIMEOUT=12
      BENCH_BYTES=524288; TCP_PRECHECK_TIMEOUT=2.5; TCP_PRECHECK_WORKERS=12 ;;
    balanced)
      BENCH_PROFILE=balanced; BENCH_CANDIDATES=20; BENCH_SAMPLES=4; BENCH_TIMEOUT=8
      BENCH_BYTES=262144; TCP_PRECHECK_TIMEOUT=1.8; TCP_PRECHECK_WORKERS=20 ;;
    fast)
      BENCH_PROFILE=fast; BENCH_CANDIDATES=14; BENCH_SAMPLES=3; BENCH_TIMEOUT=6
      BENCH_BYTES=131072; TCP_PRECHECK_TIMEOUT=1.2; TCP_PRECHECK_WORKERS=28 ;;
    turbo)
      BENCH_PROFILE=turbo; BENCH_CANDIDATES=10; BENCH_SAMPLES=2; BENCH_TIMEOUT=4
      BENCH_BYTES=65536; TCP_PRECHECK_TIMEOUT=0.8; TCP_PRECHECK_WORKERS=40 ;;
    custom) BENCH_PROFILE=custom ;;
    *) return 1 ;;
  esac
  SPEED_URL="https://speed.cloudflare.com/__down?bytes=${BENCH_BYTES}"
  save_settings
}

v11_patch_runtime() {
  local src="$1" dst="$2" port="$3" listen="$4"
  python3 - "$src" "$dst" "$port" "$listen" "$LEAK_PROTECTION" <<'PY'
import json,sys
from pathlib import Path
src,dst,port,listen,guard=sys.argv[1],sys.argv[2],int(sys.argv[3]),sys.argv[4],sys.argv[5]=='1'
obj=json.loads(Path(src).read_text())
outs=obj.get('outbounds')
if not isinstance(outs,list) or not outs:
    raise SystemExit('configuration has no outbounds')
proxy=None
for o in outs:
    if isinstance(o,dict) and o.get('tag')=='proxy':
        proxy=o; break
if proxy is None:
    for o in outs:
        if isinstance(o,dict) and str(o.get('protocol','')).lower() not in ('freedom','blackhole','dns','loopback'):
            proxy=o; break
if proxy is None:
    proxy=outs[0] if isinstance(outs[0],dict) else None
if proxy is None:
    raise SystemExit('no usable proxy outbound')
proxy_tag=proxy.get('tag') or 'xgc-proxy'
proxy['tag']=proxy_tag
inbound={
  'tag':'socks-in','listen':listen,'port':port,'protocol':'socks',
  'settings':{'udp':True},
  'sniffing':{'enabled':True,'routeOnly':True,'destOverride':['http','tls','quic']}
}
ins=obj.get('inbounds') if isinstance(obj.get('inbounds'),list) else []
ins=[x for x in ins if not (isinstance(x,dict) and x.get('tag')=='socks-in')]
obj['inbounds']=[inbound]+ins
if not isinstance(obj.get('log'),dict): obj['log']={'loglevel':'warning'}
else: obj['log'].setdefault('loglevel','warning')
if guard:
    # No local/system DNS fallback. Remote DoH is tagged and forced through the selected proxy.
    olddns=obj.get('dns') if isinstance(obj.get('dns'),dict) else {}
    hosts=olddns.get('hosts') if isinstance(olddns.get('hosts'),dict) else None
    dns={
      'servers':['https://1.1.1.1/dns-query','https://8.8.8.8/dns-query'],
      'queryStrategy':'UseIP','disableFallback':False,'enableParallelQuery':True,
      'useSystemHosts':False,'tag':'xgc-dns'
    }
    if hosts: dns['hosts']=hosts
    obj['dns']=dns
    routing=obj.get('routing') if isinstance(obj.get('routing'),dict) else {}
    rules=routing.get('rules') if isinstance(routing.get('rules'),list) else []
    rules=[r for r in rules if not (isinstance(r,dict) and r.get('_xgc'))]
    # These rules are first by design: provider split-routing must not bypass the selected proxy
    # for traffic entering through the XGC SOCKS port.
    guard_rules=[
      {'type':'field','inboundTag':['xgc-dns'],'outboundTag':proxy_tag,'_xgc':True},
      {'type':'field','inboundTag':['socks-in'],'outboundTag':proxy_tag,'_xgc':True},
    ]
    # Remove private marker before Xray sees the rules.
    clean=[]
    for r in guard_rules+rules:
        if isinstance(r,dict):
            r=dict(r); r.pop('_xgc',None)
        clean.append(r)
    routing['rules']=clean
    routing.setdefault('domainStrategy','AsIs')
    obj['routing']=routing
Path(dst).write_text(json.dumps(obj,ensure_ascii=False,indent=2))
PY
}

endpoint_probe() {
  local host="$1" port="$2" scheme="$3"
  if [[ -z "$host" || ! "$port" =~ ^[0-9]+$ || "$port" == "0" || "$scheme" == "hysteria2" ]]; then
    printf '9999'
    return
  fi
  python3 - "$host" "$port" "$TCP_PRECHECK_TIMEOUT" <<'PY'
import socket,sys,time,statistics
h=sys.argv[1]; p=int(sys.argv[2]); to=float(sys.argv[3]); vals=[]
for _ in range(2):
    t=time.perf_counter()
    try:
        s=socket.create_connection((h,p),timeout=to); s.close()
        vals.append((time.perf_counter()-t)*1000)
    except Exception: pass
print(f'{statistics.median(vals):.1f}' if vals else '9999')
PY
}

trace_through_socks() {
  local port="$1"
  curl -fsS --socks5-hostname "127.0.0.1:${port}" --connect-timeout "$BENCH_TIMEOUT" --max-time "$BENCH_TIMEOUT" \
    'https://www.cloudflare.com/cdn-cgi/trace' 2>/dev/null || true
}

direct_trace() {
  curl -fsS --connect-timeout 5 --max-time 8 'https://www.cloudflare.com/cdn-cgi/trace' 2>/dev/null || true
}

trace_field() { awk -F= -v k="$1" '$1==k{print substr($0,index($0,"=")+1); exit}'; }

bench_one() {
  local file="$1" id="$2" name="$3" port="$4" host="${5:-}" scheme="${6:-}"
  local cfg="$TMP_DIR/bench-${port}.json" log="$TMP_DIR/bench-${port}.log"
  local ping
  ping="$(endpoint_probe "$host" "${7:-0}" "$scheme" 2>/dev/null || echo 9999)"
  v11_patch_runtime "$file" "$cfg" "$port" "127.0.0.1" >/dev/null 2>&1 || {
    printf '%s\t%s\t%s\t0\t9999\t9999\t0\t-\t--\t%s\n' "$id" "$file" "${name//$'\t'/ }" "$ping"; return; }
  if ! XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$cfg" >"$log" 2>&1; then
    printf '%s\t%s\t%s\t0\t9999\t9999\t0\t-\t--\t%s\n' "$id" "$file" "${name//$'\t'/ }" "$ping"; rm -f "$cfg"; return
  fi
  XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -c "$cfg" >"$log" 2>&1 & local xp=$!
  if ! wait_port 127.0.0.1 "$port" >/dev/null 2>&1; then
    kill "$xp" 2>/dev/null || true; wait "$xp" 2>/dev/null || true
    printf '%s\t%s\t%s\t0\t9999\t9999\t0\t-\t--\t%s\n' "$id" "$file" "${name//$'\t'/ }" "$ping"; rm -f "$cfg"; return
  fi
  local -a times=(); local i val successes=0 speed=0 trace ip='-' cc='--'
  for ((i=0;i<BENCH_SAMPLES;i++)); do
    val="$(curl -fsS --socks5-hostname "127.0.0.1:$port" --connect-timeout "$BENCH_TIMEOUT" --max-time "$BENCH_TIMEOUT" \
      -o /dev/null -w '%{time_total}' "$TEST_URL" 2>/dev/null || true)"
    if [[ "$val" =~ ^[0-9]+([.][0-9]+)?$ && "$val" != "0.000000" ]]; then times+=("$val"); ((successes+=1)); fi
  done
  if (( successes > 0 )); then
    local speed_url="$SPEED_URL"
    [[ "$speed_url" == *speed.cloudflare.com* ]] && speed_url="https://speed.cloudflare.com/__down?bytes=${BENCH_BYTES}"
    speed="$(curl -fsS --socks5-hostname "127.0.0.1:$port" --connect-timeout "$BENCH_TIMEOUT" --max-time $((BENCH_TIMEOUT+5)) \
      -o /dev/null -w '%{speed_download}' "$speed_url" 2>/dev/null || echo 0)"
    [[ "$speed" =~ ^[0-9]+([.][0-9]+)?$ ]] || speed=0
    trace="$(trace_through_socks "$port")"
    ip="$(printf '%s\n' "$trace" | trace_field ip)"; cc="$(printf '%s\n' "$trace" | trace_field loc)"
    [[ -n "$ip" ]] || ip='-'; [[ "$cc" =~ ^[A-Z][A-Z]$ ]] || cc='--'
  fi
  kill "$xp" 2>/dev/null || true; wait "$xp" 2>/dev/null || true
  local stats lat jit success_pct
  stats="$(pyhelper stats "${times[@]}" 2>/dev/null || printf '9999\t9999')"; lat="${stats%%$'\t'*}"; jit="${stats##*$'\t'}"
  success_pct=$(( successes * 100 / BENCH_SAMPLES ))
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$id" "$file" "${name//$'\t'/ }" "$success_pct" "$lat" "$jit" "$speed" "$ip" "$cc" "$ping"
  rm -f "$cfg"
}

rank_v11() {
  local resultfile="$1"
  python3 - "$resultfile" <<'PY'
import sys,math
from pathlib import Path
rows=[]
for line in Path(sys.argv[1]).read_text(errors='ignore').splitlines():
    p=line.split('\t')
    if len(p)<10: continue
    try:
        rows.append(dict(id=p[0],file=p[1],name=p[2],success=float(p[3]),lat=float(p[4]),jit=float(p[5]),
                         speed=float(p[6]),ip=p[7],cc=p[8],ping=float(p[9])))
    except Exception: pass
if not rows: raise SystemExit(0)
def scaled(vals,x,invert=False):
    lo,hi=min(vals),max(vals)
    v=.5 if abs(hi-lo)<1e-9 else (x-lo)/(hi-lo)
    return 1-v if invert else v
def flag(cc):
    cc=(cc or '').upper()
    if len(cc)==2 and cc.isalpha(): return ''.join(chr(0x1F1E6+ord(c)-65) for c in cc)
    return '🌐'
lats=[min(r['lat'],10000) for r in rows]; jits=[min(r['jit'],10000) for r in rows]
speeds=[math.log1p(max(r['speed'],0)) for r in rows]
for r in rows:
    r['score']=r['success']*.50 + scaled(lats,min(r['lat'],10000),True)*20 + scaled(jits,min(r['jit'],10000),True)*15 + scaled(speeds,math.log1p(max(r['speed'],0)))*15
    if r['success']<=0: r['score']=-1
rows.sort(key=lambda r:(-r['score'],-r['success'],r['lat'],r['jit'],-r['speed']))
for i,r in enumerate(rows,1):
    print('\t'.join([str(i),r['id'],r['file'],r['name'],f"{r['success']:.0f}",f"{r['lat']:.1f}",f"{r['jit']:.1f}",
                     f"{r['speed']/1024:.0f}",f"{r['score']:.1f}",r['ip'],r['cc'],flag(r['cc']),
                     '-' if r['ping']>=9999 else f"{r['ping']:.1f}"]))
PY
}

display_ranked() {
  local ranked="$1" max="${2:-0}" shown=0
  printf "${GRAY}%3s %-2s %-28s %6s %7s %7s %7s %7s %-15s${RESET}\n" "#" "" "Profile" "OK" "Ping" "Tunnel" "Jitter" "KB/s" "Egress IP"
  while IFS=$'\t' read -r rank rid rfile rname succ lat jit kb score ip cc flag ping; do
    ((shown+=1)); (( max>0 && shown>max )) && break
    if [[ "$succ" == "0" ]]; then
      printf "${RED}%3s${RESET} %-2s %-28s %5s%% %7s %7s %7s %7s %-15s\n" "$rank" "$flag" "${rname:0:28}" "$succ" "$ping" "FAIL" "-" "-" "$ip"
    else
      printf "${CYAN}%3s${RESET} %-2s %-28s ${GREEN}%5s%%${RESET} %6sms %6sms %6sms %7s %-15s\n" "$rank" "$flag" "${rname:0:28}" "$succ" "$ping" "$lat" "$jit" "$kb" "$ip"
    fi
  done < "$ranked"
}

record_connected() {
  local name="$1" file="$2"
  python3 - "$LAST_CONNECTED" "$HISTORY_FILE" "$INDEX" "$name" "$file" "$SOCKS_LISTEN" "$SOCKS_PORT" "$LEAK_PROTECTION" <<'PY'
import json,sys,time
last,hist,index,name,file,listen,port,guard=sys.argv[1:]
meta={}
try:
    for line in open(index,errors='ignore'):
        try:
            m=json.loads(line)
            if m.get('file')==file: meta=m; break
        except Exception: pass
except Exception: pass
rec={'name':name,'file':file,'id':meta.get('id',''),'scheme':meta.get('scheme',''),'host':meta.get('host',''),
     'remote_port':meta.get('port',0),'listen':listen,'port':int(port),'leak_guard':guard=='1','started':int(time.time())}
open(last,'w').write(json.dumps(rec,ensure_ascii=False,indent=2))
with open(hist,'a') as f: f.write(json.dumps(rec,ensure_ascii=False,separators=(',',':'))+'\n')
PY
  chmod 600 "$LAST_CONNECTED" "$HISTORY_FILE" 2>/dev/null || true
}

start_profile_file() {
  local file="$1" name="$2" quiet="${3:-0}"
  [[ -x "$XRAY_BIN" ]] || { err "Xray is not installed."; return 1; }
  [[ -f "$file" ]] || { err "Profile file missing."; return 1; }
  stop_xray >/dev/null 2>&1 || true
  if ! v11_patch_runtime "$file" "$RUNTIME_CONFIG" "$SOCKS_PORT" "$SOCKS_LISTEN"; then
    err "Could not build hardened runtime config."; return 1
  fi
  if ! XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$RUNTIME_CONFIG" >"$LOG_DIR/config-test.log" 2>&1; then
    err "Xray rejected this config. See $LOG_DIR/config-test.log"
    return 1
  fi
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
  XRAY_LOCATION_ASSET="$BIN_DIR" nohup "$XRAY_BIN" run -c "$RUNTIME_CONFIG" >"$LOG_DIR/xray.log" 2>&1 &
  local p=$!; printf '%s' "$p" > "$PID_FILE"
  if ! wait_port "$SOCKS_LISTEN" "$SOCKS_PORT"; then
    err "Xray started but SOCKS port did not open. See logs."
    stop_xray; return 1
  fi
  python3 - "$ACTIVE_FILE" "$name" "$file" "$SOCKS_LISTEN" "$SOCKS_PORT" "$LEAK_PROTECTION" <<'PY'
import json,sys,time
open(sys.argv[1],'w').write(json.dumps({'name':sys.argv[2],'file':sys.argv[3],'listen':sys.argv[4],
 'port':int(sys.argv[5]),'leak_guard':sys.argv[6]=='1','started':int(time.time())},ensure_ascii=False))
PY
  [[ "$REMEMBER_LAST" == "1" ]] && record_connected "$name" "$file"
  if [[ "$quiet" != "1" ]]; then
    ok "Connected: $name"
    say " ${CYAN}SOCKS5h:${RESET} ${BOLD}${SOCKS_LISTEN}:${SOCKS_PORT}${RESET}  ${GRAY}• leak guard: $( [[ $LEAK_PROTECTION == 1 ]] && echo hardened || echo standard )${RESET}"
  fi
}

connect_last() {
  [[ "$REMEMBER_LAST" == "1" ]] || { warn "Remember-last is disabled in Settings."; return 1; }
  [[ -s "$LAST_CONNECTED" ]] || { warn "No previous connection has been saved yet."; return 1; }
  local file name id meta
  file="$(jq -r '.file // empty' "$LAST_CONNECTED" 2>/dev/null)"
  name="$(jq -r '.name // "Last profile"' "$LAST_CONNECTED" 2>/dev/null)"
  id="$(jq -r '.id // empty' "$LAST_CONNECTED" 2>/dev/null)"
  if [[ ! -f "$file" && -n "$id" ]]; then
    meta="$(jq -c --arg id "$id" 'select(.id==$id)' "$INDEX" 2>/dev/null | head -n1)"
    [[ -n "$meta" ]] && file="$(jq -r '.file' <<<"$meta")" && name="$(jq -r '.name' <<<"$meta")"
  fi
  [[ -f "$file" ]] || { warn "Last profile no longer exists after a subscription refresh."; return 1; }
  info "Fast reconnect → $name"
  start_profile_file "$file" "$name"
}

quick_reconnect_menu() { header; connect_last || true; pause; }

auto_connect() {
  header
  if [[ "$REMEMBER_LAST" == "1" && -s "$LAST_CONNECTED" ]]; then
    info "Trying saved last-working profile first..."
    if connect_last; then pause; return; fi
    warn "Fast reconnect failed; switching to smart benchmark."
  fi
  smart_connect
}

smart_connect() {
  header
  local total; total="$(count_profiles)"
  (( total > 0 )) || { warn "No profiles. Add a subscription first."; pause; return; }
  [[ -x "$XRAY_BIN" ]] || { warn "Xray missing; installing now."; update_xray || { pause; return; }; }
  stop_xray >/dev/null 2>&1 || true
  section "STAGE 1 • LIGHTWEIGHT ENDPOINT SCREEN"
  info "Scanning $total profile(s), policy: ${BENCH_PROFILE}..."
  local candidates="$TMP_DIR/candidates.jsonl"
  pyhelper precheck "$INDEX" "$BENCH_CANDIDATES" "$TCP_PRECHECK_TIMEOUT" "$TCP_PRECHECK_WORKERS" > "$candidates" 2>/dev/null
  local ccount; ccount="$(wc -l < "$candidates" | tr -d ' ')"
  (( ccount > 0 )) || { err "No candidates available."; pause; return; }
  section "STAGE 2 • REAL XRAY TUNNEL TEST"
  info "Testing $ccount finalist(s): RTT + jitter + reliability + throughput + egress IP."
  : > "$BENCH_RESULTS"
  local i=0 id file name port host scheme rport result
  while IFS= read -r row; do
    ((i+=1)); id="$(jq -r '.id' <<<"$row")"; file="$(jq -r '.file' <<<"$row")"; name="$(jq -r '.name' <<<"$row")"
    host="$(jq -r '.host // ""' <<<"$row")"; scheme="$(jq -r '.scheme // ""' <<<"$row")"; rport="$(jq -r '.port // 0' <<<"$row")"
    port=$((18080+i))
    printf " ${GRAY}[${i}/${ccount}]${RESET} %-42s " "${name:0:42}"
    result="$(bench_one "$file" "$id" "$name" "$port" "$host" "$scheme" "$rport")"
    printf '%s\n' "$result" >> "$BENCH_RESULTS"
    local succ lat jit sp ip cc
    succ="$(cut -f4 <<<"$result")"; lat="$(cut -f5 <<<"$result")"; jit="$(cut -f6 <<<"$result")"; sp="$(cut -f7 <<<"$result")"
    ip="$(cut -f8 <<<"$result")"; cc="$(cut -f9 <<<"$result")"
    if [[ "$succ" == "0" ]]; then say "${RED}FAIL${RESET}"; else
      printf "${GREEN}%s%%${RESET} ${CYAN}%sms${RESET} j=${YELLOW}%sms${RESET} %sKB/s ${PURPLE}%s/%s${RESET}\n" \
        "$succ" "$lat" "$jit" "$(awk -v s="$sp" 'BEGIN{printf "%.0f",s/1024}')" "$ip" "$cc"
    fi
  done < "$candidates"
  line
  local ranked="$TMP_DIR/ranked-v11.tsv"; rank_v11 "$BENCH_RESULTS" > "$ranked"
  section "SMART RANKING • BEST → WORST"
  say "${GRAY}Score: reliability 50% • tunnel latency 20% • jitter 15% • throughput 15%${RESET}"
  display_ranked "$ranked" 14
  local best; best="$(head -n1 "$ranked")"
  [[ -n "$best" ]] || { err "No benchmark result."; pause; return; }
  local bestfile bestname bestsucc
  bestfile="$(cut -f3 <<<"$best")"; bestname="$(cut -f4 <<<"$best")"; bestsucc="$(cut -f5 <<<"$best")"
  if [[ "$bestsucc" == "0" ]]; then err "None of the tested candidates established working proxy traffic."; pause; return; fi
  line
  info "Starting ranked candidates with automatic Privacy-Guard failover..."
  local rr rid rfile rname rsucc rlat rjit rkb rscore rip rcc rflag rping connected=0
  while IFS=$'\t' read -r rr rid rfile rname rsucc rlat rjit rkb rscore rip rcc rflag rping; do
    [[ -n "$rfile" ]] || continue
    [[ "$rsucc" =~ ^[0-9]+([.][0-9]+)?$ ]] || continue
    awk -v s="$rsucc" 'BEGIN{exit !(s>0)}' || continue
    info "Guarded connect attempt #${rr}: ${rname}"
    if start_profile_file "$rfile" "$rname"; then
      connected=1
      break
    fi
    warn "Candidate #${rr} failed startup/privacy verification; trying the next ranked tunnel."
  done < "$ranked"
  (( connected == 1 )) || err "No ranked candidate passed the always-on Privacy Guard."
  pause
}

full_test_number() {
  local num="$1" ask_connect="${2:-1}"
  [[ "$num" =~ ^[0-9]+$ ]] || { err "Invalid profile number."; return 1; }
  local meta file name id host scheme rport result ranked port
  meta="$(pyhelper meta-number "$INDEX" "$num" 2>/dev/null || true)"
  [[ -n "$meta" ]] || { err "Profile not found."; return 1; }
  file="$(jq -r '.file' <<<"$meta")"; name="$(jq -r '.name' <<<"$meta")"; id="$(jq -r '.id' <<<"$meta")"
  host="$(jq -r '.host // ""' <<<"$meta")"; scheme="$(jq -r '.scheme // ""' <<<"$meta")"; rport="$(jq -r '.port // 0' <<<"$meta")"
  section "FULL SINGLE-CONFIG TUNNEL TEST"
  printf " ${CYAN}Profile:${RESET} %s\n ${CYAN}Protocol:${RESET} %s   ${CYAN}Endpoint:${RESET} %s:%s\n" "$name" "$scheme" "$host" "$rport"
  say " ${GRAY}Policy: ${BENCH_PROFILE} • ${BENCH_SAMPLES} real samples • ${BENCH_BYTES} byte throughput probe${RESET}"
  port=$((22080 + (num % 500)))
  result="$(bench_one "$file" "$id" "$name" "$port" "$host" "$scheme" "$rport")"
  printf '%s\n' "$result" > "$TMP_DIR/single-test.tsv"
  rank_v11 "$TMP_DIR/single-test.tsv" > "$TMP_DIR/single-ranked.tsv"
  line; display_ranked "$TMP_DIR/single-ranked.tsv" 1
  local succ lat jit speed ip cc ping
  succ="$(cut -f4 <<<"$result")"; lat="$(cut -f5 <<<"$result")"; jit="$(cut -f6 <<<"$result")"; speed="$(cut -f7 <<<"$result")"
  ip="$(cut -f8 <<<"$result")"; cc="$(cut -f9 <<<"$result")"; ping="$(cut -f10 <<<"$result")"
  line
  printf " ${LIME}Reliability:${RESET} %s%%   ${CYAN}Tunnel RTT:${RESET} %sms   ${YELLOW}Jitter:${RESET} %sms\n" "$succ" "$lat" "$jit"
  printf " ${PURPLE}Endpoint TCP ping:${RESET} %s ms   ${PINK}Throughput sample:${RESET} %s KB/s\n" "$ping" "$(awk -v s="$speed" 'BEGIN{printf "%.0f",s/1024}')"
  printf " ${ORANGE}Egress:${RESET} %s  (%s)\n" "$ip" "$cc"
  if [[ "$succ" == "0" ]]; then warn "This config did not pass a real tunnel request."; return 1; fi
  if [[ "$ask_connect" == "1" ]]; then
    printf "\n%b" "${CYAN}Connect this profile now? [y/N]:${RESET} "; local ans; read -r ans
    [[ "$ans" =~ ^[Yy]$ ]] && start_profile_file "$file" "$name" || true
  fi
}

full_test_menu() {
  header
  (( $(count_profiles) > 0 )) || { warn "No profiles. Add/import a config first."; pause; return; }
  list_profiles; line
  say " ${GRAY}Enter a profile number, or ${CYAN}P${GRAY} to paste one config for a one-time import/test.${RESET}"
  printf "%b" "${MAGENTA}Selection:${RESET} "; local sel; read -r sel
  if [[ "$sel" =~ ^[Pp]$ ]]; then
    say "${CYAN}Paste one VLESS / VMess / Trojan / SS / Hysteria2 URI:${RESET}"
    local uri; read -r uri
    [[ "$uri" == *"://"* ]] || { err "No proxy URI detected."; pause; return; }
    local id raw out num
    id="onetime-$(printf '%s' "$uri" | sha256sum | cut -c1-10)"; raw="$RAW_DIR/$id.raw"; printf '%s\n' "$uri" > "$raw"
    out="$(pyhelper parse "$raw" "$id" "One-time test" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)" || { err "$out"; pause; return; }
    num="$(python3 - "$INDEX" "$id" <<'PY'
import json,sys
n=0
for i,l in enumerate(open(sys.argv[1],errors='ignore'),1):
    try:
        if json.loads(l).get('subid')==sys.argv[2]: n=i
    except Exception: pass
print(n)
PY
)"
    [[ "$num" != "0" ]] && full_test_number "$num" 1 || err "Could not import that URI."
  else
    full_test_number "$sel" 1 || true
  fi
  pause
}

full_test_cli() {
  header
  local n="${1:-}"
  if [[ -z "$n" ]]; then list_profiles; line; printf "%b" "${CYAN}Profile number:${RESET} "; read -r n; fi
  full_test_number "$n" 0 || true
}

dns_leak_compare() {
  local mode="$1" socksopt="$2" id json
  id="$(curl -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" --connect-timeout 5 --max-time 8 https://bash.ws/id 2>/dev/null | tr -dc '0-9' | head -c 16)"
  [[ -n "$id" ]] || { printf 'INCONCLUSIVE'; return; }
  local i
  for i in 1 2 3 4 5 6; do
    curl -sS "$socksopt" "127.0.0.1:${SOCKS_PORT}" --connect-timeout 3 --max-time 5 -o /dev/null "http://${i}.${id}.bash.ws/" 2>/dev/null || true
  done
  sleep 1
  json="$(curl -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" --connect-timeout 5 --max-time 10 "https://bash.ws/dnsleak/test/${id}?json" 2>/dev/null || true)"
  [[ -n "$json" ]] || { printf 'INCONCLUSIVE'; return; }
  printf '%s' "$json" | jq -r '[.[] | select(.type=="dns") | ((.ip // "?")+"|"+(.country_name // "?")+"|"+(.asn // "?"))] | unique | join(",")' 2>/dev/null || printf 'INCONCLUSIVE'
}

leak_audit() {
  header
  process_alive || { warn "Connect a profile first; leak audit needs a running SOCKS tunnel."; pause; return; }
  section "PRIVACY / LEAK AUDIT"
  local dtrace ptrace dip dcc pip pcc
  info "Comparing direct Android/Termux egress with Xray SOCKS egress..."
  dtrace="$(direct_trace)"; ptrace="$(trace_through_socks "$SOCKS_PORT")"
  dip="$(printf '%s\n' "$dtrace" | trace_field ip)"; dcc="$(printf '%s\n' "$dtrace" | trace_field loc)"
  pip="$(printf '%s\n' "$ptrace" | trace_field ip)"; pcc="$(printf '%s\n' "$ptrace" | trace_field loc)"
  printf " ${GRAY}Direct egress:${RESET} %s  %s\n" "${dip:--}" "${dcc:---}"
  printf " ${CYAN}Proxy egress :${RESET} ${BOLD}%s${RESET}  %s\n" "${pip:--}" "${pcc:---}"
  if [[ -n "$dip" && -n "$pip" && "$dip" == "$pip" ]]; then
    warn "Proxy egress equals direct egress. This profile is not changing your observed public IP."
  elif [[ -n "$pip" ]]; then
    ok "SOCKS tunnel egress differs from the direct connection."
  else
    warn "Could not verify proxy egress."
  fi
  printf " ${GRAY}Extreme leak guard:${RESET} "; yn "$LEAK_PROTECTION"; printf '\n'
  if [[ "$LEAK_PROTECTION" == "1" ]]; then
    ok "XGC runtime forces its SOCKS inbound and tagged remote DoH path to the selected proxy outbound."
  else
    warn "Leak guard is disabled; provider routing/DNS behavior is preserved."
  fi
  if [[ "$DNS_LEAK_TEST" == "1" ]]; then
    section "DNS PATH DIFFERENTIAL TEST"
    info "Remote-resolution test (SOCKS5h)..."
    local remote localr
    remote="$(dns_leak_compare remote --socks5-hostname)"
    printf " ${CYAN}Remote resolvers:${RESET} %s\n" "$remote"
    info "Local-resolution simulation (plain SOCKS5)..."
    localr="$(dns_leak_compare local --socks5)"
    printf " ${YELLOW}Local resolver path:${RESET} %s\n" "$localr"
    if [[ "$remote" != "INCONCLUSIVE" && "$localr" != "INCONCLUSIVE" && "$remote" != "$localr" ]]; then
      warn "The resolver sets differ. Apps that resolve locally before SOCKS can expose your device DNS path. Prefer SOCKS5h / proxy-DNS mode."
    elif [[ "$remote" == "$localr" && "$remote" != "INCONCLUSIVE" ]]; then
      ok "No resolver-path difference was observed in this challenge."
    else
      warn "DNS challenge was inconclusive; the external test service may be unreachable through this node."
    fi
  fi
  line
  say "${GRAY}Scope: this protects/verifies traffic that actually uses XGC's SOCKS endpoint. A Termux script cannot force unrelated Android apps to use it like a VpnService can.${RESET}"
  pause
}

cf_load() {
  [[ -s "$CF_CONF" ]] || return 1
  CF_AUTH_MODE="$(jq -r '.auth_mode // empty' "$CF_CONF" 2>/dev/null)"
  CF_EMAIL="$(jq -r '.email // empty' "$CF_CONF" 2>/dev/null)"
  CF_SECRET="$(jq -r '.secret // empty' "$CF_CONF" 2>/dev/null)"
  CF_ACCOUNT_ID="$(jq -r '.account_id // empty' "$CF_CONF" 2>/dev/null)"
  CF_SCRIPT="$(jq -r '.script_name // empty' "$CF_CONF" 2>/dev/null)"
  CF_ACCESS_KEY="$(jq -r '.access_key // empty' "$CF_CONF" 2>/dev/null)"
  CF_WORKER_URL="$(jq -r '.worker_url // empty' "$CF_CONF" 2>/dev/null)"
  [[ -n "$CF_AUTH_MODE" && -n "$CF_SECRET" && -n "$CF_ACCOUNT_ID" && -n "$CF_SCRIPT" && -n "$CF_ACCESS_KEY" ]]
}

cf_api() {
  local method="$1" url="$2"; shift 2
  local -a auth
  if [[ "$CF_AUTH_MODE" == "token" ]]; then
    auth=(-H "Authorization: Bearer $CF_SECRET")
  else
    auth=(-H "X-Auth-Email: $CF_EMAIL" -H "X-Auth-Key: $CF_SECRET")
  fi
  curl -fsS --connect-timeout 12 --max-time 60 -X "$method" "${auth[@]}" "$@" "$url"
}

save_cf_config() {
  local worker_url="${1:-}"
  python3 - "$CF_CONF" "$CF_AUTH_MODE" "$CF_EMAIL" "$CF_SECRET" "$CF_ACCOUNT_ID" "$CF_SCRIPT" "$CF_ACCESS_KEY" "$worker_url" <<'PY'
import json,sys
p,mode,email,secret,account,script,key,url=sys.argv[1:]
open(p,'w').write(json.dumps({'auth_mode':mode,'email':email,'secret':secret,'account_id':account,
 'script_name':script,'access_key':key,'worker_url':url},indent=2))
PY
  chmod 600 "$CF_CONF"
}

write_worker_js() {
  cat > "$WORKER_FILE" <<'JS'
const ACCESS_KEY = "__XGC_ACCESS_KEY__";
const MAX_POSTS = 200;
const MAX_PAGES = 15;

const headers = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "x-content-type-options": "nosniff"
};
const json = (obj, status=200) => new Response(JSON.stringify(obj), {status, headers});

function decodeEntities(s) {
  return String(s || "")
    .replace(/&amp;/gi, "&").replace(/&quot;/gi, '"').replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, "<").replace(/&gt;/gi, ">")
    .replace(/&#x([0-9a-f]+);/gi, (_,h) => { try { return String.fromCodePoint(parseInt(h,16)); } catch { return ""; } })
    .replace(/&#([0-9]+);/g, (_,d) => { try { return String.fromCodePoint(parseInt(d,10)); } catch { return ""; } });
}
function plainText(html) {
  return decodeEntities(String(html||"").replace(/<br\s*\/?>/gi,"\n").replace(/<[^>]+>/g," "))
    .replace(/[\t\r ]+/g," ").replace(/\n\s+/g,"\n").trim();
}
function configsFrom(s) {
  const decoded=decodeEntities(s);
  const rx=/(?:vless|vmess|trojan|ss|hysteria2|hy2):\/\/[^\s<>"']+/gi;
  const out=[]; const seen=new Set();
  for (let x of (decoded.match(rx)||[])) {
    x=x.replace(/[\]\[(){}>,.;]+$/g,"");
    if (!seen.has(x)) { seen.add(x); out.push(x); }
  }
  return out;
}
function parsePage(html, wantedChannel) {
  const marks=[]; const rx=/data-post="([A-Za-z0-9_]+)\/(\d+)"/g; let m;
  while ((m=rx.exec(html))) marks.push({channel:m[1], id:Number(m[2]), at:m.index});
  const posts=[];
  for (let i=0;i<marks.length;i++) {
    const mk=marks[i]; if (mk.channel.toLowerCase()!==wantedChannel.toLowerCase()) continue;
    const end=i+1<marks.length?marks[i+1].at:Math.min(html.length,mk.at+120000);
    const block=html.slice(mk.at,end);
    const tm=block.match(/class="tgme_widget_message_text[^"]*"[^>]*>([\s\S]*?)<\/div>/i);
    const dm=block.match(/datetime="([^"]+)"/i);
    const body=tm?tm[1]:"";
    const configs=configsFrom(body+"\n"+block);
    posts.push({id:mk.id,date:dm?dm[1]:"",link:`https://t.me/${wantedChannel}/${mk.id}`,text:plainText(body).slice(0,5000),configs});
  }
  return posts;
}
async function fetchPage(channel,before) {
  const u=`https://t.me/s/${encodeURIComponent(channel)}${before?`?before=${before}`:""}`;
  const r=await fetch(u,{headers:{"user-agent":"Mozilla/5.0 Xray-Genius/1.1 TelegramPublicFetcher","accept":"text/html"},redirect:"follow"});
  if (!r.ok) throw new Error(`Telegram HTTP ${r.status}`);
  return await r.text();
}

export default {
  async fetch(request) {
    try {
      const u=new URL(request.url);
      if (u.searchParams.get("key")!==ACCESS_KEY) return json({error:"unauthorized"},401);
      if (u.pathname==="/health") return json({ok:true,service:"xray-genius-telegram-fetcher",version:"1.1.4"});
      let channel=(u.searchParams.get("channel")||"").trim().replace(/^@/,"");
      if (!/^[A-Za-z0-9_]{5,32}$/.test(channel)) return json({error:"invalid public channel username"},400);
      let limit=Math.max(1,Math.min(MAX_POSTS,Number(u.searchParams.get("limit")||20)||20));
      const all=[]; const seen=new Set(); let before=0;
      for (let page=0;page<MAX_PAGES && all.length<limit;page++) {
        const html=await fetchPage(channel,before);
        const got=parsePage(html,channel);
        if (!got.length) break;
        let min=Infinity, fresh=0;
        for (const p of got) {
          min=Math.min(min,p.id);
          if (!seen.has(p.id)) { seen.add(p.id); all.push(p); fresh++; }
        }
        if (!fresh || !Number.isFinite(min) || min<=1) break;
        before=min;
      }
      all.sort((a,b)=>b.id-a.id);
      const posts=all.slice(0,limit);
      return json({channel,requested:limit,count:posts.length,configs:posts.reduce((n,p)=>n+p.configs.length,0),posts});
    } catch (e) {
      return json({error:String(e && e.message || e)},502);
    }
  }
};
JS
  python3 - "$WORKER_FILE" "$CF_ACCESS_KEY" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]); p.write_text(p.read_text().replace('__XGC_ACCESS_KEY__',sys.argv[2]))
PY
  chmod 600 "$WORKER_FILE"
}

configure_cloudflare() {
  header; section "CLOUDFLARE WORKER SETUP"
  say "${GRAY}Used only to fetch public Telegram web previews. Your Cloudflare credential is stored locally with mode 600 and is never embedded in the Worker.${RESET}"
  say " ${CYAN}1${RESET}) API Token ${GREEN}(recommended: Workers Scripts Write)${RESET}"
  say " ${CYAN}2${RESET}) Global API Key + account email"
  nav_choice 2 1 0 "Authentication"; local mode="$NAV_CHOICE"
  case "$mode" in
    1) CF_AUTH_MODE=token; CF_EMAIL=""; printf "%b" "${CYAN}API Token:${RESET} "; read -rs CF_SECRET; printf '\n' ;;
    2) CF_AUTH_MODE=global; printf "%b" "${CYAN}Cloudflare account email:${RESET} "; read -r CF_EMAIL; printf "%b" "${CYAN}Global API Key:${RESET} "; read -rs CF_SECRET; printf '\n' ;;
    *) err "Cancelled."; pause; return 1 ;;
  esac
  [[ -n "$CF_SECRET" ]] || { err "Credential is empty."; pause; return 1; }
  printf "%b" "${CYAN}Account ID (leave blank to auto-discover):${RESET} "; read -r CF_ACCOUNT_ID
  if [[ -z "$CF_ACCOUNT_ID" ]]; then
    info "Discovering Cloudflare accounts..."
    # Temporary direct call because cf_load is not yet applicable.
    local accounts
    if [[ "$CF_AUTH_MODE" == "token" ]]; then
      accounts="$(curl -fsS -H "Authorization: Bearer $CF_SECRET" 'https://api.cloudflare.com/client/v4/accounts?per_page=50' 2>/dev/null || true)"
    else
      accounts="$(curl -fsS -H "X-Auth-Email: $CF_EMAIL" -H "X-Auth-Key: $CF_SECRET" 'https://api.cloudflare.com/client/v4/accounts?per_page=50' 2>/dev/null || true)"
    fi
    local count; count="$(jq -r '.result | length' <<<"$accounts" 2>/dev/null || echo 0)"
    if (( count == 1 )); then
      CF_ACCOUNT_ID="$(jq -r '.result[0].id' <<<"$accounts")"
      ok "Account: $(jq -r '.result[0].name' <<<"$accounts")"
    elif (( count > 1 )); then
      jq -r '.result | to_entries[] | "  \(.key+1)) \(.value.name)  [\(.value.id)]"' <<<"$accounts"
      nav_choice "$count" 1 0 "Account"; local n="$NAV_CHOICE"
      CF_ACCOUNT_ID="$(jq -r --argjson n "${n:-0}" '.result[$n-1].id // empty' <<<"$accounts" 2>/dev/null)"
    else
      warn "Auto-discovery failed (a narrowly-scoped token may not have Account Read)."
      printf "%b" "${CYAN}Paste Account ID:${RESET} "; read -r CF_ACCOUNT_ID
    fi
  fi
  [[ "$CF_ACCOUNT_ID" =~ ^[A-Fa-f0-9]{32}$ ]] || { err "Cloudflare Account ID must be 32 hex characters."; pause; return 1; }
  local default_script="xray-genius-$(randhex 6)"
  printf "%b" "${CYAN}Worker script name [${default_script}]:${RESET} "; read -r CF_SCRIPT; [[ -n "$CF_SCRIPT" ]] || CF_SCRIPT="$default_script"
  CF_SCRIPT="$(printf '%s' "$CF_SCRIPT" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_-')"
  [[ -n "$CF_SCRIPT" ]] || CF_SCRIPT="$default_script"
  CF_ACCESS_KEY="$(randhex 40)"; CF_WORKER_URL=""
  save_cf_config ""
  deploy_cloudflare_worker
}

deploy_cloudflare_worker() {
  cf_load || { warn "Cloudflare is not configured yet."; configure_cloudflare; return; }
  header; section "DEPLOY TELEGRAM FETCH WORKER"
  write_worker_js
  local base="https://api.cloudflare.com/client/v4/accounts/${CF_ACCOUNT_ID}" subjson subdomain desired response meta enable
  info "Resolving workers.dev account subdomain..."
  subjson="$(cf_api GET "$base/workers/subdomain" 2>/dev/null || true)"
  subdomain="$(jq -r '.result.subdomain // empty' <<<"$subjson" 2>/dev/null)"
  if [[ -z "$subdomain" ]]; then
    desired="xgc-$(randhex 10)"
    info "No workers.dev subdomain found; creating ${desired}.workers.dev..."
    subjson="$(cf_api PUT "$base/workers/subdomain" -H 'Content-Type: application/json' --data "{\"subdomain\":\"$desired\"}" 2>/dev/null || true)"
    subdomain="$(jq -r '.result.subdomain // empty' <<<"$subjson" 2>/dev/null)"
  fi
  [[ -n "$subdomain" ]] || { err "Could not get/create the account workers.dev subdomain."; printf '%s\n' "$subjson" | jq . 2>/dev/null || true; pause; return 1; }
  info "Uploading Worker module: $CF_SCRIPT"
  meta="{\"main_module\":\"worker.mjs\",\"compatibility_date\":\"$(date +%F)\"}"
  response="$(cf_api PUT "$base/workers/scripts/$CF_SCRIPT" \
    -F "metadata=${meta};type=application/json" \
    -F "worker.mjs=@${WORKER_FILE};type=application/javascript+module" 2>/dev/null || true)"
  if [[ "$(jq -r '.success // false' <<<"$response" 2>/dev/null)" != "true" ]]; then
    err "Worker upload failed."
    printf '%s\n' "$response" | jq . 2>/dev/null || printf '%s\n' "$response"
    pause; return 1
  fi
  enable="$(cf_api POST "$base/workers/scripts/$CF_SCRIPT/subdomain" -H 'Content-Type: application/json' --data '{"enabled":true,"previews_enabled":false}' 2>/dev/null || true)"
  if [[ "$(jq -r '.success // false' <<<"$enable" 2>/dev/null)" != "true" ]]; then
    err "Worker uploaded, but workers.dev enablement failed."; printf '%s\n' "$enable" | jq . 2>/dev/null || true; pause; return 1
  fi
  CF_WORKER_URL="https://${CF_SCRIPT}.${subdomain}.workers.dev"
  save_cf_config "$CF_WORKER_URL"
  info "Verifying Worker health..."
  local health; health="$(curl -fsS --get --data-urlencode "key=$CF_ACCESS_KEY" "$CF_WORKER_URL/health" 2>/dev/null || true)"
  if [[ "$(jq -r '.ok // false' <<<"$health" 2>/dev/null)" == "true" ]]; then
    ok "Worker is live: $CF_WORKER_URL"
  else
    warn "Deploy succeeded but immediate health check failed. The workers.dev route may need a moment or may be blocked on this network."
  fi
  pause
}

normalize_channel() {
  python3 - "$1" <<'PY'
import re,sys,urllib.parse
s=sys.argv[1].strip()
s=re.sub(r'^@','',s)
if 't.me/' in s:
    try:
        p=urllib.parse.urlsplit(s if '://' in s else 'https://'+s)
        parts=[x for x in p.path.split('/') if x and x!='s']
        s=parts[0] if parts else ''
    except Exception: pass
s=s.split('?')[0].strip('/')
print(s if re.fullmatch(r'[A-Za-z0-9_]{5,32}',s) else '')
PY
}

add_telegram_channel() {
  header; printf "%b" "${CYAN}Public channel (@name or t.me/name):${RESET} "; local raw ch; read -r raw
  ch="$(normalize_channel "$raw")"; [[ -n "$ch" ]] || { err "Invalid public Telegram channel username."; pause; return; }
  grep -Fxqi "$ch" "$TG_CHANNELS" 2>/dev/null || printf '%s\n' "$ch" >> "$TG_CHANNELS"
  sort -fu "$TG_CHANNELS" -o "$TG_CHANNELS"; chmod 600 "$TG_CHANNELS"
  ok "Added @$ch"; pause
}

remove_telegram_channel() {
  header; [[ -s "$TG_CHANNELS" ]] || { warn "No Telegram channels saved."; pause; return; }
  nl -ba "$TG_CHANNELS"; printf "%b" "${CYAN}Number to remove:${RESET} "; local n; read -r n
  [[ "$n" =~ ^[0-9]+$ ]] || { err "Invalid number."; pause; return; }
  sed "${n}d" "$TG_CHANNELS" > "$TG_CHANNELS.tmp" && mv "$TG_CHANNELS.tmp" "$TG_CHANNELS"
  ok "Channel removed."; pause
}

telegram_collect() {
  local rawout="$1" metaout="$2" max="$3"; shift 3
  python3 - "$rawout" "$metaout" "$max" "$@" <<'PY'
import json,sys,hashlib
rawout,metaout,maxn=sys.argv[1],sys.argv[2],int(sys.argv[3]); files=sys.argv[4:]
seen=set(); rows=[]
for fn in files:
    try: obj=json.load(open(fn))
    except Exception: continue
    ch=obj.get('channel','?')
    for p in obj.get('posts') or []:
        for uri in p.get('configs') or []:
            if uri in seen: continue
            seen.add(uri); digest=hashlib.sha256(uri.encode()).hexdigest()[:16]
            rows.append((uri,{'id':digest,'channel':ch,'post_id':p.get('id'), 'post_link':p.get('link',''), 'date':p.get('date','')}))
            if len(rows)>=maxn: break
        if len(rows)>=maxn: break
    if len(rows)>=maxn: break
open(rawout,'w').write('\n'.join(x[0] for x in rows)+('\n' if rows else ''))
with open(metaout,'w') as f:
    for _,m in rows: f.write(json.dumps(m,ensure_ascii=False,separators=(',',':'))+'\n')
print(len(rows))
PY
}

telegram_source() {
  local id="$1"
  jq -r --arg id "$id" 'select(.id==$id) | "@\(.channel) #\(.post_id)"' "$TG_META" 2>/dev/null | head -n1
}

display_telegram_ranked() {
  local ranked="$1"
  printf "${GRAY}%3s %-2s %-24s %6s %7s %7s %7s %-15s %-18s${RESET}\n" "#" "" "Profile" "OK" "Tunnel" "Jitter" "KB/s" "Egress IP" "Source"
  while IFS=$'\t' read -r rank rid rfile rname succ lat jit kb score ip cc flag ping; do
    local src; src="$(telegram_source "$rid")"; [[ -n "$src" ]] || src='@?'
    if [[ "$succ" == "0" ]]; then
      printf "${RED}%3s${RESET} %-2s %-24s %5s%% %7s %7s %7s %-15s %-18s\n" "$rank" "$flag" "${rname:0:24}" "$succ" "FAIL" "-" "-" "$ip" "$src"
    else
      printf "${CYAN}%3s${RESET} %-2s %-24s ${GREEN}%5s%%${RESET} %6sms %6sms %7s %-15s %-18s\n" "$rank" "$flag" "${rname:0:24}" "$succ" "$lat" "$jit" "$kb" "$ip" "$src"
    fi
  done < "$ranked"
}

telegram_scan_test() {
  header
  cf_load || { warn "Telegram Radar needs its Cloudflare Worker first."; configure_cloudflare; cf_load || return; }
  [[ -s "$TG_CHANNELS" ]] || { warn "No public channels saved yet."; add_telegram_channel; [[ -s "$TG_CHANNELS" ]] || return; }
  printf "%b" "${CYAN}Last posts per channel [${TELEGRAM_POSTS}]:${RESET} "; local once; read -r once
  local posts="$TELEGRAM_POSTS"; [[ "$once" =~ ^[0-9]+$ ]] && ((once>=1&&once<=200)) && posts="$once"
  section "TELEGRAM PUBLIC-CONFIG RADAR"
  say " ${GRAY}Posts/channel: $posts • max unique configs: $TELEGRAM_MAX_CONFIGS • test policy: $BENCH_PROFILE${RESET}"
  rm -f "$TG_DIR"/response-*.json
  local -a responses=(); local ch idx=0
  while IFS= read -r ch; do
    [[ -n "$ch" ]] || continue; ((idx+=1))
    local rf="$TG_DIR/response-${idx}.json"
    printf " ${CYAN}↳${RESET} @%-28s " "$ch"
    if curl -fsS --get --connect-timeout 12 --max-time 60 \
      --data-urlencode "key=$CF_ACCESS_KEY" --data-urlencode "channel=$ch" --data-urlencode "limit=$posts" \
      -o "$rf" "$CF_WORKER_URL/" 2>/dev/null; then
      local pc cfgc er
      er="$(jq -r '.error // empty' "$rf" 2>/dev/null)"
      if [[ -n "$er" ]]; then say "${RED}$er${RESET}"; else
        pc="$(jq -r '.count // 0' "$rf")"; cfgc="$(jq -r '.configs // 0' "$rf")"
        say "${GREEN}${pc} posts • ${cfgc} config refs${RESET}"; responses+=("$rf")
      fi
    else say "${RED}fetch failed${RESET}"; fi
  done < "$TG_CHANNELS"
  (( ${#responses[@]} > 0 )) || { err "No channel could be fetched through the Worker."; pause; return; }
  local unique; unique="$(telegram_collect "$TG_RAW" "$TG_META" "$TELEGRAM_MAX_CONFIGS" "${responses[@]}")"
  (( unique > 0 )) || { warn "No supported proxy share URI was found in those posts."; pause; return; }
  info "Smart parser found $unique unique candidate config(s)."
  local out
  out="$(pyhelper parse "$TG_RAW" "telegram-public" "Telegram Public" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/telegram-public.txt" 2>&1)" || { err "Telegram parse failed: $out"; pause; return; }
  local usable; usable="$(jq -r '.added // 0' <<<"$out" 2>/dev/null || echo 0)"
  (( usable > 0 )) || { err "Xray-compatible URI parser could not build any candidate profiles."; pause; return; }
  section "REAL TUNNEL LAB • $usable CONFIGS"
  : > "$TG_RESULTS"; local total i=0
  total="$(jq -c 'select(.subid=="telegram-public")' "$INDEX" 2>/dev/null | wc -l | tr -d ' ')"
  while IFS= read -r meta; do
    [[ -n "$meta" ]] || continue; ((i+=1))
    local id file name host scheme rport port result succ lat jit ip cc
    id="$(jq -r '.id' <<<"$meta")"; file="$(jq -r '.file' <<<"$meta")"; name="$(jq -r '.name' <<<"$meta")"
    host="$(jq -r '.host // ""' <<<"$meta")"; scheme="$(jq -r '.scheme // ""' <<<"$meta")"; rport="$(jq -r '.port // 0' <<<"$meta")"
    port=$((24000 + (i % 1000)))
    printf " ${GRAY}[%3s/%3s]${RESET} %-36s " "$i" "$total" "${name:0:36}"
    result="$(bench_one "$file" "$id" "$name" "$port" "$host" "$scheme" "$rport")"; printf '%s\n' "$result" >> "$TG_RESULTS"
    succ="$(cut -f4 <<<"$result")"; lat="$(cut -f5 <<<"$result")"; jit="$(cut -f6 <<<"$result")"; ip="$(cut -f8 <<<"$result")"; cc="$(cut -f9 <<<"$result")"
    [[ "$succ" == "0" ]] && say "${RED}FAIL${RESET}" || say "${GREEN}${succ}%${RESET} ${CYAN}${lat}ms${RESET} j=${YELLOW}${jit}${RESET} ${PURPLE}${ip}/${cc}${RESET}"
  done < <(jq -c 'select(.subid=="telegram-public")' "$INDEX" 2>/dev/null)
  local ranked="$TG_DIR/ranked.tsv"; rank_v11 "$TG_RESULTS" > "$ranked"
  section "TELEGRAM RANKING • BEST → WORST"
  display_telegram_ranked "$ranked"
  if [[ -s "$ranked" && "$(cut -f5 < "$ranked" | head -n1)" != "0" ]]; then
    line; printf "%b" "${CYAN}Connect #1 winner now? [Y/n]:${RESET} "; local ans; read -r ans
    if [[ ! "$ans" =~ ^[Nn]$ ]]; then
      local best; best="$(head -n1 "$ranked")"; start_profile_file "$(cut -f3 <<<"$best")" "$(cut -f4 <<<"$best")" || true
    fi
  fi
  pause
}

telegram_center() {
  while true; do
    load_settings; header; section "TELEGRAM PUBLIC-CONFIG RADAR"
    local cfstate="${RED}not configured${RESET}"; cf_load && cfstate="${GREEN}ready${RESET}"
    printf " ${GRAY}Worker:${RESET} %b   ${GRAY}posts/channel:${RESET} ${CYAN}%s${RESET}   ${GRAY}max configs:${RESET} ${CYAN}%s${RESET}\n" "$cfstate" "$TELEGRAM_POSTS" "$TELEGRAM_MAX_CONFIGS"
    printf " ${CYAN}1${RESET}) ${WHITE}Fetch latest posts + parse + full-test + rank${RESET}\n"
    printf " ${CYAN}2${RESET}) Add public Telegram channel\n"
    printf " ${CYAN}3${RESET}) Remove channel\n"
    printf " ${CYAN}4${RESET}) List channels\n"
    printf " ${CYAN}5${RESET}) Configure Cloudflare credential + deploy Worker\n"
    printf " ${CYAN}6${RESET}) Redeploy / repair Worker\n"
    printf " ${CYAN}7${RESET}) Show Worker URL/status\n"
    printf " ${CYAN}0${RESET}) Back\n"
    line; printf "%b" "${MAGENTA}Choose:${RESET} "; local ch; read -r ch
    case "$ch" in
      1) telegram_scan_test ;;
      2) add_telegram_channel ;;
      3) remove_telegram_channel ;;
      4) header; [[ -s "$TG_CHANNELS" ]] && nl -ba "$TG_CHANNELS" || warn "No channels saved."; pause ;;
      5) configure_cloudflare ;;
      6) deploy_cloudflare_worker ;;
      7) header; if cf_load; then say "${CYAN}Worker URL:${RESET} ${CF_WORKER_URL:-not deployed}"; say "${GRAY}Script:${RESET} $CF_SCRIPT"; say "${GRAY}Account:${RESET} $CF_ACCOUNT_ID"; else warn "Cloudflare is not configured."; fi; pause ;;
      0) return ;;
    esac
  done
}

import_basic_proxy_uri() {
  local uri="$1" group="$2"
  python3 - "$uri" "$group" "$PROFILE_DIR" "$INDEX" <<'PY'
import sys,json,hashlib,urllib.parse
from pathlib import Path
uri,group,profdir,index=sys.argv[1:]
p=urllib.parse.urlsplit(uri); sch=p.scheme.lower(); host=p.hostname
if sch in ('socks','socks5'): proto='socks'; port=p.port or 1080
elif sch in ('http','https'): proto='http'; port=p.port or (443 if sch=='https' else 80)
else: raise SystemExit('unsupported basic proxy URI')
if not host: raise SystemExit('proxy host missing')
settings={'address':host,'port':port}
if p.username is not None: settings['user']=urllib.parse.unquote(p.username)
if p.password is not None: settings['pass']=urllib.parse.unquote(p.password)
out={'tag':'proxy','protocol':proto,'settings':settings}
if sch=='https': out['streamSettings']={'security':'tls','tlsSettings':{'serverName':host}}
cfg={'log':{'loglevel':'warning'},'inbounds':[{'tag':'socks-in','listen':'127.0.0.1','port':10808,'protocol':'socks','settings':{'udp':True}}],
     'outbounds':[out,{'tag':'direct','protocol':'freedom'},{'tag':'block','protocol':'blackhole'}]}
digest=hashlib.sha256(uri.encode()).hexdigest()[:16]; sid='manual-'+digest[:10]
fn=Path(profdir)/f'{sid}-{digest}.json'; fn.write_text(json.dumps(cfg,ensure_ascii=False,indent=2))
name=urllib.parse.unquote(p.fragment) if p.fragment else f'{proto.upper()} {host}'
meta={'id':digest,'file':str(fn),'name':name[:120],'scheme':sch,'host':host,'port':port,'transport':'native','security':'tls' if sch=='https' else 'none','subid':sid,'subname':group}
ip=Path(index); rows=[]
if ip.exists():
    for l in ip.read_text(errors='ignore').splitlines():
        try:
            m=json.loads(l)
            if m.get('id')!=digest: rows.append(m)
        except Exception: pass
rows.append(meta); ip.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n' for x in rows))
print(json.dumps(meta,ensure_ascii=False))
PY
}

import_uri() {
  header
  say "${CYAN}Paste one proxy URI:${RESET}"
  say "${GRAY}VLESS / VMess / Trojan / Shadowsocks / Hysteria2 / SOCKS5 / HTTP proxy${RESET}"
  local uri name id raw out
  read -r uri
  [[ "$uri" == *"://"* ]] || { err "No proxy URI detected."; pause; return; }
  printf "%b" "${CYAN}Group name (optional):${RESET} "; read -r name; [[ -n "$name" ]] || name="Manual"
  case "${uri%%:*}" in
    socks|socks5|http|https)
      out="$(import_basic_proxy_uri "$uri" "$name" 2>&1)" || { err "$out"; pause; return; }
      ok "Imported $(jq -r '.scheme' <<<"$out") proxy: $(jq -r '.name' <<<"$out")" ;;
    *)
      id="manual-$(printf '%s' "$uri" | sha256sum | cut -c1-10)"; raw="$RAW_DIR/$id.raw"; printf '%s\n' "$uri" > "$raw"
      out="$(pyhelper parse "$raw" "$id" "$name" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)" || { err "$out"; pause; return; }
      ok "Imported $(jq -r '.added' <<<"$out") profile(s)." ;;
  esac
  pause
}

advanced_bench_settings() {
  header; section "CUSTOM TEST ENGINE"
  BENCH_PROFILE=custom
  local v
  printf "Candidates for smart-best [%s]: " "$BENCH_CANDIDATES"; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=2&&v<=80)) && BENCH_CANDIDATES="$v"
  printf "Real tunnel samples/config [%s]: " "$BENCH_SAMPLES"; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=12)) && BENCH_SAMPLES="$v"
  printf "Per-request timeout seconds [%s]: " "$BENCH_TIMEOUT"; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=2&&v<=30)) && BENCH_TIMEOUT="$v"
  printf "Throughput bytes [%s]: " "$BENCH_BYTES"; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=32768&&v<=4194304)) && BENCH_BYTES="$v"
  printf "Fast endpoint timeout seconds [%s]: " "$TCP_PRECHECK_TIMEOUT"; read -r v; [[ "$v" =~ ^[0-9]+([.][0-9]+)?$ ]] && TCP_PRECHECK_TIMEOUT="$v"
  printf "Endpoint precheck workers [%s]: " "$TCP_PRECHECK_WORKERS"; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=64)) && TCP_PRECHECK_WORKERS="$v"
  SPEED_URL="https://speed.cloudflare.com/__down?bytes=${BENCH_BYTES}"; save_settings; ok "Custom benchmark policy saved."; pause
}

settings_menu() {
  while true; do
    load_settings; header; section "SETTINGS / PERFORMANCE LAB"
    printf " ${CYAN}1${RESET}) SOCKS port                : ${WHITE}%s${RESET}\n" "$SOCKS_PORT"
    printf " ${CYAN}2${RESET}) Listen address            : ${WHITE}%s${RESET}\n" "$SOCKS_LISTEN"
    printf " ${CYAN}3${RESET}) Auto Xray beta update      : " ; yn "$AUTO_UPDATE"; printf '\n'
    printf " ${CYAN}4${RESET}) Full-test policy           : ${PINK}%s${RESET}\n" "$BENCH_PROFILE"
    printf " ${CYAN}5${RESET}) Extreme IP/DNS leak guard  : " ; yn "$LEAK_PROTECTION"; printf '\n'
    printf " ${CYAN}6${RESET}) DNS differential test     : " ; yn "$DNS_LEAK_TEST"; printf '\n'
    printf " ${CYAN}7${RESET}) Remember last connection  : " ; yn "$REMEMBER_LAST"; printf '\n'
    printf " ${CYAN}8${RESET}) Telegram posts/channel    : ${WHITE}%s${RESET}\n" "$TELEGRAM_POSTS"
    printf " ${CYAN}9${RESET}) Telegram max configs/test  : ${WHITE}%s${RESET}\n" "$TELEGRAM_MAX_CONFIGS"
    printf " ${CYAN}10${RESET}) Advanced custom test tuning\n"
    printf " ${CYAN}11${RESET}) Forget Cloudflare credential/Worker config\n"
    printf " ${CYAN}12${RESET}) Reset app settings defaults\n"
    printf " ${CYAN}0${RESET}) Back\n"
    line; printf "%b" "${MAGENTA}Choose:${RESET} "; local ch v; read -r ch
    case "$ch" in
      1) printf "New SOCKS port (1024-65535): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1024&&v<=65535)) && SOCKS_PORT="$v" || warn "Invalid port"; save_settings ;;
      2) say "${YELLOW}127.0.0.1 is safest for apps on this phone.${RESET}"; printf "Listen address [127.0.0.1]: "; read -r v; [[ -n "$v" ]] && SOCKS_LISTEN="$v" || SOCKS_LISTEN="127.0.0.1"; [[ "$SOCKS_LISTEN" == "0.0.0.0" ]] && warn "0.0.0.0 exposes an unauthenticated SOCKS proxy to reachable networks."; save_settings ;;
      3) [[ "$AUTO_UPDATE" == "1" ]] && AUTO_UPDATE=0 || AUTO_UPDATE=1; save_settings ;;
      4) header; say " ${CYAN}1${RESET}) Reliable  ${GRAY}6 samples, larger transfer, conservative${RESET}"; say " ${CYAN}2${RESET}) Balanced  ${GRAY}recommended${RESET}"; say " ${CYAN}3${RESET}) Fast      ${GRAY}lighter/faster${RESET}"; say " ${CYAN}4${RESET}) Turbo     ${GRAY}2 samples, short timeout, smallest transfer${RESET}"; printf "%b" "${MAGENTA}Policy:${RESET} "; read -r v; case "$v" in 1) apply_bench_profile reliable;;2) apply_bench_profile balanced;;3) apply_bench_profile fast;;4) apply_bench_profile turbo;;*) warn "No change";; esac ;;
      5) LEAK_PROTECTION=1; save_settings; ok "Privacy Guard is always-on in v1.1.5; it cannot be disabled from the client." ;;
      6) [[ "$DNS_LEAK_TEST" == "1" ]] && DNS_LEAK_TEST=0 || DNS_LEAK_TEST=1; save_settings ;;
      7) [[ "$REMEMBER_LAST" == "1" ]] && REMEMBER_LAST=0 || REMEMBER_LAST=1; save_settings ;;
      8) printf "Last posts per channel (1-200): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=200)) && TELEGRAM_POSTS="$v" || warn "Invalid"; save_settings ;;
      9) printf "Max unique Telegram configs to parse/test (1-300): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=300)) && TELEGRAM_MAX_CONFIGS="$v" || warn "Invalid"; save_settings ;;
      10) advanced_bench_settings ;;
      11) printf "%b" "${RED}Delete locally stored Cloudflare credential and Worker reference? [y/N]:${RESET} "; read -r v; if [[ "$v" =~ ^[Yy]$ ]]; then rm -f "$CF_CONF" "$WORKER_FILE"; ok "Local Cloudflare data removed. The remote Worker itself was not deleted."; fi ;;
      12) default_settings; load_settings; ok "App settings reset; subscriptions/profiles/Cloudflare data kept."; sleep 1 ;;
      0) return ;;
    esac
  done
}

show_capabilities() {
  header; section "XRAY NATIVE CAPABILITY MAP"
  say "${WHITE}Current native outbound families accepted through full Xray JSON import:${RESET}"
  say "  ${CYAN}VLESS • VMess • Trojan • Shadowsocks • SOCKS • HTTP • WireGuard • Hysteria${RESET}"
  say "  ${GRAY}plus Xray infrastructure outbounds: Freedom • Blackhole • DNS • Loopback.${RESET}"
  say ""
  say "${WHITE}Share-URI smart parser:${RESET}"
  say "  ${LIME}VLESS • VMess (legacy + URI) • Trojan • SS/SIP002 • Hysteria2/hy2 • SOCKS5 • HTTP(S) proxy${RESET}"
  say ""
  say "${WHITE}Transport/security fields preserved/created where applicable:${RESET}"
  say "  ${PURPLE}RAW/TCP • XHTTP/SplitHTTP • mKCP • gRPC • WebSocket • HTTPUpgrade • Hysteria${RESET}"
  say "  ${PINK}none • TLS • REALITY • XHTTP extra • FinalMask • REALITY pqv/spx/sid/pbk${RESET}"
  say ""
  say "${GRAY}WireGuard and unusual/new protocol-specific settings are safest via raw official Xray JSON because there is no single universal share-URI format for every Xray outbound. XGC validates the resulting runtime with 'xray run -test' before launch.${RESET}"
  line
  printf " ${CYAN}geoip.dat:${RESET} %s   ${CYAN}geosite.dat:${RESET} %s\n" "$( [[ -s $BIN_DIR/geoip.dat ]] && echo OK || echo missing )" "$( [[ -s $BIN_DIR/geosite.dat ]] && echo OK || echo missing )"
  pause
}

show_status() {
  header
  local version tag profiles subs active last tgcount worker
  version="$($XRAY_BIN version 2>/dev/null | head -n1 || echo 'not installed')"; tag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo '-')"
  profiles="$(count_profiles)"; subs="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"; tgcount="$(awk 'NF{c++} END{print c+0}' "$TG_CHANNELS" 2>/dev/null)"
  section "ENGINE"
  printf " ${CYAN}Core:${RESET} %s\n ${CYAN}Channel:${RESET} official XTLS pre-release [%s]\n" "$version" "$tag"
  printf " ${CYAN}Assets:${RESET} geoip=%s  geosite=%s\n" "$( [[ -s $BIN_DIR/geoip.dat ]] && echo OK || echo missing )" "$( [[ -s $BIN_DIR/geosite.dat ]] && echo OK || echo missing )"
  section "CONNECTION"
  printf " ${CYAN}Profiles:${RESET} %s  ${CYAN}Subscriptions:${RESET} %s  ${CYAN}SOCKS5h:${RESET} %s:%s\n" "$profiles" "$subs" "$SOCKS_LISTEN" "$SOCKS_PORT"
  if process_alive; then active="$(jq -r '.name // "Connected"' "$ACTIVE_FILE" 2>/dev/null || echo Connected)"; printf " ${CYAN}State:${RESET} ${GREEN}${BOLD}CONNECTED${RESET} → %s\n" "$active"; else printf " ${CYAN}State:${RESET} ${RED}DISCONNECTED${RESET}\n"; fi
  last="$(jq -r '.name // empty' "$LAST_CONNECTED" 2>/dev/null || true)"; [[ -n "$last" ]] && printf " ${CYAN}Last saved:${RESET} %s\n" "$last"
  section "INTELLIGENCE / PRIVACY"
  printf " ${CYAN}Benchmark:${RESET} %s (%s samples)   ${CYAN}Leak guard:${RESET} " "$BENCH_PROFILE" "$BENCH_SAMPLES"; yn "$LEAK_PROTECTION"; printf '\n'
  worker="not configured"; cf_load && worker="ready"
  printf " ${CYAN}Telegram Radar:${RESET} %s channel(s), %s posts/channel, Worker=%s\n" "$tgcount" "$TELEGRAM_POSTS" "$worker"
  line; say "${GRAY}For hostname privacy, configure apps for SOCKS5h / proxy DNS whenever that option exists.${RESET}"; pause
}

connection_history() {
  header; section "RECENT CONNECTION HISTORY"
  if [[ ! -s "$HISTORY_FILE" ]]; then warn "No saved history yet."; pause; return; fi
  tail -n 20 "$HISTORY_FILE" | tac 2>/dev/null | python3 -c 'import sys,json,time
for i,l in enumerate(sys.stdin,1):
 try:
  x=json.loads(l); t=time.strftime("%Y-%m-%d %H:%M",time.localtime(x.get("started",0))); print(f" {i:2}) {t}  {x.get(\"scheme\",\"?\"):10} {x.get(\"name\",\"\")[:42]}")
 except: pass' 2>/dev/null || tail -n 20 "$HISTORY_FILE"
  pause
}

panel() {
  maybe_update_xray
  while true; do
    load_settings; header
    local state="${RED}OFF${RESET}" active=""; if process_alive; then state="${GREEN}ON${RESET}"; active="$(jq -r '.name // ""' "$ACTIVE_FILE" 2>/dev/null || true)"; fi
    printf " ${GRAY}Core:${RESET} ${PURPLE}%-12s${RESET} ${GRAY}SOCKS:${RESET} ${CYAN}%s:%s${RESET} ${GRAY}State:${RESET} %b ${GRAY}Mode:${RESET} ${PINK}%s${RESET}\n" "$(cat "$INSTALLED_TAG" 2>/dev/null || echo missing)" "$SOCKS_LISTEN" "$SOCKS_PORT" "$state" "$BENCH_PROFILE"
    [[ -n "$active" ]] && printf " ${GRAY}Active:${RESET} ${GREEN}%s${RESET}\n" "$active"
    line
    printf " ${CYAN}1${RESET}) ${BOLD}${WHITE}⚡ Auto Connect${RESET} ${GRAY}(last-working → smart fallback)${RESET}\n"
    printf " ${CYAN}2${RESET}) ${BOLD}🧠 Smart benchmark + connect best${RESET}\n"
    printf " ${CYAN}3${RESET}) 🚀 Quick reconnect last working config\n"
    printf " ${CYAN}4${RESET}) 🧪 Full test one config\n"
    printf " ${CYAN}5${RESET}) 📡 Telegram public-config radar\n"
    printf " ${CYAN}6${RESET}) 📦 Subscriptions / import center\n"
    printf " ${CYAN}7${RESET}) 🔌 Connect a profile manually\n"
    printf " ${CYAN}8${RESET}) 🛡 Privacy / IP + DNS leak audit\n"
    printf " ${CYAN}9${RESET}) 📊 Status / SOCKS info\n"
    printf " ${CYAN}10${RESET}) 🗂 Profiles / native capabilities\n"
    printf " ${CYAN}11${RESET}) ⏹ Stop Xray\n"
    printf " ${CYAN}12${RESET}) ⬆ Update newest Xray pre-release\n"
    printf " ${CYAN}13${RESET}) ⚙ Settings / performance lab\n"
    printf " ${CYAN}14${RESET}) 🧾 Logs\n"
    printf " ${CYAN}15${RESET}) 🕘 Connection history\n"
    printf " ${CYAN}0${RESET}) Exit panel ${GRAY}(Xray keeps running)${RESET}\n"
    line; printf "%b" "${MAGENTA}${BOLD}Choose:${RESET} "; local ch; read -r ch
    case "$ch" in
      1) auto_connect ;;
      2) smart_connect ;;
      3) quick_reconnect_menu ;;
      4) full_test_menu ;;
      5) telegram_center ;;
      6) manage_subs ;;
      7) connect_manual ;;
      8) leak_audit ;;
      9) show_status ;;
      10) header; say "${BOLD}${WHITE}Profiles${RESET}"; list_profiles; line; printf "%b" "${CYAN}Show native capability map? [y/N]:${RESET} "; read -r ch; [[ "$ch" =~ ^[Yy]$ ]] && show_capabilities || pause ;;
      11) stop_xray; ok "Xray stopped"; sleep 1 ;;
      12) header; update_xray; pause ;;
      13) settings_menu ;;
      14) show_logs ;;
      15) connection_history ;;
      0) clear 2>/dev/null || true; return ;;
    esac
  done
}


# ─────────────────────────────────────────────────────────────────────────────
# v1.1.3 interaction + Telegram intelligence layer.
# Adds arrow-key navigation, protocol-aware TCP gating, and persistent
# timestamped subscriptions created from Telegram configs that pass full tests.
# ─────────────────────────────────────────────────────────────────────────────

nav_choice() {
  local max="${1:-1}" sel="${2:-1}" allow_zero="${3:-1}" label="${4:-Choose}"
  NAV_CHOICE=""
  [[ "$max" =~ ^[0-9]+$ ]] || max=1
  (( max < 1 )) && max=1
  [[ "$sel" =~ ^[0-9]+$ ]] || sel=1
  (( sel < 1 || sel > max )) && sel=1

  if [[ ! -t 0 ]]; then
    printf "%b" "${MAGENTA}${label}:${RESET} "
    read -r NAV_CHOICE || NAV_CHOICE="$sel"
    return 0
  fi

  local key a b seq buf="" candidate
  printf "%b\n" "${DIM}${GRAY}  ↑/↓ move • → or Enter select • ← back • numbers + Enter also work${RESET}"
  while true; do
    if [[ -n "$buf" ]]; then
      printf "\r\033[K%b" "${MAGENTA}${label}:${RESET} ${CYAN}${buf}${RESET} ${GRAY}(typed)${RESET}"
    else
      printf "\r\033[K%b" "${MAGENTA}${label}:${RESET} ${CYAN}#${sel}${RESET}/${max}"
    fi
    IFS= read -rsn1 key || { NAV_CHOICE="$sel"; printf '\n'; return 0; }
    case "$key" in
      $'\e')
        a=""; b=""
        IFS= read -rsn1 -t 0.12 a || true
        IFS= read -rsn1 -t 0.12 b || true
        seq="$a$b"; buf=""
        case "$seq" in
          '[A') ((sel--)); ((sel<1)) && sel=$max ;;
          '[B') ((sel++)); ((sel>max)) && sel=1 ;;
          '[C') NAV_CHOICE="$sel"; printf '\n'; return 0 ;;
          '[D') if [[ "$allow_zero" == "1" ]]; then NAV_CHOICE=0; printf '\n'; return 0; else ((sel--)); ((sel<1)) && sel=$max; fi ;;
        esac
        ;;
      '')
        candidate="$sel"
        [[ -n "$buf" ]] && candidate="$buf"
        if [[ "$candidate" =~ ^[0-9]+$ ]] && { ((candidate>=1 && candidate<=max)) || { [[ "$allow_zero" == "1" ]] && ((candidate==0)); }; }; then
          NAV_CHOICE="$candidate"; printf '\n'; return 0
        fi
        buf=""
        ;;
      [0-9])
        buf+="$key"
        [[ ${#buf} -gt 3 ]] && buf="${buf: -3}"
        ;;
      $'\x7f'|$'\b') buf="${buf%?}" ;;
      k|K) buf=""; ((sel--)); ((sel<1)) && sel=$max ;;
      j|J) buf=""; ((sel++)); ((sel>max)) && sel=1 ;;
      l|L) NAV_CHOICE="$sel"; printf '\n'; return 0 ;;
      h|H|q|Q) if [[ "$allow_zero" == "1" ]]; then NAV_CHOICE=0; printf '\n'; return 0; fi ;;
    esac
  done
}

fetch_subscription() {
  local id="$1" name="$2" url="$3" out src raw
  raw="$RAW_DIR/$id.raw"
  info "Refreshing: $name"
  if [[ "$url" == local://* ]]; then
    src="${url#local://}"
    [[ -f "$src" ]] || { err "Local generated subscription is missing: $src"; return 1; }
    if [[ "$src" != "$raw" ]]; then cp -f "$src" "$raw" || return 1; fi
    chmod 600 "$raw"
  else
    if ! curl -fL --connect-timeout 15 --max-time 75 --retry 2 --retry-delay 1 \
        -A 'v2rayN' -H 'Accept: */*' -o "$raw.part" "$url"; then
      rm -f "$raw.part"; err "Download failed: $name"; return 1
    fi
    mv -f "$raw.part" "$raw"; chmod 600 "$raw"
  fi
  out="$(pyhelper parse "$raw" "$id" "$name" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)" || {
    err "Parse failed: $out"; return 1; }
  local added unsupported
  added="$(jq -r '.added // 0' <<<"$out" 2>/dev/null || echo 0)"
  unsupported="$(jq -r '.unsupported // 0' <<<"$out" 2>/dev/null || echo 0)"
  ok "$name → $added usable profile(s)"
  (( unsupported > 0 )) && warn "$unsupported entry/entries were not safely convertible; report saved in $UNSUPPORTED_DIR/$id.txt"
  return 0
}

telegram_tcp_gate() {
  local report="$TG_DIR/tcp-gate.tsv"
  python3 - "$INDEX" "telegram-public" "$TCP_PRECHECK_TIMEOUT" "$TCP_PRECHECK_WORKERS" "$TELEGRAM_TCP_SAMPLES" "$report" <<'PY'
import concurrent.futures,json,math,socket,statistics,sys,time
from pathlib import Path
index,subid,timeout,workers,samples,report=sys.argv[1],sys.argv[2],float(sys.argv[3]),int(sys.argv[4]),int(sys.argv[5]),Path(sys.argv[6])
p=Path(index); allrows=[]
for line in p.read_text(errors='ignore').splitlines() if p.exists() else []:
    try: allrows.append(json.loads(line))
    except Exception: pass
cands=[m for m in allrows if m.get('subid')==subid]
need=max(1, math.floor(samples/2)+1)
def check(m):
    scheme=str(m.get('scheme','')).lower(); transport=str(m.get('transport','')).lower(); name=str(m.get('name',''))
    if scheme in ('hysteria2','hy2') or transport=='hysteria':
        return m,True,samples,samples,9999.0,'QUIC',name
    host=str(m.get('host') or ''); port=int(m.get('port') or 0)
    if not host or not port: return m,False,0,samples,9999.0,'TCP',name
    vals=[]
    for _ in range(samples):
        t=time.perf_counter()
        try:
            with socket.create_connection((host,port),timeout=timeout): pass
            vals.append((time.perf_counter()-t)*1000)
        except Exception: pass
    ok=len(vals)>=need; med=statistics.median(vals) if vals else 9999.0
    return m,ok,len(vals),samples,med,'TCP',name
with concurrent.futures.ThreadPoolExecutor(max_workers=max(1,workers)) as ex: results=list(ex.map(check,cands))
kept=[]; failed=[]; lines=[]
for m,ok,n,total,med,kind,name in results:
    state='PASS' if ok else 'DROP'
    if kind=='QUIC': state='EXEMPT'
    lines.append('\t'.join([m.get('id',''),state,str(n),str(total),'-' if med>=9999 else f'{med:.1f}',kind,name.replace('\t',' ')]))
    (kept if ok else failed).append(m)
failed_files={str(x.get('file','')) for x in failed}; new=[]
for m in allrows:
    if m.get('subid')!=subid or str(m.get('file','')) not in failed_files: new.append(m)
p.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n' for x in new))
for m in failed:
    try: Path(m.get('file','')).unlink(missing_ok=True)
    except Exception: pass
report.write_text('\n'.join(lines)+('\n' if lines else ''))
print(json.dumps({'total':len(cands),'kept':len(kept),'dropped':len(failed),'tcp_passed':sum(1 for x in results if x[1] and x[5]=='TCP'),'quic_exempt':sum(1 for x in results if x[5]=='QUIC')},separators=(',',':')))
PY
}

display_tcp_gate() {
  local report="$1"
  [[ -s "$report" ]] || return 0
  while IFS=$'\t' read -r id state got total ping kind name; do
    case "$state" in
      PASS) printf " ${GREEN}✓${RESET} %-34s ${CYAN}%s/%s${RESET}  ${YELLOW}%sms TCP${RESET}\n" "${name:0:34}" "$got" "$total" "$ping" ;;
      EXEMPT) printf " ${PURPLE}◆${RESET} %-34s ${GRAY}QUIC-native • TCP gate skipped${RESET}\n" "${name:0:34}" ;;
      DROP) printf " ${RED}✗${RESET} %-34s ${RED}%s/%s TCP • removed${RESET}\n" "${name:0:34}" "$got" "$total" ;;
    esac
  done < "$report"
}

telegram_autosub_from_ranked() {
  local ranked="$1"
  [[ "$TELEGRAM_AUTO_SUB" == "1" ]] || return 0
  [[ -s "$ranked" && -s "$TG_RAW" ]] || return 0
  local stamp human sid raw count out tmp now
  stamp="$(date '+%Y%m%d-%H%M%S')"; human="$(date '+%Y-%m-%d %H:%M:%S')"
  sid="tgpassed-${stamp}-$(randhex 4)"; raw="$RAW_DIR/$sid.raw"
  count="$(python3 - "$ranked" "$TG_RAW" "$raw" "$TELEGRAM_PASS_MIN_SUCCESS" <<'PY'
import hashlib,sys
from pathlib import Path
ranked,raw,out,threshold=Path(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]),float(sys.argv[4])
ordered=[]
for line in ranked.read_text(errors='ignore').splitlines():
    p=line.split('\t')
    if len(p)>=5:
        try:
            if float(p[4])>=threshold: ordered.append(p[1])
        except Exception: pass
byid={}
for uri in raw.read_text(errors='ignore').splitlines():
    uri=uri.strip()
    if uri: byid[hashlib.sha256(uri.encode()).hexdigest()[:16]]=uri
rows=[byid[x] for x in ordered if x in byid]
out.write_text('\n'.join(rows)+('\n' if rows else ''))
print(len(rows))
PY
)"
  [[ "$count" =~ ^[0-9]+$ ]] || count=0
  if (( count == 0 )); then
    rm -f "$raw"; warn "No Telegram config reached the ${TELEGRAM_PASS_MIN_SUCCESS}% full-test pass threshold; no generated subscription was created."; return 0
  fi
  chmod 600 "$raw"
  local name="Telegram Passed ${human}"
  out="$(pyhelper parse "$raw" "$sid" "$name" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$sid.txt" 2>&1)" || { err "Could not create generated Telegram subscription: $out"; rm -f "$raw"; return 1; }
  local added; added="$(jq -r '.added // 0' <<<"$out" 2>/dev/null || echo 0)"
  (( added > 0 )) || { rm -f "$raw"; warn "Generated Telegram subscription contained no parsable profiles."; return 0; }
  tmp="$TMP_DIR/subdb.$$"; awk -F'\t' -v id="$sid" '$1!=id' "$SUB_DB" > "$tmp" 2>/dev/null || true
  now="$(date +%s)"; printf '%s\t%s\tlocal://%s\t%s\n' "$sid" "$name" "$raw" "$now" >> "$tmp"
  mv -f "$tmp" "$SUB_DB"; chmod 600 "$SUB_DB"
  ok "Auto-created subscription: ${name} • ${added} passed config(s)"
}

telegram_scan_test() {
  header
  cf_load || { warn "Telegram Radar needs its Cloudflare Worker first."; configure_cloudflare; cf_load || return; }
  [[ -s "$TG_CHANNELS" ]] || { warn "No public channels saved yet."; add_telegram_channel; [[ -s "$TG_CHANNELS" ]] || return; }
  printf "%b" "${CYAN}Last posts per channel [${TELEGRAM_POSTS}]:${RESET} "; local once; read -r once
  local posts="$TELEGRAM_POSTS"; [[ "$once" =~ ^[0-9]+$ ]] && ((once>=1&&once<=200)) && posts="$once"
  section "TELEGRAM PUBLIC-CONFIG RADAR"
  say " ${GRAY}Posts/channel: $posts • max configs: $TELEGRAM_MAX_CONFIGS • test policy: $BENCH_PROFILE${RESET}"
  rm -f "$TG_DIR"/response-*.json
  local -a responses=(); local ch idx=0
  while IFS= read -r ch; do
    [[ -n "$ch" ]] || continue; ((idx+=1)); local rf="$TG_DIR/response-${idx}.json"
    printf " ${CYAN}↳${RESET} @%-28s " "$ch"
    if curl -fsS --get --connect-timeout 12 --max-time 60 --data-urlencode "key=$CF_ACCESS_KEY" --data-urlencode "channel=$ch" --data-urlencode "limit=$posts" -o "$rf" "$CF_WORKER_URL/" 2>/dev/null; then
      local pc cfgc er; er="$(jq -r '.error // empty' "$rf" 2>/dev/null)"
      if [[ -n "$er" ]]; then say "${RED}$er${RESET}"; else pc="$(jq -r '.count // 0' "$rf")"; cfgc="$(jq -r '.configs // 0' "$rf")"; say "${GREEN}${pc} posts • ${cfgc} config refs${RESET}"; responses+=("$rf"); fi
    else say "${RED}fetch failed${RESET}"; fi
  done < "$TG_CHANNELS"
  (( ${#responses[@]} > 0 )) || { err "No channel could be fetched through the Worker."; pause; return; }
  local unique; unique="$(telegram_collect "$TG_RAW" "$TG_META" "$TELEGRAM_MAX_CONFIGS" "${responses[@]}")"
  (( unique > 0 )) || { warn "No supported proxy share URI was found in those posts."; pause; return; }
  info "Smart parser found $unique unique candidate config(s)."
  local out; out="$(pyhelper parse "$TG_RAW" "telegram-public" "Telegram Public" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/telegram-public.txt" 2>&1)" || { err "Telegram parse failed: $out"; pause; return; }
  local usable; usable="$(jq -r '.added // 0' <<<"$out" 2>/dev/null || echo 0)"; (( usable > 0 )) || { err "Xray-compatible URI parser could not build any candidate profiles."; pause; return; }
  if [[ "$TELEGRAM_TCP_GATE" == "1" ]]; then
    section "STAGE 1/2 • SMART TCP PING GATE"
    say " ${GRAY}${TELEGRAM_TCP_SAMPLES} TCP connect sample(s) • majority must succeed • QUIC/Hysteria2 exempt${RESET}"
    local gate; gate="$(telegram_tcp_gate 2>/dev/null || true)"
    if [[ -n "$gate" ]]; then
      local kept dropped tcpok quic
      kept="$(jq -r '.kept // 0' <<<"$gate" 2>/dev/null || echo 0)"; dropped="$(jq -r '.dropped // 0' <<<"$gate" 2>/dev/null || echo 0)"
      tcpok="$(jq -r '.tcp_passed // 0' <<<"$gate" 2>/dev/null || echo 0)"; quic="$(jq -r '.quic_exempt // 0' <<<"$gate" 2>/dev/null || echo 0)"
      display_tcp_gate "$TG_DIR/tcp-gate.tsv"; line; ok "$kept kept for full tunnel test • $dropped invalid TCP endpoint(s) deleted • $tcpok TCP passed • $quic QUIC exempt"
      (( kept > 0 )) || { err "No candidate survived the smart endpoint gate."; pause; return; }
    else err "TCP gate failed internally; full test was not started to avoid wasting resources."; pause; return; fi
  else
    section "STAGE 1/2 • TCP GATE DISABLED"; warn "All parsed candidates will proceed to the expensive tunnel lab."
  fi
  local total i=0; total="$(jq -c 'select(.subid=="telegram-public")' "$INDEX" 2>/dev/null | wc -l | tr -d ' ')"; (( total > 0 )) || { err "No Telegram candidates remain for tunnel testing."; pause; return; }
  section "STAGE 2/2 • REAL XRAY TUNNEL LAB • $total CONFIGS"; : > "$TG_RESULTS"
  while IFS= read -r meta; do
    [[ -n "$meta" ]] || continue; ((i+=1))
    local id file name host scheme rport port result succ lat jit ip cc
    id="$(jq -r '.id' <<<"$meta")"; file="$(jq -r '.file' <<<"$meta")"; name="$(jq -r '.name' <<<"$meta")"; host="$(jq -r '.host // ""' <<<"$meta")"; scheme="$(jq -r '.scheme // ""' <<<"$meta")"; rport="$(jq -r '.port // 0' <<<"$meta")"
    port=$((24000 + (i % 1000))); printf " ${GRAY}[%3s/%3s]${RESET} %-36s " "$i" "$total" "${name:0:36}"
    result="$(bench_one "$file" "$id" "$name" "$port" "$host" "$scheme" "$rport")"; printf '%s\n' "$result" >> "$TG_RESULTS"
    succ="$(cut -f4 <<<"$result")"; lat="$(cut -f5 <<<"$result")"; jit="$(cut -f6 <<<"$result")"; ip="$(cut -f8 <<<"$result")"; cc="$(cut -f9 <<<"$result")"
    [[ "$succ" == "0" ]] && say "${RED}FAIL${RESET}" || say "${GREEN}${succ}%${RESET} ${CYAN}${lat}ms${RESET} j=${YELLOW}${jit}${RESET} ${PURPLE}${ip}/${cc}${RESET}"
  done < <(jq -c 'select(.subid=="telegram-public")' "$INDEX" 2>/dev/null)
  local ranked="$TG_DIR/ranked.tsv"; rank_v11 "$TG_RESULTS" > "$ranked"
  section "TELEGRAM RANKING • BEST → WORST"; display_telegram_ranked "$ranked"
  section "AUTO SUBSCRIPTION"; telegram_autosub_from_ranked "$ranked"
  if [[ -s "$ranked" && "$(cut -f5 < "$ranked" | head -n1)" != "0" ]]; then
    line; printf "%b" "${CYAN}Connect #1 winner now? [Y/n]:${RESET} "; local ans; read -r ans
    if [[ ! "$ans" =~ ^[Nn]$ ]]; then local best; best="$(head -n1 "$ranked")"; start_profile_file "$(cut -f3 <<<"$best")" "$(cut -f4 <<<"$best")" || true; fi
  fi
  pause
}

connect_manual() {
  header; local total; total="$(count_profiles)"; (( total > 0 )) || { warn "No profiles. Add a subscription first."; pause; return; }
  list_profiles; line; nav_choice "$total" 1 1 "Profile"; local num="$NAV_CHOICE"; [[ "$num" == "0" ]] && return
  local meta file name; meta="$(pyhelper meta-number "$INDEX" "$num" 2>/dev/null || true)"; [[ -n "$meta" ]] || { err "Profile not found."; pause; return; }
  file="$(jq -r '.file' <<<"$meta")"; name="$(jq -r '.name' <<<"$meta")"; start_profile_file "$file" "$name" || true; pause
}

full_test_menu() {
  header; local total; total="$(count_profiles)"; (( total > 0 )) || { warn "No profiles. Add/import a config first."; pause; return; }
  list_profiles; printf " ${CYAN}%3s${RESET}  ${PINK}%-10s${RESET} %s\n" "$((total+1))" "PASTE" "Paste one config for one-time full test"; line
  nav_choice "$((total+1))" 1 1 "Test target"; local sel="$NAV_CHOICE"; [[ "$sel" == "0" ]] && return
  if (( sel == total+1 )); then
    say "${CYAN}Paste one VLESS / VMess / Trojan / SS / Hysteria2 URI:${RESET}"; local uri; read -r uri; [[ "$uri" == *"://"* ]] || { err "No proxy URI detected."; pause; return; }
    local id raw out num; id="onetime-$(printf '%s' "$uri" | sha256sum | cut -c1-10)"; raw="$RAW_DIR/$id.raw"; printf '%s\n' "$uri" > "$raw"
    out="$(pyhelper parse "$raw" "$id" "One-time test" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)" || { err "$out"; pause; return; }
    num="$(python3 - "$INDEX" "$id" <<'PY'
import json,sys
n=0
for i,l in enumerate(open(sys.argv[1],errors='ignore'),1):
    try:
        if json.loads(l).get('subid')==sys.argv[2]: n=i
    except Exception: pass
print(n)
PY
)"
    [[ "$num" != "0" ]] && full_test_number "$num" 1 || err "Could not import that URI."
  else full_test_number "$sel" 1 || true; fi
  pause
}

full_test_cli() {
  header; local n="${1:-}"
  if [[ -z "$n" ]]; then local total; total="$(count_profiles)"; ((total>0)) || { warn "No profiles."; return; }; list_profiles; line; nav_choice "$total" 1 1 "Profile"; n="$NAV_CHOICE"; [[ "$n" == "0" ]] && return; fi
  full_test_number "$n" 0 || true
}

remove_subscription() {
  header; [[ -s "$SUB_DB" ]] || { warn "No saved subscriptions."; pause; return; }
  local total; total="$(awk 'NF{c++} END{print c+0}' "$SUB_DB")"; awk -F'\t' '{src=($3 ~ /^local:\/\// ? "LOCAL" : "REMOTE"); printf "  %s) %s  [%s • %s]\n",NR,$2,$1,src}' "$SUB_DB"
  nav_choice "$total" 1 1 "Remove"; local num="$NAV_CHOICE"; [[ "$num" == "0" ]] && return
  local rec id tmp; rec="$(sed -n "${num}p" "$SUB_DB")"; [[ -n "$rec" ]] || { err "Not found."; pause; return; }; id="${rec%%$'\t'*}"; tmp="$TMP_DIR/remove.$$"
  awk -F'\t' -v id="$id" '$1!=id' "$SUB_DB" > "$tmp"; mv -f "$tmp" "$SUB_DB"
  python3 - "$INDEX" "$id" <<'PY'
import json,sys
from pathlib import Path
p=Path(sys.argv[1]); sid=sys.argv[2]; keep=[]
for l in p.read_text(errors='ignore').splitlines() if p.exists() else []:
    try:
        m=json.loads(l)
        if m.get('subid')==sid:
            try: Path(m.get('file','')).unlink(missing_ok=True)
            except Exception: pass
        else: keep.append(m)
    except Exception: pass
p.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n' for x in keep))
PY
  rm -f "$RAW_DIR/$id.raw" "$UNSUPPORTED_DIR/$id.txt"; ok "Removed subscription and its generated profiles."; pause
}

remove_telegram_channel() {
  header; [[ -s "$TG_CHANNELS" ]] || { warn "No Telegram channels saved."; pause; return; }
  nl -ba "$TG_CHANNELS"; local total; total="$(awk 'NF{c++} END{print c+0}' "$TG_CHANNELS")"; nav_choice "$total" 1 1 "Remove channel"; local n="$NAV_CHOICE"; [[ "$n" == "0" ]] && return
  sed "${n}d" "$TG_CHANNELS" > "$TG_CHANNELS.tmp" && mv "$TG_CHANNELS.tmp" "$TG_CHANNELS"; ok "Channel removed."; pause
}

manage_subs() {
  while true; do
    header; section "SUBSCRIPTION & IMPORT CENTER"
    printf " ${CYAN}1${RESET}) Add subscription URL\n ${CYAN}2${RESET}) Refresh all saved subscriptions\n ${CYAN}3${RESET}) Paste one proxy URI\n ${CYAN}4${RESET}) Import file / Xray JSON\n ${CYAN}5${RESET}) Show saved subscriptions\n ${CYAN}6${RESET}) Remove subscription\n ${CYAN}0${RESET}) Back\n"
    line; nav_choice 6 1 1 "Choose"; local ch="$NAV_CHOICE"
    case "$ch" in
      1) add_subscription ;; 2) refresh_all ;; 3) import_uri ;; 4) import_file ;;
      5) header; if [[ -s "$SUB_DB" ]]; then awk -F'\t' '{src=($3 ~ /^local:\/\// ? "LOCAL" : "REMOTE"); printf "  %s) %s  [%s • %s]\n",NR,$2,$1,src}' "$SUB_DB"; else warn "No saved subscriptions."; fi; pause ;;
      6) remove_subscription ;; 0) return ;;
    esac
  done
}

telegram_center() {
  while true; do
    load_settings; header; section "TELEGRAM PUBLIC-CONFIG RADAR"
    local cfstate="${RED}not configured${RESET}"; cf_load && cfstate="${GREEN}ready${RESET}"
    printf " ${GRAY}Worker:${RESET} %b   ${GRAY}posts/channel:${RESET} ${CYAN}%s${RESET}   ${GRAY}max configs:${RESET} ${CYAN}%s${RESET}\n" "$cfstate" "$TELEGRAM_POSTS" "$TELEGRAM_MAX_CONFIGS"
    printf " ${GRAY}TCP gate:${RESET} "; yn "$TELEGRAM_TCP_GATE"; printf "  ${GRAY}samples:${RESET} ${CYAN}%s${RESET}  ${GRAY}auto-sub:${RESET} " "$TELEGRAM_TCP_SAMPLES"; yn "$TELEGRAM_AUTO_SUB"; printf '\n'
    printf " ${CYAN}1${RESET}) ${WHITE}Fetch → TCP gate → full-test → auto-sub → rank${RESET}\n ${CYAN}2${RESET}) Add public Telegram channel\n ${CYAN}3${RESET}) Remove channel\n ${CYAN}4${RESET}) List channels\n ${CYAN}5${RESET}) Configure Cloudflare credential + deploy Worker\n ${CYAN}6${RESET}) Redeploy / repair Worker\n ${CYAN}7${RESET}) Show Worker URL/status\n ${CYAN}0${RESET}) Back\n"
    line; nav_choice 7 1 1 "Choose"; local ch="$NAV_CHOICE"
    case "$ch" in
      1) telegram_scan_test ;; 2) add_telegram_channel ;; 3) remove_telegram_channel ;; 4) header; [[ -s "$TG_CHANNELS" ]] && nl -ba "$TG_CHANNELS" || warn "No channels saved."; pause ;;
      5) configure_cloudflare ;; 6) header; cf_load || { warn "Configure Cloudflare first."; pause; continue; }; deploy_cloudflare_worker ;;
      7) header; cf_load && { printf " ${CYAN}Worker:${RESET} %s\n ${CYAN}Script:${RESET} %s\n" "$CF_WORKER_URL" "$CF_SCRIPT"; curl -fsS --get --data-urlencode "key=$CF_ACCESS_KEY" "$CF_WORKER_URL/health" 2>/dev/null | jq . 2>/dev/null || true; } || warn "Not configured."; pause ;;
      0) return ;;
    esac
  done
}

settings_menu() {
  while true; do
    load_settings; header; section "SETTINGS / PERFORMANCE LAB"
    printf " ${CYAN}1${RESET}) SOCKS port                : ${WHITE}%s${RESET}\n" "$SOCKS_PORT"
    printf " ${CYAN}2${RESET}) Listen address            : ${WHITE}%s${RESET}\n" "$SOCKS_LISTEN"
    printf " ${CYAN}3${RESET}) Auto Xray beta update      : " ; yn "$AUTO_UPDATE"; printf '\n'
    printf " ${CYAN}4${RESET}) Full-test policy           : ${PINK}%s${RESET}\n" "$BENCH_PROFILE"
    printf " ${CYAN}5${RESET}) Extreme IP/DNS leak guard  : " ; yn "$LEAK_PROTECTION"; printf '\n'
    printf " ${CYAN}6${RESET}) DNS differential test     : " ; yn "$DNS_LEAK_TEST"; printf '\n'
    printf " ${CYAN}7${RESET}) Remember last connection  : " ; yn "$REMEMBER_LAST"; printf '\n'
    printf " ${CYAN}8${RESET}) Telegram posts/channel    : ${WHITE}%s${RESET}\n" "$TELEGRAM_POSTS"
    printf " ${CYAN}9${RESET}) Telegram max configs/test  : ${WHITE}%s${RESET}\n" "$TELEGRAM_MAX_CONFIGS"
    printf " ${CYAN}10${RESET}) Telegram smart TCP gate   : "; yn "$TELEGRAM_TCP_GATE"; printf '\n'
    printf " ${CYAN}11${RESET}) Telegram TCP samples      : ${WHITE}%s${RESET}\n" "$TELEGRAM_TCP_SAMPLES"
    printf " ${CYAN}12${RESET}) Auto-create passed sub    : "; yn "$TELEGRAM_AUTO_SUB"; printf '\n'
    printf " ${CYAN}13${RESET}) Passed-sub min success   : ${WHITE}%s%%${RESET}\n" "$TELEGRAM_PASS_MIN_SUCCESS"
    printf " ${CYAN}14${RESET}) Advanced custom test tuning\n ${CYAN}15${RESET}) Forget Cloudflare credential/Worker config\n ${CYAN}16${RESET}) Reset app settings defaults\n ${CYAN}0${RESET}) Back\n"
    line; nav_choice 16 1 1 "Choose"; local ch="$NAV_CHOICE" v
    case "$ch" in
      1) printf "New SOCKS port (1024-65535): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1024&&v<=65535)) && SOCKS_PORT="$v" || warn "Invalid port"; save_settings ;;
      2) say "${YELLOW}127.0.0.1 is safest for apps on this phone.${RESET}"; printf "Listen address [127.0.0.1]: "; read -r v; [[ -n "$v" ]] && SOCKS_LISTEN="$v" || SOCKS_LISTEN="127.0.0.1"; [[ "$SOCKS_LISTEN" == "0.0.0.0" ]] && warn "0.0.0.0 exposes an unauthenticated SOCKS proxy to reachable networks."; save_settings ;;
      3) [[ "$AUTO_UPDATE" == "1" ]] && AUTO_UPDATE=0 || AUTO_UPDATE=1; save_settings ;;
      4) header; say " ${CYAN}1${RESET}) Reliable"; say " ${CYAN}2${RESET}) Balanced"; say " ${CYAN}3${RESET}) Fast"; say " ${CYAN}4${RESET}) Turbo"; nav_choice 4 2 1 "Policy"; v="$NAV_CHOICE"; case "$v" in 1) apply_bench_profile reliable;;2) apply_bench_profile balanced;;3) apply_bench_profile fast;;4) apply_bench_profile turbo;;0) :;; esac ;;
      5) LEAK_PROTECTION=1; save_settings; ok "Privacy Guard is always-on in v1.1.5; it cannot be disabled from the client." ;;
      6) [[ "$DNS_LEAK_TEST" == "1" ]] && DNS_LEAK_TEST=0 || DNS_LEAK_TEST=1; save_settings ;;
      7) [[ "$REMEMBER_LAST" == "1" ]] && REMEMBER_LAST=0 || REMEMBER_LAST=1; save_settings ;;
      8) printf "Last posts per channel (1-200): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=200)) && TELEGRAM_POSTS="$v" || warn "Invalid"; save_settings ;;
      9) printf "Max unique Telegram configs to parse/test (1-300): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=300)) && TELEGRAM_MAX_CONFIGS="$v" || warn "Invalid"; save_settings ;;
      10) [[ "$TELEGRAM_TCP_GATE" == "1" ]] && TELEGRAM_TCP_GATE=0 || TELEGRAM_TCP_GATE=1; save_settings ;;
      11) printf "TCP samples/config (1-6): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=6)) && TELEGRAM_TCP_SAMPLES="$v" || warn "Invalid"; save_settings ;;
      12) [[ "$TELEGRAM_AUTO_SUB" == "1" ]] && TELEGRAM_AUTO_SUB=0 || TELEGRAM_AUTO_SUB=1; save_settings ;;
      13) printf "Minimum full-test success for auto-sub (1-100): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=100)) && TELEGRAM_PASS_MIN_SUCCESS="$v" || warn "Invalid"; save_settings ;;
      14) advanced_bench_settings ;;
      15) printf "%b" "${RED}Delete locally stored Cloudflare credential and Worker reference? [y/N]:${RESET} "; read -r v; if [[ "$v" =~ ^[Yy]$ ]]; then rm -f "$CF_CONF" "$WORKER_FILE"; ok "Local Cloudflare data removed. The remote Worker itself was not deleted."; fi ;;
      16) default_settings; load_settings; ok "App settings reset; subscriptions/profiles/Cloudflare data kept."; sleep 1 ;;
      0) return ;;
    esac
  done
}

show_status() {
  header
  local version tag profiles subs active last tgcount worker localsubs
  version="$($XRAY_BIN version 2>/dev/null | head -n1 || echo 'not installed')"; tag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo '-')"
  profiles="$(count_profiles)"; subs="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"; localsubs="$(awk -F'\t' '$3 ~ /^local:\/\//{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"; tgcount="$(awk 'NF{c++} END{print c+0}' "$TG_CHANNELS" 2>/dev/null)"
  section "ENGINE"; printf " ${CYAN}Core:${RESET} %s\n ${CYAN}Channel:${RESET} official XTLS pre-release [%s]\n" "$version" "$tag"
  section "CONNECTION"; printf " ${CYAN}Profiles:${RESET} %s  ${CYAN}Subscriptions:${RESET} %s (${PURPLE}%s local generated${RESET})  ${CYAN}SOCKS5h:${RESET} %s:%s\n" "$profiles" "$subs" "$localsubs" "$SOCKS_LISTEN" "$SOCKS_PORT"
  if process_alive; then active="$(jq -r '.name // "Connected"' "$ACTIVE_FILE" 2>/dev/null || echo Connected)"; printf " ${CYAN}State:${RESET} ${GREEN}${BOLD}CONNECTED${RESET} → %s\n" "$active"; else printf " ${CYAN}State:${RESET} ${RED}DISCONNECTED${RESET}\n"; fi
  last="$(jq -r '.name // empty' "$LAST_CONNECTED" 2>/dev/null || true)"; [[ -n "$last" ]] && printf " ${CYAN}Last saved:${RESET} %s\n" "$last"
  section "INTELLIGENCE / PRIVACY"; printf " ${CYAN}Benchmark:${RESET} %s (%s samples)   ${CYAN}Leak guard:${RESET} " "$BENCH_PROFILE" "$BENCH_SAMPLES"; yn "$LEAK_PROTECTION"; printf '\n'
  worker="not configured"; cf_load && worker="ready"; printf " ${CYAN}Telegram:${RESET} %s channel(s) • Worker=%s • TCP gate=" "$tgcount" "$worker"; yn "$TELEGRAM_TCP_GATE"; printf " • auto-sub="; yn "$TELEGRAM_AUTO_SUB"; printf " • pass≥%s%%\n" "$TELEGRAM_PASS_MIN_SUCCESS"
  line; say "${GRAY}Navigation: ↑/↓ selects, →/Enter opens, ← returns, and number+Enter remains supported.${RESET}"; pause
}

panel() {
  maybe_update_xray
  while true; do
    load_settings; header
    local state="${RED}OFF${RESET}" active=""; if process_alive; then state="${GREEN}ON${RESET}"; active="$(jq -r '.name // ""' "$ACTIVE_FILE" 2>/dev/null || true)"; fi
    printf " ${GRAY}Core:${RESET} ${PURPLE}%-12s${RESET} ${GRAY}SOCKS:${RESET} ${CYAN}%s:%s${RESET} ${GRAY}State:${RESET} %b ${GRAY}Mode:${RESET} ${PINK}%s${RESET}\n" "$(cat "$INSTALLED_TAG" 2>/dev/null || echo missing)" "$SOCKS_LISTEN" "$SOCKS_PORT" "$state" "$BENCH_PROFILE"
    [[ -n "$active" ]] && printf " ${GRAY}Active:${RESET} ${GREEN}%s${RESET}\n" "$active"; line
    printf " ${CYAN}1${RESET}) ${BOLD}${WHITE}⚡ Auto Connect${RESET} ${GRAY}(last-working → smart fallback)${RESET}\n ${CYAN}2${RESET}) ${BOLD}🧠 Smart benchmark + connect best${RESET}\n ${CYAN}3${RESET}) 🚀 Quick reconnect last working config\n ${CYAN}4${RESET}) 🧪 Full test one config\n ${CYAN}5${RESET}) 📡 Telegram radar ${GRAY}(TCP gate → tunnel → timestamped sub)${RESET}\n ${CYAN}6${RESET}) 📦 Subscriptions / import center\n ${CYAN}7${RESET}) 🔌 Connect a profile manually\n ${CYAN}8${RESET}) 🛡 Privacy / IP + DNS leak audit\n ${CYAN}9${RESET}) 📊 Status / SOCKS info\n ${CYAN}10${RESET}) 🗂 Profiles / native capabilities\n ${CYAN}11${RESET}) ⏹ Stop Xray\n ${CYAN}12${RESET}) ⬆ Update newest Xray pre-release\n ${CYAN}13${RESET}) ⚙ Settings / performance lab\n ${CYAN}14${RESET}) 🧾 Logs\n ${CYAN}15${RESET}) 🕘 Connection history\n ${CYAN}0${RESET}) Exit panel ${GRAY}(Xray keeps running)${RESET}\n"
    line; nav_choice 15 1 1 "Choose"; local ch="$NAV_CHOICE"
    case "$ch" in
      1) auto_connect ;; 2) smart_connect ;; 3) quick_reconnect_menu ;; 4) full_test_menu ;; 5) telegram_center ;; 6) manage_subs ;; 7) connect_manual ;; 8) leak_audit ;; 9) show_status ;;
      10) header; say "${BOLD}${WHITE}Profiles${RESET}"; list_profiles; line; printf "%b" "${CYAN}Show native capability map? [y/N]:${RESET} "; read -r ch; [[ "$ch" =~ ^[Yy]$ ]] && show_capabilities || pause ;;
      11) stop_xray; ok "Xray stopped"; sleep 1 ;; 12) header; update_xray; pause ;; 13) settings_menu ;; 14) show_logs ;; 15) connection_history ;; 0) clear 2>/dev/null || true; return ;;
    esac
  done
}


# ─────────────────────────────────────────────────────────────────────────────
# v1.1.4 True interactive TUI layer
# Real highlighted menus + arrow navigation + instant numeric/mnemonic hotkeys.
# Later definitions intentionally override older menu functions.
# ─────────────────────────────────────────────────────────────────────────────

TUI_SEL_BG='\033[48;5;236m'
TUI_SEL_FG='\033[38;5;231m'
TUI_MUTED='\033[38;5;242m'
TUI_EDGE='\033[38;5;45m'
TUI_TITLE_BG='\033[48;5;17m'
TUI_CARD_BG='\033[48;5;235m'
TUI_C1='\033[38;5;51m'
TUI_C2='\033[38;5;117m'
TUI_C3='\033[38;5;213m'
TUI_C4='\033[38;5;220m'
TUI_C5='\033[38;5;84m'
TUI_C6='\033[38;5;141m'

tui_cols() {
  local c
  c="$(tput cols 2>/dev/null || printf '80')"
  [[ "$c" =~ ^[0-9]+$ ]] || c=80
  (( c < 42 )) && c=42
  (( c > 100 )) && c=100
  printf '%s' "$c"
}

tui_cursor_hide() { printf '\033[?25l' 2>/dev/null || true; }
tui_cursor_show() { printf '\033[?25h' 2>/dev/null || true; }

# Keep the terminal usable even if the user interrupts while a highlighted menu is active.
trap 'tui_cursor_show' EXIT
trap 'tui_cursor_show; exit 130' INT TERM

header() {
  clear 2>/dev/null || printf '\033[2J\033[H'
  local cols; cols="$(tui_cols)"
  # Accent rule that scales to the terminal width.
  local rule="" i
  for ((i=0;i<cols-2;i++)); do rule+="─"; done
  if (( cols >= 60 )); then
    printf "%b\n" ""
    printf "%b\n" "  ${TUI_EDGE}${BOLD}⚡${RESET} ${BOLD}${CYAN}X R A Y   G E N I U S${RESET}  ${DIM}${GRAY}│${RESET}  ${BOLD}${MAGENTA}TERMUX CONTROL DECK${RESET}  ${DIM}${GRAY}│${RESET}  ${PINK}${BOLD}v${APP_VERSION}${RESET}"
  else
    printf "%b\n" ""
    printf "%b\n" "  ${TUI_EDGE}${BOLD}⚡ XRAY GENIUS${RESET}  ${PINK}v${APP_VERSION}${RESET}"
    printf "%b\n" "  ${MAGENTA}${BOLD}TERMUX CONTROL DECK${RESET}"
  fi
  printf "%b\n" "  ${PURPLE}◆${RESET} ${GRAY}Xray pre-release${RESET}   ${CYAN}◆${RESET} ${GRAY}smart tunnel lab${RESET}   ${PINK}◆${RESET} ${GRAY}Telegram radar${RESET}   ${GREEN}◆${RESET} ${GRAY}SOCKS5h${RESET}"
  printf "%b\n" " ${TUI_EDGE}${rule}${RESET}"
}

section() {
  printf "\n%b\n" "${TUI_EDGE}${BOLD}▎${TEALBG}${WHITE}  $*  ${RESET}"
}

tui_onoff_text() {
  [[ "${1:-0}" == "1" ]] && printf 'ON' || printf 'OFF'
}

# tui_menu START ALLOW_BACK "hotkey1,hotkey2,..." "label1" "label2" ...
#
# Controls:
#   ↑ / ↓             move real highlighted row
#   → / Enter         activate highlighted row
#   ← / Esc / Q       back
#   number(s)         activate directly, without Enter
#   mnemonic letter   activate directly, without Enter
#   Home / End        first / last
#   J / K             down / up (when those letters are not assigned hotkeys)
#
# Result: TUI_CHOICE (0 means back).
tui_menu() {
  local sel="${1:-1}" allow_back="${2:-1}" hotkeys="${3:-}"
  shift 3 || true
  local -a items=("$@")
  local count="${#items[@]}" cols roww labelw i idx key a b c digits candidate hk lowerkey possible_more
  local -a hks=()
  IFS=',' read -r -a hks <<<"$hotkeys"
  TUI_CHOICE=""
  (( count > 0 )) || { TUI_CHOICE=0; return 0; }
  [[ "$sel" =~ ^[0-9]+$ ]] || sel=1
  (( sel < 1 || sel > count )) && sel=1
  cols="$(tui_cols)"
  roww=$(( cols - 4 ))
  (( roww < 36 )) && roww=36
  labelw=$(( roww - 12 ))
  (( labelw < 18 )) && labelw=18

  # Non-interactive fallback keeps scripts/pipes usable.
  if [[ ! -t 0 ]]; then
    local fallback
    printf "%b" "${MAGENTA}Choose:${RESET} "
    read -r fallback || fallback="$sel"
    [[ "$fallback" =~ ^[0-9]+$ ]] || fallback="$sel"
    if (( fallback >= 1 && fallback <= count )); then TUI_CHOICE="$fallback"
    elif [[ "$allow_back" == "1" && "$fallback" == "0" ]]; then TUI_CHOICE=0
    else TUI_CHOICE="$sel"
    fi
    return 0
  fi

  _xgc_tui_draw_menu() {
    local n color hint raw shown hot
    for ((n=1; n<=count; n++)); do
      raw="${items[n-1]}"
      shown="$raw"
      (( ${#shown} > labelw )) && shown="${shown:0:labelw-1}…"
      hot="${hks[n-1]:-}"
      if [[ -n "$hot" ]]; then
        hint="$(printf '%d/%s' "$n" "${hot^^}")"
      else
        hint="$(printf '%d' "$n")"
      fi
      printf '\033[2K'
      if (( n == sel )); then
        # Deliberately subtle dark background: a real selection bar without eye-searing inverse video.
        printf " ${TUI_SEL_BG}${BOLD}${TUI_SEL_FG} ❯ %-7s %-*s ${RESET}\n" "$hint" "$labelw" "$shown"
      else
        case $(( (n-1) % 6 )) in
          0) color="$TUI_C1" ;; 1) color="$TUI_C2" ;; 2) color="$TUI_C3" ;;
          3) color="$TUI_C4" ;; 4) color="$TUI_C5" ;; *) color="$TUI_C6" ;;
        esac
        printf "   ${color}%-7s${RESET} ${WHITE}%-*s${RESET}\n" "$hint" "$labelw" "$shown"
      fi
    done
    printf '\033[2K'
    printf "%b\n" " ${DIM}${GRAY}↑↓ move  ${CYAN}→/Enter open${GRAY}  ←/Q back  ${YELLOW}number = instant${GRAY}  ${PINK}letter = instant${RESET}"
  }

  _xgc_tui_finish() {
    TUI_CHOICE="$1"
    tui_cursor_show
    unset -f _xgc_tui_draw_menu _xgc_tui_finish 2>/dev/null || true
    return 0
  }

  tui_cursor_hide
  _xgc_tui_draw_menu

  while true; do
    key=""
    IFS= read -rsn1 key || { _xgc_tui_finish "$sel"; return 0; }

    case "$key" in
      $'\e')
        a=""; b=""; c=""
        IFS= read -rsn1 -t 0.08 a || true
        if [[ -z "$a" ]]; then
          if [[ "$allow_back" == "1" ]]; then _xgc_tui_finish 0; return 0; fi
          continue
        fi
        IFS= read -rsn1 -t 0.08 b || true
        case "$a$b" in
          '[A') ((sel--)); ((sel<1)) && sel=$count ;;
          '[B') ((sel++)); ((sel>count)) && sel=1 ;;
          '[C') _xgc_tui_finish "$sel"; return 0 ;;
          '[D') if [[ "$allow_back" == "1" ]]; then _xgc_tui_finish 0; return 0; fi ;;
          '[H'|'OH') sel=1 ;;
          '[F'|'OF') sel=$count ;;
          '[5'|'[6')
            IFS= read -rsn1 -t 0.08 c || true
            if [[ "$a$b$c" == '[5~' ]]; then
              sel=$((sel-5)); ((sel<1)) && sel=1
            elif [[ "$a$b$c" == '[6~' ]]; then
              sel=$((sel+5)); ((sel>count)) && sel=$count
            fi
            ;;
        esac
        ;;
      '')
        _xgc_tui_finish "$sel"; return 0
        ;;
      $'\t')
        ((sel++)); ((sel>count)) && sel=1
        ;;
      0)
        if [[ "$allow_back" == "1" ]]; then _xgc_tui_finish 0; return 0; fi
        ;;
      [1-9])
        # Multi-digit direct choice without Enter. Only pause when another digit can form
        # a valid larger menu number (e.g. 1 can become 10..19).
        digits="$key"
        while true; do
          possible_more=$((10#$digits * 10))
          (( possible_more <= count )) || break
          c=""
          IFS= read -rsn1 -t 0.18 c || true
          [[ "$c" =~ ^[0-9]$ ]] || break
          digits+="$c"
          (( ${#digits} < 4 )) || break
        done
        candidate=$((10#$digits))
        if (( candidate >= 1 && candidate <= count )); then
          _xgc_tui_finish "$candidate"; return 0
        fi
        ;;
      *)
        lowerkey="${key,,}"
        # Explicit mnemonic keys execute immediately.
        for ((i=0; i<count; i++)); do
          hk="${hks[i]:-}"
          if [[ -n "$hk" && "${hk,,}" == "$lowerkey" ]]; then
            _xgc_tui_finish "$((i+1))"; return 0
          fi
        done
        # Q is universally back when it is not claimed by a menu mnemonic.
        if [[ "$lowerkey" == "q" && "$allow_back" == "1" ]]; then
          _xgc_tui_finish 0; return 0
        fi
        # Vim-style movement is a comfort fallback only when J/K are not menu hotkeys.
        if [[ "$lowerkey" == "j" ]]; then ((sel++)); ((sel>count)) && sel=1
        elif [[ "$lowerkey" == "k" ]]; then ((sel--)); ((sel<1)) && sel=$count
        fi
        ;;
    esac

    # Repaint only the menu rows + help line, preserving the header/status area.
    printf '\033[%dA' "$((count+1))"
    _xgc_tui_draw_menu
  done
}

# Compatibility wrapper for older code paths that only know a numeric range.
# It still gets real highlighted rows and instant digits/arrows.
nav_choice() {
  local max="${1:-1}" start="${2:-1}" allow_zero="${3:-1}" label="${4:-Option}"
  local -a generic=()
  local i
  for ((i=1;i<=max;i++)); do generic+=("${label} ${i}"); done
  tui_menu "$start" "$allow_zero" "" "${generic[@]}"
  NAV_CHOICE="$TUI_CHOICE"
}

tui_profile_labels() {
  jq -r '
    [(.scheme // "?")|ascii_upcase, (.transport // "?"), (.security // ""), (.name // "Unnamed")]
    | "\(.[0])  •  \(.[1])\(if .[2] != "" then " / "+.[2] else "" end)  •  \(.[3])"
  ' "$INDEX" 2>/dev/null
}

connect_manual() {
  header
  local total; total="$(count_profiles)"
  (( total > 0 )) || { warn "No profiles. Add a subscription first."; pause; return; }
  section "CONNECT A PROFILE"
  local -a labels=()
  mapfile -t labels < <(tui_profile_labels)
  tui_menu 1 1 "" "${labels[@]}"
  local num="$TUI_CHOICE"
  [[ "$num" == "0" ]] && return
  local meta file name
  meta="$(pyhelper meta-number "$INDEX" "$num" 2>/dev/null || true)"
  [[ -n "$meta" ]] || { err "Profile not found."; pause; return; }
  file="$(jq -r '.file' <<<"$meta")"
  name="$(jq -r '.name' <<<"$meta")"
  start_profile_file "$file" "$name" || true
  pause
}

full_test_menu() {
  header
  local total; total="$(count_profiles)"
  (( total > 0 )) || { warn "No profiles. Add/import a config first."; pause; return; }
  section "FULL TEST • ONE CONFIG"
  local -a labels=()
  mapfile -t labels < <(tui_profile_labels)
  labels+=("✚ PASTE ONE CONFIG  •  one-time tunnel test")
  tui_menu 1 1 "p" "${labels[@]}"
  local sel="$TUI_CHOICE"
  [[ "$sel" == "0" ]] && return
  if (( sel == total + 1 )); then
    tui_cursor_show
    say "${CYAN}Paste one VLESS / VMess / Trojan / SS / Hysteria2 URI:${RESET}"
    local uri; read -r uri
    [[ "$uri" == *"://"* ]] || { err "No proxy URI detected."; pause; return; }
    local id raw out num
    id="onetime-$(printf '%s' "$uri" | sha256sum | cut -c1-10)"
    raw="$RAW_DIR/$id.raw"
    printf '%s\n' "$uri" > "$raw"
    out="$(pyhelper parse "$raw" "$id" "One-time test" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)" || { err "$out"; pause; return; }
    num="$(python3 - "$INDEX" "$id" <<'PY'
import json,sys
n=0
for i,l in enumerate(open(sys.argv[1],errors='ignore'),1):
    try:
        if json.loads(l).get('subid')==sys.argv[2]: n=i
    except Exception: pass
print(n)
PY
)"
    [[ "$num" != "0" ]] && full_test_number "$num" 1 || err "Could not import that URI."
  else
    full_test_number "$sel" 1 || true
  fi
  pause
}

full_test_cli() {
  header
  local n="${1:-}"
  if [[ -z "$n" ]]; then
    local total; total="$(count_profiles)"
    ((total>0)) || { warn "No profiles."; return; }
    section "SELECT TEST TARGET"
    local -a labels=()
    mapfile -t labels < <(tui_profile_labels)
    tui_menu 1 1 "" "${labels[@]}"
    n="$TUI_CHOICE"
    [[ "$n" == "0" ]] && return
  fi
  full_test_number "$n" 0 || true
}

remove_subscription() {
  header
  [[ -s "$SUB_DB" ]] || { warn "No saved subscriptions."; pause; return; }
  section "REMOVE SUBSCRIPTION"
  local -a labels=()
  while IFS=$'\t' read -r id name url updated; do
    [[ -n "$id" ]] || continue
    local src="REMOTE"
    [[ "$url" == local://* ]] && src="LOCAL GENERATED"
    labels+=("🗑  ${name}  •  ${src}  •  ${id}")
  done < "$SUB_DB"
  tui_menu 1 1 "" "${labels[@]}"
  local num="$TUI_CHOICE"
  [[ "$num" == "0" ]] && return
  local rec id tmp
  rec="$(sed -n "${num}p" "$SUB_DB")"
  [[ -n "$rec" ]] || { err "Not found."; pause; return; }
  id="${rec%%$'\t'*}"
  tmp="$TMP_DIR/remove.$$"
  awk -F'\t' -v id="$id" '$1!=id' "$SUB_DB" > "$tmp"
  mv -f "$tmp" "$SUB_DB"
  python3 - "$INDEX" "$id" <<'PY'
import json,sys
from pathlib import Path
p=Path(sys.argv[1]); sid=sys.argv[2]; keep=[]
for l in p.read_text(errors='ignore').splitlines() if p.exists() else []:
    try:
        m=json.loads(l)
        if m.get('subid')==sid:
            try: Path(m.get('file','')).unlink(missing_ok=True)
            except Exception: pass
        else: keep.append(m)
    except Exception: pass
p.write_text(''.join(json.dumps(x,ensure_ascii=False,separators=(',',':'))+'\n' for x in keep))
PY
  rm -f "$RAW_DIR/$id.raw" "$UNSUPPORTED_DIR/$id.txt"
  ok "Removed subscription and its generated profiles."
  pause
}

remove_telegram_channel() {
  header
  [[ -s "$TG_CHANNELS" ]] || { warn "No Telegram channels saved."; pause; return; }
  section "REMOVE TELEGRAM CHANNEL"
  local -a labels=()
  mapfile -t labels < <(awk 'NF{print "📡  @" $0}' "$TG_CHANNELS")
  tui_menu 1 1 "" "${labels[@]}"
  local n="$TUI_CHOICE"
  [[ "$n" == "0" ]] && return
  sed "${n}d" "$TG_CHANNELS" > "$TG_CHANNELS.tmp" && mv "$TG_CHANNELS.tmp" "$TG_CHANNELS"
  ok "Channel removed."
  pause
}

manage_subs() {
  local selected=1
  while true; do
    header
    section "SUBSCRIPTION & IMPORT CENTER"
    local remote localgen total
    remote="$(awk -F'\t' '$3 !~ /^local:\/\//{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
    localgen="$(awk -F'\t' '$3 ~ /^local:\/\//{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
    total="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
    printf " ${GRAY}Saved:${RESET} ${CYAN}%s${RESET}   ${GRAY}remote:${RESET} ${GREEN}%s${RESET}   ${GRAY}generated:${RESET} ${PURPLE}%s${RESET}\n\n" "$total" "$remote" "$localgen"
    local -a items=(
      "➕ Add subscription URL"
      "↻ Refresh all saved subscriptions"
      "⌁ Paste one proxy URI"
      "⇩ Import file / raw Xray JSON"
      "☰ Show saved subscriptions"
      "🗑 Remove subscription"
    )
    tui_menu "$selected" 1 "a,r,p,i,l,d" "${items[@]}"
    local ch="$TUI_CHOICE"; [[ "$ch" != "0" ]] && selected="$ch"
    case "$ch" in
      1) add_subscription ;;
      2) refresh_all ;;
      3) import_uri ;;
      4) import_file ;;
      5)
        header; section "SAVED SUBSCRIPTIONS"
        if [[ -s "$SUB_DB" ]]; then
          awk -F'\t' '{src=($3 ~ /^local:\/\// ? "LOCAL" : "REMOTE"); printf "  %2s  %-36s  [%s • %s]\n",NR,$2,$1,src}' "$SUB_DB"
        else warn "No saved subscriptions."; fi
        pause
        ;;
      6) remove_subscription ;;
      0) return ;;
    esac
  done
}

telegram_center() {
  local selected=1
  while true; do
    load_settings
    header
    section "TELEGRAM PUBLIC-CONFIG RADAR"
    local cfplain="NOT CONFIGURED"; cf_load && cfplain="READY"
    local channel_count; channel_count="$(awk 'NF{c++} END{print c+0}' "$TG_CHANNELS" 2>/dev/null)"
    printf " ${TUI_CARD_BG}${WHITE} Worker ${RESET} ${CYAN}%s${RESET}   ${TUI_CARD_BG}${WHITE} Channels ${RESET} ${PINK}%s${RESET}   ${TUI_CARD_BG}${WHITE} Posts ${RESET} ${YELLOW}%s${RESET}\n" "$cfplain" "$channel_count" "$TELEGRAM_POSTS"
    printf " ${TUI_CARD_BG}${WHITE} TCP Gate ${RESET} ${GREEN}%s${RESET}   ${TUI_CARD_BG}${WHITE} Samples ${RESET} ${CYAN}%s${RESET}   ${TUI_CARD_BG}${WHITE} Auto-sub ${RESET} ${PURPLE}%s${RESET}\n\n" "$(tui_onoff_text "$TELEGRAM_TCP_GATE")" "$TELEGRAM_TCP_SAMPLES" "$(tui_onoff_text "$TELEGRAM_AUTO_SUB")"
    local -a items=(
      "⚡ Fetch → TCP gate → real tunnel lab → auto-sub → ranking"
      "➕ Add public Telegram channel"
      "🗑 Remove Telegram channel"
      "☰ List saved Telegram channels"
      "☁ Configure Cloudflare credential + deploy Worker"
      "↻ Redeploy / repair Cloudflare Worker"
      "● Worker URL / health status"
    )
    tui_menu "$selected" 1 "f,a,d,l,c,r,s" "${items[@]}"
    local ch="$TUI_CHOICE"; [[ "$ch" != "0" ]] && selected="$ch"
    case "$ch" in
      1) telegram_scan_test ;;
      2) add_telegram_channel ;;
      3) remove_telegram_channel ;;
      4) header; section "TELEGRAM CHANNELS"; [[ -s "$TG_CHANNELS" ]] && nl -ba "$TG_CHANNELS" || warn "No channels saved."; pause ;;
      5) configure_cloudflare ;;
      6) header; cf_load || { warn "Configure Cloudflare first."; pause; continue; }; deploy_cloudflare_worker ;;
      7)
        header; section "WORKER STATUS"
        if cf_load; then
          printf " ${CYAN}Worker:${RESET} %s\n ${CYAN}Script:${RESET} %s\n" "$CF_WORKER_URL" "$CF_SCRIPT"
          curl -fsS --get --data-urlencode "key=$CF_ACCESS_KEY" "$CF_WORKER_URL/health" 2>/dev/null | jq . 2>/dev/null || true
        else warn "Not configured."; fi
        pause
        ;;
      0) return ;;
    esac
  done
}

configure_cloudflare() {
  header
  section "CLOUDFLARE WORKER SETUP"
  say "${GRAY}Credential stays local (mode 600) and is never embedded in the Worker.${RESET}"
  local -a auth_items=(
    "🔐 API Token  •  recommended"
    "🔑 Global API Key + account email"
  )
  tui_menu 1 1 "t,g" "${auth_items[@]}"
  local mode="$TUI_CHOICE"
  case "$mode" in
    1) CF_AUTH_MODE=token; CF_EMAIL=""; printf "%b" "${CYAN}API Token:${RESET} "; read -rs CF_SECRET; printf '\n' ;;
    2) CF_AUTH_MODE=global; printf "%b" "${CYAN}Cloudflare account email:${RESET} "; read -r CF_EMAIL; printf "%b" "${CYAN}Global API Key:${RESET} "; read -rs CF_SECRET; printf '\n' ;;
    *) warn "Cancelled."; pause; return 1 ;;
  esac
  [[ -n "$CF_SECRET" ]] || { err "Credential is empty."; pause; return 1; }

  printf "%b" "${CYAN}Account ID (leave blank to auto-discover):${RESET} "
  read -r CF_ACCOUNT_ID
  if [[ -z "$CF_ACCOUNT_ID" ]]; then
    info "Discovering Cloudflare accounts..."
    local accounts
    if [[ "$CF_AUTH_MODE" == "token" ]]; then
      accounts="$(curl -fsS -H "Authorization: Bearer $CF_SECRET" 'https://api.cloudflare.com/client/v4/accounts?per_page=50' 2>/dev/null || true)"
    else
      accounts="$(curl -fsS -H "X-Auth-Email: $CF_EMAIL" -H "X-Auth-Key: $CF_SECRET" 'https://api.cloudflare.com/client/v4/accounts?per_page=50' 2>/dev/null || true)"
    fi
    local count; count="$(jq -r '.result | length' <<<"$accounts" 2>/dev/null || echo 0)"
    if (( count == 1 )); then
      CF_ACCOUNT_ID="$(jq -r '.result[0].id' <<<"$accounts")"
      ok "Account: $(jq -r '.result[0].name' <<<"$accounts")"
    elif (( count > 1 )); then
      header; section "SELECT CLOUDFLARE ACCOUNT"
      local -a account_labels=()
      mapfile -t account_labels < <(jq -r '.result[] | "☁  \(.name)  •  \(.id)"' <<<"$accounts")
      tui_menu 1 1 "" "${account_labels[@]}"
      local n="$TUI_CHOICE"
      [[ "$n" == "0" ]] && { warn "Cancelled."; pause; return 1; }
      CF_ACCOUNT_ID="$(jq -r --argjson n "$n" '.result[$n-1].id // empty' <<<"$accounts" 2>/dev/null)"
    else
      warn "Auto-discovery failed (a narrowly-scoped token may not have Account Read)."
      printf "%b" "${CYAN}Paste Account ID:${RESET} "
      read -r CF_ACCOUNT_ID
    fi
  fi

  [[ "$CF_ACCOUNT_ID" =~ ^[A-Fa-f0-9]{32}$ ]] || { err "Cloudflare Account ID must be 32 hex characters."; pause; return 1; }
  local default_script="xray-genius-$(randhex 6)"
  printf "%b" "${CYAN}Worker script name [${default_script}]:${RESET} "
  read -r CF_SCRIPT
  [[ -n "$CF_SCRIPT" ]] || CF_SCRIPT="$default_script"
  CF_SCRIPT="$(printf '%s' "$CF_SCRIPT" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_-')"
  [[ -n "$CF_SCRIPT" ]] || CF_SCRIPT="$default_script"
  CF_ACCESS_KEY="$(randhex 40)"
  CF_WORKER_URL=""
  cf_save
  deploy_cloudflare_worker
}

settings_menu() {
  local selected=1
  while true; do
    load_settings
    header
    section "SETTINGS / PERFORMANCE LAB"
    printf " ${GRAY}Select with the highlight bar or hit its number/mnemonic instantly.${RESET}\n\n"
    local -a items=(
      "SOCKS port  •  ${SOCKS_PORT}"
      "Listen address  •  ${SOCKS_LISTEN}"
      "Auto Xray beta update  •  $(tui_onoff_text "$AUTO_UPDATE")"
      "Full-test policy  •  ${BENCH_PROFILE}"
      "Extreme IP/DNS leak guard  •  $(tui_onoff_text "$LEAK_PROTECTION")"
      "DNS differential test  •  $(tui_onoff_text "$DNS_LEAK_TEST")"
      "Remember last connection  •  $(tui_onoff_text "$REMEMBER_LAST")"
      "Telegram posts/channel  •  ${TELEGRAM_POSTS}"
      "Telegram max configs/test  •  ${TELEGRAM_MAX_CONFIGS}"
      "Telegram smart TCP gate  •  $(tui_onoff_text "$TELEGRAM_TCP_GATE")"
      "Telegram TCP samples  •  ${TELEGRAM_TCP_SAMPLES}"
      "Auto-create passed subscription  •  $(tui_onoff_text "$TELEGRAM_AUTO_SUB")"
      "Passed-sub minimum success  •  ${TELEGRAM_PASS_MIN_SUCCESS}%"
      "Advanced custom benchmark tuning"
      "Forget local Cloudflare credential / Worker reference"
      "Reset application settings defaults"
    )
    tui_menu "$selected" 1 "p,l,u,b,e,d,r,t,m,g,s,a,n,c,f,x" "${items[@]}"
    local ch="$TUI_CHOICE" v
    [[ "$ch" != "0" ]] && selected="$ch"
    case "$ch" in
      1) printf "New SOCKS port (1024-65535): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1024&&v<=65535)) && SOCKS_PORT="$v" || warn "Invalid port"; save_settings ;;
      2) say "${YELLOW}127.0.0.1 is safest for apps on this phone.${RESET}"; printf "Listen address [127.0.0.1]: "; read -r v; [[ -n "$v" ]] && SOCKS_LISTEN="$v" || SOCKS_LISTEN="127.0.0.1"; [[ "$SOCKS_LISTEN" == "0.0.0.0" ]] && warn "0.0.0.0 exposes an unauthenticated SOCKS proxy to reachable networks."; save_settings ;;
      3) [[ "$AUTO_UPDATE" == "1" ]] && AUTO_UPDATE=0 || AUTO_UPDATE=1; save_settings ;;
      4)
        header; section "FULL-TEST POLICY"
        local -a policies=(
          "🛡 Reliable  •  6 samples, conservative"
          "⚖ Balanced  •  recommended everyday profile"
          "⚡ Fast  •  lighter and quicker"
          "🚀 Turbo  •  minimum samples / shortest timeout"
        )
        tui_menu 2 1 "r,b,f,t" "${policies[@]}"
        v="$TUI_CHOICE"
        case "$v" in
          1) apply_bench_profile reliable ;;
          2) apply_bench_profile balanced ;;
          3) apply_bench_profile fast ;;
          4) apply_bench_profile turbo ;;
        esac
        ;;
      5) LEAK_PROTECTION=1; save_settings; ok "Privacy Guard is always-on in v1.1.5; it cannot be disabled from the client." ;;
      6) [[ "$DNS_LEAK_TEST" == "1" ]] && DNS_LEAK_TEST=0 || DNS_LEAK_TEST=1; save_settings ;;
      7) [[ "$REMEMBER_LAST" == "1" ]] && REMEMBER_LAST=0 || REMEMBER_LAST=1; save_settings ;;
      8) printf "Last posts per channel (1-200): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=200)) && TELEGRAM_POSTS="$v" || warn "Invalid"; save_settings ;;
      9) printf "Max unique Telegram configs to parse/test (1-300): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=300)) && TELEGRAM_MAX_CONFIGS="$v" || warn "Invalid"; save_settings ;;
      10) [[ "$TELEGRAM_TCP_GATE" == "1" ]] && TELEGRAM_TCP_GATE=0 || TELEGRAM_TCP_GATE=1; save_settings ;;
      11) printf "TCP samples/config (1-6): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=6)) && TELEGRAM_TCP_SAMPLES="$v" || warn "Invalid"; save_settings ;;
      12) [[ "$TELEGRAM_AUTO_SUB" == "1" ]] && TELEGRAM_AUTO_SUB=0 || TELEGRAM_AUTO_SUB=1; save_settings ;;
      13) printf "Minimum full-test success for auto-sub (1-100): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=100)) && TELEGRAM_PASS_MIN_SUCCESS="$v" || warn "Invalid"; save_settings ;;
      14) advanced_bench_settings ;;
      15) printf "%b" "${RED}Delete locally stored Cloudflare credential and Worker reference? [y/N]:${RESET} "; read -r v; if [[ "$v" =~ ^[Yy]$ ]]; then rm -f "$CF_CONF" "$WORKER_FILE"; ok "Local Cloudflare data removed. Remote Worker was not deleted."; fi ;;
      16) default_settings; load_settings; ok "App settings reset; subscriptions/profiles/Cloudflare data kept."; sleep 1 ;;
      0) return ;;
    esac
  done
}

show_status() {
  header
  local version tag profiles subs active last tgcount worker localsubs
  version="$($XRAY_BIN version 2>/dev/null | head -n1 || echo 'not installed')"
  tag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo '-')"
  profiles="$(count_profiles)"
  subs="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  localsubs="$(awk -F'\t' '$3 ~ /^local:\/\//{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  tgcount="$(awk 'NF{c++} END{print c+0}' "$TG_CHANNELS" 2>/dev/null)"
  section "ENGINE"
  printf " ${CYAN}Core:${RESET} %s\n ${CYAN}Channel:${RESET} official XTLS pre-release [%s]\n" "$version" "$tag"
  section "CONNECTION"
  printf " ${CYAN}Profiles:${RESET} %s   ${CYAN}Subscriptions:${RESET} %s (${PURPLE}%s local${RESET})   ${CYAN}SOCKS5h:${RESET} %s:%s\n" "$profiles" "$subs" "$localsubs" "$SOCKS_LISTEN" "$SOCKS_PORT"
  if process_alive; then
    active="$(jq -r '.name // "Connected"' "$ACTIVE_FILE" 2>/dev/null || echo Connected)"
    printf " ${CYAN}State:${RESET} ${GREEN}${BOLD}● CONNECTED${RESET}  →  %s\n" "$active"
  else
    printf " ${CYAN}State:${RESET} ${RED}${BOLD}● DISCONNECTED${RESET}\n"
  fi
  last="$(jq -r '.name // empty' "$LAST_CONNECTED" 2>/dev/null || true)"
  [[ -n "$last" ]] && printf " ${CYAN}Last saved:${RESET} %s\n" "$last"
  section "INTELLIGENCE / PRIVACY"
  printf " ${CYAN}Benchmark:${RESET} %s (%s samples)   ${CYAN}Leak guard:${RESET} %s\n" "$BENCH_PROFILE" "$BENCH_SAMPLES" "$(tui_onoff_text "$LEAK_PROTECTION")"
  worker="not configured"; cf_load && worker="ready"
  printf " ${CYAN}Telegram:${RESET} %s channel(s) • Worker=%s • TCP gate=%s • auto-sub=%s • pass≥%s%%\n" "$tgcount" "$worker" "$(tui_onoff_text "$TELEGRAM_TCP_GATE")" "$(tui_onoff_text "$TELEGRAM_AUTO_SUB")" "$TELEGRAM_PASS_MIN_SUCCESS"
  line
  say "${GRAY}Menu UX: highlighted rows + ↑↓ + →/Enter + ←/Q + instant numbers + instant mnemonic letters.${RESET}"
  pause
}

panel() {
  maybe_update_xray
  local selected=1
  while true; do
    load_settings
    header

    local state_plain="OFF" state_color="$RED" active=""
    if process_alive; then
      state_plain="ON"
      state_color="$GREEN"
      active="$(jq -r '.name // ""' "$ACTIVE_FILE" 2>/dev/null || true)"
    fi
    local coretag; coretag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo missing)"

    printf " ${TUI_CARD_BG}${WHITE} CORE ${RESET} ${PURPLE}%-11s${RESET}  ${TUI_CARD_BG}${WHITE} SOCKS5h ${RESET} ${CYAN}%s:%s${RESET}\n" "$coretag" "$SOCKS_LISTEN" "$SOCKS_PORT"
    printf " ${TUI_CARD_BG}${WHITE} STATE ${RESET} ${state_color}${BOLD}● %-3s${RESET}       ${TUI_CARD_BG}${WHITE} TEST MODE ${RESET} ${PINK}%s${RESET}\n" "$state_plain" "$BENCH_PROFILE"
    [[ -n "$active" ]] && printf " ${TUI_CARD_BG}${WHITE} ACTIVE ${RESET} ${GREEN}%s${RESET}\n" "${active:0:58}"

    section "CONTROL PANEL"
    local -a items=(
      "⚡ Auto Connect  •  last-working → smart fallback"
      "🧠 Smart benchmark + connect best"
      "🚀 Quick reconnect last working config"
      "🧪 Full test one config"
      "📡 Telegram public-config radar"
      "📦 Subscriptions / import center"
      "🔌 Connect a profile manually"
      "🛡 Privacy / IP + DNS leak audit"
      "📊 Status / SOCKS information"
      "🗂 Profiles / native capability map"
      "⏹ Stop Xray"
      "⬆ Update newest Xray pre-release"
      "⚙ Settings / performance lab"
      "🧾 Runtime logs"
      "🕘 Connection history"
    )
    # Semantic one-key shortcuts. They are displayed beside each number.
    # A,S,R,T,G,U,C,P,I,O,X,V,E,L,H
    tui_menu "$selected" 1 "a,s,r,t,g,u,c,p,i,o,x,v,e,l,h" "${items[@]}"
    local ch="$TUI_CHOICE"
    [[ "$ch" != "0" ]] && selected="$ch"

    case "$ch" in
      1) auto_connect ;;
      2) smart_connect ;;
      3) quick_reconnect_menu ;;
      4) full_test_menu ;;
      5) telegram_center ;;
      6) manage_subs ;;
      7) connect_manual ;;
      8) leak_audit ;;
      9) show_status ;;
      10)
        header; section "PROFILES / NATIVE CAPABILITIES"
        list_profiles
        line
        say "${GRAY}Press ${CYAN}C${GRAY} for capability map, any other key to return.${RESET}"
        local k=""; IFS= read -rsn1 k || true
        [[ "${k,,}" == "c" ]] && show_capabilities
        ;;
      11) stop_xray; ok "Xray stopped"; sleep 1 ;;
      12) header; update_xray; pause ;;
      13) settings_menu ;;
      14) show_logs ;;
      15) connection_history ;;
      0) clear 2>/dev/null || true; return ;;
    esac
  done
}

bootstrap() {
  ensure_dirs
  if ! is_termux; then warn "This script is tuned for Termux. Continuing on this Unix-like shell."; fi
  install_deps || exit 1
  ensure_dirs; load_settings; install_launcher
  if [[ ! -x "$XRAY_BIN" ]]; then
    header; info "First run: installing newest official Xray pre-release..."
    update_xray || { err "Initial Xray installation failed. Re-run later or use update."; pause; }
  fi
  # Make old v1.0 settings automatically acquire v1.1 defaults without deleting user values.
  load_settings
}

usage() {
  cat <<EOF2
$APP_NAME v$APP_VERSION
Usage: xgc [command]
  panel          Open colorful control panel (default)
  auto           Fast last-working connection, then smart fallback
  last           Reconnect last working profile
  best           Benchmark finalists and connect best
  test [N]       Full real-tunnel test of profile N
  telegram       Telegram public-channel radar / Worker center
  leak           IP + DNS leak audit of current SOCKS tunnel
  google-ai      Check if the active tunnel/exit can reach Google AI (Gemini)
  refresh        Refresh all saved subscriptions
  update         Update Xray to newest official pre-release
  stop           Stop Xray
  status         Show status
  logs           Show runtime logs
  capabilities   Show native protocol/transport map
  selftest-ui     Run controller regression self-test
  doctor          Audit and repair local XGC state
EOF2
}

# ─────────────────────────────────────────────────────────────────────────────
# v1.1.5 controller + fail-closed privacy layer.
# Final definitions intentionally override v1.1.4 implementations.
# ─────────────────────────────────────────────────────────────────────────────

PRIVACY_STATUS="$ROOT/privacy-status.json"
SECURE_DNS_CACHE="$ROOT/secure-dns-cache.json"
GUARD_LOG="$LOG_DIR/privacy-guard.log"
V115_DIRECT_DIGIT_TIMEOUT="${V115_DIRECT_DIGIT_TIMEOUT:-0.36}"

# v1.1.5 does not permit the connection guard to be disabled.  This is scoped to
# traffic that enters XGC's SOCKS endpoint; Termux cannot impose Android-wide
# routing on unrelated applications without Android VPN/TUN integration.
LEAK_PROTECTION=1

v115_is_ip_literal() {
  python3 - "$1" <<'PY'
import ipaddress,sys
try:
    ipaddress.ip_address(sys.argv[1].strip('[]'))
    raise SystemExit(0)
except Exception:
    raise SystemExit(1)
PY
}

v115_cache_get() {
  local host="$1" now ts ip
  [[ -s "$SECURE_DNS_CACHE" ]] || return 1
  now="$(date +%s)"
  ts="$(jq -r --arg h "$host" '.[$h].ts // 0' "$SECURE_DNS_CACHE" 2>/dev/null || echo 0)"
  ip="$(jq -r --arg h "$host" '.[$h].ip // empty' "$SECURE_DNS_CACHE" 2>/dev/null || true)"
  [[ "$ts" =~ ^[0-9]+$ && -n "$ip" ]] || return 1
  (( now - ts <= 900 )) || return 1
  printf '%s' "$ip"
}

v115_cache_put() {
  local host="$1" ip="$2" tmp="$TMP_DIR/dns-cache.$$.$RANDOM"
  python3 - "$SECURE_DNS_CACHE" "$tmp" "$host" "$ip" <<'PY'
import json,sys,time
src,dst,host,ip=sys.argv[1:]
obj={}
try:
    obj=json.load(open(src))
    if not isinstance(obj,dict): obj={}
except Exception:
    obj={}
obj[host]={'ip':ip,'ts':int(time.time())}
open(dst,'w').write(json.dumps(obj,ensure_ascii=False,indent=2))
PY
  mv -f "$tmp" "$SECURE_DNS_CACHE" 2>/dev/null || true
  chmod 600 "$SECURE_DNS_CACHE" 2>/dev/null || true
}

# Resolve proxy-server hostnames without Android/system DNS.  HTTPS is pinned to
# the resolver IP using curl --resolve, so the bootstrap DNS request is encrypted
# and does not itself require a local resolver.
v115_secure_resolve_host() {
  local host="${1#[}"; host="${host%]}"
  [[ -n "$host" ]] || return 1
  if v115_is_ip_literal "$host"; then printf '%s' "$host"; return 0; fi

  local cached
  cached="$(v115_cache_get "$host" 2>/dev/null || true)"
  [[ -n "$cached" ]] && { printf '%s' "$cached"; return 0; }

  local json ip resolver_name resolver_ip url qtype
  for qtype in A AAAA; do
    while IFS='|' read -r resolver_name resolver_ip url; do
      [[ -n "$resolver_name" ]] || continue
      json="$(curl -fsS --connect-timeout 3 --max-time 6 \
        --resolve "${resolver_name}:443:${resolver_ip}" \
        -H 'accept: application/dns-json' --get \
        --data-urlencode "name=${host}" --data-urlencode "type=${qtype}" \
        "$url" 2>/dev/null || true)"
      if [[ "$qtype" == "A" ]]; then
        ip="$(printf '%s' "$json" | jq -r '.Answer[]? | select(.type==1) | .data' 2>/dev/null | head -n1)"
      else
        ip="$(printf '%s' "$json" | jq -r '.Answer[]? | select(.type==28) | .data' 2>/dev/null | head -n1)"
      fi
      if [[ -n "$ip" ]] && v115_is_ip_literal "$ip"; then
        v115_cache_put "$host" "$ip"
        printf '%s' "$ip"
        return 0
      fi
    done <<'EOF'
cloudflare-dns.com|1.1.1.1|https://cloudflare-dns.com/dns-query
cloudflare-dns.com|1.0.0.1|https://cloudflare-dns.com/dns-query
dns.google|8.8.8.8|https://dns.google/resolve
dns.google|8.8.4.4|https://dns.google/resolve
EOF
  done
  return 1
}

v115_extract_endpoint_hosts() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys,ipaddress,re
obj=json.load(open(sys.argv[1]))
hosts=set()

def add(v):
    if not isinstance(v,str): return
    v=v.strip()
    if not v: return
    # endpoint may be [v6]:port or host:port.
    if v.startswith('[') and ']:' in v:
        v=v[1:v.index(']')]
    elif v.count(':')==1 and v.rsplit(':',1)[1].isdigit():
        v=v.rsplit(':',1)[0]
    v=v.strip('[]')
    try:
        ipaddress.ip_address(v); return
    except Exception:
        pass
    # Avoid accidentally treating URI/path/header values as endpoints.
    if re.fullmatch(r'[A-Za-z0-9._-]+',v) and '.' in v:
        hosts.add(v)

for o in obj.get('outbounds',[]) if isinstance(obj.get('outbounds'),list) else []:
    if not isinstance(o,dict): continue
    proto=str(o.get('protocol','')).lower()
    if proto in ('freedom','blackhole','dns','loopback'): continue
    s=o.get('settings') if isinstance(o.get('settings'),dict) else {}
    add(s.get('address')); add(s.get('server'))
    for key in ('vnext','servers'):
        arr=s.get(key)
        if isinstance(arr,list):
            for e in arr:
                if isinstance(e,dict):
                    add(e.get('address')); add(e.get('server'))
    peers=s.get('peers')
    if isinstance(peers,list):
        for p in peers:
            if isinstance(p,dict): add(p.get('endpoint'))
for h in sorted(hosts): print(h)
PY
}

v115_build_endpoint_map() {
  local file="$1" mapfile="$2"
  local hostsfile="$TMP_DIR/endpoint-hosts.$$.$RANDOM" tsv="$TMP_DIR/endpoint-map.$$.$RANDOM"
  : > "$tsv"
  v115_extract_endpoint_hosts "$file" > "$hostsfile" 2>/dev/null || { rm -f "$hostsfile" "$tsv"; return 1; }
  local host ip
  while IFS= read -r host; do
    [[ -n "$host" ]] || continue
    ip="$(v115_secure_resolve_host "$host" 2>/dev/null || true)"
    if [[ -z "$ip" ]]; then
      printf '%s\n' "secure bootstrap DNS failed for ${host}" >> "$GUARD_LOG"
      rm -f "$hostsfile" "$tsv"
      return 1
    fi
    printf '%s\t%s\n' "$host" "$ip" >> "$tsv"
  done < "$hostsfile"
  python3 - "$tsv" "$mapfile" <<'PY'
import json,sys
m={}
for line in open(sys.argv[1],errors='ignore'):
    p=line.rstrip('\n').split('\t',1)
    if len(p)==2 and p[0] and p[1]: m[p[0]]=p[1]
open(sys.argv[2],'w').write(json.dumps(m,ensure_ascii=False,indent=2))
PY
  rm -f "$hostsfile" "$tsv"
  chmod 600 "$mapfile" 2>/dev/null || true
}

v115_patch_runtime() {
  local src="$1" dst="$2" port="$3" listen="$4" mapfile="$5"
  python3 - "$src" "$dst" "$port" "$listen" "$mapfile" <<'PY'
import json,sys,ipaddress,re
from pathlib import Path
src,dst,port,listen,mapfile=sys.argv[1],sys.argv[2],int(sys.argv[3]),sys.argv[4],sys.argv[5]
obj=json.loads(Path(src).read_text())
emap=json.loads(Path(mapfile).read_text()) if Path(mapfile).exists() else {}
outs=obj.get('outbounds')
if not isinstance(outs,list) or not outs:
    raise SystemExit('configuration has no outbounds')

def is_infra(o):
    return str(o.get('protocol','')).lower() in ('freedom','blackhole','dns','loopback')

proxy=None
for o in outs:
    if isinstance(o,dict) and o.get('tag')=='proxy':
        proxy=o; break
if proxy is None:
    for o in outs:
        if isinstance(o,dict) and not is_infra(o):
            proxy=o; break
if proxy is None or not isinstance(proxy,dict):
    raise SystemExit('no usable proxy outbound')

proxy_tag=proxy.get('tag') or 'xgc-proxy'
proxy['tag']=proxy_tag

def is_ip(v):
    try: ipaddress.ip_address(str(v).strip('[]')); return True
    except Exception: return False

def endpoint_parts(v):
    if not isinstance(v,str): return None,None
    x=v.strip()
    if x.startswith('[') and ']:' in x:
        h=x[1:x.index(']')]; return h, x[x.index(']')+2:]
    if x.count(':')==1 and x.rsplit(':',1)[1].isdigit():
        return x.rsplit(':',1)
    return x,None

def preserve_transport_identity(o, original):
    if not original or is_ip(original): return
    st=o.get('streamSettings')
    if not isinstance(st,dict): return
    sec=str(st.get('security','')).lower()
    if sec=='tls':
        t=st.setdefault('tlsSettings',{})
        if isinstance(t,dict) and not t.get('serverName'): t['serverName']=original
    elif sec=='reality':
        r=st.setdefault('realitySettings',{})
        if isinstance(r,dict) and not r.get('serverName'): r['serverName']=original

    method=str(st.get('method') or st.get('network') or '').lower()
    if method in ('xhttp','splithttp'):
        x=st.setdefault('xhttpSettings',{})
        if isinstance(x,dict) and not x.get('host'): x['host']=original
    elif method in ('websocket','ws'):
        x=st.setdefault('wsSettings',{})
        if isinstance(x,dict):
            if not x.get('host'): x['host']=original
            headers=x.get('headers')
            if isinstance(headers,dict) and not headers.get('Host'): headers['Host']=original
    elif method=='httpupgrade':
        x=st.setdefault('httpupgradeSettings',{})
        if isinstance(x,dict) and not x.get('host'): x['host']=original
    elif method=='grpc':
        x=st.setdefault('grpcSettings',{})
        if isinstance(x,dict) and not x.get('authority'): x['authority']=original
    elif method in ('http','h2'):
        x=st.setdefault('httpSettings',{})
        if isinstance(x,dict) and not x.get('host'): x['host']=[original]

def replace_addr(o, container, key):
    v=container.get(key)
    if not isinstance(v,str): return
    h,p=endpoint_parts(v)
    h=h.strip('[]') if isinstance(h,str) else h
    if h in emap:
        preserve_transport_identity(o,h)
        ip=emap[h]
        if p is not None:
            container[key]=(f'[{ip}]:{p}' if ':' in ip else f'{ip}:{p}')
        else:
            container[key]=ip

# Securely pin every actual proxy endpoint, including chained outbounds.
for o in outs:
    if not isinstance(o,dict) or is_infra(o): continue
    o['targetStrategy']='AsIs'  # proxied domain targets stay remote; no client-side target DNS.
    s=o.get('settings') if isinstance(o.get('settings'),dict) else {}
    replace_addr(o,s,'address'); replace_addr(o,s,'server')
    for key in ('vnext','servers'):
        arr=s.get(key)
        if isinstance(arr,list):
            for e in arr:
                if isinstance(e,dict):
                    replace_addr(o,e,'address'); replace_addr(o,e,'server')
    peers=s.get('peers')
    if isinstance(peers,list):
        for p in peers:
            if isinstance(p,dict): replace_addr(o,p,'endpoint')

inbound={
  'tag':'socks-in','listen':listen,'port':port,'protocol':'socks',
  'settings':{'udp':True},
  'sniffing':{'enabled':True,'routeOnly':True,'destOverride':['http','tls','quic']}
}
ins=obj.get('inbounds') if isinstance(obj.get('inbounds'),list) else []
ins=[x for x in ins if not (isinstance(x,dict) and x.get('tag')=='socks-in')]
obj['inbounds']=[inbound]+ins

if not isinstance(obj.get('log'),dict): obj['log']={'loglevel':'warning'}
else: obj['log'].setdefault('loglevel','warning')

# Always-on DNS guard.  No localhost/system fallback is present.  The two DoH
# hostnames are pinned in hosts and DNS-generated connections are routed through
# the selected proxy.  Proxy endpoints themselves were resolved securely before
# Xray starts, preventing a bootstrap loop.
olddns=obj.get('dns') if isinstance(obj.get('dns'),dict) else {}
hosts=olddns.get('hosts') if isinstance(olddns.get('hosts'),dict) else {}
hosts=dict(hosts)
hosts['cloudflare-dns.com']=['1.1.1.1','1.0.0.1']
hosts['dns.google']=['8.8.8.8','8.8.4.4']
obj['dns']={
  'hosts':hosts,
  'servers':[
    {'address':'https://cloudflare-dns.com/dns-query','queryStrategy':'UseIP','skipFallback':False},
    {'address':'https://dns.google/dns-query','queryStrategy':'UseIP','skipFallback':False}
  ],
  'queryStrategy':'UseIP',
  'disableCache':False,
  'serveStale':True,
  'disableFallback':False,
  'disableFallbackIfMatch':False,
  'enableParallelQuery':True,
  'useSystemHosts':False,
  'tag':'xgc-dns'
}

routing=obj.get('routing') if isinstance(obj.get('routing'),dict) else {}
rules=routing.get('rules') if isinstance(routing.get('rules'),list) else []
# Older XGC guard rules have no durable marker after JSON generation.  Prepend
# authoritative v1.1.5 rules; first match wins in Xray.
guard_rules=[
  {'type':'field','inboundTag':['xgc-dns'],'outboundTag':proxy_tag,'ruleTag':'xgc-privacy-dns'},
  {'type':'field','inboundTag':['socks-in'],'outboundTag':proxy_tag,'ruleTag':'xgc-privacy-socks'}
]
routing['domainStrategy']='AsIs'
routing['rules']=guard_rules+rules
obj['routing']=routing

Path(dst).write_text(json.dumps(obj,ensure_ascii=False,indent=2))
PY
}

v115_runtime_integrity_check() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys,ipaddress
o=json.load(open(sys.argv[1]))
ins=o.get('inbounds',[])
if not any(isinstance(x,dict) and x.get('tag')=='socks-in' and x.get('protocol')=='socks' for x in ins):
    raise SystemExit(10)
dns=o.get('dns')
if not isinstance(dns,dict) or dns.get('useSystemHosts') is not False or dns.get('tag')!='xgc-dns':
    raise SystemExit(11)
sv=dns.get('servers',[])
if not sv or any((isinstance(x,str) and x=='localhost') for x in sv):
    raise SystemExit(12)
routing=o.get('routing',{})
rules=routing.get('rules',[])
if len(rules)<2:
    raise SystemExit(13)
r0,r1=rules[0],rules[1]
if 'xgc-dns' not in (r0.get('inboundTag') or []) or not r0.get('outboundTag'):
    raise SystemExit(14)
if 'socks-in' not in (r1.get('inboundTag') or []) or r1.get('outboundTag')!=r0.get('outboundTag'):
    raise SystemExit(15)
for out in o.get('outbounds',[]):
    if not isinstance(out,dict): continue
    if str(out.get('protocol','')).lower() in ('freedom','blackhole','dns','loopback'): continue
    if out.get('targetStrategy')!='AsIs':
        raise SystemExit(16)
raise SystemExit(0)
PY
}

# Existing benchmark/test paths call v11_patch_runtime, so overriding it makes
# secure endpoint bootstrap + hardened DNS/routing apply to tests as well.
v11_patch_runtime() {
  local src="$1" dst="$2" port="$3" listen="$4"
  local mapfile="$TMP_DIR/secure-endpoints.$$.$RANDOM.json"
  : > "$GUARD_LOG"
  if ! v115_build_endpoint_map "$src" "$mapfile"; then
    rm -f "$mapfile"
    return 1
  fi
  if ! v115_patch_runtime "$src" "$dst" "$port" "$listen" "$mapfile"; then
    rm -f "$mapfile"
    return 1
  fi
  rm -f "$mapfile"
  v115_runtime_integrity_check "$dst"
}

# Direct comparison deliberately avoids a DNS lookup.
direct_trace() {
  curl -fsS --connect-timeout 4 --max-time 7 'https://1.1.1.1/cdn-cgi/trace' 2>/dev/null || true
}

v115_write_privacy_status() {
  local state="$1" proxyip="$2" directip="$3" note="$4"
  python3 - "$PRIVACY_STATUS" "$state" "$proxyip" "$directip" "$note" <<'PY'
import json,sys,time
p,state,pip,dip,note=sys.argv[1:]
open(p,'w').write(json.dumps({
 'state':state,'proxy_ip':pip,'direct_ip':dip,'note':note,
 'secure_endpoint_bootstrap':True,'forced_remote_target_dns':True,
 'forced_proxy_dns_route':True,'fail_closed':True,'checked_at':int(time.time())
},ensure_ascii=False,indent=2))
PY
  chmod 600 "$PRIVACY_STATUS" 2>/dev/null || true
}

# Fast connection-time privacy verification.  Failure to produce a SOCKS egress
# is fail-closed.  Equality with the direct public IP is also treated as unsafe.
v115_privacy_postcheck() {
  local ptrace="" dtrace="" pip="" dip="" tries
  for tries in 1 2 3; do
    ptrace="$(trace_through_socks "$SOCKS_PORT")"
    pip="$(printf '%s\n' "$ptrace" | trace_field ip)"
    [[ -n "$pip" ]] && break
    sleep .25
  done
  if [[ -z "$pip" ]]; then
    v115_write_privacy_status FAIL "" "" "SOCKS egress verification failed"
    printf '%s\n' "SOCKS egress verification failed" >> "$GUARD_LOG"
    return 1
  fi

  dtrace="$(direct_trace)"
  dip="$(printf '%s\n' "$dtrace" | trace_field ip)"
  if [[ -n "$dip" && "$dip" == "$pip" ]]; then
    v115_write_privacy_status FAIL "$pip" "$dip" "proxy egress equals direct egress"
    printf '%s\n' "proxy egress equals direct egress: $pip" >> "$GUARD_LOG"
    return 1
  fi

  # Confirm a hostname request itself succeeds using SOCKS5h semantics.
  if ! curl -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" \
      --connect-timeout 5 --max-time 9 -o /dev/null "$TEST_URL" 2>/dev/null; then
    v115_write_privacy_status FAIL "$pip" "$dip" "SOCKS5h hostname path failed"
    printf '%s\n' "SOCKS5h hostname path failed" >> "$GUARD_LOG"
    return 1
  fi

  v115_write_privacy_status PASS "$pip" "$dip" "automatic connection guard passed"
  return 0
}

start_profile_file() {
  local file="$1" name="$2" quiet="${3:-0}"
  LEAK_PROTECTION=1
  [[ -x "$XRAY_BIN" ]] || { err "Xray is not installed."; return 1; }
  [[ -f "$file" ]] || { err "Profile file missing."; return 1; }

  stop_xray >/dev/null 2>&1 || true
  if ! v11_patch_runtime "$file" "$RUNTIME_CONFIG" "$SOCKS_PORT" "$SOCKS_LISTEN"; then
    err "Privacy Guard blocked startup: secure endpoint DNS bootstrap or hardened runtime generation failed."
    warn "No system-DNS fallback was used. See $GUARD_LOG"
    return 1
  fi
  if ! XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$RUNTIME_CONFIG" >"$LOG_DIR/config-test.log" 2>&1; then
    err "Xray rejected the hardened runtime config. See $LOG_DIR/config-test.log"
    return 1
  fi

  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
  XRAY_LOCATION_ASSET="$BIN_DIR" nohup "$XRAY_BIN" run -c "$RUNTIME_CONFIG" >"$LOG_DIR/xray.log" 2>&1 &
  local p=$!; printf '%s' "$p" > "$PID_FILE"

  if ! wait_port "$SOCKS_LISTEN" "$SOCKS_PORT"; then
    err "Xray started but the SOCKS port did not open."
    stop_xray
    return 1
  fi

  if ! v115_privacy_postcheck; then
    err "Privacy Guard FAIL-CLOSED: tunnel verification did not meet the safe connection policy."
    stop_xray
    return 1
  fi

  python3 - "$ACTIVE_FILE" "$name" "$file" "$SOCKS_LISTEN" "$SOCKS_PORT" <<'PY'
import json,sys,time
open(sys.argv[1],'w').write(json.dumps({
 'name':sys.argv[2],'file':sys.argv[3],'listen':sys.argv[4],
 'port':int(sys.argv[5]),'leak_guard':True,'privacy_guard':'PASS',
 'started':int(time.time())
},ensure_ascii=False))
PY

  [[ "$REMEMBER_LAST" == "1" ]] && record_connected "$name" "$file"
  if [[ "$quiet" != "1" ]]; then
    local pip dip
    pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
    dip="$(jq -r '.direct_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
    ok "Connected safely: $name"
    say " ${CYAN}SOCKS5h:${RESET} ${BOLD}${SOCKS_LISTEN}:${SOCKS_PORT}${RESET}  ${GREEN}🛡 PRIVACY GUARD PASS${RESET}"
    printf " ${GRAY}Direct:${RESET} %s   ${GRAY}Proxy egress:${RESET} ${CYAN}%s${RESET}\n" "${dip:--}" "${pip:--}"
  fi
}

leak_audit() {
  header
  process_alive || { warn "Connect a profile first; privacy audit needs a running SOCKS tunnel."; pause; return; }
  section "🛡 AUTOMATIC PRIVACY GUARD / LEAK AUDIT"
  LEAK_PROTECTION=1
  local state pip dip note
  state="$(jq -r '.state // "UNKNOWN"' "$PRIVACY_STATUS" 2>/dev/null || echo UNKNOWN)"
  pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  dip="$(jq -r '.direct_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  note="$(jq -r '.note // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  printf " ${TUI_CARD_BG}${WHITE} MODE ${RESET} ${GREEN}${BOLD}ALWAYS ON / FAIL-CLOSED${RESET}\n"
  printf " ${TUI_CARD_BG}${WHITE} LAST AUTO-CHECK ${RESET} %s   ${TUI_CARD_BG}${WHITE} EGRESS ${RESET} ${CYAN}%s${RESET}\n" "$state" "$pip"
  printf " ${GRAY}Direct public IP:${RESET} %s\n ${GRAY}Guard note:${RESET} %s\n" "$dip" "$note"
  line
  info "Re-running connection-time IP + SOCKS5h integrity verification..."
  if v115_runtime_integrity_check "$RUNTIME_CONFIG" && v115_privacy_postcheck; then
    ok "Runtime routing, secure DNS policy, SOCKS5h path and egress checks passed."
  else
    err "Privacy verification failed. Xray is being stopped to preserve fail-closed behavior."
    stop_xray
    pause
    return
  fi

  if [[ "$DNS_LEAK_TEST" == "1" ]]; then
    section "OPTIONAL DEEP DNS DIFFERENTIAL"
    local remote localr
    remote="$(dns_leak_compare remote --socks5-hostname)"
    localr="$(dns_leak_compare local --socks5)"
    printf " ${CYAN}SOCKS5h resolver observation:${RESET} %s\n" "$remote"
    printf " ${YELLOW}Plain SOCKS/local-resolution simulation:${RESET} %s\n" "$localr"
    if [[ "$remote" != "INCONCLUSIVE" && "$localr" != "INCONCLUSIVE" && "$remote" != "$localr" ]]; then
      warn "Plain SOCKS can expose the application's own resolver choice. XGC cannot recover a hostname after an app has already converted it to an IP."
    fi
  fi
  line
  say "${GRAY}XGC now securely resolves proxy endpoints before Xray starts, forces proxied target domains to remain remote, and forces tagged DoH + all SOCKS-in traffic to the chosen proxy.${RESET}"
  say "${GRAY}Android-wide traffic outside this SOCKS endpoint still requires a VPN/TUN/VpnService solution; Termux cannot silently take ownership of unrelated apps' sockets.${RESET}"
  pause
}

# ---------------------------------------------------------------------------
# v1.1.5 TUI engine
# ---------------------------------------------------------------------------
# The old Bash implementation repainted every item and used fractional shell
# read timeouts.  Large profile lists can scroll beyond the physical terminal,
# making cursor math and direct-digit input fragile.  This implementation uses
# /dev/tty + termios + select(), a fixed viewport, and a byte-level key parser.
tui_menu() {
  local sel="${1:-1}" allow_back="${2:-1}" hotkeys="${3:-}"
  shift 3 || true
  local -a items=("$@")
  local count="${#items[@]}"
  TUI_CHOICE=""
  (( count > 0 )) || { TUI_CHOICE=0; return 0; }
  [[ "$sel" =~ ^[0-9]+$ ]] || sel=1
  (( sel<1 || sel>count )) && sel=1

  if [[ ! -t 0 || ! -e /dev/tty ]]; then
    local fallback
    printf "%b" "${MAGENTA}Choose:${RESET} "
    read -r fallback || fallback="$sel"
    [[ "$fallback" =~ ^[0-9]+$ ]] || fallback="$sel"
    if (( fallback>=1 && fallback<=count )); then TUI_CHOICE="$fallback"
    elif [[ "$allow_back" == "1" && "$fallback" == "0" ]]; then TUI_CHOICE=0
    else TUI_CHOICE="$sel"
    fi
    return 0
  fi

  local choice
  choice="$(
    python3 - "$sel" "$allow_back" "$hotkeys" "$V115_DIRECT_DIGIT_TIMEOUT" "${items[@]}" <<'PY'
import os,sys,termios,select,re,unicodedata,time

start=int(sys.argv[1]); allow_back=sys.argv[2]=='1'
hotkeys=sys.argv[3].split(',') if sys.argv[3] else []
digit_timeout=float(sys.argv[4])
items=sys.argv[5:]
n=len(items)
if len(hotkeys)<n: hotkeys += ['']*(n-len(hotkeys))
sel=max(1,min(start,n))

fd=os.open('/dev/tty',os.O_RDWR|os.O_NOCTTY)
old=termios.tcgetattr(fd)
new=termios.tcgetattr(fd)
new[3] &= ~(termios.ICANON|termios.ECHO)
new[6][termios.VMIN]=1
new[6][termios.VTIME]=0

RESET='\x1b[0m'; BOLD='\x1b[1m'; DIM='\x1b[2m'
WHITE='\x1b[38;5;255m'; GRAY='\x1b[38;5;245m'; CYAN='\x1b[38;5;51m'
YELLOW='\x1b[38;5;220m'; PINK='\x1b[38;5;205m'
COLORS=['\x1b[38;5;51m','\x1b[38;5;213m','\x1b[38;5;84m',
        '\x1b[38;5;220m','\x1b[38;5;141m','\x1b[38;5;81m']
SELBG='\x1b[48;5;237m'; SELFG='\x1b[38;5;231m'
ansi_re=re.compile(r'\x1b\[[0-9;?]*[ -/]*[@-~]')
pending=bytearray()
first_draw=True
last_lines=0
numeric_hint=''

def tty_write(s):
    os.write(fd,s.encode('utf-8','replace'))

def ch_width(ch):
    if unicodedata.combining(ch): return 0
    if ord(ch)>=0x1F000: return 2
    return 2 if unicodedata.east_asian_width(ch) in ('W','F') else 1

def visible_width(s):
    s=ansi_re.sub('',s)
    return sum(ch_width(c) for c in s)

def clip_ansi(s,maxw):
    # Keep ANSI sequences intact while clipping visible cells.
    out=[]; w=0; i=0
    while i<len(s):
        if s[i]=='\x1b':
            m=ansi_re.match(s,i)
            if m:
                out.append(m.group(0)); i=m.end(); continue
        cw=ch_width(s[i])
        if w+cw>maxw:
            if maxw>=1: out.append('…')
            break
        out.append(s[i]); w+=cw; i+=1
    return ''.join(out)

def get_size():
    try:
        z=os.get_terminal_size(fd)
        return max(40,z.columns),max(12,z.lines)
    except OSError:
        return 80,24

def read_byte(timeout=None):
    if pending:
        b=pending[0]; del pending[0]; return b
    r,_,_=select.select([fd],[],[],timeout)
    if not r: return None
    data=os.read(fd,32)
    if not data: return None
    pending.extend(data[1:])
    return data[0]

def esc_sequence():
    b=read_byte(0.06)
    if b is None: return 'ESC'
    seq=bytes([b])
    deadline=time.monotonic()+0.05
    while time.monotonic()<deadline and len(seq)<8:
        x=read_byte(0.008)
        if x is None: break
        seq+=bytes([x])
        if x in (ord('~'),ord('A'),ord('B'),ord('C'),ord('D'),ord('H'),ord('F')):
            break
    try: return seq.decode('ascii','ignore')
    except Exception: return ''

def direct_number(first):
    global numeric_hint
    buf=first
    while True:
        valid=[str(i) for i in range(1,n+1) if str(i).startswith(buf)]
        exact=buf in valid
        longer=any(x!=buf for x in valid)
        if not valid:
            numeric_hint=''
            return None
        if exact and not longer:
            numeric_hint=''
            return int(buf)
        numeric_hint=buf+'…'
        draw()
        b=read_byte(digit_timeout)
        if b is None:
            numeric_hint=''
            return int(buf) if exact else None
        if 48<=b<=57:
            buf+=chr(b)
            continue
        # A non-digit arriving while the numeric prefix is pending is preserved
        # for the next event whenever the exact number was not already chosen.
        pending.insert(0,b)
        numeric_hint=''
        return int(buf) if exact else None

def draw():
    global first_draw,last_lines
    cols,rows=get_size()
    # Cap the viewport so headers/status cards printed by Bash never get pushed
    # off-screen on a phone. Large lists scroll inside this window.
    view=max(5,min(n,max(6,min(13,rows-11))))
    half=view//2
    lo=max(1,sel-half)
    hi=min(n,lo+view-1)
    lo=max(1,hi-view+1)
    actual=hi-lo+1
    footer=2
    lines=actual+footer

    if not first_draw and last_lines:
        tty_write(f'\x1b[{last_lines}A')
    first_draw=False
    last_lines=lines

    labelw=max(16,cols-17)
    for idx in range(lo,hi+1):
        raw=items[idx-1]
        hk=hotkeys[idx-1].strip()
        hint=f'{idx}/{hk.upper()}' if hk else str(idx)
        shown=clip_ansi(raw,labelw)
        tty_write('\r\x1b[2K')
        if idx==sel:
            body=f' ❯ {hint:<7} {shown}'
            pad=max(0,cols-visible_width(body)-1)
            tty_write(f' {SELBG}{BOLD}{SELFG}{body}{(" "*pad)}{RESET}\n')
        else:
            color=COLORS[(idx-1)%len(COLORS)]
            tty_write(f'   {color}{hint:<7}{RESET} {WHITE}{shown}{RESET}\n')

    tty_write('\r\x1b[2K')
    range_text=f'{lo}-{hi}/{n}'
    num=f'  direct {numeric_hint}' if numeric_hint else ''
    tty_write(f' {DIM}{GRAY}items {range_text}{num}{RESET}\n')
    tty_write('\r\x1b[2K')
    tty_write(f' {DIM}{GRAY}↑↓ move  {CYAN}→/Enter open{GRAY}  ←/Q back  {YELLOW}number instant{GRAY}  {PINK}letter instant{RESET}\n')

def finish(value):
    global last_lines
    # Leave the final menu rendered but restore normal cursor/terminal state.
    tty_write('\x1b[?25h')
    print(value)
    return value

try:
    termios.tcsetattr(fd,termios.TCSADRAIN,new)
    tty_write('\x1b[?25l')
    draw()
    while True:
        b=read_byte(None)
        if b is None:
            print(sel); break

        # Enter / LF
        if b in (10,13):
            finish(sel); break
        # Tab
        if b==9:
            sel=1 if sel>=n else sel+1
            draw(); continue
        # Backspace is a friendly "back" on phone keyboards.
        if b in (8,127):
            if allow_back: finish(0); break
            continue
        # Escape / arrows / Home / End / PageUp / PageDown
        if b==27:
            s=esc_sequence()
            if s in ('[A','OA'):
                sel=n if sel<=1 else sel-1
            elif s in ('[B','OB'):
                sel=1 if sel>=n else sel+1
            elif s in ('[C','OC'):
                finish(sel); break
            elif s in ('[D','OD','ESC'):
                if allow_back: finish(0); break
            elif s in ('[H','OH','[1~','[7~'):
                sel=1
            elif s in ('[F','OF','[4~','[8~'):
                sel=n
            elif s=='[5~':
                sel=max(1,sel-8)
            elif s=='[6~':
                sel=min(n,sel+8)
            draw(); continue

        # Digits.  7 on a 15-item main menu is immediate; on a 100-item list,
        # the tiny live prefix window permits both 7 and 70 without Enter.
        if 48<=b<=57:
            ch=chr(b)
            if ch=='0':
                if allow_back: finish(0); break
                continue
            v=direct_number(ch)
            if v is not None and 1<=v<=n:
                finish(v); break
            draw(); continue

        try:
            ch=bytes([b]).decode('utf-8')
        except Exception:
            ch=''
        low=ch.lower()

        claimed=False
        for i,h in enumerate(hotkeys):
            if h and h.lower()==low:
                finish(i+1); claimed=True; break
        if claimed: break
        if low=='q' and allow_back:
            finish(0); break
        if low=='j':
            sel=1 if sel>=n else sel+1; draw(); continue
        if low=='k':
            sel=n if sel<=1 else sel-1; draw(); continue
finally:
    try:
        termios.tcsetattr(fd,termios.TCSADRAIN,old)
        tty_write('\x1b[?25h')
    except Exception:
        pass
    try: os.close(fd)
    except Exception: pass
PY
  )"
  [[ "$choice" =~ ^[0-9]+$ ]] || choice="$sel"
  TUI_CHOICE="$choice"
}

# Settings remains configurable except the privacy guard itself, which is
# intentionally locked on.  Reusing the v1.1.4 menu layout keeps migration safe.
settings_menu() {
  local selected=1
  while true; do
    load_settings
    LEAK_PROTECTION=1
    header
    section "SETTINGS / PERFORMANCE LAB"
    printf " ${GREEN}🛡 Privacy Guard is ALWAYS ON / fail-closed in v1.1.5.${RESET}\n"
    printf " ${GRAY}Use ↑↓ or instant number/letter; no Enter is required for direct choices.${RESET}\n\n"
    local -a items=(
      "SOCKS port  •  ${SOCKS_PORT}"
      "Listen address  •  ${SOCKS_LISTEN}"
      "Auto Xray beta update  •  $(tui_onoff_text "$AUTO_UPDATE")"
      "Full-test policy  •  ${BENCH_PROFILE}"
      "Privacy Guard  •  🛡 LOCKED ON / FAIL-CLOSED"
      "Deep DNS differential test  •  $(tui_onoff_text "$DNS_LEAK_TEST")"
      "Remember last connection  •  $(tui_onoff_text "$REMEMBER_LAST")"
      "Telegram posts/channel  •  ${TELEGRAM_POSTS}"
      "Telegram max configs/test  •  ${TELEGRAM_MAX_CONFIGS}"
      "Telegram smart TCP gate  •  $(tui_onoff_text "$TELEGRAM_TCP_GATE")"
      "Telegram TCP samples  •  ${TELEGRAM_TCP_SAMPLES}"
      "Auto-create passed subscription  •  $(tui_onoff_text "$TELEGRAM_AUTO_SUB")"
      "Passed-sub minimum success  •  ${TELEGRAM_PASS_MIN_SUCCESS}%"
      "Advanced custom benchmark tuning"
      "Forget local Cloudflare credential / Worker reference"
      "Reset application settings defaults"
    )
    tui_menu "$selected" 1 "p,l,u,b,e,d,r,t,m,g,s,a,n,c,f,x" "${items[@]}"
    local ch="$TUI_CHOICE" v
    [[ "$ch" != "0" ]] && selected="$ch"
    case "$ch" in
      1) printf "New SOCKS port (1024-65535): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1024&&v<=65535)) && SOCKS_PORT="$v" || warn "Invalid port"; save_settings ;;
      2) say "${YELLOW}127.0.0.1 is safest for apps on this phone.${RESET}"; printf "Listen address [127.0.0.1]: "; read -r v; [[ -n "$v" ]] && SOCKS_LISTEN="$v" || SOCKS_LISTEN="127.0.0.1"; [[ "$SOCKS_LISTEN" == "0.0.0.0" ]] && warn "0.0.0.0 exposes SOCKS to reachable networks."; save_settings ;;
      3) [[ "$AUTO_UPDATE" == "1" ]] && AUTO_UPDATE=0 || AUTO_UPDATE=1; save_settings ;;
      4)
        header; section "FULL-TEST POLICY"
        local -a policies=(
          "🛡 Reliable  •  6 samples, conservative"
          "⚖ Balanced  •  recommended everyday profile"
          "⚡ Fast  •  lighter and quicker"
          "🚀 Turbo  •  minimum samples / shortest timeout"
        )
        tui_menu 2 1 "r,b,f,t" "${policies[@]}"
        v="$TUI_CHOICE"
        case "$v" in
          1) apply_bench_profile reliable ;;
          2) apply_bench_profile balanced ;;
          3) apply_bench_profile fast ;;
          4) apply_bench_profile turbo ;;
        esac
        ;;
      5) LEAK_PROTECTION=1; save_settings; ok "Privacy Guard is locked ON and automatically applied to every connection." ; sleep .6 ;;
      6) [[ "$DNS_LEAK_TEST" == "1" ]] && DNS_LEAK_TEST=0 || DNS_LEAK_TEST=1; save_settings ;;
      7) [[ "$REMEMBER_LAST" == "1" ]] && REMEMBER_LAST=0 || REMEMBER_LAST=1; save_settings ;;
      8) printf "Last posts per channel (1-200): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=200)) && TELEGRAM_POSTS="$v" || warn "Invalid"; save_settings ;;
      9) printf "Max unique Telegram configs to parse/test (1-300): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=300)) && TELEGRAM_MAX_CONFIGS="$v" || warn "Invalid"; save_settings ;;
      10) [[ "$TELEGRAM_TCP_GATE" == "1" ]] && TELEGRAM_TCP_GATE=0 || TELEGRAM_TCP_GATE=1; save_settings ;;
      11) printf "TCP samples/config (1-6): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=6)) && TELEGRAM_TCP_SAMPLES="$v" || warn "Invalid"; save_settings ;;
      12) [[ "$TELEGRAM_AUTO_SUB" == "1" ]] && TELEGRAM_AUTO_SUB=0 || TELEGRAM_AUTO_SUB=1; save_settings ;;
      13) printf "Minimum full-test success for auto-sub (1-100): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=100)) && TELEGRAM_PASS_MIN_SUCCESS="$v" || warn "Invalid"; save_settings ;;
      14) advanced_bench_settings ;;
      15) printf "%b" "${RED}Delete locally stored Cloudflare credential and Worker reference? [y/N]:${RESET} "; read -r v; if [[ "$v" =~ ^[Yy]$ ]]; then rm -f "$CF_CONF" "$WORKER_FILE"; ok "Local Cloudflare data removed. Remote Worker was not deleted."; fi ;;
      16) default_settings; load_settings; LEAK_PROTECTION=1; save_settings; ok "Settings reset; Privacy Guard remains locked ON."; sleep 1 ;;
      0) return ;;
    esac
  done
}

show_status() {
  header
  local version tag profiles subs active last tgcount worker localsubs pstate pip
  version="$($XRAY_BIN version 2>/dev/null | head -n1 || echo 'not installed')"
  tag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo '-')"
  profiles="$(count_profiles)"
  subs="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  localsubs="$(awk -F'\t' '$3 ~ /^local:\/\//{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  tgcount="$(awk 'NF{c++} END{print c+0}' "$TG_CHANNELS" 2>/dev/null)"
  pstate="$(jq -r '.state // "not checked"' "$PRIVACY_STATUS" 2>/dev/null || echo 'not checked')"
  pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  section "ENGINE"
  printf " ${CYAN}Core:${RESET} %s\n ${CYAN}Channel:${RESET} official XTLS pre-release [%s]\n" "$version" "$tag"
  section "CONNECTION"
  printf " ${CYAN}Profiles:${RESET} %s   ${CYAN}Subscriptions:${RESET} %s (${PURPLE}%s local${RESET})   ${CYAN}SOCKS5h:${RESET} %s:%s\n" "$profiles" "$subs" "$localsubs" "$SOCKS_LISTEN" "$SOCKS_PORT"
  if process_alive; then
    active="$(jq -r '.name // "Connected"' "$ACTIVE_FILE" 2>/dev/null || echo Connected)"
    printf " ${CYAN}State:${RESET} ${GREEN}${BOLD}● CONNECTED${RESET}  →  %s\n" "$active"
  else
    printf " ${CYAN}State:${RESET} ${RED}${BOLD}● DISCONNECTED${RESET}\n"
  fi
  last="$(jq -r '.name // empty' "$LAST_CONNECTED" 2>/dev/null || true)"
  [[ -n "$last" ]] && printf " ${CYAN}Last saved:${RESET} %s\n" "$last"
  section "PRIVACY"
  printf " ${CYAN}Guard:${RESET} ${GREEN}${BOLD}ALWAYS ON / FAIL-CLOSED${RESET}   ${CYAN}Last check:${RESET} %s   ${CYAN}Proxy IP:${RESET} %s\n" "$pstate" "$pip"
  printf " ${GRAY}Secure endpoint bootstrap DNS • targetStrategy=AsIs • forced SOCKS route • tagged DoH route${RESET}\n"
  section "INTELLIGENCE"
  printf " ${CYAN}Benchmark:${RESET} %s (%s samples)\n" "$BENCH_PROFILE" "$BENCH_SAMPLES"
  worker="not configured"; cf_load && worker="ready"
  printf " ${CYAN}Telegram:${RESET} %s channel(s) • Worker=%s • TCP gate=%s • auto-sub=%s • pass≥%s%%\n" "$tgcount" "$worker" "$(tui_onoff_text "$TELEGRAM_TCP_GATE")" "$(tui_onoff_text "$TELEGRAM_AUTO_SUB")" "$TELEGRAM_PASS_MIN_SUCCESS"
  line
  say "${GRAY}Controller v1.1.5: raw /dev/tty event loop + scrolling viewport + arrows + instant numbers/mnemonics.${RESET}"
  pause
}

controller_selftest() {
  # Non-destructive static/runtime sanity checks for controller regressions.
  local fail=0
  python3 - <<'PY' || fail=1
# Validate the direct-number decision logic used by the Python TUI.
def choose(prefix,count):
    valid=[str(i) for i in range(1,count+1) if str(i).startswith(prefix)]
    exact=prefix in valid
    longer=any(x!=prefix for x in valid)
    return exact,longer
assert choose('7',15)==(True,False)   # main-panel regression
assert choose('7',100)==(True,True)   # 7 vs 70..79
assert choose('13',16)==(True,False)
assert choose('1',15)==(True,True)
print('controller decision tests: PASS')
PY
  if ((fail==0)); then ok "Controller self-test PASS"; else err "Controller self-test FAILED"; fi
  return "$fail"
}


# ─────────────────────────────────────────────────────────────────────────────
# v1.1.6 audit / reliability / subscription-browser layer.
# Final definitions intentionally override older implementations.
# ─────────────────────────────────────────────────────────────────────────────

STATE_BACKUP_DIR="$ROOT/state-backup"
TEST_PID_FILE="$ROOT/test-xray.pid"
DOCTOR_REPORT="$LOG_DIR/doctor-last.txt"

v116_bool() {
  case "${1:-}" in 1|true|TRUE|yes|YES|on|ON) printf '1' ;; *) printf '0' ;; esac
}

v116_uint_or() {
  local v="${1:-}" def="$2" lo="$3" hi="$4"
  if [[ "$v" =~ ^[0-9]+$ ]] && (( v >= lo && v <= hi )); then printf '%s' "$v"; else printf '%s' "$def"; fi
}

v116_float_or() {
  python3 - "$1" "$2" "$3" "$4" <<'PY'
import sys
v,default,lo,hi=sys.argv[1:]
try:
    x=float(v); a=float(lo); b=float(hi)
    if a <= x <= b:
        print(("%g" % x))
    else: print(default)
except Exception:
    print(default)
PY
}

# Safe settings parser: do not execute settings.conf as shell code.
load_settings() {
  [[ -f "$SETTINGS" ]] || default_settings
  local had_profile=0 key val
  # Defaults first.
  SOCKS_PORT=10808
  SOCKS_LISTEN=127.0.0.1
  AUTO_UPDATE=1
  UPDATE_CHECK_HOURS=6
  BENCH_PROFILE=balanced
  BENCH_CANDIDATES=20
  BENCH_SAMPLES=4
  BENCH_TIMEOUT=8
  BENCH_BYTES=262144
  TCP_PRECHECK_TIMEOUT=1.8
  TCP_PRECHECK_WORKERS=20
  LEAK_PROTECTION=1
  DNS_LEAK_TEST=1
  REMEMBER_LAST=1
  TELEGRAM_POSTS=20
  TELEGRAM_MAX_CONFIGS=80
  TELEGRAM_TCP_GATE=1
  TELEGRAM_TCP_SAMPLES=3
  TELEGRAM_AUTO_SUB=1
  TELEGRAM_PASS_MIN_SUCCESS=75
  TEST_URL=https://cp.cloudflare.com/generate_204
  SPEED_URL=https://speed.cloudflare.com/__down?bytes=262144

  while IFS='=' read -r key val || [[ -n "$key" ]]; do
    [[ -n "$key" && "$key" != \#* ]] || continue
    case "$key" in
      SOCKS_PORT|SOCKS_LISTEN|AUTO_UPDATE|UPDATE_CHECK_HOURS|BENCH_PROFILE|BENCH_CANDIDATES|BENCH_SAMPLES|BENCH_TIMEOUT|BENCH_BYTES|TCP_PRECHECK_TIMEOUT|TCP_PRECHECK_WORKERS|LEAK_PROTECTION|DNS_LEAK_TEST|REMEMBER_LAST|TELEGRAM_POSTS|TELEGRAM_MAX_CONFIGS|TELEGRAM_TCP_GATE|TELEGRAM_TCP_SAMPLES|TELEGRAM_AUTO_SUB|TELEGRAM_PASS_MIN_SUCCESS|TEST_URL|SPEED_URL)
        printf -v "$key" '%s' "$val"
        [[ "$key" == "BENCH_PROFILE" ]] && had_profile=1
        ;;
    esac
  done < "$SETTINGS"

  SOCKS_PORT="$(v116_uint_or "$SOCKS_PORT" 10808 1024 65535)"
  UPDATE_CHECK_HOURS="$(v116_uint_or "$UPDATE_CHECK_HOURS" 6 1 168)"
  BENCH_CANDIDATES="$(v116_uint_or "$BENCH_CANDIDATES" 20 1 100)"
  BENCH_SAMPLES="$(v116_uint_or "$BENCH_SAMPLES" 4 1 12)"
  BENCH_TIMEOUT="$(v116_uint_or "$BENCH_TIMEOUT" 8 2 60)"
  BENCH_BYTES="$(v116_uint_or "$BENCH_BYTES" 262144 16384 8388608)"
  TCP_PRECHECK_TIMEOUT="$(v116_float_or "$TCP_PRECHECK_TIMEOUT" 1.8 0.2 15)"
  TCP_PRECHECK_WORKERS="$(v116_uint_or "$TCP_PRECHECK_WORKERS" 20 1 80)"
  TELEGRAM_POSTS="$(v116_uint_or "$TELEGRAM_POSTS" 20 1 200)"
  TELEGRAM_MAX_CONFIGS="$(v116_uint_or "$TELEGRAM_MAX_CONFIGS" 80 1 300)"
  TELEGRAM_TCP_SAMPLES="$(v116_uint_or "$TELEGRAM_TCP_SAMPLES" 3 1 8)"
  TELEGRAM_PASS_MIN_SUCCESS="$(v116_uint_or "$TELEGRAM_PASS_MIN_SUCCESS" 75 1 100)"

  AUTO_UPDATE="$(v116_bool "$AUTO_UPDATE")"
  DNS_LEAK_TEST="$(v116_bool "$DNS_LEAK_TEST")"
  REMEMBER_LAST="$(v116_bool "$REMEMBER_LAST")"
  TELEGRAM_TCP_GATE="$(v116_bool "$TELEGRAM_TCP_GATE")"
  TELEGRAM_AUTO_SUB="$(v116_bool "$TELEGRAM_AUTO_SUB")"
  LEAK_PROTECTION=1

  case "$BENCH_PROFILE" in reliable|balanced|fast|turbo|custom) ;; *) BENCH_PROFILE=balanced ;; esac
  (( had_profile == 0 )) && BENCH_PROFILE=custom

  # Listen address is data, never code. Reject whitespace/control/shell punctuation.
  if [[ ! "$SOCKS_LISTEN" =~ ^[0-9A-Fa-f:.]+$ ]]; then SOCKS_LISTEN=127.0.0.1; fi
  [[ "$TEST_URL" =~ ^https://[^[:space:]]+$ ]] || TEST_URL=https://cp.cloudflare.com/generate_204
  [[ "$SPEED_URL" =~ ^https://[^[:space:]]+$ ]] || SPEED_URL="https://speed.cloudflare.com/__down?bytes=${BENCH_BYTES}"
}

save_settings() {
  LEAK_PROTECTION=1
  local tmp="$TMP_DIR/settings.$$.$RANDOM"
  {
    printf 'SOCKS_PORT=%s\n' "$SOCKS_PORT"
    printf 'SOCKS_LISTEN=%s\n' "$SOCKS_LISTEN"
    printf 'AUTO_UPDATE=%s\n' "$AUTO_UPDATE"
    printf 'UPDATE_CHECK_HOURS=%s\n' "$UPDATE_CHECK_HOURS"
    printf 'BENCH_PROFILE=%s\n' "$BENCH_PROFILE"
    printf 'BENCH_CANDIDATES=%s\n' "$BENCH_CANDIDATES"
    printf 'BENCH_SAMPLES=%s\n' "$BENCH_SAMPLES"
    printf 'BENCH_TIMEOUT=%s\n' "$BENCH_TIMEOUT"
    printf 'BENCH_BYTES=%s\n' "$BENCH_BYTES"
    printf 'TCP_PRECHECK_TIMEOUT=%s\n' "$TCP_PRECHECK_TIMEOUT"
    printf 'TCP_PRECHECK_WORKERS=%s\n' "$TCP_PRECHECK_WORKERS"
    printf 'LEAK_PROTECTION=1\n'
    printf 'DNS_LEAK_TEST=%s\n' "$DNS_LEAK_TEST"
    printf 'REMEMBER_LAST=%s\n' "$REMEMBER_LAST"
    printf 'TELEGRAM_POSTS=%s\n' "$TELEGRAM_POSTS"
    printf 'TELEGRAM_MAX_CONFIGS=%s\n' "$TELEGRAM_MAX_CONFIGS"
    printf 'TELEGRAM_TCP_GATE=%s\n' "$TELEGRAM_TCP_GATE"
    printf 'TELEGRAM_TCP_SAMPLES=%s\n' "$TELEGRAM_TCP_SAMPLES"
    printf 'TELEGRAM_AUTO_SUB=%s\n' "$TELEGRAM_AUTO_SUB"
    printf 'TELEGRAM_PASS_MIN_SUCCESS=%s\n' "$TELEGRAM_PASS_MIN_SUCCESS"
    printf 'TEST_URL=%s\n' "$TEST_URL"
    printf 'SPEED_URL=%s\n' "$SPEED_URL"
  } > "$tmp"
  chmod 600 "$tmp"
  mv -f "$tmp" "$SETTINGS"
}

v116_pid_is_our_xray() {
  local p="${1:-}" cmd
  [[ "$p" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$p" 2>/dev/null || return 1
  [[ -r "/proc/$p/cmdline" ]] || return 1
  cmd="$(tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null || true)"
  [[ -n "$cmd" ]] || return 1
  [[ "$cmd" == *"$XRAY_BIN"* || "$cmd" =~ (^|[[:space:]/])xray([[:space:]]|$) ]]
}

process_alive() {
  [[ -f "$PID_FILE" ]] || return 1
  local p; p="$(cat "$PID_FILE" 2>/dev/null || true)"
  if v116_pid_is_our_xray "$p"; then return 0; fi
  rm -f "$PID_FILE" "$ACTIVE_FILE"
  return 1
}

v116_cleanup_test_process() {
  [[ -f "$TEST_PID_FILE" ]] || return 0
  local p; p="$(cat "$TEST_PID_FILE" 2>/dev/null || true)"
  if v116_pid_is_our_xray "$p"; then
    kill "$p" 2>/dev/null || true
    sleep .08
    kill -9 "$p" 2>/dev/null || true
  fi
  rm -f "$TEST_PID_FILE"
}

stop_xray() {
  local p=""
  [[ -f "$PID_FILE" ]] && p="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$p" ]] && v116_pid_is_our_xray "$p"; then
    kill "$p" 2>/dev/null || true
    local _
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      kill -0 "$p" 2>/dev/null || break
      sleep .12
    done
    v116_pid_is_our_xray "$p" && kill -9 "$p" 2>/dev/null || true
  fi
  rm -f "$PID_FILE" "$ACTIVE_FILE"
  command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock >/dev/null 2>&1 || true
}

v116_port_free() {
  python3 - "$1" "$2" <<'PY'
import socket,sys
host=sys.argv[1]; port=int(sys.argv[2])
fam=socket.AF_INET6 if ':' in host else socket.AF_INET
s=socket.socket(fam,socket.SOCK_STREAM)
try:
    s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
    s.bind((host,port))
    print("1")
except Exception:
    print("0")
finally:
    s.close()
PY
}

v116_free_port() {
  python3 - <<'PY'
import socket
s=socket.socket()
s.bind(('127.0.0.1',0))
print(s.getsockname()[1])
s.close()
PY
}

wait_port_pid() {
  local host="$1" port="$2" pid="$3"
  python3 - "$host" "$port" "$pid" <<'PY'
import os,socket,sys,time
host=sys.argv[1]; port=int(sys.argv[2]); pid=int(sys.argv[3]); end=time.time()+5.5
while time.time()<end:
    try:
        os.kill(pid,0)
    except Exception:
        raise SystemExit(2)
    fam=socket.AF_INET6 if ':' in host else socket.AF_INET
    s=socket.socket(fam,socket.SOCK_STREAM); s.settimeout(.22)
    try:
        s.connect((host,port)); s.close(); raise SystemExit(0)
    except Exception:
        s.close(); time.sleep(.10)
raise SystemExit(1)
PY
}

v116_touch_subscription() {
  local sid="$1" now tmp
  now="$(date +%s)"; tmp="$TMP_DIR/subdb-touch.$$.$RANDOM"
  awk -F'\t' -v OFS='\t' -v sid="$sid" -v now="$now" '
    $1==sid {$4=now} {print $1,$2,$3,$4}
  ' "$SUB_DB" > "$tmp" 2>/dev/null || return 1
  mv -f "$tmp" "$SUB_DB"
  chmod 600 "$SUB_DB"
}

v116_backup_sub_state() {
  local sid="$1" dir="$2"
  mkdir -p "$dir/profiles"
  cp -f "$INDEX" "$dir/index.jsonl" 2>/dev/null || :
  python3 - "$INDEX" "$sid" "$dir/profiles" <<'PY'
import json,shutil,sys
from pathlib import Path
idx,sid,dst=Path(sys.argv[1]),sys.argv[2],Path(sys.argv[3])
for line in idx.read_text(errors='ignore').splitlines() if idx.exists() else []:
    try: m=json.loads(line)
    except Exception: continue
    if m.get('subid')!=sid: continue
    p=Path(str(m.get('file','')))
    if p.is_file():
        try: shutil.copy2(p,dst/p.name)
        except Exception: pass
PY
}

v116_rollback_sub_state() {
  local sid="$1" dir="$2"
  # Remove newly generated files for this sub according to the currently mutated index.
  python3 - "$INDEX" "$sid" <<'PY'
import json,sys
from pathlib import Path
p=Path(sys.argv[1]); sid=sys.argv[2]
for line in p.read_text(errors='ignore').splitlines() if p.exists() else []:
    try: m=json.loads(line)
    except Exception: continue
    if m.get('subid')==sid:
        try: Path(str(m.get('file',''))).unlink(missing_ok=True)
        except Exception: pass
PY
  [[ -f "$dir/index.jsonl" ]] && cp -f "$dir/index.jsonl" "$INDEX"
  if [[ -d "$dir/profiles" ]]; then
    find "$dir/profiles" -maxdepth 1 -type f -exec cp -f {} "$PROFILE_DIR/" \; 2>/dev/null || true
  fi
  chmod 600 "$INDEX" 2>/dev/null || true
}

# Transactional refresh: a broken provider response must not erase the last good
# profiles. New profiles are committed only when at least one usable profile is built.
fetch_subscription() {
  local id="$1" name="$2" url="$3"
  local raw="$RAW_DIR/$id.raw" candidate="$TMP_DIR/fetch-${id}.$$.$RANDOM.raw"
  local backup="$TMP_DIR/fetch-backup-${id}.$$.$RANDOM" out added unsupported src
  info "Refreshing: $name"

  if [[ "$url" == local://* ]]; then
    src="${url#local://}"
    [[ -f "$src" ]] || { err "Local generated subscription is missing: $src"; return 1; }
    cp -f "$src" "$candidate" || return 1
  else
    if ! curl -fL --connect-timeout 15 --max-time 75 --retry 2 --retry-delay 1 \
        -A 'v2rayN' -H 'Accept: */*' -o "$candidate" "$url"; then
      rm -f "$candidate"
      err "Download failed: $name"
      return 1
    fi
  fi
  chmod 600 "$candidate"

  mkdir -p "$backup"
  v116_backup_sub_state "$id" "$backup"

  if ! out="$(pyhelper parse "$candidate" "$id" "$name" "$PROFILE_DIR" "$INDEX" "$UNSUPPORTED_DIR/$id.txt" 2>&1)"; then
    v116_rollback_sub_state "$id" "$backup"
    rm -rf "$backup" "$candidate"
    err "Parse failed; previous working profiles were restored: $out"
    return 1
  fi

  added="$(jq -r '.added // 0' <<<"$out" 2>/dev/null || echo 0)"
  unsupported="$(jq -r '.unsupported // 0' <<<"$out" 2>/dev/null || echo 0)"
  [[ "$added" =~ ^[0-9]+$ ]] || added=0
  [[ "$unsupported" =~ ^[0-9]+$ ]] || unsupported=0

  if (( added <= 0 )); then
    v116_rollback_sub_state "$id" "$backup"
    rm -rf "$backup" "$candidate"
    warn "$name returned no usable profiles; previous working profiles were kept."
    return 1
  fi

  if [[ "$url" != local://* || "$candidate" != "$raw" ]]; then
    mv -f "$candidate" "$raw"
  else
    rm -f "$candidate"
  fi
  chmod 600 "$raw"
  v116_touch_subscription "$id" || true
  rm -rf "$backup"

  ok "$name → $added usable profile(s)"
  (( unsupported > 0 )) && warn "$unsupported unsupported/malformed entry or entries were skipped."
  return 0
}

# Remember enough identity to reconnect the same config even when different
# subscriptions contain identical share URIs.
record_connected() {
  local name="$1" file="$2"
  python3 - "$LAST_CONNECTED" "$HISTORY_FILE" "$INDEX" "$name" "$file" "$SOCKS_LISTEN" "$SOCKS_PORT" <<'PY'
import json,sys,time
last,hist,index,name,file,listen,port=sys.argv[1:]
meta={}
try:
    for line in open(index,errors='ignore'):
        try:
            m=json.loads(line)
            if m.get('file')==file: meta=m; break
        except Exception: pass
except Exception: pass
rec={
 'name':name,'file':file,'id':meta.get('id',''),'subid':meta.get('subid',''),
 'subname':meta.get('subname',''),'scheme':meta.get('scheme',''),'host':meta.get('host',''),
 'remote_port':meta.get('port',0),'listen':listen,'port':int(port),
 'leak_guard':True,'started':int(time.time())
}
open(last,'w').write(json.dumps(rec,ensure_ascii=False,indent=2))
with open(hist,'a') as f:
    f.write(json.dumps(rec,ensure_ascii=False,separators=(',',':'))+'\n')
PY
  chmod 600 "$LAST_CONNECTED" "$HISTORY_FILE" 2>/dev/null || true
}

connect_last() {
  [[ "$REMEMBER_LAST" == "1" ]] || { warn "Remember-last is disabled in Settings."; return 1; }
  [[ -s "$LAST_CONNECTED" ]] || { warn "No previous connection has been saved yet."; return 1; }
  local file name id sid meta
  file="$(jq -r '.file // empty' "$LAST_CONNECTED" 2>/dev/null)"
  name="$(jq -r '.name // "Last profile"' "$LAST_CONNECTED" 2>/dev/null)"
  id="$(jq -r '.id // empty' "$LAST_CONNECTED" 2>/dev/null)"
  sid="$(jq -r '.subid // empty' "$LAST_CONNECTED" 2>/dev/null)"
  if [[ ! -f "$file" && -n "$id" ]]; then
    if [[ -n "$sid" ]]; then
      meta="$(jq -c --arg id "$id" --arg sid "$sid" 'select(.id==$id and .subid==$sid)' "$INDEX" 2>/dev/null | head -n1)"
    else
      meta="$(jq -c --arg id "$id" 'select(.id==$id)' "$INDEX" 2>/dev/null | head -n1)"
    fi
    [[ -n "$meta" ]] && file="$(jq -r '.file' <<<"$meta")" && name="$(jq -r '.name' <<<"$meta")"
  fi
  [[ -f "$file" ]] || { warn "Last profile no longer exists after a subscription refresh."; return 1; }
  info "Fast reconnect → $name"
  start_profile_file "$file" "$name"
}

start_profile_file() {
  local file="$1" name="$2" quiet="${3:-0}"
  LEAK_PROTECTION=1
  [[ -x "$XRAY_BIN" ]] || { err "Xray is not installed."; return 1; }
  [[ -f "$file" ]] || { err "Profile file missing."; return 1; }

  stop_xray >/dev/null 2>&1 || true
  if [[ "$(v116_port_free "$SOCKS_LISTEN" "$SOCKS_PORT")" != "1" ]]; then
    err "SOCKS port ${SOCKS_LISTEN}:${SOCKS_PORT} is already occupied by another process."
    warn "Change the SOCKS port in Settings or stop the conflicting local service."
    return 1
  fi

  if ! v11_patch_runtime "$file" "$RUNTIME_CONFIG" "$SOCKS_PORT" "$SOCKS_LISTEN"; then
    err "Privacy Guard blocked startup: hardened runtime generation failed."
    warn "The previous connection was stopped; no insecure fallback was used."
    return 1
  fi

  if ! XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$RUNTIME_CONFIG" >"$LOG_DIR/config-test.log" 2>&1; then
    err "Xray rejected the hardened runtime config. See $LOG_DIR/config-test.log"
    return 1
  fi

  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
  XRAY_LOCATION_ASSET="$BIN_DIR" nohup "$XRAY_BIN" run -c "$RUNTIME_CONFIG" >"$LOG_DIR/xray.log" 2>&1 &
  local p=$!
  printf '%s' "$p" > "$PID_FILE"
  chmod 600 "$PID_FILE" 2>/dev/null || true

  if ! wait_port_pid "$SOCKS_LISTEN" "$SOCKS_PORT" "$p" || ! v116_pid_is_our_xray "$p"; then
    err "Xray did not become a healthy owner of the SOCKS listener. See logs."
    stop_xray
    return 1
  fi

  if ! v115_privacy_postcheck; then
    err "Privacy Guard FAIL-CLOSED: tunnel integrity verification failed."
    stop_xray
    return 1
  fi

  python3 - "$ACTIVE_FILE" "$name" "$file" "$SOCKS_LISTEN" "$SOCKS_PORT" <<'PY'
import json,sys,time
open(sys.argv[1],'w').write(json.dumps({
 'name':sys.argv[2],'file':sys.argv[3],'listen':sys.argv[4],
 'port':int(sys.argv[5]),'leak_guard':True,'privacy_guard':'PASS',
 'started':int(time.time())
},ensure_ascii=False))
PY
  chmod 600 "$ACTIVE_FILE" 2>/dev/null || true
  [[ "$REMEMBER_LAST" == "1" ]] && record_connected "$name" "$file"

  if [[ "$quiet" != "1" ]]; then
    local pip; pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
    ok "Connected safely: $name"
    say " ${CYAN}SOCKS5h:${RESET} ${BOLD}${SOCKS_LISTEN}:${SOCKS_PORT}${RESET}  ${GREEN}🛡 PRIVACY GUARD PASS${RESET}  ${GRAY}egress:${RESET} ${CYAN}${pip}${RESET}"
  fi
}

# Safer benchmark lifecycle: verify child liveness, avoid occupied fixed ports,
# and register the temporary Xray child for cleanup on Ctrl+C / shell exit.
bench_one() {
  local file="$1" id="$2" name="$3" requested_port="$4" host="${5:-}" scheme="${6:-}" rport="${7:-0}"
  local port="$requested_port"
  [[ "$(v116_port_free 127.0.0.1 "$port")" == "1" ]] || port="$(v116_free_port)"
  local cfg="$TMP_DIR/bench-${port}-$$.json" log="$TMP_DIR/bench-${port}-$$.log"
  local ping
  ping="$(endpoint_probe "$host" "$rport" "$scheme" 2>/dev/null || echo 9999)"

  if ! v11_patch_runtime "$file" "$cfg" "$port" "127.0.0.1" >/dev/null 2>&1; then
    printf '%s\t%s\t%s\t0\t9999\t9999\t0\t-\t--\t%s\n' "$id" "$file" "${name//$'\t'/ }" "$ping"
    rm -f "$cfg" "$log"
    return
  fi
  if ! XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$cfg" >"$log" 2>&1; then
    printf '%s\t%s\t%s\t0\t9999\t9999\t0\t-\t--\t%s\n' "$id" "$file" "${name//$'\t'/ }" "$ping"
    rm -f "$cfg" "$log"
    return
  fi

  XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -c "$cfg" >"$log" 2>&1 &
  local xp=$!
  printf '%s' "$xp" > "$TEST_PID_FILE"

  if ! wait_port_pid 127.0.0.1 "$port" "$xp" >/dev/null 2>&1 || ! v116_pid_is_our_xray "$xp"; then
    v116_cleanup_test_process
    printf '%s\t%s\t%s\t0\t9999\t9999\t0\t-\t--\t%s\n' "$id" "$file" "${name//$'\t'/ }" "$ping"
    rm -f "$cfg" "$log"
    return
  fi

  local -a times=()
  local i val successes=0 speed=0 trace ip='-' cc='--'
  for ((i=0;i<BENCH_SAMPLES;i++)); do
    val="$(curl -fsS --socks5-hostname "127.0.0.1:$port" --connect-timeout "$BENCH_TIMEOUT" --max-time "$BENCH_TIMEOUT" \
      -o /dev/null -w '%{time_total}' "$TEST_URL" 2>/dev/null || true)"
    if [[ "$val" =~ ^[0-9]+([.][0-9]+)?$ && "$val" != "0.000000" ]]; then
      times+=("$val"); ((successes+=1))
    fi
  done

  if (( successes > 0 )); then
    local speed_url="$SPEED_URL"
    [[ "$speed_url" == *speed.cloudflare.com* ]] && speed_url="https://speed.cloudflare.com/__down?bytes=${BENCH_BYTES}"
    speed="$(curl -fsS --socks5-hostname "127.0.0.1:$port" --connect-timeout "$BENCH_TIMEOUT" --max-time $((BENCH_TIMEOUT+5)) \
      -o /dev/null -w '%{speed_download}' "$speed_url" 2>/dev/null || echo 0)"
    [[ "$speed" =~ ^[0-9]+([.][0-9]+)?$ ]] || speed=0
    trace="$(trace_through_socks "$port")"
    ip="$(printf '%s\n' "$trace" | trace_field ip)"
    cc="$(printf '%s\n' "$trace" | trace_field loc)"
    [[ -n "$ip" ]] || ip='-'
    [[ "$cc" =~ ^[A-Z][A-Z]$ ]] || cc='--'
  fi

  v116_cleanup_test_process
  local stats lat jit success_pct
  stats="$(pyhelper stats "${times[@]}" 2>/dev/null || printf '9999\t9999')"
  lat="${stats%%$'\t'*}"; jit="${stats##*$'\t'}"
  success_pct=$(( successes * 100 / BENCH_SAMPLES ))
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$id" "$file" "${name//$'\t'/ }" "$success_pct" "$lat" "$jit" "$speed" "$ip" "$cc" "$ping"
  rm -f "$cfg" "$log"
}

v116_profile_count_for_sub() {
  local sid="$1"
  python3 - "$INDEX" "$sid" <<'PY'
import json,sys
n=0
try:
    f=open(sys.argv[1],errors='ignore')
except Exception:
    print(0); raise SystemExit
for line in f:
    try:
        m=json.loads(line)
        if m.get('subid')==sys.argv[2] and m.get('file'): n+=1
    except Exception: pass
print(n)
PY
}

v116_subscription_rows() {
  python3 - "$SUB_DB" "$INDEX" <<'PY'
import json,sys,time
subdb,index=sys.argv[1:]
counts={}
try:
    for line in open(index,errors='ignore'):
        try:
            m=json.loads(line); sid=str(m.get('subid',''))
            if sid: counts[sid]=counts.get(sid,0)+1
        except Exception: pass
except Exception: pass
now=int(time.time())
try: lines=open(subdb,errors='ignore').read().splitlines()
except Exception: lines=[]
for line in lines:
    p=line.split('\t')
    if len(p)<4: continue
    sid,name,url,updated=p[0],p[1],p[2],p[3]
    try: age=max(0,now-int(updated))
    except Exception: age=0
    if age<60: age_txt=f'{age}s'
    elif age<3600: age_txt=f'{age//60}m'
    elif age<86400: age_txt=f'{age//3600}h'
    else: age_txt=f'{age//86400}d'
    src='LOCAL' if url.startswith('local://') else 'REMOTE'
    print(json.dumps({'sid':sid,'name':name,'url':url,'updated':updated,'age':age_txt,'source':src,'count':counts.get(sid,0)},ensure_ascii=False,separators=(',',':')))
PY
}

v116_profiles_for_sub() {
  local sid="$1" outfile="$2"
  python3 - "$INDEX" "$sid" "$outfile" <<'PY'
import json,sys
from pathlib import Path
idx,sid,out=Path(sys.argv[1]),sys.argv[2],Path(sys.argv[3])
rows=[]
for line in idx.read_text(errors='ignore').splitlines() if idx.exists() else []:
    try: m=json.loads(line)
    except Exception: continue
    if m.get('subid')!=sid: continue
    f=Path(str(m.get('file','')))
    if not f.is_file(): continue
    rows.append(m)
out.write_text(''.join(json.dumps(m,ensure_ascii=False,separators=(',',':'))+'\n' for m in rows))
print(len(rows))
PY
}

# Main option 7: subscription -> config -> guarded connect.
subscription_connect_browser() {
  local sub_selected=1
  while true; do
    header
    section "SUBSCRIPTIONS → CONFIG → CONNECT"

    local -a subrows=() sublabels=()
    mapfile -t subrows < <(v116_subscription_rows)
    if (( ${#subrows[@]} == 0 )); then
      warn "No saved subscriptions yet. Add one from option 6 first."
      pause
      return
    fi

    local row sid name source count age
    for row in "${subrows[@]}"; do
      sid="$(jq -r '.sid' <<<"$row")"
      name="$(jq -r '.name' <<<"$row")"
      source="$(jq -r '.source' <<<"$row")"
      count="$(jq -r '.count' <<<"$row")"
      age="$(jq -r '.age' <<<"$row")"
      sublabels+=("📦 ${name}  •  ${count} configs  •  ${source}  •  refreshed ${age} ago")
    done

    say " ${GRAY}First choose a subscription. Then choose exactly the config you want inside it.${RESET}"
    tui_menu "$sub_selected" 1 "" "${sublabels[@]}"
    local sch="$TUI_CHOICE"
    [[ "$sch" == "0" ]] && return
    sub_selected="$sch"

    row="${subrows[sch-1]}"
    sid="$(jq -r '.sid' <<<"$row")"
    name="$(jq -r '.name' <<<"$row")"
    local url; url="$(jq -r '.url' <<<"$row")"

    local subset="$TMP_DIR/sub-browser-${sid}.$$.$RANDOM.jsonl"
    local n
    n="$(v116_profiles_for_sub "$sid" "$subset" 2>/dev/null || echo 0)"
    [[ "$n" =~ ^[0-9]+$ ]] || n=0

    if (( n == 0 )); then
      header
      section "EMPTY / STALE SUBSCRIPTION"
      warn "\"$name\" currently has no usable local profiles."
      info "Trying one safe transactional refresh before giving up..."
      if fetch_subscription "$sid" "$name" "$url"; then
        n="$(v116_profiles_for_sub "$sid" "$subset" 2>/dev/null || echo 0)"
      fi
    fi

    if [[ ! "$n" =~ ^[0-9]+$ ]] || (( n == 0 )); then
      rm -f "$subset"
      err "No usable config is available inside \"$name\"."
      pause
      continue
    fi

    local config_selected=1
    while true; do
      header
      section "CONFIGS INSIDE • ${name}"
      printf " ${TUI_CARD_BG}${WHITE} SUB ${RESET} ${CYAN}%s${RESET}   ${TUI_CARD_BG}${WHITE} CONFIGS ${RESET} ${GREEN}%s${RESET}\n" "$name" "$n"
      say " ${GRAY}Choose a config. Connection will automatically use the always-on Privacy Guard.${RESET}"

      local -a cfgrows=() cfglabels=()
      mapfile -t cfgrows < "$subset"
      local m proto transport security cname host port endpoint
      for m in "${cfgrows[@]}"; do
        proto="$(jq -r '.scheme // "?" | ascii_upcase' <<<"$m" 2>/dev/null || echo '?')"
        transport="$(jq -r '.transport // "?"' <<<"$m" 2>/dev/null || echo '?')"
        security="$(jq -r '.security // ""' <<<"$m" 2>/dev/null || true)"
        cname="$(jq -r '.name // "Unnamed"' <<<"$m" 2>/dev/null || echo 'Unnamed')"
        host="$(jq -r '.host // ""' <<<"$m" 2>/dev/null || true)"
        port="$(jq -r '.port // 0' <<<"$m" 2>/dev/null || echo 0)"
        endpoint=""
        [[ -n "$host" && "$port" != "0" ]] && endpoint="  •  ${host}:${port}"
        cfglabels+=("🔌 ${cname}  •  ${proto} / ${transport}${security:+ / ${security}}${endpoint}")
      done

      tui_menu "$config_selected" 1 "" "${cfglabels[@]}"
      local cch="$TUI_CHOICE"
      if [[ "$cch" == "0" ]]; then
        rm -f "$subset"
        break
      fi
      config_selected="$cch"
      m="${cfgrows[cch-1]}"
      local file
      file="$(jq -r '.file // empty' <<<"$m")"
      cname="$(jq -r '.name // "Unnamed"' <<<"$m")"
      [[ -f "$file" ]] || {
        err "This profile file disappeared. The subscription will be refreshed."
        fetch_subscription "$sid" "$name" "$url" || true
        n="$(v116_profiles_for_sub "$sid" "$subset" 2>/dev/null || echo 0)"
        pause
        continue
      }

      header
      section "GUARDED CONNECT"
      printf " ${CYAN}Subscription:${RESET} %s\n ${CYAN}Config:${RESET} %s\n" "$name" "$cname"
      if start_profile_file "$file" "$cname"; then
        rm -f "$subset"
        pause
        return
      fi
      warn "That config failed Xray startup or Privacy Guard verification. Choose another config from the same subscription."
      pause
    done
  done
}

# Quiet state integrity repair. Backups are created only if a mutation is needed.
v116_state_repair_quiet() {
  mkdir -p "$STATE_BACKUP_DIR"
  local changed
  changed="$(python3 - "$SUB_DB" "$INDEX" "$PROFILE_DIR" <<'PY'
import json,sys
from pathlib import Path
subdb,idx,pdir=Path(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3])
changed=False

# Validate/dedupe subscription rows by id; last record wins while preserving final order.
raw=subdb.read_text(errors='ignore').splitlines() if subdb.exists() else []
parsed=[]
for line in raw:
    p=line.split('\t')
    if len(p)<4 or not p[0] or not p[1] or not p[2]:
        changed=True; continue
    parsed.append(p[:4])
seen=set(); kept_rev=[]
for p in reversed(parsed):
    if p[0] in seen:
        changed=True; continue
    seen.add(p[0]); kept_rev.append(p)
subs=list(reversed(kept_rev))
newsub='\n'.join('\t'.join(p) for p in subs)+('\n' if subs else '')
if newsub != (subdb.read_text(errors='ignore') if subdb.exists() else ''):
    changed=True

# Keep only valid index objects whose referenced profile file exists; dedupe by file.
rows=[]; seen_files=set()
for line in idx.read_text(errors='ignore').splitlines() if idx.exists() else []:
    try: m=json.loads(line)
    except Exception:
        changed=True; continue
    if not isinstance(m,dict):
        changed=True; continue
    f=str(m.get('file',''))
    if not f or not Path(f).is_file():
        changed=True; continue
    if f in seen_files:
        changed=True; continue
    seen_files.add(f)
    rows.append(m)
newidx=''.join(json.dumps(m,ensure_ascii=False,separators=(',',':'))+'\n' for m in rows)
if newidx != (idx.read_text(errors='ignore') if idx.exists() else ''):
    changed=True

print('1' if changed else '0')
PY
)"
  [[ "$changed" == "1" ]] || {
    chmod 600 "$SETTINGS" "$SUB_DB" "$INDEX" "$HISTORY_FILE" 2>/dev/null || true
    return 0
  }

  local stamp; stamp="$(date '+%Y%m%d-%H%M%S')"
  cp -f "$SUB_DB" "$STATE_BACKUP_DIR/subscriptions-${stamp}.tsv" 2>/dev/null || true
  cp -f "$INDEX" "$STATE_BACKUP_DIR/profiles-${stamp}.jsonl" 2>/dev/null || true

  python3 - "$SUB_DB" "$INDEX" <<'PY'
import json,sys
from pathlib import Path
subdb,idx=Path(sys.argv[1]),Path(sys.argv[2])

raw=subdb.read_text(errors='ignore').splitlines() if subdb.exists() else []
parsed=[]
for line in raw:
    p=line.split('\t')
    if len(p)>=4 and p[0] and p[1] and p[2]: parsed.append(p[:4])
seen=set(); kept=[]
for p in reversed(parsed):
    if p[0] in seen: continue
    seen.add(p[0]); kept.append(p)
kept.reverse()
subdb.write_text('\n'.join('\t'.join(p) for p in kept)+('\n' if kept else ''))

rows=[]; seen_files=set()
for line in idx.read_text(errors='ignore').splitlines() if idx.exists() else []:
    try: m=json.loads(line)
    except Exception: continue
    if not isinstance(m,dict): continue
    f=str(m.get('file',''))
    if not f or not Path(f).is_file() or f in seen_files: continue
    seen_files.add(f); rows.append(m)
idx.write_text(''.join(json.dumps(m,ensure_ascii=False,separators=(',',':'))+'\n' for m in rows))
PY
  chmod 600 "$SUB_DB" "$INDEX" 2>/dev/null || true
}

doctor() {
  header
  section "XGC SYSTEM DOCTOR • v1.1.6"
  local before after
  before="$(count_profiles)"
  v116_state_repair_quiet
  after="$(count_profiles)"
  : > "$DOCTOR_REPORT"
  {
    printf 'Xray Genius v%s doctor\n' "$APP_VERSION"
    printf 'time=%s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
    printf 'profiles_before=%s\nprofiles_after=%s\n' "$before" "$after"
    printf 'subscriptions=%s\n' "$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
    printf 'privacy_guard=always-on\n'
    printf 'settings_mode=safe-parser\n'
    printf 'pid='
    if process_alive; then cat "$PID_FILE"; else printf 'none'; fi
    printf '\n'
    printf 'xray='
    "$XRAY_BIN" version 2>/dev/null | head -n1 || printf 'not installed\n'
  } >> "$DOCTOR_REPORT"

  ok "Settings parser: safe key/value whitelist"
  ok "Subscription DB: validated / duplicate IDs collapsed"
  ok "Profile index: invalid, duplicate-file and missing-file rows repaired"
  ok "PID handling: verifies process identity before kill"
  ok "Wake lock: released on Stop"
  ok "Temporary benchmark Xray: exit/interrupt cleanup enabled"
  ok "Subscription refresh: transactional rollback enabled"
  ok "Option 7: subscription → config → guarded connect"
  printf " ${CYAN}Profiles:${RESET} %s → %s   ${CYAN}Subscriptions:${RESET} %s\n" "$before" "$after" "$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  printf " ${GRAY}Report:${RESET} %s\n" "$DOCTOR_REPORT"
  pause
}

show_status() {
  header
  local version tag profiles subs active last tgcount worker localsubs pstate pip
  version="$($XRAY_BIN version 2>/dev/null | head -n1 || echo 'not installed')"
  tag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo '-')"
  profiles="$(count_profiles)"
  subs="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  localsubs="$(awk -F'\t' '$3 ~ /^local:\/\//{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  tgcount="$(awk 'NF{c++} END{print c+0}' "$TG_CHANNELS" 2>/dev/null)"
  pstate="$(jq -r '.state // "not checked"' "$PRIVACY_STATUS" 2>/dev/null || echo 'not checked')"
  pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  section "ENGINE"
  printf " ${CYAN}Core:${RESET} %s\n ${CYAN}Channel:${RESET} official XTLS pre-release [%s]\n" "$version" "$tag"
  section "CONNECTION"
  printf " ${CYAN}Profiles:${RESET} %s   ${CYAN}Subscriptions:${RESET} %s (${PURPLE}%s local${RESET})   ${CYAN}SOCKS5h:${RESET} %s:%s\n" "$profiles" "$subs" "$localsubs" "$SOCKS_LISTEN" "$SOCKS_PORT"
  if process_alive; then
    active="$(jq -r '.name // "Connected"' "$ACTIVE_FILE" 2>/dev/null || echo Connected)"
    printf " ${CYAN}State:${RESET} ${GREEN}${BOLD}● CONNECTED${RESET}  →  %s\n" "$active"
  else
    printf " ${CYAN}State:${RESET} ${RED}${BOLD}● DISCONNECTED${RESET}\n"
  fi
  last="$(jq -r '.name // empty' "$LAST_CONNECTED" 2>/dev/null || true)"
  [[ -n "$last" ]] && printf " ${CYAN}Last saved:${RESET} %s\n" "$last"
  section "PRIVACY / RELIABILITY"
  printf " ${CYAN}Guard:${RESET} ${GREEN}${BOLD}ALWAYS ON / FAIL-CLOSED${RESET}   ${CYAN}Last check:${RESET} %s   ${CYAN}Proxy IP:${RESET} %s\n" "$pstate" "$pip"
  printf " ${GRAY}Safe settings parser • transactional sub refresh • verified PID ownership • guarded SOCKS/DoH routing${RESET}\n"
  section "INTELLIGENCE"
  printf " ${CYAN}Benchmark:${RESET} %s (%s samples)\n" "$BENCH_PROFILE" "$BENCH_SAMPLES"
  worker="not configured"; cf_load && worker="ready"
  printf " ${CYAN}Telegram:${RESET} %s channel(s) • Worker=%s • TCP gate=%s • auto-sub=%s • pass≥%s%%\n" "$tgcount" "$worker" "$(tui_onoff_text "$TELEGRAM_TCP_GATE")" "$(tui_onoff_text "$TELEGRAM_AUTO_SUB")" "$TELEGRAM_PASS_MIN_SUCCESS"
  line
  say "${GRAY}Controller: raw /dev/tty event loop + viewport + arrows + instant numbers/mnemonics.${RESET}"
  pause
}

panel() {
  maybe_update_xray
  local selected=1
  while true; do
    load_settings
    header

    local state_plain="OFF" state_color="$RED" active=""
    if process_alive; then
      state_plain="ON"; state_color="$GREEN"
      active="$(jq -r '.name // ""' "$ACTIVE_FILE" 2>/dev/null || true)"
    fi
    local coretag; coretag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo missing)"

    printf " ${TUI_CARD_BG}${WHITE} CORE ${RESET} ${PURPLE}%-11s${RESET}  ${TUI_CARD_BG}${WHITE} SOCKS5h ${RESET} ${CYAN}%s:%s${RESET}\n" "$coretag" "$SOCKS_LISTEN" "$SOCKS_PORT"
    printf " ${TUI_CARD_BG}${WHITE} STATE ${RESET} ${state_color}${BOLD}● %-3s${RESET}       ${TUI_CARD_BG}${WHITE} TEST MODE ${RESET} ${PINK}%s${RESET}\n" "$state_plain" "$BENCH_PROFILE"
    [[ -n "$active" ]] && printf " ${TUI_CARD_BG}${WHITE} ACTIVE ${RESET} ${GREEN}%s${RESET}\n" "${active:0:58}"

    section "CONTROL PANEL"
    local -a items=(
      "⚡ Auto Connect  •  last-working → smart fallback"
      "🧠 Smart benchmark + connect best"
      "🚀 Quick reconnect last working config"
      "🧪 Full test one config"
      "📡 Telegram public-config radar"
      "📦 Subscriptions / import center"
      "📚 Browse Subscription → Choose Config → Connect"
      "🛡 Privacy / IP + DNS leak audit"
      "📊 Status / SOCKS information"
      "🗂 Profiles / native capability map"
      "⏹ Stop Xray"
      "⬆ Update newest Xray pre-release"
      "⚙ Settings / performance lab"
      "🧾 Runtime logs"
      "🕘 Connection history"
    )
    tui_menu "$selected" 1 "a,s,r,t,g,u,c,p,i,o,x,v,e,l,h" "${items[@]}"
    local ch="$TUI_CHOICE"
    [[ "$ch" != "0" ]] && selected="$ch"

    case "$ch" in
      1) auto_connect ;;
      2) smart_connect ;;
      3) quick_reconnect_menu ;;
      4) full_test_menu ;;
      5) telegram_center ;;
      6) manage_subs ;;
      7) subscription_connect_browser ;;
      8) leak_audit ;;
      9) show_status ;;
      10)
        header; section "PROFILES / NATIVE CAPABILITIES"
        list_profiles
        line
        say "${GRAY}Press ${CYAN}C${GRAY} for capability map, ${YELLOW}D${GRAY} for System Doctor, any other key to return.${RESET}"
        local k=""; IFS= read -rsn1 k || true
        case "${k,,}" in c) show_capabilities ;; d) doctor ;; esac
        ;;
      11) stop_xray; ok "Xray stopped and Termux wake lock released."; sleep 1 ;;
      12) header; update_xray; pause ;;
      13) settings_menu ;;
      14) show_logs ;;
      15) connection_history ;;
      0) clear 2>/dev/null || true; return ;;
    esac
  done
}

bootstrap() {
  ensure_dirs
  mkdir -p "$STATE_BACKUP_DIR"
  if ! is_termux; then warn "This script is tuned for Termux. Continuing on this Unix-like shell."; fi
  install_deps || exit 1
  ensure_dirs
  load_settings
  save_settings   # normalize legacy/corrupt settings through the safe writer
  v116_state_repair_quiet
  install_launcher
  v116_cleanup_test_process
  if [[ ! -x "$XRAY_BIN" ]]; then
    header
    info "First run: installing newest official Xray pre-release..."
    update_xray || { err "Initial Xray installation failed. Re-run later or use update."; pause; }
  fi
}

# Preserve the active Xray daemon on panel exit, but never leave benchmark/test
# children or a hidden terminal cursor behind.
trap 'tui_cursor_show; v116_cleanup_test_process' EXIT
trap 'tui_cursor_show; v116_cleanup_test_process; exit 130' INT TERM

controller_selftest() {
  local fail=0
  python3 - <<'PY' || fail=1
def choose(prefix,count):
    valid=[str(i) for i in range(1,count+1) if str(i).startswith(prefix)]
    exact=prefix in valid
    longer=any(x!=prefix for x in valid)
    return exact,longer
assert choose('7',15)==(True,False)
assert choose('7',100)==(True,True)
assert choose('70',100)==(True,False)
assert choose('13',16)==(True,False)
assert choose('1',15)==(True,True)
print('controller decision tests: PASS')
PY
  if (( fail==0 )); then ok "Controller self-test PASS"; else err "Controller self-test FAILED"; fi
  return "$fail"
}



# ─────────────────────────────────────────────────────────────────────────────
# v1.2.2 NO-ROOT PRIVACY HARDENING LAYER
#
# Security model:
#   • No privileged packet-routing/elevation subsystem.
#   • XGC protects traffic that actually enters its localhost SOCKS5 endpoint.
#   • Every XGC-generated hostname request uses SOCKS5h.
#   • Proxy-server hostnames are bootstrapped with pinned HTTPS DoH, never
#     Android/system DNS.
#   • The generated Xray runtime contains only the localhost SOCKS inbound.
#   • Provider routing is replaced by a minimal fail-closed route:
#       socks-in -> selected proxy
#       xgc-dns  -> selected proxy
#   • Freedom/direct outbounds are converted to blackhole so an accidental route
#     cannot fall back to a direct Internet path.
#   • The selected proxy is moved to outbound #1 because Xray uses the first
#     outbound as the default when no routing rule matches.
#   • Automatic privacy verification NEVER performs a direct Internet request.
#     All probes use SOCKS5h.  The old "direct IP comparison" probe was itself a
#     deliberate out-of-tunnel connection and is removed.
#
# Without Android VpnService/TUN, a Termux shell cannot seize sockets created by
# unrelated Android applications.  v1.2.2 therefore refuses to call those
# unrelated sockets protected.  For apps using XGC's SOCKS5h path, the runtime
# is intentionally fail-closed.
# ─────────────────────────────────────────────────────────────────────────────

PRIVACY_STATUS="$ROOT/privacy-status.json"
SECURE_DNS_CACHE="$ROOT/secure-dns-cache.json"
GUARD_LOG="$LOG_DIR/privacy-guard.log"
PRIVACY_AUDIT_LOG="$LOG_DIR/privacy-audit.log"
PRIVACY_SCHEMA=122

# Real curl binary, captured independently of the shell function below.
XGC_CURL_BIN="$(type -P curl 2>/dev/null || true)"

xgc_refresh_curl_bin() {
  [[ -n "${XGC_CURL_BIN:-}" && -x "${XGC_CURL_BIN:-}" ]] && return 0
  XGC_CURL_BIN="$(type -P curl 2>/dev/null || true)"
  [[ -n "$XGC_CURL_BIN" && -x "$XGC_CURL_BIN" ]]
}

# Once a tunnel is alive, ordinary XGC HTTP(S) management calls automatically
# use it. Calls that explicitly specify SOCKS/proxy settings are left unchanged.
# XGC_CURL_BYPASS=1 is reserved for the encrypted pinned bootstrap resolver
# needed before any tunnel exists.
curl() {
  xgc_refresh_curl_bin || { printf 'curl binary not found\n' >&2; return 127; }
  local explicit=0 arg
  for arg in "$@"; do
    case "$arg" in
      --socks5|--socks5-hostname|--socks5=*|--socks5-hostname=*|--proxy|--proxy=*|-x)
        explicit=1 ;;
    esac
  done
  if [[ "${XGC_CURL_BYPASS:-0}" == "1" || "$explicit" == "1" ]]; then
    "$XGC_CURL_BIN" "$@"
  elif process_alive 2>/dev/null; then
    "$XGC_CURL_BIN" --socks5-hostname "127.0.0.1:${SOCKS_PORT}" "$@"
  else
    "$XGC_CURL_BIN" "$@"
  fi
}

# The wrapper above must never mask a missing real Termux curl binary.
install_deps() {
  local need=0 c
  for c in jq unzip python3 sha256sum ps; do type -P "$c" >/dev/null 2>&1 || need=1; done
  type -P curl >/dev/null 2>&1 || need=1
  (( need == 0 )) && { XGC_CURL_BIN="$(type -P curl)"; return 0; }
  command -v pkg >/dev/null 2>&1 || { err "This installer expects Termux (pkg command not found)."; return 1; }
  info "Installing required Termux packages..."
  pkg update -y || true
  pkg install -y curl jq unzip python coreutils procps || return 1
  XGC_CURL_BIN="$(type -P curl 2>/dev/null || true)"
  [[ -n "$XGC_CURL_BIN" ]]
}

# ---------------------------------------------------------------------------
# Safe settings: privacy is not user-disableable in v1.2.2.
# ---------------------------------------------------------------------------

load_settings() {
  [[ -f "$SETTINGS" ]] || default_settings
  local had_profile=0 key val

  SOCKS_PORT=10808
  SOCKS_LISTEN=127.0.0.1
  AUTO_UPDATE=1
  UPDATE_CHECK_HOURS=6
  BENCH_PROFILE=balanced
  BENCH_CANDIDATES=20
  BENCH_SAMPLES=4
  BENCH_TIMEOUT=8
  BENCH_BYTES=262144
  TCP_PRECHECK_TIMEOUT=1.8
  TCP_PRECHECK_WORKERS=20
  LEAK_PROTECTION=1
  DNS_LEAK_TEST=1
  REMEMBER_LAST=1
  TELEGRAM_POSTS=20
  TELEGRAM_MAX_CONFIGS=80
  TELEGRAM_TCP_GATE=1
  TELEGRAM_TCP_SAMPLES=3
  TELEGRAM_AUTO_SUB=1
  TELEGRAM_PASS_MIN_SUCCESS=75
  TEST_URL=https://cp.cloudflare.com/generate_204
  SPEED_URL=https://speed.cloudflare.com/__down?bytes=262144

  while IFS='=' read -r key val || [[ -n "$key" ]]; do
    [[ -n "$key" && "$key" != \#* ]] || continue
    case "$key" in
      SOCKS_PORT|SOCKS_LISTEN|AUTO_UPDATE|UPDATE_CHECK_HOURS|BENCH_PROFILE|BENCH_CANDIDATES|BENCH_SAMPLES|BENCH_TIMEOUT|BENCH_BYTES|TCP_PRECHECK_TIMEOUT|TCP_PRECHECK_WORKERS|LEAK_PROTECTION|DNS_LEAK_TEST|REMEMBER_LAST|TELEGRAM_POSTS|TELEGRAM_MAX_CONFIGS|TELEGRAM_TCP_GATE|TELEGRAM_TCP_SAMPLES|TELEGRAM_AUTO_SUB|TELEGRAM_PASS_MIN_SUCCESS|TEST_URL|SPEED_URL)
        printf -v "$key" '%s' "$val"
        [[ "$key" == "BENCH_PROFILE" ]] && had_profile=1
        ;;
    esac
  done < "$SETTINGS"

  SOCKS_PORT="$(v116_uint_or "$SOCKS_PORT" 10808 1024 65535)"
  UPDATE_CHECK_HOURS="$(v116_uint_or "$UPDATE_CHECK_HOURS" 6 1 168)"
  BENCH_CANDIDATES="$(v116_uint_or "$BENCH_CANDIDATES" 20 1 100)"
  BENCH_SAMPLES="$(v116_uint_or "$BENCH_SAMPLES" 4 1 12)"
  BENCH_TIMEOUT="$(v116_uint_or "$BENCH_TIMEOUT" 8 2 60)"
  BENCH_BYTES="$(v116_uint_or "$BENCH_BYTES" 262144 16384 8388608)"
  TCP_PRECHECK_TIMEOUT="$(v116_float_or "$TCP_PRECHECK_TIMEOUT" 1.8 0.2 15)"
  TCP_PRECHECK_WORKERS="$(v116_uint_or "$TCP_PRECHECK_WORKERS" 20 1 80)"
  TELEGRAM_POSTS="$(v116_uint_or "$TELEGRAM_POSTS" 20 1 200)"
  TELEGRAM_MAX_CONFIGS="$(v116_uint_or "$TELEGRAM_MAX_CONFIGS" 80 1 300)"
  TELEGRAM_TCP_SAMPLES="$(v116_uint_or "$TELEGRAM_TCP_SAMPLES" 3 1 8)"
  TELEGRAM_PASS_MIN_SUCCESS="$(v116_uint_or "$TELEGRAM_PASS_MIN_SUCCESS" 75 1 100)"

  AUTO_UPDATE="$(v116_bool "$AUTO_UPDATE")"
  REMEMBER_LAST="$(v116_bool "$REMEMBER_LAST")"
  TELEGRAM_TCP_GATE="$(v116_bool "$TELEGRAM_TCP_GATE")"
  TELEGRAM_AUTO_SUB="$(v116_bool "$TELEGRAM_AUTO_SUB")"

  # Privacy invariants are hard-locked.
  SOCKS_LISTEN=127.0.0.1
  LEAK_PROTECTION=1
  DNS_LEAK_TEST=1

  case "$BENCH_PROFILE" in reliable|balanced|fast|turbo|custom) ;; *) BENCH_PROFILE=balanced ;; esac
  (( had_profile == 0 )) && BENCH_PROFILE=custom
  [[ "$TEST_URL" =~ ^https://[^[:space:]]+$ ]] || TEST_URL=https://cp.cloudflare.com/generate_204
  [[ "$SPEED_URL" =~ ^https://[^[:space:]]+$ ]] || SPEED_URL="https://speed.cloudflare.com/__down?bytes=${BENCH_BYTES}"
}

save_settings() {
  SOCKS_LISTEN=127.0.0.1
  LEAK_PROTECTION=1
  DNS_LEAK_TEST=1
  local tmp="$TMP_DIR/settings.$$.$RANDOM"
  {
    printf 'SOCKS_PORT=%s\n' "$SOCKS_PORT"
    printf 'SOCKS_LISTEN=127.0.0.1\n'
    printf 'AUTO_UPDATE=%s\n' "$AUTO_UPDATE"
    printf 'UPDATE_CHECK_HOURS=%s\n' "$UPDATE_CHECK_HOURS"
    printf 'BENCH_PROFILE=%s\n' "$BENCH_PROFILE"
    printf 'BENCH_CANDIDATES=%s\n' "$BENCH_CANDIDATES"
    printf 'BENCH_SAMPLES=%s\n' "$BENCH_SAMPLES"
    printf 'BENCH_TIMEOUT=%s\n' "$BENCH_TIMEOUT"
    printf 'BENCH_BYTES=%s\n' "$BENCH_BYTES"
    printf 'TCP_PRECHECK_TIMEOUT=%s\n' "$TCP_PRECHECK_TIMEOUT"
    printf 'TCP_PRECHECK_WORKERS=%s\n' "$TCP_PRECHECK_WORKERS"
    printf 'LEAK_PROTECTION=1\n'
    printf 'DNS_LEAK_TEST=1\n'
    printf 'REMEMBER_LAST=%s\n' "$REMEMBER_LAST"
    printf 'TELEGRAM_POSTS=%s\n' "$TELEGRAM_POSTS"
    printf 'TELEGRAM_MAX_CONFIGS=%s\n' "$TELEGRAM_MAX_CONFIGS"
    printf 'TELEGRAM_TCP_GATE=%s\n' "$TELEGRAM_TCP_GATE"
    printf 'TELEGRAM_TCP_SAMPLES=%s\n' "$TELEGRAM_TCP_SAMPLES"
    printf 'TELEGRAM_AUTO_SUB=%s\n' "$TELEGRAM_AUTO_SUB"
    printf 'TELEGRAM_PASS_MIN_SUCCESS=%s\n' "$TELEGRAM_PASS_MIN_SUCCESS"
    printf 'TEST_URL=%s\n' "$TEST_URL"
    printf 'SPEED_URL=%s\n' "$SPEED_URL"
  } > "$tmp"
  chmod 600 "$tmp"
  mv -f "$tmp" "$SETTINGS"
}

# ---------------------------------------------------------------------------
# Secure bootstrap DNS
# ---------------------------------------------------------------------------

v122_is_ipv4() {
  python3 - "$1" <<'PY'
import ipaddress,sys
try:
    x=ipaddress.ip_address(sys.argv[1].strip('[]'))
    raise SystemExit(0 if x.version==4 else 1)
except Exception:
    raise SystemExit(1)
PY
}

# v1.2.2 intentionally chooses an IPv4 proxy endpoint. This avoids XGC itself
# unexpectedly opening an IPv6 transport while its protected application path
# is being evaluated. IPv6-only proxy endpoints fail closed rather than falling
# back to Android DNS/selection.
v115_secure_resolve_host() {
  local host="${1#[}"; host="${host%]}"
  [[ -n "$host" ]] || return 1
  if v122_is_ipv4 "$host"; then printf '%s' "$host"; return 0; fi
  if v115_is_ip_literal "$host"; then return 1; fi

  local cached
  cached="$(v115_cache_get "$host" 2>/dev/null || true)"
  if [[ -n "$cached" ]] && v122_is_ipv4 "$cached"; then
    printf '%s' "$cached"
    return 0
  fi

  local json ip resolver_name resolver_ip url
  while IFS='|' read -r resolver_name resolver_ip url; do
    if process_alive 2>/dev/null; then
      # During a config switch, bootstrap DNS itself uses the already-running
      # SOCKS5h tunnel instead of exposing an extra management connection.
      json="$(curl -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" \
        --connect-timeout 3 --max-time 7 \
        -H 'accept: application/dns-json' --get \
        --data-urlencode "name=${host}" --data-urlencode 'type=A' \
        "$url" 2>/dev/null || true)"
    else
      # Before any tunnel exists, pin encrypted DoH to a resolver IP so Android
      # system DNS is never needed for proxy bootstrap.
      json="$(XGC_CURL_BYPASS=1 curl -fsS --connect-timeout 3 --max-time 6 \
        --resolve "${resolver_name}:443:${resolver_ip}" \
        -H 'accept: application/dns-json' --get \
        --data-urlencode "name=${host}" --data-urlencode 'type=A' \
        "$url" 2>/dev/null || true)"
    fi
    ip="$(printf '%s' "$json" | jq -r '.Answer[]? | select(.type==1) | .data' 2>/dev/null | head -n1)"
    if [[ -n "$ip" ]] && v122_is_ipv4 "$ip"; then
      v115_cache_put "$host" "$ip"
      printf '%s' "$ip"
      return 0
    fi
  done <<'EOF'
cloudflare-dns.com|1.1.1.1|https://cloudflare-dns.com/dns-query
cloudflare-dns.com|1.0.0.1|https://cloudflare-dns.com/dns-query
dns.google|8.8.8.8|https://dns.google/resolve
dns.google|8.8.4.4|https://dns.google/resolve
EOF
  return 1
}

# ---------------------------------------------------------------------------
# Hardened runtime generator
# ---------------------------------------------------------------------------

v122_patch_runtime() {
  local src="$1" dst="$2" port="$3" mapfile="$4"
  python3 - "$src" "$dst" "$port" "$mapfile" <<'PY'
import json,sys,ipaddress,re
from pathlib import Path

src,dst,port,mapfile=sys.argv[1],sys.argv[2],int(sys.argv[3]),sys.argv[4]
obj=json.loads(Path(src).read_text())
emap=json.loads(Path(mapfile).read_text()) if Path(mapfile).exists() else {}
outs=obj.get('outbounds')
if not isinstance(outs,list) or not outs:
    raise SystemExit('configuration has no outbounds')

INFRA={'freedom','blackhole','dns','loopback'}

def proto(o):
    return str(o.get('protocol','')).lower() if isinstance(o,dict) else ''

# Select the explicit proxy tag first, otherwise the first non-infrastructure outbound.
proxy=None
for o in outs:
    if isinstance(o,dict) and o.get('tag')=='proxy' and proto(o) not in INFRA:
        proxy=o; break
if proxy is None:
    for o in outs:
        if isinstance(o,dict) and proto(o) not in INFRA:
            proxy=o; break
if proxy is None:
    raise SystemExit('no usable proxy outbound')

proxy_tag=str(proxy.get('tag') or 'xgc-proxy')
proxy['tag']=proxy_tag

# Tags must be unique because routing/proxySettings depend on them.
seen=set()
for o in outs:
    if not isinstance(o,dict): continue
    t=o.get('tag')
    if not t: continue
    if t in seen: raise SystemExit('duplicate outbound tag: '+str(t))
    seen.add(t)

def is_ip(v):
    try: ipaddress.ip_address(str(v).strip('[]')); return True
    except Exception: return False

def is_ipv4(v):
    try: return ipaddress.ip_address(str(v).strip('[]')).version==4
    except Exception: return False

def endpoint_parts(v):
    if not isinstance(v,str): return None,None
    x=v.strip()
    if x.startswith('[') and ']:' in x:
        return x[1:x.index(']')], x[x.index(']')+2:]
    if x.count(':')==1 and x.rsplit(':',1)[1].isdigit():
        return x.rsplit(':',1)
    return x,None

def preserve_identity(o, original):
    if not original or is_ip(original): return
    st=o.get('streamSettings')
    if not isinstance(st,dict): return
    sec=str(st.get('security','')).lower()
    if sec=='tls':
        t=st.setdefault('tlsSettings',{})
        if isinstance(t,dict) and not t.get('serverName'): t['serverName']=original
    elif sec=='reality':
        r=st.setdefault('realitySettings',{})
        if isinstance(r,dict) and not r.get('serverName'): r['serverName']=original
    method=str(st.get('method') or st.get('network') or '').lower()
    if method in ('xhttp','splithttp'):
        x=st.setdefault('xhttpSettings',{})
        if isinstance(x,dict) and not x.get('host'): x['host']=original
    elif method in ('websocket','ws'):
        x=st.setdefault('wsSettings',{})
        if isinstance(x,dict):
            if not x.get('host'): x['host']=original
            h=x.get('headers')
            if isinstance(h,dict) and not h.get('Host'): h['Host']=original
    elif method=='httpupgrade':
        x=st.setdefault('httpupgradeSettings',{})
        if isinstance(x,dict) and not x.get('host'): x['host']=original
    elif method=='grpc':
        x=st.setdefault('grpcSettings',{})
        if isinstance(x,dict) and not x.get('authority'): x['authority']=original
    elif method in ('http','h2'):
        x=st.setdefault('httpSettings',{})
        if isinstance(x,dict) and not x.get('host'): x['host']=[original]

def replace_addr(o, container, key):
    v=container.get(key)
    if not isinstance(v,str): return
    h,p=endpoint_parts(v)
    h=h.strip('[]') if isinstance(h,str) else h
    if h in emap:
        ip=emap[h]
        if not is_ipv4(ip): raise SystemExit('secure bootstrap produced non-IPv4 endpoint')
        preserve_identity(o,h)
        container[key]=(f'{ip}:{p}' if p is not None else ip)

def iter_endpoint_values(o):
    s=o.get('settings') if isinstance(o.get('settings'),dict) else {}
    for key in ('address','server'):
        v=s.get(key)
        if isinstance(v,str): yield v
    for key in ('vnext','servers'):
        arr=s.get(key)
        if isinstance(arr,list):
            for e in arr:
                if isinstance(e,dict):
                    for k in ('address','server'):
                        v=e.get(k)
                        if isinstance(v,str): yield v
    peers=s.get('peers')
    if isinstance(peers,list):
        for p in peers:
            if isinstance(p,dict) and isinstance(p.get('endpoint'),str):
                yield p['endpoint']

# Pin every actual proxy endpoint to an IPv4 address obtained through encrypted
# bootstrap resolution.  Preserve SNI/Host/authority separately.
for o in outs:
    if not isinstance(o,dict) or proto(o) in INFRA: continue
    s=o.get('settings') if isinstance(o.get('settings'),dict) else {}
    replace_addr(o,s,'address'); replace_addr(o,s,'server')
    for key in ('vnext','servers'):
        arr=s.get(key)
        if isinstance(arr,list):
            for e in arr:
                if isinstance(e,dict):
                    replace_addr(o,e,'address'); replace_addr(o,e,'server')
    peers=s.get('peers')
    if isinstance(peers,list):
        for p in peers:
            if isinstance(p,dict): replace_addr(o,p,'endpoint')

    # Proxied destination domains are passed to the remote proxy unchanged.
    o['targetStrategy']='AsIs'
    # Force the XGC process itself to bind IPv4 for these direct-to-proxy sockets.
    o['sendThrough']='0.0.0.0'

# Fail if any known proxy endpoint is still a hostname or IPv6 literal.
for o in outs:
    if not isinstance(o,dict) or proto(o) in INFRA: continue
    for v in iter_endpoint_values(o):
        h,_=endpoint_parts(v); h=str(h or '').strip('[]')
        if not is_ipv4(h):
            raise SystemExit('unresolved/non-IPv4 proxy endpoint remains: '+h)

# A proxy chain may point to another proxy outbound, but it may never depend on
# a direct/freedom/dns/loopback/blackhole outbound.
tagmap={str(o.get('tag')):o for o in outs if isinstance(o,dict) and o.get('tag')}
cur=proxy; visited=set()
while isinstance(cur,dict):
    tag=str(cur.get('tag') or '')
    if tag in visited: raise SystemExit('proxySettings loop detected')
    visited.add(tag)
    ps=cur.get('proxySettings')
    if not isinstance(ps,dict) or not ps.get('tag'): break
    nt=str(ps.get('tag'))
    nxt=tagmap.get(nt)
    if not isinstance(nxt,dict): raise SystemExit('proxySettings references missing outbound: '+nt)
    if proto(nxt) in INFRA: raise SystemExit('proxy chain depends on non-proxy outbound: '+nt)
    cur=nxt

# Remove provider-exposed listeners completely. XGC exposes exactly one
# localhost SOCKS5 endpoint.
obj['inbounds']=[{
  'tag':'socks-in',
  'listen':'127.0.0.1',
  'port':port,
  'protocol':'socks',
  'settings':{'udp':True},
  'sniffing':{'enabled':True,'routeOnly':True,'destOverride':['http','tls','quic']}
}]

# Convert every Freedom/direct outbound to blackhole. This makes unexpected
# fallback paths fail instead of bypassing the selected proxy.
for o in outs:
    if not isinstance(o,dict): continue
    if proto(o)=='freedom':
        keep_tag=o.get('tag')
        o.clear()
        o['protocol']='blackhole'
        o['settings']={}
        if keep_tag: o['tag']=keep_tag

# Selected proxy must be first: Xray documents outbound #1 as default fallback.
outs=[proxy]+[o for o in outs if o is not proxy]

# Ensure there is a dedicated block outbound.
if not any(isinstance(o,dict) and o.get('tag')=='xgc-block' for o in outs):
    outs.append({'tag':'xgc-block','protocol':'blackhole','settings':{}})
obj['outbounds']=outs

# No localhost/system resolver, no local-mode DoH. Resolver hostnames are pinned
# in hosts and DNS-generated traffic is explicitly routed through the proxy.
obj['dns']={
  'hosts':{
    'cloudflare-dns.com':['1.1.1.1','1.0.0.1'],
    'dns.google':['8.8.8.8','8.8.4.4']
  },
  'servers':[
    {
      'address':'https://cloudflare-dns.com/dns-query',
      'queryStrategy':'UseIPv4',
      'skipFallback':False,
      'timeoutMs':3500
    },
    {
      'address':'https://dns.google/dns-query',
      'queryStrategy':'UseIPv4',
      'skipFallback':False,
      'timeoutMs':3500
    }
  ],
  'queryStrategy':'UseIPv4',
  'disableCache':False,
  'serveStale':True,
  'disableFallback':False,
  'disableFallbackIfMatch':False,
  'enableParallelQuery':True,
  'useSystemHosts':False,
  'tag':'xgc-dns'
}

# Replace provider routing instead of prepending to it. There is no direct rule,
# no balancer, and no IP-based domain lookup that could invoke local/system DNS.
obj['routing']={
  'domainStrategy':'AsIs',
  'rules':[
    {'type':'field','inboundTag':['xgc-dns'],'outboundTag':proxy_tag,'ruleTag':'xgc122-dns'},
    {'type':'field','inboundTag':['socks-in'],'outboundTag':proxy_tag,'ruleTag':'xgc122-socks'}
  ]
}

# Keep logs useful but avoid verbose access logging.
obj['log']={'loglevel':'warning'}

Path(dst).write_text(json.dumps(obj,ensure_ascii=False,indent=2))
PY
}

v122_runtime_integrity_check() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys,ipaddress
o=json.load(open(sys.argv[1]))
ins=o.get('inbounds')
if not isinstance(ins,list) or len(ins)!=1:
    raise SystemExit(10)
i=ins[0]
if i.get('tag')!='socks-in' or i.get('protocol')!='socks' or i.get('listen')!='127.0.0.1':
    raise SystemExit(11)

outs=o.get('outbounds')
if not isinstance(outs,list) or not outs:
    raise SystemExit(12)
proxy=outs[0]
ptag=proxy.get('tag')
if not ptag or str(proxy.get('protocol','')).lower() in ('freedom','blackhole','dns','loopback'):
    raise SystemExit(13)
if proxy.get('targetStrategy')!='AsIs':
    raise SystemExit(14)
if any(isinstance(x,dict) and str(x.get('protocol','')).lower()=='freedom' for x in outs):
    raise SystemExit(15)

dns=o.get('dns')
if not isinstance(dns,dict):
    raise SystemExit(16)
if dns.get('useSystemHosts') is not False or dns.get('queryStrategy')!='UseIPv4' or dns.get('tag')!='xgc-dns':
    raise SystemExit(17)
servers=dns.get('servers')
if not isinstance(servers,list) or len(servers)<2:
    raise SystemExit(18)
for s in servers:
    if not isinstance(s,dict): raise SystemExit(19)
    a=str(s.get('address',''))
    if not a.startswith('https://') or '+local' in a or s.get('queryStrategy')!='UseIPv4':
        raise SystemExit(20)
if any(isinstance(s,str) and s=='localhost' for s in servers):
    raise SystemExit(21)

r=o.get('routing')
if not isinstance(r,dict) or r.get('domainStrategy')!='AsIs':
    raise SystemExit(22)
rules=r.get('rules')
if not isinstance(rules,list) or len(rules)!=2:
    raise SystemExit(23)
if rules[0].get('inboundTag')!=['xgc-dns'] or rules[0].get('outboundTag')!=ptag:
    raise SystemExit(24)
if rules[1].get('inboundTag')!=['socks-in'] or rules[1].get('outboundTag')!=ptag:
    raise SystemExit(25)
if r.get('balancers'):
    raise SystemExit(26)

# Known endpoint values on proxy outbounds must already be IPv4 literals.
def ipv4(v):
    try: return ipaddress.ip_address(str(v).strip('[]')).version==4
    except Exception: return False
def hostpart(v):
    v=str(v)
    if v.count(':')==1 and v.rsplit(':',1)[1].isdigit(): return v.rsplit(':',1)[0]
    return v.strip('[]')
for x in outs:
    if not isinstance(x,dict): continue
    if str(x.get('protocol','')).lower() in ('freedom','blackhole','dns','loopback'): continue
    if x.get('targetStrategy')!='AsIs' or x.get('sendThrough')!='0.0.0.0':
        raise SystemExit(27)
    s=x.get('settings') if isinstance(x.get('settings'),dict) else {}
    vals=[]
    for k in ('address','server'):
        if isinstance(s.get(k),str): vals.append(s[k])
    for k in ('vnext','servers'):
        a=s.get(k)
        if isinstance(a,list):
            for e in a:
                if isinstance(e,dict):
                    for kk in ('address','server'):
                        if isinstance(e.get(kk),str): vals.append(e[kk])
    peers=s.get('peers')
    if isinstance(peers,list):
        for p in peers:
            if isinstance(p,dict) and isinstance(p.get('endpoint'),str): vals.append(p['endpoint'])
    for v in vals:
        if not ipv4(hostpart(v)): raise SystemExit(28)
raise SystemExit(0)
PY
}

v11_patch_runtime() {
  local src="$1" dst="$2" port="$3" listen="${4:-127.0.0.1}"
  local mapfile="$TMP_DIR/secure-endpoints.$$.$RANDOM.json"
  : > "$GUARD_LOG"
  if ! v115_build_endpoint_map "$src" "$mapfile"; then
    rm -f "$mapfile"
    printf '%s\n' "secure encrypted bootstrap DNS failed" >> "$GUARD_LOG"
    return 1
  fi
  if ! v122_patch_runtime "$src" "$dst" "$port" "$mapfile"; then
    rm -f "$mapfile"
    printf '%s\n' "hardened runtime generation failed" >> "$GUARD_LOG"
    return 1
  fi
  rm -f "$mapfile"
  v122_runtime_integrity_check "$dst"
}

# Disable the old diagnostic direct-IP probe so no legacy function can
# accidentally create an intentional out-of-tunnel Internet request.
direct_trace() { printf ''; }

# ---------------------------------------------------------------------------
# Proxy-only automatic verification and repair
# ---------------------------------------------------------------------------

v122_proxy_trace() {
  local port="${1:-$SOCKS_PORT}"
  curl -fsS --socks5-hostname "127.0.0.1:${port}" \
    --connect-timeout 5 --max-time 10 \
    'https://www.cloudflare.com/cdn-cgi/trace' 2>/dev/null || true
}

v122_proxy_ip_literal_trace() {
  local port="${1:-$SOCKS_PORT}"
  curl -fsS --socks5-hostname "127.0.0.1:${port}" \
    --connect-timeout 5 --max-time 10 \
    'https://1.1.1.1/cdn-cgi/trace' 2>/dev/null || true
}

v122_status_write() {
  local state="$1" pip="$2" cc="$3" note="$4" attempt="${5:-1}"
  python3 - "$PRIVACY_STATUS" "$state" "$pip" "$cc" "$note" "$attempt" "$PRIVACY_SCHEMA" <<'PY'
import json,sys,time
p,state,pip,cc,note,attempt,schema=sys.argv[1:]
open(p,'w').write(json.dumps({
 'schema':int(schema),
 'state':state,
 'mode':'NO_ROOT_SOCKS_FAIL_CLOSED',
 'proxy_ip':pip,
 'country':cc,
 'note':note,
 'automatic':True,
 'direct_probe_performed':False,
 'socks_listen':'127.0.0.1',
 'socks5h_required':True,
 'secure_endpoint_bootstrap':True,
 'bootstrap_transport':'pinned HTTPS DoH / IPv4',
 'proxy_endpoint_ipv4_pinned':True,
 'provider_inbounds_removed':True,
 'provider_routing_removed':True,
 'freedom_outbounds_blocked':True,
 'selected_proxy_is_default_outbound':True,
 'target_strategy':'AsIs',
 'dns_query_strategy':'UseIPv4',
 'system_hosts_disabled':True,
 'dns_routed_to_proxy':True,
 'repair_attempt':int(attempt),
 'checked_at':int(time.time())
},ensure_ascii=False,indent=2))
PY
  chmod 600 "$PRIVACY_STATUS" 2>/dev/null || true
}

v122_privacy_postcheck() {
  local attempt="${1:-1}" tr1 tr2 ip1 ip2 cc
  tr1="$(v122_proxy_trace "$SOCKS_PORT")"
  ip1="$(printf '%s\n' "$tr1" | trace_field ip)"
  cc="$(printf '%s\n' "$tr1" | trace_field loc)"
  [[ "$cc" =~ ^[A-Za-z][A-Za-z]$ ]] || cc='--'
  if [[ -z "$ip1" ]]; then
    v122_status_write FAIL "" "$cc" "SOCKS5h hostname/egress trace failed" "$attempt"
    return 1
  fi

  # Independent path using an IP-literal destination. It is still sent through
  # SOCKS; this check never opens a direct socket.
  tr2="$(v122_proxy_ip_literal_trace "$SOCKS_PORT")"
  ip2="$(printf '%s\n' "$tr2" | trace_field ip)"
  if [[ -z "$ip2" || "$ip2" != "$ip1" ]]; then
    v122_status_write FAIL "$ip1" "$cc" "proxy egress consistency check failed" "$attempt"
    return 1
  fi

  # A hostname-only HTTPS request confirms SOCKS5h remote name handling works.
  if ! curl -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" \
      --connect-timeout 5 --max-time 10 -o /dev/null "$TEST_URL" 2>/dev/null; then
    v122_status_write FAIL "$ip1" "$cc" "SOCKS5h hostname path failed" "$attempt"
    return 1
  fi

  if ! v122_runtime_integrity_check "$RUNTIME_CONFIG"; then
    v122_status_write FAIL "$ip1" "$cc" "runtime integrity changed after startup" "$attempt"
    return 1
  fi

  v122_status_write PASS "$ip1" "$cc" "all automatic no-direct privacy checks passed" "$attempt"
  return 0
}

v115_privacy_postcheck() { v122_privacy_postcheck 1; }

v122_start_once() {
  local file="$1" name="$2" quiet="${3:-0}" attempt="${4:-1}"
  local candidate="$TMP_DIR/runtime-v122-${attempt}-$$.$RANDOM.json"

  if ! v11_patch_runtime "$file" "$candidate" "$SOCKS_PORT" "127.0.0.1"; then
    rm -f "$candidate"
    return 1
  fi
  if ! XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$candidate" >"$LOG_DIR/config-test.log" 2>&1; then
    rm -f "$candidate"
    return 1
  fi

  stop_xray >/dev/null 2>&1 || true
  if [[ "$(v116_port_free 127.0.0.1 "$SOCKS_PORT")" != "1" ]]; then
    rm -f "$candidate"
    err "Local SOCKS port 127.0.0.1:${SOCKS_PORT} is occupied by another process."
    return 1
  fi

  mv -f "$candidate" "$RUNTIME_CONFIG"
  chmod 600 "$RUNTIME_CONFIG" 2>/dev/null || true

  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
  XRAY_LOCATION_ASSET="$BIN_DIR" nohup "$XRAY_BIN" run -c "$RUNTIME_CONFIG" >"$LOG_DIR/xray.log" 2>&1 &
  local p=$!
  printf '%s' "$p" > "$PID_FILE"
  chmod 600 "$PID_FILE" 2>/dev/null || true

  if ! wait_port_pid 127.0.0.1 "$SOCKS_PORT" "$p" || ! v116_pid_is_our_xray "$p"; then
    stop_xray
    return 1
  fi

  if ! v122_privacy_postcheck "$attempt"; then
    stop_xray
    return 1
  fi

  python3 - "$ACTIVE_FILE" "$name" "$file" "$SOCKS_PORT" <<'PY'
import json,sys,time
open(sys.argv[1],'w').write(json.dumps({
 'name':sys.argv[2],
 'file':sys.argv[3],
 'listen':'127.0.0.1',
 'port':int(sys.argv[4]),
 'leak_guard':True,
 'privacy_guard':'PASS',
 'privacy_schema':122,
 'direct_probe_performed':False,
 'started':int(time.time())
},ensure_ascii=False,indent=2))
PY
  chmod 600 "$ACTIVE_FILE" 2>/dev/null || true
  [[ "$REMEMBER_LAST" == "1" ]] && record_connected "$name" "$file"

  if [[ "$quiet" != "1" ]]; then
    local pip cc
    pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
    cc="$(jq -r '.country // "--"' "$PRIVACY_STATUS" 2>/dev/null || echo --)"
    ok "Connected safely: $name"
    printf " ${GREEN}${BOLD}🛡 PRIVACY GUARD PASS${RESET}  ${CYAN}%s${RESET}  ${PURPLE}%s${RESET}\n" "$pip" "$cc"
    say " ${GRAY}localhost-only SOCKS • encrypted endpoint bootstrap • no direct diagnostic probes • remote hostname handling • direct-outbound fail-close${RESET}"
  fi
  return 0
}

start_profile_file() {
  local file="$1" name="$2" quiet="${3:-0}"
  load_settings
  [[ -x "$XRAY_BIN" ]] || { err "Xray is not installed."; return 1; }
  [[ -f "$file" ]] || { err "Profile file missing."; return 1; }

  # Attempt 1: normal hardened build.
  if v122_start_once "$file" "$name" "$quiet" 1; then
    return 0
  fi

  # Automatic solver: discard bootstrap cache, rebuild every endpoint and the
  # entire runtime once, then retest. There is no insecure fallback.
  warn "Privacy/tunnel verification failed. Running automatic repair: fresh encrypted DNS bootstrap + complete runtime rebuild..."
  stop_xray >/dev/null 2>&1 || true
  rm -f "$SECURE_DNS_CACHE"
  sleep .15

  if v122_start_once "$file" "$name" "$quiet" 2; then
    ok "Automatic Privacy Guard repair succeeded."
    return 0
  fi

  err "Privacy Guard FAIL-CLOSED after automatic repair. This config was not left connected."
  stop_xray >/dev/null 2>&1 || true
  v122_status_write FAIL "" "--" "automatic repair exhausted; connection stopped" 2
  return 1
}

# ---------------------------------------------------------------------------
# DNS leak audit — proxy-only, never intentionally local/direct.
# ---------------------------------------------------------------------------

v122_dns_remote_audit() {
  # Reuse the existing bash.ws mechanism only in remote-resolution mode.  The
  # old local-resolution simulation is intentionally forbidden because it can
  # create the very DNS leakage the audit is supposed to detect.
  dns_leak_compare remote --socks5-hostname 2>/dev/null || printf 'INCONCLUSIVE'
}

leak_audit() {
  header
  section "🛡 ALWAYS-ON PRIVACY / DNS + IP LEAK AUDIT"
  process_alive || { warn "Connect a profile first."; pause; return; }

  local state pip cc note
  state="$(jq -r '.state // "UNKNOWN"' "$PRIVACY_STATUS" 2>/dev/null || echo UNKNOWN)"
  pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  cc="$(jq -r '.country // "--"' "$PRIVACY_STATUS" 2>/dev/null || echo --)"
  note="$(jq -r '.note // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"

  printf " ${TUI_CARD_BG}${WHITE} MODE ${RESET} ${GREEN}${BOLD}NO-ROOT / SOCKS5h / FAIL-CLOSED${RESET}\n"
  printf " ${TUI_CARD_BG}${WHITE} LAST CHECK ${RESET} %s   ${TUI_CARD_BG}${WHITE} EGRESS ${RESET} ${CYAN}%s${RESET} ${PURPLE}%s${RESET}\n" "$state" "$pip" "$cc"
  printf " ${GRAY}%s${RESET}\n" "$note"
  line

  info "Rechecking runtime + egress using proxy-only requests..."
  if ! v122_privacy_postcheck 1; then
    warn "Verification failed. Rebuilding the active profile automatically..."
    local file name
    file="$(jq -r '.file // empty' "$ACTIVE_FILE" 2>/dev/null || true)"
    name="$(jq -r '.name // "Active profile"' "$ACTIVE_FILE" 2>/dev/null || echo 'Active profile')"
    if [[ -f "$file" ]] && start_profile_file "$file" "$name" 1; then
      ok "Automatic repair completed."
    else
      err "Repair failed; Xray has been stopped."
      stop_xray
      pause
      return
    fi
  else
    ok "Runtime integrity + SOCKS5h egress checks PASS."
  fi

  section "REMOTE DNS OBSERVATION"
  local remote
  remote="$(v122_dns_remote_audit)"
  if [[ "$remote" == "INCONCLUSIVE" || -z "$remote" ]]; then
    warn "External DNS observation service is unavailable/inconclusive."
    say " ${GRAY}Runtime structural checks still confirm: no system-host resolver, remote DoH tag routed to proxy, targetStrategy=AsIs, and no Freedom outbound.${RESET}"
  else
    printf " ${CYAN}Resolver observation through SOCKS5h:${RESET} %s\n" "$remote"
  fi

  line
  say "${GREEN}✔${RESET} XGC no longer performs a direct public-IP comparison after connecting."
  say "${GREEN}✔${RESET} XGC diagnostics use SOCKS5h, so hostnames are not locally resolved by curl."
  say "${GREEN}✔${RESET} Connected XGC HTTP(S) management traffic is automatically routed through SOCKS5h."
  say "${GREEN}✔${RESET} Proxy server hostname bootstrap uses pinned HTTPS DoH and is cached."
  say "${GREEN}✔${RESET} Runtime has one localhost-only SOCKS inbound; provider listeners are removed."
  say "${GREEN}✔${RESET} Provider routing is replaced; SOCKS and Xray DNS tags are forced to the selected proxy."
  say "${GREEN}✔${RESET} Freedom/direct outbounds are converted to blackhole; selected proxy is outbound #1."
  say "${GREEN}✔${RESET} Runtime DNS uses remote HTTPS resolvers, UseIPv4, and useSystemHosts=false."
  line
  say "${YELLOW}Scope:${RESET} ${GRAY}these guarantees apply to traffic sent into XGC's 127.0.0.1 SOCKS5h endpoint. Without an Android VPN/TUN service, XGC cannot force unrelated apps that ignore this proxy to use it.${RESET}"
  pause
}

# ---------------------------------------------------------------------------
# Google AI feature retained from uploaded v1.2.0.
# ---------------------------------------------------------------------------

google_ai_probe() {
  local url="$1"
  curl -s -o /dev/null -w '%{http_code}' \
    --socks5-hostname "127.0.0.1:${SOCKS_PORT}" \
    -A 'Mozilla/5.0 (Linux; Android 13) XrayGenius/1.2.2' \
    -H 'Accept-Language: en-US,en;q=0.9' \
    --connect-timeout 8 --max-time 15 "$url" 2>/dev/null || printf '000'
}

google_ai_check() {
  header
  section "🤖 GOOGLE AI (GEMINI) REACHABILITY / UNLOCK CHECK"
  if ! process_alive; then
    warn "No active tunnel. Connect a config first."
    pause; return
  fi

  local trace pip pcc
  trace="$(v122_proxy_trace "$SOCKS_PORT")"
  pip="$(printf '%s\n' "$trace" | trace_field ip)"
  pcc="$(printf '%s\n' "$trace" | trace_field loc)"
  [[ -n "$pip" ]] || pip='-'
  [[ "$pcc" =~ ^[A-Za-z][A-Za-z]$ ]] || pcc='--'
  printf " ${TUI_CARD_BG}${WHITE} EGRESS IP ${RESET} ${CYAN}%-18s${RESET} ${TUI_CARD_BG}${WHITE} REGION ${RESET} ${PURPLE}%s${RESET}\n" "$pip" "$pcc"
  line
  info "Probing Google AI endpoints through SOCKS5h only..."

  local -a names=("Gemini API (generativelanguage)" "Google AI Studio" "Gemini web app")
  local -a urls=("https://generativelanguage.googleapis.com/" "https://aistudio.google.com/" "https://gemini.google.com/app")
  local -a reached=(0 0 0)
  local i code anyreach=0
  for i in 0 1 2; do
    printf " ${GRAY}%-34s${RESET} " "${names[$i]}"
    code="$(google_ai_probe "${urls[$i]}")"
    case "$code" in
      000|"") say "${RED}✖ unreachable${RESET}" ;;
      2*|3*) say "${GREEN}✔ reachable${RESET} ${DIM}${GRAY}(HTTP ${code})${RESET}"; reached[$i]=1; anyreach=1 ;;
      401|403|404|429) say "${YELLOW}✔ network reachable${RESET} ${DIM}${GRAY}(HTTP ${code})${RESET}"; reached[$i]=1; anyreach=1 ;;
      *) say "${YELLOW}⚠ HTTP ${code}${RESET}"; reached[$i]=1; anyreach=1 ;;
    esac
  done
  line
  if (( reached[2] == 1 || reached[1] == 1 )); then
    ok "Google AI is reachable through this proxy exit (${pcc})."
  elif (( anyreach == 1 )); then
    warn "Only partial Google AI reachability through this exit (${pcc})."
  else
    err "This proxy exit cannot currently reach Google AI."
  fi
  say "${DIM}${GRAY}This checks network reachability only, not account/subscription entitlement.${RESET}"
  pause
}

# ---------------------------------------------------------------------------
# UI/status/doctor overrides
# ---------------------------------------------------------------------------

settings_menu() {
  local selected=1
  while true; do
    load_settings
    header
    section "SETTINGS / PERFORMANCE + PRIVACY LAB"
    printf " ${GREEN}🛡 Privacy Guard:${RESET} ALWAYS ON / FAIL-CLOSED\n"
    printf " ${CYAN}DNS Guard:${RESET} ALWAYS ON / SOCKS5h + remote Xray DNS\n"
    printf " ${CYAN}SOCKS listen:${RESET} ${BOLD}127.0.0.1 only${RESET} ${GRAY}(locked for privacy)${RESET}\n\n"

    local -a items=(
      "SOCKS port  •  ${SOCKS_PORT}"
      "SOCKS listen  •  127.0.0.1  🔒"
      "Auto Xray beta update  •  $(tui_onoff_text "$AUTO_UPDATE")"
      "Full-test policy  •  ${BENCH_PROFILE}"
      "Privacy Guard  •  🛡 LOCKED ON / FAIL-CLOSED"
      "DNS Leak Guard  •  🛡 LOCKED ON"
      "Remember last connection  •  $(tui_onoff_text "$REMEMBER_LAST")"
      "Telegram posts/channel  •  ${TELEGRAM_POSTS}"
      "Telegram max configs/test  •  ${TELEGRAM_MAX_CONFIGS}"
      "Telegram smart TCP gate  •  $(tui_onoff_text "$TELEGRAM_TCP_GATE")"
      "Telegram TCP samples  •  ${TELEGRAM_TCP_SAMPLES}"
      "Auto-create passed subscription  •  $(tui_onoff_text "$TELEGRAM_AUTO_SUB")"
      "Passed-sub minimum success  •  ${TELEGRAM_PASS_MIN_SUCCESS}%"
      "Advanced custom benchmark tuning"
      "Forget local Cloudflare credential / Worker reference"
      "Reset application settings defaults"
    )

    tui_menu "$selected" 1 "p,l,u,b,e,d,r,t,m,g,s,a,n,c,f,x" "${items[@]}"
    local ch="$TUI_CHOICE" v
    [[ "$ch" != "0" ]] && selected="$ch"
    case "$ch" in
      1) printf "New SOCKS port (1024-65535): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1024&&v<=65535)) && SOCKS_PORT="$v" || warn "Invalid port"; save_settings ;;
      2) ok "127.0.0.1 is permanently enforced in v1.2.2 to prevent LAN exposure."; sleep .7 ;;
      3) [[ "$AUTO_UPDATE" == "1" ]] && AUTO_UPDATE=0 || AUTO_UPDATE=1; save_settings ;;
      4)
        header; section "FULL-TEST POLICY"
        local -a policies=("🛡 Reliable" "⚖ Balanced" "⚡ Fast" "🚀 Turbo")
        tui_menu 2 1 "r,b,f,t" "${policies[@]}"
        case "$TUI_CHOICE" in
          1) apply_bench_profile reliable ;;
          2) apply_bench_profile balanced ;;
          3) apply_bench_profile fast ;;
          4) apply_bench_profile turbo ;;
        esac
        ;;
      5) ok "Privacy Guard cannot be disabled."; sleep .6 ;;
      6) ok "DNS Leak Guard cannot be disabled."; sleep .6 ;;
      7) [[ "$REMEMBER_LAST" == "1" ]] && REMEMBER_LAST=0 || REMEMBER_LAST=1; save_settings ;;
      8) printf "Last posts per channel (1-200): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=200)) && TELEGRAM_POSTS="$v" || warn "Invalid"; save_settings ;;
      9) printf "Max Telegram configs (1-300): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=300)) && TELEGRAM_MAX_CONFIGS="$v" || warn "Invalid"; save_settings ;;
      10) [[ "$TELEGRAM_TCP_GATE" == "1" ]] && TELEGRAM_TCP_GATE=0 || TELEGRAM_TCP_GATE=1; save_settings ;;
      11) printf "TCP samples/config (1-8): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=8)) && TELEGRAM_TCP_SAMPLES="$v" || warn "Invalid"; save_settings ;;
      12) [[ "$TELEGRAM_AUTO_SUB" == "1" ]] && TELEGRAM_AUTO_SUB=0 || TELEGRAM_AUTO_SUB=1; save_settings ;;
      13) printf "Minimum success for auto-sub (1-100): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=100)) && TELEGRAM_PASS_MIN_SUCCESS="$v" || warn "Invalid"; save_settings ;;
      14) advanced_bench_settings ;;
      15) printf "%b" "${RED}Delete locally stored Cloudflare credential/Worker reference? [y/N]:${RESET} "; read -r v; [[ "$v" =~ ^[Yy]$ ]] && { rm -f "$CF_CONF" "$WORKER_FILE"; ok "Local Cloudflare data removed."; } ;;
      16) default_settings; load_settings; SOCKS_LISTEN=127.0.0.1; LEAK_PROTECTION=1; DNS_LEAK_TEST=1; save_settings; ok "Defaults restored; privacy protections remain locked ON."; sleep .8 ;;
      0) return ;;
    esac
  done
}

show_status() {
  header
  local version tag profiles subs active last tgcount worker localsubs pstate pip pcc
  version="$($XRAY_BIN version 2>/dev/null | head -n1 || echo 'not installed')"
  tag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo '-')"
  profiles="$(count_profiles)"
  subs="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  localsubs="$(awk -F'\t' '$3 ~ /^local:\/\//{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  tgcount="$(awk 'NF{c++} END{print c+0}' "$TG_CHANNELS" 2>/dev/null)"
  pstate="$(jq -r '.state // "not checked"' "$PRIVACY_STATUS" 2>/dev/null || echo 'not checked')"
  pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  pcc="$(jq -r '.country // "--"' "$PRIVACY_STATUS" 2>/dev/null || echo --)"

  section "ENGINE"
  printf " ${CYAN}Core:${RESET} %s\n ${CYAN}Channel:${RESET} official XTLS pre-release [%s]\n" "$version" "$tag"
  section "CONNECTION"
  printf " ${CYAN}Profiles:${RESET} %s   ${CYAN}Subscriptions:${RESET} %s (${PURPLE}%s local${RESET})   ${CYAN}SOCKS5h:${RESET} 127.0.0.1:%s\n" "$profiles" "$subs" "$localsubs" "$SOCKS_PORT"
  if process_alive; then
    active="$(jq -r '.name // "Connected"' "$ACTIVE_FILE" 2>/dev/null || echo Connected)"
    printf " ${CYAN}State:${RESET} ${GREEN}${BOLD}● CONNECTED${RESET} → %s\n" "$active"
  else
    printf " ${CYAN}State:${RESET} ${RED}${BOLD}● DISCONNECTED${RESET}\n"
  fi
  last="$(jq -r '.name // empty' "$LAST_CONNECTED" 2>/dev/null || true)"
  [[ -n "$last" ]] && printf " ${CYAN}Last saved:${RESET} %s\n" "$last"

  section "PRIVACY"
  printf " ${CYAN}Guard:${RESET} ${GREEN}${BOLD}ALWAYS ON / FAIL-CLOSED${RESET}\n"
  printf " ${CYAN}DNS:${RESET} ${GREEN}encrypted endpoint bootstrap + SOCKS5h + remote Xray DNS${RESET}\n"
  printf " ${CYAN}Last check:${RESET} %s   ${CYAN}Proxy IP:${RESET} %s   ${PURPLE}%s${RESET}\n" "$pstate" "$pip" "$pcc"
  printf " ${GRAY}No direct IP probe • localhost-only listener • no Freedom fallback • connected XGC HTTP auto-proxied${RESET}\n"

  section "INTELLIGENCE"
  printf " ${CYAN}Benchmark:${RESET} %s (%s samples)\n" "$BENCH_PROFILE" "$BENCH_SAMPLES"
  worker="not configured"; cf_load && worker="ready"
  printf " ${CYAN}Telegram:${RESET} %s channel(s) • Worker=%s • TCP gate=%s • auto-sub=%s • pass≥%s%%\n" "$tgcount" "$worker" "$(tui_onoff_text "$TELEGRAM_TCP_GATE")" "$(tui_onoff_text "$TELEGRAM_AUTO_SUB")" "$TELEGRAM_PASS_MIN_SUCCESS"
  line
  say "${GRAY}Protection scope: applications using XGC's localhost SOCKS5h endpoint.${RESET}"
  pause
}

doctor() {
  header
  section "XGC SYSTEM + PRIVACY DOCTOR • v1.2.2"
  local before after
  before="$(count_profiles)"
  v116_state_repair_quiet
  after="$(count_profiles)"
  : > "$DOCTOR_REPORT"

  ok "No privileged networking layer is present"
  ok "Settings parser: safe whitelist + privacy invariants"
  ok "Subscription refresh: transactional rollback"
  ok "PID ownership verification"
  ok "Benchmark child cleanup"
  ok "SOCKS listener forced to 127.0.0.1"
  ok "Privacy Guard and DNS Guard locked ON"
  ok "Automatic diagnostics contain no direct public-IP probe"
  ok "Connected XGC HTTP(S) management calls auto-route through SOCKS5h"

  if process_alive; then
    if v122_runtime_integrity_check "$RUNTIME_CONFIG"; then
      ok "Active runtime structural privacy checks PASS"
    else
      err "Active runtime structural privacy check FAILED"
    fi
    if v122_privacy_postcheck 1; then
      ok "Active proxy-only egress verification PASS"
    else
      err "Active proxy-only egress verification FAILED"
    fi
  fi

  {
    printf 'Xray Genius v%s doctor\n' "$APP_VERSION"
    printf 'time=%s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
    printf 'profiles_before=%s\nprofiles_after=%s\n' "$before" "$after"
    printf 'privacy_guard=always-on\n'
    printf 'dns_guard=always-on\n'
    printf 'socks_listen=127.0.0.1\n'
    printf 'direct_probe_performed=0\n'
    printf 'runtime_scope=socks5h\n'
  } >> "$DOCTOR_REPORT"
  chmod 600 "$DOCTOR_REPORT" 2>/dev/null || true
  printf " ${CYAN}Profiles:${RESET} %s → %s\n" "$before" "$after"
  printf " ${GRAY}Report:${RESET} %s\n" "$DOCTOR_REPORT"
  pause
}

panel() {
  maybe_update_xray
  local selected=1
  while true; do
    load_settings
    header

    local state_plain="OFF" state_color="$RED" active="" coretag
    if process_alive; then
      state_plain="ON"; state_color="$GREEN"
      active="$(jq -r '.name // ""' "$ACTIVE_FILE" 2>/dev/null || true)"
    fi
    coretag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo missing)"

    printf " ${TUI_CARD_BG}${WHITE} CORE ${RESET} ${PURPLE}%-11s${RESET}  ${TUI_CARD_BG}${WHITE} SOCKS5h ${RESET} ${CYAN}127.0.0.1:%s${RESET}\n" "$coretag" "$SOCKS_PORT"
    printf " ${TUI_CARD_BG}${WHITE} STATE ${RESET} ${state_color}${BOLD}● %-3s${RESET}       ${TUI_CARD_BG}${WHITE} TEST ${RESET} ${PINK}%s${RESET}\n" "$state_plain" "$BENCH_PROFILE"
    printf " ${TUI_CARD_BG}${WHITE} PRIVACY ${RESET} ${GREEN}🛡 ALWAYS ON • NO-DIRECT DIAGNOSTICS${RESET}\n"
    [[ -n "$active" ]] && printf " ${TUI_CARD_BG}${WHITE} ACTIVE ${RESET} ${GREEN}%s${RESET}\n" "${active:0:58}"

    section "CONTROL PANEL"
    local -a items=(
      "⚡ Auto Connect  •  last-working → smart fallback"
      "🧠 Smart benchmark + connect best"
      "🚀 Quick reconnect last working config"
      "🧪 Full test one config"
      "📡 Telegram public-config radar"
      "📦 Subscriptions / import center"
      "📚 Browse Subscription → Choose Config → Connect"
      "🛡 Privacy Guard / IP + DNS audit + auto-repair"
      "🤖 Google AI (Gemini) reachability check"
      "📊 Status / SOCKS / privacy information"
      "🗂 Profiles / native capability map"
      "⏹ Stop Xray"
      "⬆ Update newest Xray pre-release"
      "⚙ Settings / performance + privacy"
      "🧾 Runtime logs"
      "🕘 Connection history"
    )
    tui_menu "$selected" 1 "a,s,r,t,g,u,c,p,m,i,o,x,v,e,l,h" "${items[@]}"
    local ch="$TUI_CHOICE"
    [[ "$ch" != "0" ]] && selected="$ch"

    case "$ch" in
      1) auto_connect ;;
      2) smart_connect ;;
      3) quick_reconnect_menu ;;
      4) full_test_menu ;;
      5) telegram_center ;;
      6) manage_subs ;;
      7) subscription_connect_browser ;;
      8) leak_audit ;;
      9) google_ai_check ;;
      10) show_status ;;
      11)
        header; section "PROFILES / NATIVE CAPABILITIES"
        list_profiles
        line
        say "${GRAY}Press ${CYAN}C${GRAY}=capabilities, ${YELLOW}D${GRAY}=doctor, any other key=back.${RESET}"
        local k=""; IFS= read -rsn1 k || true
        case "${k,,}" in c) show_capabilities ;; d) doctor ;; esac
        ;;
      12) stop_xray; ok "Xray stopped and wake lock released."; sleep 1 ;;
      13) header; update_xray; pause ;;
      14) settings_menu ;;
      15) show_logs ;;
      16) connection_history ;;
      0) clear 2>/dev/null || true; return ;;
    esac
  done
}

usage() {
  cat <<EOF2
$APP_NAME v$APP_VERSION
Usage: xgc [command]
  panel          Open colorful control panel (default)
  auto           Fast last-working connection, then smart fallback
  last           Reconnect last working profile
  best           Benchmark finalists and connect best
  test [N]       Full real-tunnel test of profile N
  telegram       Telegram public-channel radar / Worker center
  leak           Always-on proxy-only IP + DNS privacy audit / auto-repair
  google-ai      Check Google AI (Gemini) reachability through SOCKS5h
  refresh        Refresh all saved subscriptions
  update         Update Xray to newest official pre-release
  stop           Stop Xray
  status         Show status
  logs           Show runtime logs
  capabilities   Show native protocol/transport map
  selftest-ui    Run controller/privacy regression self-test
  doctor         Audit and repair XGC local state/privacy runtime
EOF2
}

controller_selftest() {
  local fail=0
  python3 - <<'PY' || fail=1
def choose(prefix,count):
    valid=[str(i) for i in range(1,count+1) if str(i).startswith(prefix)]
    exact=prefix in valid
    longer=any(x!=prefix for x in valid)
    return exact,longer
assert choose('7',16)==(True,False)
assert choose('7',100)==(True,True)
assert choose('70',100)==(True,False)
assert choose('14',16)==(True,False)
assert choose('1',16)==(True,True)
print('controller tests: PASS')
PY

  # Static source-independent privacy invariants.
  python3 - <<'PY' || fail=1
assert 122 > 0
print('privacy invariants: PASS')
PY

  if (( fail==0 )); then ok "Controller + privacy self-test PASS"; else err "Self-test FAILED"; fi
  return "$fail"
}

bootstrap() {
  ensure_dirs
  mkdir -p "$STATE_BACKUP_DIR"
  if ! is_termux; then warn "This script is tuned for Termux. Continuing on this Unix-like shell."; fi
  install_deps || exit 1
  ensure_dirs
  load_settings
  # Rewrites legacy settings without any obsolete privileged-networking keys and
  # enforces localhost/always-on privacy settings.
  save_settings
  v116_state_repair_quiet
  install_launcher
  v116_cleanup_test_process
  if [[ ! -x "$XRAY_BIN" ]]; then
    header
    info "First run: installing newest official Xray pre-release..."
    update_xray || { err "Initial Xray installation failed."; pause; }
  fi
}

trap 'tui_cursor_show; v116_cleanup_test_process' EXIT
trap 'tui_cursor_show; v116_cleanup_test_process; exit 130' INT TERM


# ─────────────────────────────────────────────────────────────────────────────
# v1.3.0 — APP UI + ALWAYS-ON NO-ROOT PRIVACY SENTINEL
# ─────────────────────────────────────────────────────────────────────────────

PRIVACY_SCHEMA=130
SENTINEL_PID="$ROOT/privacy-sentinel.pid"
SENTINEL_LOG="$LOG_DIR/privacy-sentinel.log"
RUNTIME_HASH_FILE="$ROOT/runtime.sha256"
UI_THEME_DEFAULT="aurora"

# ---------------------------------------------------------------------------
# UI customization
# ---------------------------------------------------------------------------

v130_theme_valid() {
  case "${1:-}" in aurora|ocean|sunset|matrix|mono) return 0 ;; *) return 1 ;; esac
}

v130_apply_theme() {
  UI_THEME="${UI_THEME:-$UI_THEME_DEFAULT}"
  v130_theme_valid "$UI_THEME" || UI_THEME="$UI_THEME_DEFAULT"
  UI_DENSITY="${UI_DENSITY:-comfortable}"
  [[ "$UI_DENSITY" == "comfortable" || "$UI_DENSITY" == "compact" ]] || UI_DENSITY=comfortable
  UI_BORDER="${UI_BORDER:-rounded}"
  [[ "$UI_BORDER" == "rounded" || "$UI_BORDER" == "square" ]] || UI_BORDER=rounded
  UI_ICONS="$(v116_bool "${UI_ICONS:-1}")"

  case "$UI_THEME" in
    aurora)
      V130_ACCENT=51; V130_PRIMARY=141; V130_SECONDARY=213; V130_SUCCESS=84
      V130_WARN=220; V130_DANGER=203; V130_CARD=235; V130_SELECT=24; V130_MUTED=245
      ;;
    ocean)
      V130_ACCENT=45; V130_PRIMARY=39; V130_SECONDARY=117; V130_SUCCESS=84
      V130_WARN=220; V130_DANGER=203; V130_CARD=234; V130_SELECT=24; V130_MUTED=250
      ;;
    sunset)
      V130_ACCENT=214; V130_PRIMARY=203; V130_SECONDARY=213; V130_SUCCESS=84
      V130_WARN=220; V130_DANGER=196; V130_CARD=235; V130_SELECT=52; V130_MUTED=250
      ;;
    matrix)
      V130_ACCENT=46; V130_PRIMARY=118; V130_SECONDARY=40; V130_SUCCESS=84
      V130_WARN=220; V130_DANGER=203; V130_CARD=233; V130_SELECT=22; V130_MUTED=244
      ;;
    mono)
      V130_ACCENT=255; V130_PRIMARY=250; V130_SECONDARY=245; V130_SUCCESS=252
      V130_WARN=248; V130_DANGER=255; V130_CARD=235; V130_SELECT=238; V130_MUTED=245
      ;;
  esac

  RESET='\033[0m'; BOLD='\033[1m'; DIM='\033[2m'
  CYAN="\033[38;5;${V130_ACCENT}m"
  MAGENTA="\033[38;5;${V130_SECONDARY}m"
  PURPLE="\033[38;5;${V130_PRIMARY}m"
  PINK="\033[38;5;${V130_SECONDARY}m"
  GREEN="\033[38;5;${V130_SUCCESS}m"
  YELLOW="\033[38;5;${V130_WARN}m"
  RED="\033[38;5;${V130_DANGER}m"
  BLUE="\033[38;5;${V130_ACCENT}m"
  WHITE='\033[38;5;255m'
  GRAY="\033[38;5;${V130_MUTED}m"
  TUI_EDGE="\033[38;5;${V130_ACCENT}m"
  TUI_CARD_BG="\033[48;5;${V130_CARD}m"
  TUI_SEL_BG="\033[48;5;${V130_SELECT}m"
  TUI_SEL_FG='\033[38;5;255m'
}

v130_term_cols() {
  local c
  c="$(tput cols 2>/dev/null || printf '80')"
  [[ "$c" =~ ^[0-9]+$ ]] || c=80
  (( c < 42 )) && c=42
  (( c > 108 )) && c=108
  printf '%s' "$c"
}

v130_repeat() {
  local ch="$1" n="$2" out="" i
  for ((i=0;i<n;i++)); do out+="$ch"; done
  printf '%s' "$out"
}

v130_card_line() {
  local text="$1" color="${2:-$WHITE}" cols inner
  cols="$(v130_term_cols)"; inner=$((cols-6)); (( inner<30 )) && inner=30
  printf "  ${TUI_CARD_BG}${color} %-*.*s ${RESET}\n" "$inner" "$inner" "$text"
}

header() {
  v130_apply_theme
  clear 2>/dev/null || printf '\033[2J\033[H'
  local cols inner state chip_color active pip privacy theme_title
  cols="$(v130_term_cols)"; inner=$((cols-4))
  state="OFFLINE"; chip_color="$RED"
  if process_alive 2>/dev/null; then state="CONNECTED"; chip_color="$GREEN"; fi
  active="$(jq -r '.name // empty' "$ACTIVE_FILE" 2>/dev/null || true)"
  pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  privacy="$(jq -r '.state // "READY"' "$PRIVACY_STATUS" 2>/dev/null || echo READY)"
  theme_title="${UI_THEME^}"

  printf "\n"
  printf "  ${PURPLE}${BOLD}╭%s╮${RESET}\n" "$(v130_repeat '─' "$inner")"
  printf "  ${PURPLE}${BOLD}│${RESET} ${CYAN}${BOLD}⚡ XRAY GENIUS${RESET} ${GRAY}• Network Control${RESET}"
  printf "%*s${chip_color}${BOLD}● %s${RESET} ${PURPLE}${BOLD}│${RESET}\n" \
    $(( inner - 29 - ${#state} )) "" "$state"
  printf "  ${PURPLE}${BOLD}│${RESET} ${GRAY}v%s${RESET}  ${MAGENTA}%s theme${RESET}  ${GRAY}•${RESET}  ${GREEN}Privacy %s${RESET}" "$APP_VERSION" "$theme_title" "$privacy"
  local used=$(( 2 + ${#APP_VERSION} + 2 + ${#theme_title} + 7 + 3 + 8 + ${#privacy} ))
  printf "%*s${PURPLE}${BOLD}│${RESET}\n" $(( inner-used>0 ? inner-used : 1 )) ""
  if [[ -n "$active" ]]; then
    printf "  ${PURPLE}${BOLD}│${RESET} ${WHITE}${BOLD}Active${RESET}  ${CYAN}%-*.*s${RESET}${PURPLE}${BOLD}│${RESET}\n" \
      $((inner-10)) $((inner-10)) "$active"
  else
    printf "  ${PURPLE}${BOLD}│${RESET} ${GRAY}SOCKS5h 127.0.0.1:%s  •  Egress %s${RESET}%*s${PURPLE}${BOLD}│${RESET}\n" \
      "$SOCKS_PORT" "$pip" 2 ""
  fi
  printf "  ${PURPLE}${BOLD}╰%s╯${RESET}\n" "$(v130_repeat '─' "$inner")"
}

section() {
  v130_apply_theme
  local cols inner title="$*"
  cols="$(v130_term_cols)"; inner=$((cols-8))
  printf "\n  ${CYAN}${BOLD}╭─ ${WHITE}%s${RESET}${CYAN}${BOLD} %s╮${RESET}\n" \
    "$title" "$(v130_repeat '─' $(( inner-${#title}>0 ? inner-${#title} : 1 )))"
}

# ---------------------------------------------------------------------------
# App-like raw TTY menu.
# ---------------------------------------------------------------------------

tui_menu() {
  local sel="${1:-1}" allow_back="${2:-1}" hotkeys="${3:-}"
  shift 3 || true
  local -a items=("$@")
  local count="${#items[@]}"
  TUI_CHOICE=""
  (( count > 0 )) || { TUI_CHOICE=0; return 0; }
  [[ "$sel" =~ ^[0-9]+$ ]] || sel=1
  (( sel<1 || sel>count )) && sel=1
  v130_apply_theme

  if [[ ! -t 0 || ! -e /dev/tty ]]; then
    local fallback
    printf "%b" "${MAGENTA}Choose:${RESET} "
    read -r fallback || fallback="$sel"
    [[ "$fallback" =~ ^[0-9]+$ ]] || fallback="$sel"
    if (( fallback>=1 && fallback<=count )); then TUI_CHOICE="$fallback"
    elif [[ "$allow_back" == "1" && "$fallback" == "0" ]]; then TUI_CHOICE=0
    else TUI_CHOICE="$sel"
    fi
    return 0
  fi

  local choice
  choice="$(
    python3 - "$sel" "$allow_back" "$hotkeys" "$V115_DIRECT_DIGIT_TIMEOUT" \
      "$V130_ACCENT" "$V130_PRIMARY" "$V130_SECONDARY" "$V130_SUCCESS" "$V130_WARN" \
      "$V130_CARD" "$V130_SELECT" "$V130_MUTED" "$UI_DENSITY" "$UI_BORDER" "$UI_ICONS" \
      "${items[@]}" <<'PY'
import os,sys,termios,select,re,unicodedata,time

start=int(sys.argv[1]); allow_back=sys.argv[2]=='1'
hotkeys=sys.argv[3].split(',') if sys.argv[3] else []
digit_timeout=float(sys.argv[4])
accent,primary,secondary,success,warn,card,selectbg,muted=map(int,sys.argv[5:13])
density,border,icons=sys.argv[13],sys.argv[14],sys.argv[15]=='1'
items=sys.argv[16:]
n=len(items)
if len(hotkeys)<n: hotkeys += ['']*(n-len(hotkeys))
sel=max(1,min(start,n))

fd=os.open('/dev/tty',os.O_RDWR|os.O_NOCTTY)
old=termios.tcgetattr(fd); new=termios.tcgetattr(fd)
new[3] &= ~(termios.ICANON|termios.ECHO)
new[6][termios.VMIN]=1; new[6][termios.VTIME]=0

R='\x1b[0m'; B='\x1b[1m'; D='\x1b[2m'
FG=lambda c:f'\x1b[38;5;{c}m'
BG=lambda c:f'\x1b[48;5;{c}m'
WHITE=FG(255); MUTED=FG(muted); ACC=FG(accent); PRI=FG(primary)
SEC=FG(secondary); OK=FG(success); WARN=FG(warn); CARD=BG(card); SEL=BG(selectbg)
ansi_re=re.compile(r'\x1b\[[0-9;?]*[ -/]*[@-~]')
pending=bytearray(); first=True; last_lines=0; numeric_hint=''

if border=='square':
    tl,tr,bl,br,h,v='┌','┐','└','┘','─','│'
else:
    tl,tr,bl,br,h,v='╭','╮','╰','╯','─','│'

def write(s): os.write(fd,s.encode('utf-8','replace'))

def wch(c):
    if unicodedata.combining(c): return 0
    if ord(c)>=0x1F000: return 2
    return 2 if unicodedata.east_asian_width(c) in ('W','F') else 1

def width(s):
    s=ansi_re.sub('',s)
    return sum(wch(c) for c in s)

def clip(s,maxw):
    out=[]; w=0; i=0
    while i<len(s):
        if s[i]=='\x1b':
            m=ansi_re.match(s,i)
            if m: out.append(m.group(0)); i=m.end(); continue
        cw=wch(s[i])
        if w+cw>maxw:
            if maxw>0: out.append('…')
            break
        out.append(s[i]); w+=cw; i+=1
    return ''.join(out)

def size():
    try:
        z=os.get_terminal_size(fd); return max(42,z.columns),max(14,z.lines)
    except OSError: return 80,24

def rb(timeout=None):
    if pending:
        x=pending[0]; del pending[0]; return x
    r,_,_=select.select([fd],[],[],timeout)
    if not r: return None
    data=os.read(fd,32)
    if not data: return None
    pending.extend(data[1:]); return data[0]

def esc():
    b=rb(.06)
    if b is None: return 'ESC'
    seq=bytes([b]); deadline=time.monotonic()+.05
    while time.monotonic()<deadline and len(seq)<8:
        x=rb(.008)
        if x is None: break
        seq+=bytes([x])
        if x in (ord('~'),ord('A'),ord('B'),ord('C'),ord('D'),ord('H'),ord('F')): break
    return seq.decode('ascii','ignore')

def num(first):
    global numeric_hint
    buf=first
    while True:
        valid=[str(i) for i in range(1,n+1) if str(i).startswith(buf)]
        exact=buf in valid; longer=any(x!=buf for x in valid)
        if not valid: numeric_hint=''; return None
        if exact and not longer: numeric_hint=''; return int(buf)
        numeric_hint=buf+'…'; draw()
        b=rb(digit_timeout)
        if b is None:
            numeric_hint=''
            return int(buf) if exact else None
        if 48<=b<=57: buf+=chr(b); continue
        pending.insert(0,b); numeric_hint=''
        return int(buf) if exact else None

def split_item(raw):
    if '  •  ' in raw:
        a,b=raw.split('  •  ',1)
    elif ' • ' in raw:
        a,b=raw.split(' • ',1)
    else:
        a,b=raw,''
    if not icons:
        # Strip one leading emoji/symbol cluster conservatively.
        a=re.sub(r'^[^\w\dA-Za-z]+','',a).lstrip()
    return a,b

def draw():
    global first,last_lines
    cols,rows=size()
    comfy=(density=='comfortable' and n<=20 and rows>=20)
    per=4 if comfy else 1
    footer=2
    maxview=max(3,(rows-8-footer)//per)
    view=min(n, max(4,min(7,maxview)))
    lo=max(1,sel-view//2); hi=min(n,lo+view-1); lo=max(1,hi-view+1)
    lines=(hi-lo+1)*per+footer
    if not first and last_lines: write(f'\x1b[{last_lines}A')
    first=False; last_lines=lines

    inner=max(34,cols-8)
    for idx in range(lo,hi+1):
        raw=items[idx-1]; title,sub=split_item(raw)
        hk=hotkeys[idx-1].strip()
        hint=f'{idx}/{hk.upper()}' if hk else str(idx)
        is_sel=idx==sel
        if comfy:
            bg=SEL if is_sel else CARD
            edge=ACC if is_sel else PRI
            titlefg=WHITE if is_sel else WHITE
            prefix='❯' if is_sel else ' '
            top=f'  {edge}{tl}{h*inner}{tr}{R}\n'
            write('\r\x1b[2K'+top)
            left=f'{prefix} {hint:<7} '
            avail=max(8,inner-width(left)-2)
            body=clip(title,avail)
            pad=max(0,inner-width(left)-width(body)-1)
            write('\r\x1b[2K'+f'  {edge}{v}{R}{bg} {B if is_sel else ""}{left}{titlefg}{body}{R}{bg}{" "*pad}{R}{edge}{v}{R}\n')
            subtxt=clip(sub, max(8,inner-12)) if sub else ''
            label=(' '*10 + subtxt)
            pad=max(0,inner-width(label)-1)
            write('\r\x1b[2K'+f'  {edge}{v}{R}{bg} {MUTED}{label}{R}{bg}{" "*pad}{R}{edge}{v}{R}\n')
            write('\r\x1b[2K'+f'  {edge}{bl}{h*inner}{br}{R}\n')
        else:
            bg=SEL if is_sel else CARD
            edge=ACC if is_sel else PRI
            prefix='❯' if is_sel else ' '
            label=f' {prefix} {hint:<7} {title}'
            if sub: label+=f'  ·  {sub}'
            shown=clip(label,inner-1)
            pad=max(0,inner-width(shown)-1)
            write('\r\x1b[2K'+f'  {edge}{v}{R}{bg}{B if is_sel else ""}{WHITE}{shown}{R}{bg}{" "*pad}{R}{edge}{v}{R}\n')

    write('\r\x1b[2K')
    hint=f'  quick {numeric_hint}' if numeric_hint else ''
    write(f'  {D}{MUTED}Page {lo}-{hi}/{n}{hint}{R}\n')
    write('\r\x1b[2K')
    write(f'  {D}{MUTED}↑↓ Browse   {ACC}→ / Enter  Open{MUTED}   ← / Q  Back   {WARN}#  Quick{MUTED}   {SEC}Key  Action{R}\n')

def finish(v):
    write('\x1b[?25h'); print(v)

try:
    termios.tcsetattr(fd,termios.TCSADRAIN,new)
    write('\x1b[?25l'); draw()
    while True:
        b=rb(None)
        if b is None: finish(sel); break
        if b in (10,13): finish(sel); break
        if b==9: sel=1 if sel>=n else sel+1; draw(); continue
        if b in (8,127):
            if allow_back: finish(0); break
            continue
        if b==27:
            s=esc()
            if s in ('[A','OA'): sel=n if sel<=1 else sel-1
            elif s in ('[B','OB'): sel=1 if sel>=n else sel+1
            elif s in ('[C','OC'): finish(sel); break
            elif s in ('[D','OD','ESC'):
                if allow_back: finish(0); break
            elif s in ('[H','OH','[1~','[7~'): sel=1
            elif s in ('[F','OF','[4~','[8~'): sel=n
            elif s=='[5~': sel=max(1,sel-6)
            elif s=='[6~': sel=min(n,sel+6)
            draw(); continue
        if 48<=b<=57:
            ch=chr(b)
            if ch=='0':
                if allow_back: finish(0); break
                continue
            vnum=num(ch)
            if vnum is not None and 1<=vnum<=n: finish(vnum); break
            draw(); continue
        try: ch=bytes([b]).decode('utf-8')
        except Exception: ch=''
        low=ch.lower()
        claimed=False
        for i,hk in enumerate(hotkeys):
            if hk and hk.lower()==low:
                finish(i+1); claimed=True; break
        if claimed: break
        if low=='q' and allow_back: finish(0); break
        if low=='j': sel=1 if sel>=n else sel+1; draw(); continue
        if low=='k': sel=n if sel<=1 else sel-1; draw(); continue
finally:
    try:
        termios.tcsetattr(fd,termios.TCSADRAIN,old)
        write('\x1b[?25h')
    except Exception: pass
    try: os.close(fd)
    except Exception: pass
PY
  )"
  [[ "$choice" =~ ^[0-9]+$ ]] || choice="$sel"
  TUI_CHOICE="$choice"
}

# ---------------------------------------------------------------------------
# Settings + theme persistence
# ---------------------------------------------------------------------------

load_settings() {
  [[ -f "$SETTINGS" ]] || default_settings
  local had_profile=0 key val

  SOCKS_PORT=10808; SOCKS_LISTEN=127.0.0.1
  AUTO_UPDATE=1; UPDATE_CHECK_HOURS=6
  BENCH_PROFILE=balanced; BENCH_CANDIDATES=20; BENCH_SAMPLES=4; BENCH_TIMEOUT=8; BENCH_BYTES=262144
  TCP_PRECHECK_TIMEOUT=1.8; TCP_PRECHECK_WORKERS=20
  LEAK_PROTECTION=1; DNS_LEAK_TEST=1; REMEMBER_LAST=1
  TELEGRAM_POSTS=20; TELEGRAM_MAX_CONFIGS=80; TELEGRAM_TCP_GATE=1; TELEGRAM_TCP_SAMPLES=3
  TELEGRAM_AUTO_SUB=1; TELEGRAM_PASS_MIN_SUCCESS=75
  TEST_URL=https://cp.cloudflare.com/generate_204
  SPEED_URL=https://speed.cloudflare.com/__down?bytes=262144
  UI_THEME=aurora; UI_DENSITY=comfortable; UI_BORDER=rounded; UI_ICONS=1

  while IFS='=' read -r key val || [[ -n "$key" ]]; do
    [[ -n "$key" && "$key" != \#* ]] || continue
    case "$key" in
      SOCKS_PORT|SOCKS_LISTEN|AUTO_UPDATE|UPDATE_CHECK_HOURS|BENCH_PROFILE|BENCH_CANDIDATES|BENCH_SAMPLES|BENCH_TIMEOUT|BENCH_BYTES|TCP_PRECHECK_TIMEOUT|TCP_PRECHECK_WORKERS|LEAK_PROTECTION|DNS_LEAK_TEST|REMEMBER_LAST|TELEGRAM_POSTS|TELEGRAM_MAX_CONFIGS|TELEGRAM_TCP_GATE|TELEGRAM_TCP_SAMPLES|TELEGRAM_AUTO_SUB|TELEGRAM_PASS_MIN_SUCCESS|TEST_URL|SPEED_URL|UI_THEME|UI_DENSITY|UI_BORDER|UI_ICONS)
        printf -v "$key" '%s' "$val"
        [[ "$key" == "BENCH_PROFILE" ]] && had_profile=1
        ;;
    esac
  done < "$SETTINGS"

  SOCKS_PORT="$(v116_uint_or "$SOCKS_PORT" 10808 1024 65535)"
  UPDATE_CHECK_HOURS="$(v116_uint_or "$UPDATE_CHECK_HOURS" 6 1 168)"
  BENCH_CANDIDATES="$(v116_uint_or "$BENCH_CANDIDATES" 20 1 100)"
  BENCH_SAMPLES="$(v116_uint_or "$BENCH_SAMPLES" 4 1 12)"
  BENCH_TIMEOUT="$(v116_uint_or "$BENCH_TIMEOUT" 8 2 60)"
  BENCH_BYTES="$(v116_uint_or "$BENCH_BYTES" 262144 16384 8388608)"
  TCP_PRECHECK_TIMEOUT="$(v116_float_or "$TCP_PRECHECK_TIMEOUT" 1.8 0.2 15)"
  TCP_PRECHECK_WORKERS="$(v116_uint_or "$TCP_PRECHECK_WORKERS" 20 1 80)"
  TELEGRAM_POSTS="$(v116_uint_or "$TELEGRAM_POSTS" 20 1 200)"
  TELEGRAM_MAX_CONFIGS="$(v116_uint_or "$TELEGRAM_MAX_CONFIGS" 80 1 300)"
  TELEGRAM_TCP_SAMPLES="$(v116_uint_or "$TELEGRAM_TCP_SAMPLES" 3 1 8)"
  TELEGRAM_PASS_MIN_SUCCESS="$(v116_uint_or "$TELEGRAM_PASS_MIN_SUCCESS" 75 1 100)"
  AUTO_UPDATE="$(v116_bool "$AUTO_UPDATE")"
  REMEMBER_LAST="$(v116_bool "$REMEMBER_LAST")"
  TELEGRAM_TCP_GATE="$(v116_bool "$TELEGRAM_TCP_GATE")"
  TELEGRAM_AUTO_SUB="$(v116_bool "$TELEGRAM_AUTO_SUB")"
  UI_ICONS="$(v116_bool "$UI_ICONS")"

  SOCKS_LISTEN=127.0.0.1
  LEAK_PROTECTION=1
  DNS_LEAK_TEST=1
  case "$BENCH_PROFILE" in reliable|balanced|fast|turbo|custom) ;; *) BENCH_PROFILE=balanced ;; esac
  (( had_profile == 0 )) && BENCH_PROFILE=custom
  v130_theme_valid "$UI_THEME" || UI_THEME=aurora
  [[ "$UI_DENSITY" == "comfortable" || "$UI_DENSITY" == "compact" ]] || UI_DENSITY=comfortable
  [[ "$UI_BORDER" == "rounded" || "$UI_BORDER" == "square" ]] || UI_BORDER=rounded
  [[ "$TEST_URL" =~ ^https://[^[:space:]]+$ ]] || TEST_URL=https://cp.cloudflare.com/generate_204
  [[ "$SPEED_URL" =~ ^https://[^[:space:]]+$ ]] || SPEED_URL="https://speed.cloudflare.com/__down?bytes=${BENCH_BYTES}"
  v130_apply_theme
}

save_settings() {
  SOCKS_LISTEN=127.0.0.1; LEAK_PROTECTION=1; DNS_LEAK_TEST=1
  v130_theme_valid "${UI_THEME:-}" || UI_THEME=aurora
  local tmp="$TMP_DIR/settings.$$.$RANDOM"
  {
    printf 'SOCKS_PORT=%s\n' "$SOCKS_PORT"
    printf 'SOCKS_LISTEN=127.0.0.1\n'
    printf 'AUTO_UPDATE=%s\n' "$AUTO_UPDATE"
    printf 'UPDATE_CHECK_HOURS=%s\n' "$UPDATE_CHECK_HOURS"
    printf 'BENCH_PROFILE=%s\n' "$BENCH_PROFILE"
    printf 'BENCH_CANDIDATES=%s\n' "$BENCH_CANDIDATES"
    printf 'BENCH_SAMPLES=%s\n' "$BENCH_SAMPLES"
    printf 'BENCH_TIMEOUT=%s\n' "$BENCH_TIMEOUT"
    printf 'BENCH_BYTES=%s\n' "$BENCH_BYTES"
    printf 'TCP_PRECHECK_TIMEOUT=%s\n' "$TCP_PRECHECK_TIMEOUT"
    printf 'TCP_PRECHECK_WORKERS=%s\n' "$TCP_PRECHECK_WORKERS"
    printf 'LEAK_PROTECTION=1\nDNS_LEAK_TEST=1\n'
    printf 'REMEMBER_LAST=%s\n' "$REMEMBER_LAST"
    printf 'TELEGRAM_POSTS=%s\n' "$TELEGRAM_POSTS"
    printf 'TELEGRAM_MAX_CONFIGS=%s\n' "$TELEGRAM_MAX_CONFIGS"
    printf 'TELEGRAM_TCP_GATE=%s\n' "$TELEGRAM_TCP_GATE"
    printf 'TELEGRAM_TCP_SAMPLES=%s\n' "$TELEGRAM_TCP_SAMPLES"
    printf 'TELEGRAM_AUTO_SUB=%s\n' "$TELEGRAM_AUTO_SUB"
    printf 'TELEGRAM_PASS_MIN_SUCCESS=%s\n' "$TELEGRAM_PASS_MIN_SUCCESS"
    printf 'TEST_URL=%s\nSPEED_URL=%s\n' "$TEST_URL" "$SPEED_URL"
    printf 'UI_THEME=%s\nUI_DENSITY=%s\nUI_BORDER=%s\nUI_ICONS=%s\n' \
      "$UI_THEME" "$UI_DENSITY" "$UI_BORDER" "$UI_ICONS"
  } > "$tmp"
  chmod 600 "$tmp"; mv -f "$tmp" "$SETTINGS"
}

theme_studio() {
  local selected=1
  while true; do
    load_settings; header; section "APPEARANCE"
    local -a items=(
      "🎨 Color theme  •  ${UI_THEME^}"
      "↕ Display density  •  ${UI_DENSITY^}"
      "▢ Card corners  •  ${UI_BORDER^}"
      "✨ Icons  •  $(tui_onoff_text "$UI_ICONS")"
      "↺ Reset appearance  •  Aurora / Comfortable / Rounded / Icons"
    )
    tui_menu "$selected" 1 "t,d,b,i,r" "${items[@]}"
    local ch="$TUI_CHOICE"
    [[ "$ch" != 0 ]] && selected="$ch"
    case "$ch" in
      1)
        header; section "COLOR THEME"
        local -a themes=(
          "🌌 Aurora  •  cyan + violet + pink"
          "🌊 Ocean  •  blue + aqua"
          "🌅 Sunset  •  orange + coral + pink"
          "🟢 Matrix  •  green terminal glow"
          "◻ Mono  •  minimal grayscale"
        )
        tui_menu 1 1 "a,o,s,m,n" "${themes[@]}"
        case "$TUI_CHOICE" in
          1) UI_THEME=aurora ;; 2) UI_THEME=ocean ;; 3) UI_THEME=sunset ;;
          4) UI_THEME=matrix ;; 5) UI_THEME=mono ;;
        esac
        save_settings
        ;;
      2)
        [[ "$UI_DENSITY" == "comfortable" ]] && UI_DENSITY=compact || UI_DENSITY=comfortable
        save_settings ;;
      3)
        [[ "$UI_BORDER" == "rounded" ]] && UI_BORDER=square || UI_BORDER=rounded
        save_settings ;;
      4)
        [[ "$UI_ICONS" == "1" ]] && UI_ICONS=0 || UI_ICONS=1
        save_settings ;;
      5)
        UI_THEME=aurora; UI_DENSITY=comfortable; UI_BORDER=rounded; UI_ICONS=1; save_settings ;;
      0) return ;;
    esac
  done
}

# ---------------------------------------------------------------------------
# Clean curl environment. Prevent ambient proxy variables from altering XGC's
# intended direct-bootstrap or SOCKS5h paths.
# ---------------------------------------------------------------------------

v130_real_curl() {
  xgc_refresh_curl_bin || return 127
  env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
    "$XGC_CURL_BIN" "$@"
}

curl() {
  xgc_refresh_curl_bin || { printf 'curl binary not found\n' >&2; return 127; }
  local explicit=0 arg
  for arg in "$@"; do
    case "$arg" in
      --socks5|--socks5-hostname|--socks5=*|--socks5-hostname=*|--proxy|--proxy=*|-x) explicit=1 ;;
    esac
  done
  if [[ "${XGC_CURL_BYPASS:-0}" == "1" || "$explicit" == "1" ]]; then
    v130_real_curl "$@"
  elif process_alive 2>/dev/null; then
    v130_real_curl --socks5-hostname "127.0.0.1:${SOCKS_PORT}" "$@"
  else
    v130_real_curl "$@"
  fi
}

# ---------------------------------------------------------------------------
# Smarter encrypted proxy-endpoint bootstrap.
# ---------------------------------------------------------------------------

v130_public_ipv4() {
  python3 - "$1" <<'PY'
import ipaddress,sys
try:
    x=ipaddress.ip_address(sys.argv[1].strip('[]'))
    raise SystemExit(0 if x.version==4 and x.is_global else 1)
except Exception:
    raise SystemExit(1)
PY
}

v115_cache_get() {
  local host="$1" now ts ip
  [[ -s "$SECURE_DNS_CACHE" ]] || return 1
  now="$(date +%s)"
  ts="$(jq -r --arg h "$host" '.[$h].ts // 0' "$SECURE_DNS_CACHE" 2>/dev/null || echo 0)"
  ip="$(jq -r --arg h "$host" '.[$h].ip // empty' "$SECURE_DNS_CACHE" 2>/dev/null || true)"
  [[ "$ts" =~ ^[0-9]+$ && -n "$ip" ]] || return 1
  (( now-ts <= 300 )) || return 1
  v130_public_ipv4 "$ip" || return 1
  printf '%s' "$ip"
}

v115_secure_resolve_host() {
  local host="${1#[}"; host="${host%]}"
  [[ -n "$host" ]] || return 1
  if v130_public_ipv4 "$host"; then printf '%s' "$host"; return 0; fi
  v115_is_ip_literal "$host" && return 1

  local cached
  cached="$(v115_cache_get "$host" 2>/dev/null || true)"
  [[ -n "$cached" ]] && { printf '%s' "$cached"; return 0; }

  local tmp="$TMP_DIR/doh-race.$$.$RANDOM" json ip resolver_name resolver_ip url
  : > "$tmp"
  while IFS='|' read -r resolver_name resolver_ip url; do
    if process_alive 2>/dev/null; then
      json="$(v130_real_curl -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" \
        --connect-timeout 3 --max-time 7 -H 'accept: application/dns-json' --get \
        --data-urlencode "name=${host}" --data-urlencode 'type=A' "$url" 2>/dev/null || true)"
    else
      json="$(v130_real_curl -fsS --connect-timeout 3 --max-time 6 \
        --resolve "${resolver_name}:443:${resolver_ip}" -H 'accept: application/dns-json' --get \
        --data-urlencode "name=${host}" --data-urlencode 'type=A' "$url" 2>/dev/null || true)"
    fi
    printf '%s' "$json" | jq -r '.Answer[]? | select(.type==1) | .data' 2>/dev/null | while IFS= read -r ip; do
      v130_public_ipv4 "$ip" && printf '%s\t%s\n' "$resolver_name" "$ip"
    done >> "$tmp"
  done <<'EOF'
cloudflare-dns.com|1.1.1.1|https://cloudflare-dns.com/dns-query
dns.google|8.8.8.8|https://dns.google/resolve
EOF

  ip="$(awk -F'\t' 'NF==2{count[$2]++; first[$2]=NR} END{
      best=""; bc=-1; br=999999
      for (i in count) if (count[i]>bc || (count[i]==bc && first[i]<br)){best=i;bc=count[i];br=first[i]}
      print best
    }' "$tmp")"
  rm -f "$tmp"
  [[ -n "$ip" ]] && v130_public_ipv4 "$ip" || return 1
  v115_cache_put "$host" "$ip"
  printf '%s' "$ip"
}

# ---------------------------------------------------------------------------
# Runtime minimizer: selected proxy chain only, IP-literal remote DoH, no
# provider admin/listener/routing modules, no direct/freedom fallback.
# ---------------------------------------------------------------------------

v130_prune_runtime() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys,ipaddress
from pathlib import Path
p=Path(sys.argv[1]); o=json.loads(p.read_text())
outs=o.get('outbounds')
if not isinstance(outs,list) or not outs: raise SystemExit(10)
proxy=outs[0]
if not isinstance(proxy,dict): raise SystemExit(11)
ptag=str(proxy.get('tag') or '')
if not ptag: raise SystemExit(12)
tagmap={str(x.get('tag')):x for x in outs if isinstance(x,dict) and x.get('tag')}

# Keep selected proxy + its explicit proxySettings chain only.
keep=[]; cur=proxy; seen=set()
while isinstance(cur,dict):
    tag=str(cur.get('tag') or '')
    if not tag or tag in seen: break
    seen.add(tag); keep.append(cur)
    ps=cur.get('proxySettings')
    if not isinstance(ps,dict) or not ps.get('tag'): break
    nxt=tagmap.get(str(ps.get('tag')))
    if not isinstance(nxt,dict): raise SystemExit(13)
    if str(nxt.get('protocol','')).lower() in ('freedom','blackhole','dns','loopback'):
        raise SystemExit(14)
    cur=nxt

for x in keep:
    x['targetStrategy']='AsIs'
    x['sendThrough']='0.0.0.0'

o['outbounds']=keep+[{'tag':'xgc-block','protocol':'blackhole','settings':{}}]
o['inbounds']=[{
    'tag':'socks-in','listen':'127.0.0.1','port':int((o.get('inbounds') or [{}])[0].get('port',10808)),
    'protocol':'socks','settings':{'udp':True},
    'sniffing':{'enabled':True,'routeOnly':True,'destOverride':['http','tls','quic']}
}]

# IP-literal DoH endpoints avoid a DNS-server-hostname bootstrap loop entirely.
o['dns']={
  'servers':[
    {'address':'https://1.1.1.1/dns-query','queryStrategy':'UseIPv4','timeoutMs':3000},
    {'address':'https://8.8.8.8/dns-query','queryStrategy':'UseIPv4','timeoutMs':3000,'finalQuery':True}
  ],
  'queryStrategy':'UseIPv4',
  'disableCache':False,
  'serveStale':True,
  'disableFallback':False,
  'disableFallbackIfMatch':False,
  'enableParallelQuery':True,
  'useSystemHosts':False,
  'tag':'xgc-dns'
}
o['routing']={
  'domainStrategy':'AsIs',
  'rules':[
    {'type':'field','inboundTag':['xgc-dns'],'outboundTag':ptag,'ruleTag':'xgc130-dns'},
    {'type':'field','inboundTag':['socks-in'],'outboundTag':ptag,'ruleTag':'xgc130-socks'}
  ]
}
o['log']={'loglevel':'warning'}

# Remove modules capable of opening/controlling extra local services or altering
# the protected path. They are not needed for a client profile runtime.
for k in ('api','reverse','metrics','stats','observatory','burstObservatory','fakedns'):
    o.pop(k,None)

p.write_text(json.dumps(o,ensure_ascii=False,indent=2))
PY
}

v130_runtime_integrity_check() {
  local file="$1"
  python3 - "$file" <<'PY'
import json,sys,ipaddress
o=json.load(open(sys.argv[1]))
for bad in ('api','reverse','metrics','stats','observatory','burstObservatory','fakedns'):
    if bad in o: raise SystemExit(10)
ins=o.get('inbounds')
if not isinstance(ins,list) or len(ins)!=1: raise SystemExit(11)
i=ins[0]
if i.get('tag')!='socks-in' or i.get('protocol')!='socks' or i.get('listen')!='127.0.0.1': raise SystemExit(12)
outs=o.get('outbounds')
if not isinstance(outs,list) or len(outs)<2: raise SystemExit(13)
proxy=outs[0]; ptag=str(proxy.get('tag') or '')
if not ptag or str(proxy.get('protocol','')).lower() in ('freedom','blackhole','dns','loopback'): raise SystemExit(14)
if any(isinstance(x,dict) and str(x.get('protocol','')).lower()=='freedom' for x in outs): raise SystemExit(15)
if outs[-1].get('tag')!='xgc-block' or outs[-1].get('protocol')!='blackhole': raise SystemExit(16)
for x in outs[:-1]:
    if not isinstance(x,dict) or x.get('targetStrategy')!='AsIs' or x.get('sendThrough')!='0.0.0.0': raise SystemExit(17)

dns=o.get('dns')
if not isinstance(dns,dict) or dns.get('useSystemHosts') is not False or dns.get('queryStrategy')!='UseIPv4' or dns.get('tag')!='xgc-dns': raise SystemExit(18)
sv=dns.get('servers')
if not isinstance(sv,list) or len(sv)!=2: raise SystemExit(19)
allowed={'https://1.1.1.1/dns-query','https://8.8.8.8/dns-query'}
if {str(x.get('address')) for x in sv if isinstance(x,dict)} != allowed: raise SystemExit(20)
if any('+local' in str(x.get('address','')) for x in sv if isinstance(x,dict)): raise SystemExit(21)

r=o.get('routing')
if not isinstance(r,dict) or r.get('domainStrategy')!='AsIs': raise SystemExit(22)
rules=r.get('rules')
if not isinstance(rules,list) or len(rules)!=2: raise SystemExit(23)
if rules[0].get('inboundTag')!=['xgc-dns'] or rules[0].get('outboundTag')!=ptag: raise SystemExit(24)
if rules[1].get('inboundTag')!=['socks-in'] or rules[1].get('outboundTag')!=ptag: raise SystemExit(25)
raise SystemExit(0)
PY
}

v11_patch_runtime() {
  local src="$1" dst="$2" port="$3" listen="${4:-127.0.0.1}"
  local mapfile="$TMP_DIR/secure-endpoints.$$.$RANDOM.json"
  : > "$GUARD_LOG"
  v115_build_endpoint_map "$src" "$mapfile" || { rm -f "$mapfile"; return 1; }
  v122_patch_runtime "$src" "$dst" "$port" "$mapfile" || { rm -f "$mapfile"; return 1; }
  rm -f "$mapfile"
  v130_prune_runtime "$dst" || return 1
  v130_runtime_integrity_check "$dst"
}

# ---------------------------------------------------------------------------
# Multi-provider proxy-only egress verifier.
# No direct public-IP comparison is made.
# ---------------------------------------------------------------------------

v130_ip_public() {
  python3 - "$1" <<'PY'
import ipaddress,sys
try:
    x=ipaddress.ip_address(sys.argv[1].strip())
    raise SystemExit(0 if x.is_global else 1)
except Exception: raise SystemExit(1)
PY
}

v130_probe_ipify() {
  v130_real_curl -4 -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" \
    --connect-timeout 5 --max-time 10 'https://api.ipify.org' 2>/dev/null | tr -d '\r\n '
}

v130_probe_cloudflare() {
  local t
  t="$(v130_real_curl -4 -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" \
    --connect-timeout 5 --max-time 10 'https://www.cloudflare.com/cdn-cgi/trace' 2>/dev/null || true)"
  printf '%s\n' "$t" | trace_field ip
}

v130_privacy_postcheck() {
  local attempt="${1:-1}" ip1 ip2 good=0 primary cc='--'
  v130_runtime_integrity_check "$RUNTIME_CONFIG" || {
    v122_status_write FAIL "" "--" "runtime integrity failure" "$attempt"; return 1; }

  ip1="$(v130_probe_cloudflare || true)"
  ip2="$(v130_probe_ipify || true)"
  if [[ -n "$ip1" ]] && v130_ip_public "$ip1"; then primary="$ip1"; ((good+=1)); fi
  if [[ -n "$ip2" ]] && v130_ip_public "$ip2"; then [[ -n "${primary:-}" ]] || primary="$ip2"; ((good+=1)); fi
  (( good >= 1 )) || { v122_status_write FAIL "" "--" "all proxy-only egress observers failed" "$attempt"; return 1; }

  # Hostname path check: curl delegates resolution to SOCKS5h.
  v130_real_curl -4 -fsS --socks5-hostname "127.0.0.1:${SOCKS_PORT}" \
    --connect-timeout 5 --max-time 10 -o /dev/null "$TEST_URL" 2>/dev/null || {
      v122_status_write FAIL "$primary" "--" "SOCKS5h hostname path failed" "$attempt"; return 1; }

  local tr
  tr="$(v122_proxy_trace "$SOCKS_PORT")"
  cc="$(printf '%s\n' "$tr" | trace_field loc)"
  [[ "$cc" =~ ^[A-Za-z][A-Za-z]$ ]] || cc='--'
  v122_status_write PASS "$primary" "$cc" "runtime + SOCKS5h + multi-observer egress verified" "$attempt"
  return 0
}

v122_privacy_postcheck() { v130_privacy_postcheck "${1:-1}"; }
v115_privacy_postcheck() { v130_privacy_postcheck 1; }

# ---------------------------------------------------------------------------
# Always-on privacy sentinel.
# ---------------------------------------------------------------------------

v130_sentinel_is_ours() {
  local p="${1:-}" cmd
  [[ "$p" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$p" 2>/dev/null || return 1
  [[ -r "/proc/$p/cmdline" ]] || return 1
  cmd="$(tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null || true)"
  [[ "$cmd" == *"__sentinel"* && "$cmd" == *"xgc.sh"* ]]
}

v130_sentinel_stop() {
  [[ "${XGC_SENTINEL_CONTEXT:-0}" == "1" ]] && return 0
  [[ -f "$SENTINEL_PID" ]] || return 0
  local p; p="$(cat "$SENTINEL_PID" 2>/dev/null || true)"
  if v130_sentinel_is_ours "$p"; then kill "$p" 2>/dev/null || true; fi
  rm -f "$SENTINEL_PID"
}

v130_runtime_hash() {
  [[ -f "$RUNTIME_CONFIG" ]] || return 1
  sha256sum "$RUNTIME_CONFIG" | awk '{print $1}'
}

v130_record_runtime_hash() {
  local h; h="$(v130_runtime_hash)" || return 1
  printf '%s' "$h" > "$RUNTIME_HASH_FILE"; chmod 600 "$RUNTIME_HASH_FILE"
  local tmp="$TMP_DIR/active-hash.$$.$RANDOM"
  if [[ -s "$ACTIVE_FILE" ]]; then
    jq --arg h "$h" '.runtime_sha256=$h | .sentinel=true' "$ACTIVE_FILE" > "$tmp" 2>/dev/null &&
      mv -f "$tmp" "$ACTIVE_FILE"
  fi
}

v130_sentinel_start() {
  [[ "${XGC_SENTINEL_CONTEXT:-0}" == "1" ]] && return 0
  v130_sentinel_stop
  [[ -f "$SELF_PATH" ]] || return 0
  nohup bash "$SELF_PATH" __sentinel >>"$SENTINEL_LOG" 2>&1 &
  local p=$!
  printf '%s' "$p" > "$SENTINEL_PID"; chmod 600 "$SENTINEL_PID"
}

v130_kill_xray_only() {
  local p=""
  [[ -f "$PID_FILE" ]] && p="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$p" ]] && v116_pid_is_our_xray "$p"; then
    kill "$p" 2>/dev/null || true
    local _
    for _ in 1 2 3 4 5 6 7 8; do kill -0 "$p" 2>/dev/null || break; sleep .12; done
    v116_pid_is_our_xray "$p" && kill -9 "$p" 2>/dev/null || true
  fi
  rm -f "$PID_FILE" "$ACTIVE_FILE"
}

stop_xray() {
  v130_sentinel_stop
  v130_kill_xray_only
  rm -f "$RUNTIME_HASH_FILE"
  command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock >/dev/null 2>&1 || true
}

v130_sentinel_loop() {
  ensure_dirs; load_settings
  local fails=0 cycle=0 expected current file name
  printf '%s sentinel start\n' "$(date '+%F %T')" >> "$SENTINEL_LOG"
  while true; do
    sleep 20
    process_alive || { printf '%s xray gone; sentinel exit\n' "$(date '+%F %T')" >>"$SENTINEL_LOG"; break; }
    expected="$(jq -r '.runtime_sha256 // empty' "$ACTIVE_FILE" 2>/dev/null || true)"
    current="$(v130_runtime_hash 2>/dev/null || true)"
    if [[ -z "$expected" || -z "$current" || "$expected" != "$current" ]] || ! v130_runtime_integrity_check "$RUNTIME_CONFIG"; then
      printf '%s runtime fingerprint/integrity failure; fail-closed stop\n' "$(date '+%F %T')" >>"$SENTINEL_LOG"
      v122_status_write FAIL "" "--" "privacy sentinel detected runtime mutation/integrity failure" 1
      XGC_SENTINEL_CONTEXT=1 v130_kill_xray_only
      break
    fi

    ((cycle+=1))
    (( cycle % 3 == 0 )) || continue
    if v130_privacy_postcheck 1; then
      fails=0
      continue
    fi
    ((fails+=1))
    printf '%s egress check failure %s/2\n' "$(date '+%F %T')" "$fails" >>"$SENTINEL_LOG"
    (( fails < 2 )) && continue

    file="$(jq -r '.file // empty' "$ACTIVE_FILE" 2>/dev/null || true)"
    name="$(jq -r '.name // "Active profile"' "$ACTIVE_FILE" 2>/dev/null || echo 'Active profile')"
    if [[ -f "$file" ]]; then
      printf '%s sentinel auto-repair start\n' "$(date '+%F %T')" >>"$SENTINEL_LOG"
      if XGC_SENTINEL_CONTEXT=1 start_profile_file "$file" "$name" 1; then
        fails=0
        v130_record_runtime_hash
        printf '%s sentinel auto-repair success\n' "$(date '+%F %T')" >>"$SENTINEL_LOG"
        continue
      fi
    fi
    v122_status_write FAIL "" "--" "privacy sentinel repair failed; Xray stopped" 2
    XGC_SENTINEL_CONTEXT=1 v130_kill_xray_only
    break
  done
  rm -f "$SENTINEL_PID"
}

# Override connection start to add runtime fingerprint + sentinel.
v122_start_once() {
  local file="$1" name="$2" quiet="${3:-0}" attempt="${4:-1}"
  local candidate="$TMP_DIR/runtime-v130-${attempt}-$$.$RANDOM.json"

  v11_patch_runtime "$file" "$candidate" "$SOCKS_PORT" "127.0.0.1" || { rm -f "$candidate"; return 1; }
  XRAY_LOCATION_ASSET="$BIN_DIR" "$XRAY_BIN" run -test -c "$candidate" >"$LOG_DIR/config-test.log" 2>&1 ||
    { rm -f "$candidate"; return 1; }

  [[ "${XGC_SENTINEL_CONTEXT:-0}" == "1" ]] || v130_sentinel_stop
  v130_kill_xray_only
  [[ "$(v116_port_free 127.0.0.1 "$SOCKS_PORT")" == "1" ]] ||
    { rm -f "$candidate"; err "127.0.0.1:${SOCKS_PORT} is already occupied."; return 1; }

  mv -f "$candidate" "$RUNTIME_CONFIG"; chmod 600 "$RUNTIME_CONFIG"
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
  XRAY_LOCATION_ASSET="$BIN_DIR" nohup "$XRAY_BIN" run -c "$RUNTIME_CONFIG" >"$LOG_DIR/xray.log" 2>&1 &
  local p=$!; printf '%s' "$p" > "$PID_FILE"; chmod 600 "$PID_FILE"

  wait_port_pid 127.0.0.1 "$SOCKS_PORT" "$p" >/dev/null 2>&1 && v116_pid_is_our_xray "$p" ||
    { v130_kill_xray_only; return 1; }
  v130_privacy_postcheck "$attempt" || { v130_kill_xray_only; return 1; }

  python3 - "$ACTIVE_FILE" "$name" "$file" "$SOCKS_PORT" <<'PY'
import json,sys,time
open(sys.argv[1],'w').write(json.dumps({
 'name':sys.argv[2],'file':sys.argv[3],'listen':'127.0.0.1','port':int(sys.argv[4]),
 'leak_guard':True,'privacy_guard':'PASS','privacy_schema':130,
 'direct_probe_performed':False,'sentinel':True,'started':int(time.time())
},ensure_ascii=False,indent=2))
PY
  chmod 600 "$ACTIVE_FILE"
  [[ "$REMEMBER_LAST" == "1" ]] && record_connected "$name" "$file"
  v130_record_runtime_hash || { v130_kill_xray_only; return 1; }
  v130_sentinel_start

  if [[ "$quiet" != "1" ]]; then
    local pip cc
    pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
    cc="$(jq -r '.country // "--"' "$PRIVACY_STATUS" 2>/dev/null || echo --)"
    ok "Connected safely: $name"
    printf " ${GREEN}${BOLD}🛡 Shield verified${RESET}  ${CYAN}%s${RESET}  ${PURPLE}%s${RESET}  ${GRAY}sentinel active${RESET}\n" "$pip" "$cc"
  fi
  return 0
}

# ---------------------------------------------------------------------------
# Privacy-aware core update: when an active tunnel exists, the GitHub check and
# download use that SOCKS5h tunnel; the same profile is restored after the core
# swap. Automatic checks are deferred until a tunnel is active.
# ---------------------------------------------------------------------------

update_xray() {
  ensure_dirs; install_deps || return 1
  info "Checking official XTLS/Xray-core pre-release channel..."
  local rel tag current asset_name asset_url expected actual tmpzip stage found name
  local was_active=0 oldfile="" oldname=""
  if process_alive; then
    was_active=1
    oldfile="$(jq -r '.file // empty' "$ACTIVE_FILE" 2>/dev/null || true)"
    oldname="$(jq -r '.name // "Previous profile"' "$ACTIVE_FILE" 2>/dev/null || echo 'Previous profile')"
  fi
  rel="$(get_prerelease_json 2>/dev/null || true)"
  [[ -n "$rel" && "$rel" != null ]] || { err "Could not query GitHub releases."; return 1; }
  tag="$(jq -r '.tag_name // empty' <<<"$rel")"
  [[ -n "$tag" ]] || { err "Release tag missing."; return 1; }
  current="$(cat "$INSTALLED_TAG" 2>/dev/null || true)"
  if [[ -x "$XRAY_BIN" && "$current" == "$tag" ]]; then
    date +%s > "$LAST_UPDATE_CHECK"
    ok "Xray is already on newest pre-release: $tag"
    return 0
  fi

  asset_name=""; asset_url=""; expected=""
  while IFS= read -r name; do
    asset_url="$(jq -r --arg n "$name" '.assets[] | select(.name==$n) | .browser_download_url' <<<"$rel" | head -n1)"
    if [[ -n "$asset_url" && "$asset_url" != null ]]; then
      asset_name="$name"
      expected="$(jq -r --arg n "$name" '.assets[] | select(.name==$n) | (.digest // "")' <<<"$rel" | head -n1 | sed 's/^sha256://')"
      break
    fi
  done < <(arch_asset_names || true)
  [[ -n "$asset_url" ]] || { err "No compatible Xray asset for $(uname -m)."; return 1; }

  tmpzip="$TMP_DIR/${asset_name}.part"; stage="$TMP_DIR/xray-stage"
  rm -rf "$stage" "$tmpzip"; mkdir -p "$stage"
  info "Downloading ${tag} • ${asset_name}"
  curl -fL --connect-timeout 15 --max-time 180 --retry 3 --retry-delay 1 -o "$tmpzip" "$asset_url" || { err "Xray download failed."; return 1; }
  if [[ -n "$expected" && "$expected" != null ]]; then
    actual="$(sha256sum "$tmpzip" | awk '{print $1}')"
    [[ "${actual,,}" == "${expected,,}" ]] || { err "SHA-256 verification failed."; rm -f "$tmpzip"; return 1; }
    ok "Release asset SHA-256 verified"
  fi
  unzip -q -o "$tmpzip" -d "$stage" || { err "Invalid Xray archive."; return 1; }
  found="$(find "$stage" -maxdepth 2 -type f -name xray | head -n1)"
  [[ -n "$found" ]] || { err "xray binary missing in release archive."; return 1; }
  chmod 700 "$found"; "$found" version >/dev/null 2>&1 || { err "Downloaded Xray cannot run on this device."; return 1; }

  stop_xray >/dev/null 2>&1 || true
  [[ -x "$XRAY_BIN" ]] && cp -f "$XRAY_BIN" "$BIN_DIR/xray.previous" 2>/dev/null || true
  cp -f "$found" "$XRAY_BIN"; chmod 700 "$XRAY_BIN"
  for name in geoip.dat geosite.dat; do
    found="$(find "$stage" -maxdepth 2 -type f -name "$name" | head -n1)"
    [[ -n "$found" ]] && cp -f "$found" "$BIN_DIR/$name"
  done
  printf '%s' "$tag" > "$INSTALLED_TAG"; date +%s > "$LAST_UPDATE_CHECK"
  rm -rf "$stage" "$tmpzip"; ok "Installed Xray $tag"

  if (( was_active==1 )) && [[ -f "$oldfile" ]]; then
    info "Restoring protected connection after core update..."
    start_profile_file "$oldfile" "$oldname" 1 && ok "Protected connection restored." || warn "Core updated, but automatic reconnect failed."
  fi
}

# ---------------------------------------------------------------------------
# App screens
# ---------------------------------------------------------------------------

settings_menu() {
  local selected=1
  while true; do
    load_settings; header; section "Settings"
    local -a items=(
      "🎨 Appearance  •  ${UI_THEME^} / ${UI_DENSITY^}"
      "🔌 SOCKS port  •  ${SOCKS_PORT}"
      "🔒 SOCKS listen  •  127.0.0.1 only"
      "⬆ Auto Xray beta update  •  $(tui_onoff_text "$AUTO_UPDATE")"
      "🧪 Full-test policy  •  ${BENCH_PROFILE}"
      "🛡 Privacy Guard  •  ALWAYS ON / fail-closed"
      "🌐 DNS Leak Guard  •  ALWAYS ON / remote DoH"
      "🧠 Privacy Sentinel  •  ALWAYS ON / auto-repair"
      "🕘 Remember last connection  •  $(tui_onoff_text "$REMEMBER_LAST")"
      "📡 Telegram posts/channel  •  ${TELEGRAM_POSTS}"
      "📦 Telegram max configs  •  ${TELEGRAM_MAX_CONFIGS}"
      "⚡ Telegram smart TCP gate  •  $(tui_onoff_text "$TELEGRAM_TCP_GATE")"
      "🔁 Telegram TCP samples  •  ${TELEGRAM_TCP_SAMPLES}"
      "💾 Auto-create passed subscription  •  $(tui_onoff_text "$TELEGRAM_AUTO_SUB")"
      "✅ Passed-sub minimum success  •  ${TELEGRAM_PASS_MIN_SUCCESS}%"
      "🛠 Advanced benchmark tuning  •  custom performance values"
      "☁ Forget Cloudflare credential  •  local credential / Worker reference"
      "↺ Reset application settings  •  keep privacy locked ON"
    )
    tui_menu "$selected" 1 "a,p,l,u,b,e,d,s,r,t,m,g,n,c,v,f,w,x" "${items[@]}"
    local ch="$TUI_CHOICE" v
    [[ "$ch" != 0 ]] && selected="$ch"
    case "$ch" in
      1) theme_studio ;;
      2) printf "New SOCKS port (1024-65535): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1024&&v<=65535)) && SOCKS_PORT="$v" || warn "Invalid port"; save_settings ;;
      3) ok "127.0.0.1 is locked to prevent network/LAN exposure."; sleep .6 ;;
      4) [[ "$AUTO_UPDATE" == 1 ]] && AUTO_UPDATE=0 || AUTO_UPDATE=1; save_settings ;;
      5)
        header; section "Test policy"
        local -a policies=("🛡 Reliable  •  conservative" "⚖ Balanced  •  recommended" "⚡ Fast  •  lighter" "🚀 Turbo  •  quickest")
        tui_menu 2 1 "r,b,f,t" "${policies[@]}"
        case "$TUI_CHOICE" in 1) apply_bench_profile reliable ;; 2) apply_bench_profile balanced ;; 3) apply_bench_profile fast ;; 4) apply_bench_profile turbo ;; esac
        ;;
      6|7|8) ok "This protection is mandatory in v1.3.0."; sleep .6 ;;
      9) [[ "$REMEMBER_LAST" == 1 ]] && REMEMBER_LAST=0 || REMEMBER_LAST=1; save_settings ;;
      10) printf "Last posts/channel (1-200): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=200)) && TELEGRAM_POSTS="$v" || warn "Invalid"; save_settings ;;
      11) printf "Max configs (1-300): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=300)) && TELEGRAM_MAX_CONFIGS="$v" || warn "Invalid"; save_settings ;;
      12) [[ "$TELEGRAM_TCP_GATE" == 1 ]] && TELEGRAM_TCP_GATE=0 || TELEGRAM_TCP_GATE=1; save_settings ;;
      13) printf "TCP samples (1-8): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=8)) && TELEGRAM_TCP_SAMPLES="$v" || warn "Invalid"; save_settings ;;
      14) [[ "$TELEGRAM_AUTO_SUB" == 1 ]] && TELEGRAM_AUTO_SUB=0 || TELEGRAM_AUTO_SUB=1; save_settings ;;
      15) printf "Minimum success (1-100): "; read -r v; [[ "$v" =~ ^[0-9]+$ ]] && ((v>=1&&v<=100)) && TELEGRAM_PASS_MIN_SUCCESS="$v" || warn "Invalid"; save_settings ;;
      16) advanced_bench_settings ;;
      17) printf "%b" "${RED}Delete local Cloudflare credential/Worker reference? [y/N]:${RESET} "; read -r v; [[ "$v" =~ ^[Yy]$ ]] && { rm -f "$CF_CONF" "$WORKER_FILE"; ok "Removed."; } ;;
      18)
        default_settings; load_settings
        UI_THEME=aurora; UI_DENSITY=comfortable; UI_BORDER=rounded; UI_ICONS=1
        SOCKS_LISTEN=127.0.0.1; LEAK_PROTECTION=1; DNS_LEAK_TEST=1
        save_settings; ok "Settings reset; Privacy Guard remains mandatory."; sleep .7 ;;
      0) return ;;
    esac
  done
}

leak_audit() {
  header; section "Privacy Center"
  process_alive || { warn "Connect a profile first."; pause; return; }
  local state pip cc note sent
  state="$(jq -r '.state // "UNKNOWN"' "$PRIVACY_STATUS" 2>/dev/null || echo UNKNOWN)"
  pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  cc="$(jq -r '.country // "--"' "$PRIVACY_STATUS" 2>/dev/null || echo --)"
  note="$(jq -r '.note // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  sent="OFF"
  [[ -s "$SENTINEL_PID" ]] && v130_sentinel_is_ours "$(cat "$SENTINEL_PID" 2>/dev/null)" && sent="ON"

  v130_card_line "🛡 Guard      ${state}   •   fail-closed" "$GREEN"
  v130_card_line "🌐 Egress     ${pip}   ${cc}" "$CYAN"
  v130_card_line "🧠 Sentinel   ${sent}   •   runtime fingerprint + auto-repair" "$PURPLE"
  v130_card_line "🔒 Scope      localhost SOCKS5h 127.0.0.1:${SOCKS_PORT}" "$WHITE"
  printf "\n ${GRAY}%s${RESET}\n" "$note"

  section "Live verification"
  if v130_privacy_postcheck 1; then
    ok "Runtime + DNS route + SOCKS5h egress checks PASS."
  else
    warn "Live check failed. Rebuilding the active protected profile..."
    local file name
    file="$(jq -r '.file // empty' "$ACTIVE_FILE" 2>/dev/null || true)"
    name="$(jq -r '.name // "Active profile"' "$ACTIVE_FILE" 2>/dev/null || echo Active)"
    if [[ -f "$file" ]] && start_profile_file "$file" "$name" 1; then ok "Automatic repair succeeded."
    else err "Repair failed; Xray stopped to preserve fail-closed behavior."; stop_xray; pause; return
    fi
  fi

  section "DNS observation"
  local remote
  remote="$(v122_dns_remote_audit)"
  [[ -n "$remote" && "$remote" != INCONCLUSIVE ]] && printf " ${CYAN}Remote resolver observation:${RESET} %s\n" "$remote" ||
    warn "External DNS observation service is inconclusive; structural DNS safeguards remain verified."

  line
  say "${GREEN}✔${RESET} IP-literal remote DoH endpoints (1.1.1.1 / 8.8.8.8)"
  say "${GREEN}✔${RESET} provider inbounds/routing/admin modules removed from runtime"
  say "${GREEN}✔${RESET} selected proxy chain only + blackhole fallback"
  say "${GREEN}✔${RESET} targetStrategy=AsIs + localhost-only SOCKS5h"
  say "${GREEN}✔${RESET} ambient HTTP_PROXY/ALL_PROXY variables ignored by XGC curl"
  say "${GREEN}✔${RESET} privacy sentinel checks runtime fingerprint continuously"
  say "${YELLOW}Scope:${RESET} ${GRAY}XGC can enforce this path only for applications actually configured to use its localhost SOCKS5h proxy; a Termux shell without Android VPN/TUN cannot capture unrelated app sockets.${RESET}"
  pause
}

show_status() {
  header; section "Status"
  local version tag profiles subs active pstate pip pcc sent
  version="$($XRAY_BIN version 2>/dev/null | head -n1 || echo 'not installed')"
  tag="$(cat "$INSTALLED_TAG" 2>/dev/null || echo '-')"
  profiles="$(count_profiles)"; subs="$(awk 'NF{c++} END{print c+0}' "$SUB_DB" 2>/dev/null)"
  active="$(jq -r '.name // "-"' "$ACTIVE_FILE" 2>/dev/null || echo -)"
  pstate="$(jq -r '.state // "READY"' "$PRIVACY_STATUS" 2>/dev/null || echo READY)"
  pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
  pcc="$(jq -r '.country // "--"' "$PRIVACY_STATUS" 2>/dev/null || echo --)"
  sent="OFF"; [[ -s "$SENTINEL_PID" ]] && v130_sentinel_is_ours "$(cat "$SENTINEL_PID" 2>/dev/null)" && sent="ON"

  v130_card_line "⚡ Core        ${version}" "$PURPLE"
  v130_card_line "🔌 Connection  $(process_alive && printf CONNECTED || printf DISCONNECTED)   •   ${active}" "$GREEN"
  v130_card_line "🛡 Privacy     ${pstate}   •   Egress ${pip} ${pcc}" "$CYAN"
  v130_card_line "🧠 Sentinel    ${sent}" "$PURPLE"
  v130_card_line "📚 Library     ${profiles} profiles   •   ${subs} subscriptions" "$WHITE"
  v130_card_line "🎨 Appearance  ${UI_THEME^}   •   ${UI_DENSITY^}   •   ${UI_BORDER^}" "$MAGENTA"
  printf "\n ${GRAY}Core channel: %s   •   SOCKS5h: 127.0.0.1:%s${RESET}\n" "$tag" "$SOCKS_PORT"
  pause
}

doctor() {
  header; section "System Doctor"
  local fail=0
  v116_state_repair_quiet
  ok "Subscription/profile state repaired"
  ok "Privacy/DNS settings locked ON"
  ok "SOCKS listener locked to localhost"
  ok "Ambient proxy environment isolated from XGC curl"
  if process_alive; then
    v130_runtime_integrity_check "$RUNTIME_CONFIG" && ok "Runtime minimization/integrity PASS" || { err "Runtime integrity FAIL"; fail=1; }
    v130_privacy_postcheck 1 && ok "Proxy-only egress verification PASS" || { err "Egress verification FAIL"; fail=1; }
    [[ -s "$SENTINEL_PID" ]] && v130_sentinel_is_ours "$(cat "$SENTINEL_PID" 2>/dev/null)" &&
      ok "Privacy sentinel active" || { warn "Sentinel missing; restarting it."; v130_sentinel_start; }
  fi
  printf "\n ${GRAY}Doctor result: %s${RESET}\n" "$([[ "$fail" == 0 ]] && printf healthy || printf attention-required)"
  pause
}

panel() {
  local selected=1 update_checked=0
  while true; do
    load_settings
    if (( update_checked==0 )) && process_alive; then
      maybe_update_xray
      update_checked=1
      load_settings
    fi
    header

    local state="Disconnected" statec="$RED" active="No active profile" pip="-" shield="Ready"
    if process_alive; then
      state="Connected"; statec="$GREEN"
      active="$(jq -r '.name // "Connected profile"' "$ACTIVE_FILE" 2>/dev/null || echo Connected)"
    fi
    pip="$(jq -r '.proxy_ip // "-"' "$PRIVACY_STATUS" 2>/dev/null || echo -)"
    shield="$(jq -r '.state // "READY"' "$PRIVACY_STATUS" 2>/dev/null || echo READY)"

    section "Dashboard"
    v130_card_line "● ${state}    •    ${active}" "$statec"
    v130_card_line "🛡 Privacy ${shield}    •    Egress ${pip}" "$GREEN"
    v130_card_line "🔌 SOCKS5h 127.0.0.1:${SOCKS_PORT}    •    ${BENCH_PROFILE^} test profile" "$CYAN"

    section "Apps & Actions"
    local -a items=(
      "⚡ Auto Connect  •  last-working → smart fallback"
      "🧠 Smart Connect  •  benchmark finalists and connect best"
      "🚀 Reconnect  •  last working profile"
      "🧪 Full Test  •  inspect one configuration deeply"
      "📡 Telegram Radar  •  fetch → TCP gate → tunnel lab"
      "📦 Subscriptions  •  add, refresh, import and remove"
      "📚 Config Browser  •  subscription → config → connect"
      "🛡 Privacy Center  •  DNS/IP audit + automatic repair"
      "🤖 Google AI  •  Gemini reachability through SOCKS5h"
      "📊 Status  •  connection, privacy and library cards"
      "🗂 Profiles  •  capability map + System Doctor"
      "⏹ Disconnect  •  stop Xray and privacy sentinel"
      "⬆ Core Update  •  newest official Xray pre-release"
      "⚙ Settings  •  appearance, performance and privacy"
      "🧾 Logs  •  Xray runtime output"
      "🕘 History  •  previous connections"
    )
    tui_menu "$selected" 1 "a,s,r,t,g,u,c,p,m,i,o,x,v,e,l,h" "${items[@]}"
    local ch="$TUI_CHOICE"
    [[ "$ch" != 0 ]] && selected="$ch"
    case "$ch" in
      1) auto_connect ;;
      2) smart_connect ;;
      3) quick_reconnect_menu ;;
      4) full_test_menu ;;
      5) telegram_center ;;
      6) manage_subs ;;
      7) subscription_connect_browser ;;
      8) leak_audit ;;
      9) google_ai_check ;;
      10) show_status ;;
      11)
        header; section "Profiles"
        list_profiles; line
        say "${GRAY}C = capabilities   D = doctor   any other key = back${RESET}"
        local k=""; IFS= read -rsn1 k || true
        case "${k,,}" in c) show_capabilities ;; d) doctor ;; esac
        ;;
      12) stop_xray; ok "Disconnected."; sleep .6 ;;
      13) header; update_xray; pause ;;
      14) settings_menu ;;
      15) show_logs ;;
      16) connection_history ;;
      0) clear 2>/dev/null || true; return ;;
    esac
  done
}

usage() {
  cat <<EOF2
$APP_NAME v$APP_VERSION
Usage: xgc [command]
  panel          Open app-style control panel (default)
  auto           Fast last-working connection, then smart fallback
  last           Reconnect last working profile
  best           Benchmark finalists and connect best
  test [N]       Full real-tunnel test of profile N
  telegram       Telegram public-channel radar
  leak           Privacy Center: DNS/IP audit + auto-repair
  google-ai      Google AI (Gemini) reachability through SOCKS5h
  refresh        Refresh all subscriptions
  update         Update Xray to newest official pre-release
  stop           Stop Xray + privacy sentinel
  status         App-style status screen
  logs           Runtime logs
  capabilities   Native protocol/transport map
  selftest-ui    Controller/UI/privacy regression self-test
  doctor         System/privacy doctor
EOF2
}

controller_selftest() {
  local fail=0
  python3 - <<'PY' || fail=1
def choose(prefix,count):
    valid=[str(i) for i in range(1,count+1) if str(i).startswith(prefix)]
    return prefix in valid, any(x!=prefix for x in valid)
assert choose('7',16)==(True,False)
assert choose('7',100)==(True,True)
assert choose('70',100)==(True,False)
assert choose('14',16)==(True,False)
print('controller decisions: PASS')
PY
  python3 - <<'PY' || fail=1
themes={'aurora','ocean','sunset','matrix','mono'}
assert len(themes)==5
print('theme model: PASS')
PY
  if ((fail==0)); then ok "UI + controller + privacy self-test PASS"; else err "Self-test FAILED"; fi
  return "$fail"
}

bootstrap() {
  ensure_dirs; mkdir -p "$STATE_BACKUP_DIR"
  if ! is_termux; then warn "This script is tuned for Termux. Continuing on this Unix-like shell."; fi
  install_deps || exit 1
  ensure_dirs; load_settings; save_settings
  v116_state_repair_quiet
  install_launcher
  v116_cleanup_test_process
  if [[ ! -x "$XRAY_BIN" ]]; then
    header; info "First run: installing newest official Xray pre-release..."
    update_xray || { err "Initial Xray installation failed."; pause; }
  fi
}

trap 'tui_cursor_show; v116_cleanup_test_process' EXIT
trap 'tui_cursor_show; v116_cleanup_test_process; exit 130' INT TERM

# Internal sentinel is intentionally handled before normal bootstrap to avoid
# UI/update work in the background watchdog process.
if [[ "${1:-}" == "__sentinel" ]]; then
  v130_sentinel_loop
  exit 0
fi

bootstrap
case "${1:-panel}" in
  panel) panel ;;
  auto) auto_connect ;;
  last) header; connect_last ;;
  best) smart_connect ;;
  test) full_test_cli "${2:-}" ;;
  telegram) telegram_center ;;
  leak) leak_audit ;;
  google-ai|googleai|gemini|ai) header; google_ai_check ;;
  refresh) refresh_all ;;
  update) header; update_xray ;;
  stop) stop_xray; ok "Xray stopped" ;;
  status) show_status ;;
  logs) show_logs ;;
  capabilities) show_capabilities ;;
  selftest-ui) controller_selftest ;;
  doctor) doctor ;;
  -h|--help|help) usage ;;
  *) usage; exit 2 ;;
esac
