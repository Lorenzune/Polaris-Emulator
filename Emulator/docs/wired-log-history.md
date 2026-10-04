# Persistent Wired history

`wf_act_log`, `wf_act_neg_log`, the newer write-to-log effects, and system monitor
 events are recorded exclusively in `wired_log_history`. The negative log runs
when the stack's conditions fail. No JSONL files or disk recovery journals are
read or written by the Wired history implementation.

Log messages use the same stack-extra placeholder resolver as `wf_act_show_message`.
For example, `Valore: $(test)` records `Valore: 42` when the configured variable
output extra resolves `test` to 42. Resolution happens at each execution using
the current context, including selectors, variable targets, and text connectors.
The saved template is preserved. Positive and negative log effects both support
this behavior; tokens without a matching extra follow the show-message behavior.

Flyway migration `V20261002120000__wired_log_history.sql` creates the table in the
normal Polaris migration workflow. The runtime schema contract includes its
columns. History has no TTL, pruning job, or cascading foreign keys. Resetting
monitor counters, picking up a wired, unloading a room, or restarting the server
does not delete stored logs.

Each write uses a database transaction and is acknowledged only after commit.
Failed writes raise an error and are not added to the visible history as if
persisted. There is no asynchronous queue that could lose uncommitted logs on
shutdown. Event UUIDs prevent duplicate rows if an already committed write is
retried. Database failure does not fall back to file storage or silently return
an empty history. This favors durable storage over the latency of asynchronous
writes; an unavailable database cannot accept new history entries.

The existing client receives a complete room history and paginates it locally,
using the unchanged monitor packet. Server-side page/filter queries require a
future compatible protocol extension; memory and packet size still grow with
room history.

Validation uses an isolated real MariaDB and checks more than 200 logs, Unicode,
system events, counter resets, reloads, room isolation, idempotent writes, and
explicit failure/recovery behavior.
