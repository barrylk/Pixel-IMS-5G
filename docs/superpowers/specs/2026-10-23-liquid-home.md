# Home page: Liquid Glass preview alignment

The user asked to redesign the opening Home/Setup page to match the existing approved preview monitor while ADB is unavailable. Keep the existing Kotlin/Compose architecture, midnight blue / pearl palette, blue accent, sans-serif type, frosted rounded panes and glass pill controls. Share the monitor pane implementation rather than inventing another style.

Order: Home + refresh control; selected-SIM glass selector; actual connection summary; visible 5G / IMS / access / patch status; Live network and Field test actions; open setup checks with existing fix actions; expanded modem patch controls; controls/help navigation. No readiness percentage ring. A mounted patch or enabled setting must never be presented as proof of active 5G. Missing/stale readings must remain identifiable. Device checks are a snapshot refreshed explicitly, not a continuously live monitor.

Preserve root neutrality, current monitor work, production package identity, patch confirmation/reboot behavior and no-GitHub-push instruction. Do not connect to, install onto, or modify an unprovided device. Validate debug/release builds, existing tests and focused home-status regression cases; native rendering remains pending ADB. Include theme previews for later visual inspection.

## User correction during device review
Home is for setup, access and compatibility only. Actual connection state, IMS registration/transport, signal, CA bands and bandwidth stay in Network. Home must not fetch the radio snapshot just to duplicate monitor content.

Network CA values must follow identified components for each sample. Physical-channel lists take precedence over older CellInfo components; a smaller fresh list removes old rows. Calculate widths and totals from those components, preserve unknown widths, and keep ServiceState's potentially retained registration summary separately labeled. A CA flag without identified secondary carriers is “4G+ reported”, not confirmed multi-component detail.

The Pixel 7 Pro was connected during implementation, so install and verify the final native preview there. Keep the separate Pixel 10 investigation pending its own ADB connection.
