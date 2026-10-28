#!/usr/bin/env bash
# Systempakete für die Linux-Jobs installieren, ohne an einem hängenden Paket-Spiegel festzuhängen:
# jeder Versuch hat eine Zeitgrenze, insgesamt drei Versuche. Sonst wartet der Job bis zu seinem Timeout
# (CI: 90 Minuten) – und ohne grünes CI gibt es kein Release.
# Aufruf: bash scripts/apt-install.sh paket1 paket2 …
set -uo pipefail

for attempt in 1 2 3; do
  if timeout 300 sudo DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 update &&
     timeout 900 sudo DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 install -y "$@"; then
    exit 0
  fi
  echo "::warning title=apt-get::Versuch $attempt von 3 ist fehlgeschlagen oder hing (Paket-Spiegel?) – neuer Versuch."
  # Ein abgebrochenes Installieren kann dpkg halb fertig hinterlassen.
  sudo DEBIAN_FRONTEND=noninteractive dpkg --configure -a || true
  sleep 15
done

echo "::error title=apt-get::Die Systempakete ließen sich nach drei Versuchen nicht installieren – meist ist ein Ubuntu-Paket-Spiegel gestört. Den Lauf später neu starten."
exit 1
