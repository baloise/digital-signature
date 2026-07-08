#!/usr/bin/env bash
set -euo pipefail
# ─────────────────────────────────────────────────────────────────────────────
# toggle-dark-feature.sh — check or toggle a Confluence DC **site** dark feature
# via ScriptRunner (delegates to sr-exec.sh).
#
# Usage:
#   toggle-dark-feature.sh status [feature-key]
#   toggle-dark-feature.sh on     [feature-key]
#   toggle-dark-feature.sh off    [feature-key]
#
# Default feature: migration-assistant.app-migration.dev-mode — the CMA "app-migration
# dev mode" flag. It must be ON for the Digital Signature app to be offered by the Cloud
# Migration Assistant (the app isn't registered for CMA otherwise).
#
# Host/creds come from sr-exec.sh defaults: $CONFLUENCE_URL (else prod) and
# $ATLAS_BALOISE_NET_COM_ADMIN_USR/_PWD. Target int by exporting
# CONFLUENCE_URL=https://int-confluence.baloisenet.com.
#
# Examples:
#   scripts/toggle-dark-feature.sh status
#   scripts/toggle-dark-feature.sh off
#   CONFLUENCE_URL=https://int-confluence.baloisenet.com scripts/toggle-dark-feature.sh on
# ─────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ACTION="${1:-status}"
FEATURE="${2:-migration-assistant.app-migration.dev-mode}"

case "$ACTION" in
  status) OP=status ;;
  on)     OP=enable ;;
  off)    OP=disable ;;
  *) echo "usage: $(basename "$0") <status|on|off> [feature-key]" >&2; exit 2 ;;
esac

GROOVY="
import com.atlassian.sal.api.component.ComponentLocator
import com.atlassian.confluence.setup.settings.DarkFeaturesManager
def dfm = ComponentLocator.getComponent(DarkFeaturesManager)
def key = '${FEATURE}'
def before = dfm.getDarkFeatures().isFeatureEnabled(key)
if ('${OP}' == 'enable'  && !before) dfm.enableSiteFeature(key)
if ('${OP}' == 'disable' &&  before) dfm.disableSiteFeature(key)
def after = dfm.getDarkFeatures().isFeatureEnabled(key)
return 'feature=' + key + '  action=${OP}  enabled: ' + before + ' -> ' + after
"

exec "$SCRIPT_DIR/sr-exec.sh" -e "$GROOVY"
