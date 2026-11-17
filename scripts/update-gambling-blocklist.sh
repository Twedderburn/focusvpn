#!/usr/bin/env bash
# Rebuilds blocklists/gambling-domains.txt from HaGeZi's gambling list (GPL-3.0), downloaded fresh, merged with the
# current list so nothing already listed is ever dropped (lists only grow, PRD §5). Entries already covered by a
# listed parent domain are dropped, since the filter blocks every subdomain anyway.
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/gambling-onlydomains.txt
OUT=blocklists/gambling-domains.txt
tmp=$(mktemp)
curl -fsSL "$SRC" -o "$tmp"
version=$(grep -m1 -i '^# Version:' "$tmp" | sed 's/^# [Vv]ersion: //' || true)
{
  echo "# Gambling sites blocked by FocusVPN when the Gambling switch is on. One domain per line; each also blocks its subdomains."
  echo "# Source: $SRC"
  echo "# Source version: $version"
  echo "# From HaGeZi's DNS Blocklists, GPL-3.0, Copyright (c) HaGeZi. https://github.com/hagezi/dns-blocklists"
  echo "# Plus everything already in this list (lists only grow)."
  echo "# Regenerate with scripts/update-gambling-blocklist.sh."
  {
    grep -vE '^[[:space:]]*(#|$)' "$tmp" | tr -d '\r' | awk '{print tolower($1)}'
    if [ -f "$OUT" ]; then grep -vE '^[[:space:]]*(#|$)' "$OUT" | tr -d '\r' | awk '{print tolower($1)}'; fi
  } | sed 's/\.$//' | grep -E '^[a-z0-9.-]+\.[a-z0-9-]+$' | sort -u \
    | awk -F. '{print NF"\t"$0}' | sort -n -k1,1 | cut -f2 \
    | awk '{n=split($0,p,"."); s=p[n]; dup=0; for(i=n-1;i>=1;i--){ if(s in seen){dup=1;break}; s=p[i]"."s } if(!dup && !($0 in seen)){seen[$0]=1; print}}' \
    | sort
} > "$OUT.new"
mv "$OUT.new" "$OUT"
rm -f "$tmp"
echo "$(grep -vc '^#' "$OUT") domains written to $OUT"
