<script setup lang="ts">
// Erklärung unter der Meldung „Microsoft-Anmeldung nicht verfügbar“ (Fehler `auth_not_approved`, d. h.
// `login_with_xbox` antwortet 403): Mojang hat die App nicht freigegeben. Ehrlich und ruhig, ohne Termin und ohne
// Versprechen. Konten-Seite und Einrichtungs-Assistent zeigen sie gleich.
//
// Andockpunkt für einen Weg zum Spielen ohne Anmeldung in Cubiq (z. B. „Mit dem offiziellen Minecraft Launcher
// spielen“): Satz und Knopf hier unter dem Hinweis ergänzen – dann erscheinen sie an beiden Stellen. Der Slot nimmt
// zusätzlich Dinge auf, die nur eine Stelle braucht.
</script>

<template>
  <div class="mt-1 space-y-2" data-testid="sign-in-unavailable">
    <p class="text-xs text-base-400">{{ t('accounts.login.notApprovedHint') }}</p>
    <slot />
  </div>
</template>
