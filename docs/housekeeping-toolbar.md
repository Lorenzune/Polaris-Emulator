# Housekeeping currencies and toolbar widgets

The **Currencies and widgets** housekeeping page is guarded by `acc_housekeeping` and the rank-controlled `acc_hk_toolbar` capability, including per-user permission overrides and the housekeeping lockdown. The migration initially copies the existing `acc_hk_hotel` grants. Replaying it preserves configured grants and saved toolbar settings.

Operators can configure up to 30 currencies, ordered in the purse or in separate currency panels. Each entry has an existing currency type (`-1` for credits), label, optional icon URL, color and visibility. Newly displayed activity-point types show zero until they receive a balance. This changes presentation; it does not create balances, grant points, or change catalog prices.

The widget switches control radio, voice chat, HC, earnings, existing new-feature bubbles, group information, Mystery Box, offers and room promotions. They control visibility; room voice access and other server permissions continue to apply independently.

Up to 20 additional bubbles have plain text, optional image and link, color, order and visibility. Links support HTTP/HTTPS, root-relative web paths and allowlisted existing UI routes. No HTML or executable URL schemes are accepted. Each bubble optionally allows dismissal for the current mounted session. Optional start/end timestamps use UTC seconds (start inclusive, end exclusive) and update automatically. Housekeeping accepts local browser dates and shows a live preview, including hidden or scheduled bubbles. Width always fills the 232px toolbar rail; only height is editable (48–320px, or automatic to fit content). Legacy width values are preserved but ignored. Both actions are underlined text inside the bubble: Close and a configurable link label (80 characters, default View). Optional metadata preserves existing saved bubbles without another database migration.

## Persistence and compatibility

`V20261014100000__toolbar_configuration.sql` creates a singleton `toolbar_configuration` row with a revision and nullable JSON configuration. A null configuration preserves each deployed client's existing defaults. The first successful save stores the complete configuration globally. Saves use an atomic revision check; stale editors must reload rather than overwrite someone else's update. Changes are recorded in the housekeeping audit log.

The public authenticated request is `9133` (empty body); public response `9208` carries an integer revision and a JSON string (`null` before the first save). The permission-checked save `9134` carries the expected revision and configuration JSON and uses the existing housekeeping action ACK with key `hotel.toolbar.save`. Only clients subscribing through `9133` receive live updates. Existing packet formats are unchanged, and older clients can continue using their deployed configuration.

Deploy the updated emulator (which applies its migration), renderer communication package and client together to use this page. No CMS change is needed. The hotel database is not modified by local tests; database validation uses disposable MariaDB schemas/containers.
