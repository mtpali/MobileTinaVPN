# Subscription expiry label correction

Both editions use the same non-persisted UI status policy for legacy and Compose cards. At now >= expiry, display پایان اشتراک; before expiry retain اتمام حجم for exhausted quota. If both conditions hold, expiry has priority. Unknown/nonpositive expiry does not mean ended. Fractional future days still round up; exact expiry never displays remaining days.

The visible legacy card schedules one delayed refresh at the next day/expiry boundary, cancels it on pause/destroy and recalculates on resume or subscription changes. Compose state follows the visible lifecycle and new subscription data rather than remembering the initial wall clock forever. No background worker, polling, storage or network request is added. Scheduling uses the existing device wall clock display; trusted expiry enforcement and marker/session behavior are unchanged.

Tests cover exact expiry ±1 ms, quota/time precedence, no expiry, day boundaries and large epoch conversion. Other final-no-log behavior, social links, artwork, signing/integrity guard, protected subscriptions, QR/import/reset, per-app proxy and native cores remain unchanged. Source and compiled resource text are checked in the final validation; no device execution is implied by CI success.
