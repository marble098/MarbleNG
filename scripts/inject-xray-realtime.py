#!/usr/bin/env python3
# Anchor-guarded Xray source injector. MARBLE_REALTIME_ENGINE_V70
from pathlib import Path
import shutil,sys
if len(sys.argv)!=2:raise SystemExit('usage: inject-xray-realtime.py XRAY_SOURCE')
x=Path(sys.argv[1]).resolve();root=Path(__file__).resolve().parents[1];src=root/'native/xraypatch/marble_telemetry_linux.go';dst=x/'transport/internet/marble_telemetry_linux.go';sock=x/'transport/internet/sockopt_linux.go'
if not src.is_file() or not sock.is_file():raise SystemExit('missing source/Xray sockopt')
shutil.copyfile(src,dst);text=sock.read_text();call='\tif isTCPSocket(network) {\n\t\tmarbleTrackSocket(fd, network, address)\n\t}\n'
if 'marbleTrackSocket(fd, network, address)' not in text:
 start=text.find('func applyOutboundSocketOptions(');end=text.find('\n}\n\n// applyInboundSocketOptions',start)
 if start<0 or end<0:raise SystemExit('Xray outbound sockopt function anchor changed')
 seg=text[start:end];needle='\n\treturn nil\n';pos=seg.rfind(needle)
 if pos<0:raise SystemExit('Xray outbound return anchor changed')
 seg=seg[:pos]+'\n'+call+seg[pos:];text=text[:start]+seg+text[end:];sock.write_text(text)
if text.count('marbleTrackSocket(fd, network, address)')!=1:raise SystemExit('telemetry hook count != 1')
print('[OK] Marble realtime TCP_INFO hook injected')
