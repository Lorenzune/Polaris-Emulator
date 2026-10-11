# Audit e override dei permessi â€” implementazione in due fasi

Target: Polaris locale, base ae9f4279ce3828673490dc2e1e3bfbeca17ec4e2, 11 ottobre 2026. Implementazione derivata dai commit upstream df114bb9 e fba6eca1, adattata ai rank temporanei e al protocollo giÃ  presenti. Nessuna dipendenza Redis/PostgreSQL. Nessuna modifica alla semantica di esecuzione WIRED.

## Fase 1: audit e diagnostica

`:perm check nome chiave` mostra rank, valore base, override e risultato globale/con diritti stanza. Per utenti online include i plugin; offline distingue il risultato rank/override dall'esito dei plugin non verificabili.

`:perm log [nome|*] [primaId]` legge al massimo 15 eventi, con paginazione per ID. Registra attore, origine, destinatario, chiave, vecchio/nuovo valore e motivo. Gli aggiornamenti permanenti via comando rank, housekeeping e RCON, le assegnazioni temporanee e il ripristino alla scadenza appendono nella medesima transazione del cambiamento. Un errore dell'audit annulla la modifica. RCON e ripristini automatici sono attori di sistema; non viene attribuita loro un'identitÃ  utente inventata.

Il reload dei permessi confronta snapshot prima/dopo, inclusi aggiunta/rimozione rank, chiavi, nome e livello. L'evento Ã¨ `permission_observed`: documenta ciÃ² che il reload ha osservato, non identifica chi ha eseguito SQL esterno. In questo caso un errore dell'audit non impedisce di recepire una revoca giÃ  persistita fuori dall'emulatore. Non Ã¨ un audit di tutte le query amministrative SQL o di tutte le azioni dell'hotel.

## Fase 2: override personali

```
:perm list nome [pagina]
:perm set nome chiave 0|1|2 [30m|12h|7d|2w|perm] [motivo]
:perm unset nome chiave [motivo]
```

0 nega; 1 concede globalmente; 2 concede solo quando il chiamante del controllo passa i diritti stanza. La durata omessa Ã¨ permanente; la durata temporanea massima Ã¨ 30 giorni. Un override attivo prevale sia sul rank sia sui plugin. Alla scadenza torna il comportamento base; non viene cambiato il rank. Gli override sono indipendenti: una successiva modifica del rank non elimina automaticamente le eccezioni personali. Per rimuovere anche quelle usare `unset` e verificare `list`.

Lettura e scrittura sono separate: `cmd_perm` e `cmd_perm_edit`. La migrazione inizializza la lettura dai rank con `cmd_update_permissions`; la scrittura richiede che il rank avesse sia `cmd_update_permissions` sia `cmd_give_rank`. Chiavi/grant giÃ  configurati non vengono sovrascritti.

L'attore deriva dal client autenticato. Sono vietate modifiche a sÃ© stessi e a destinatari oltre la gerarchia giÃ  stabilita da Polaris (ID rank, inclusa l'eccezione del rank massimo; non viene introdotto un nuovo ordinamento per livello). Non si possono concedere chiavi sconosciute o scope superiori ai propri: un permesso con diritti stanza non Ã¨ delegabile come globale. Una concessione non puÃ² durare oltre il permesso temporaneo che autorizza l'operatore, compreso il suo rank temporaneo. Anche togliere una negazione richiede poter concedere il permesso che verrebbe ripristinato. Per plugin offline non valutabili la rimozione richiede cautelativamente lo scope globale.

La transazione blocca le righe utenti in ordine ID e ricontrolla rank persistiti, gerarchia e autorizzazione prima di scrivere. Gli override dell’operatore vengono riletti sulla stessa connessione, senza fidarsi della cache per autorizzare la mutazione. Audit e override sono atomici. Nessun lock della cache viene mantenuto durante questa transazione: evita deadlock tra controlli dell'attore e modifiche incrociate. L'invalidazione avviene dopo commit e attende eventuali caricamenti della cache prima di scartarli.

## Scadenza, cache e sincronizzazione client

La cache dura 5 minuti; la scadenza del singolo override Ã¨ verificata a ogni controllo, anche con cache valida. Login/ripresa, modifica in gioco e reload invalidano le rispettive cache. Un reload invalida anche caricamenti in corso tramite una revisione monotona. Un fallimento del caricamento Ã¨ distinto dall'assenza di override: i controlli negano i permessi e ritentano dopo 5 secondi. Questo puÃ² rendere temporaneamente indisponibili funzioni che richiedono permessi quando il DB non Ã¨ raggiungibile; non usa vecchie concessioni come fallback.

Un solo task condiviso controlla al massimo 500 utenti online per passaggio, ogni secondo dopo il passaggio precedente. Spedisce permessi, perks e lista comandi solo quando cambia la firma. La revoca server non dipende dal task. In condizioni normali, fino a 500 online la UI converge al passaggio successivo; oltre, occorre attraversare piÃ¹ batch. Latenza DB e scheduling possono aumentare il ritardo. I pacchetti esistenti non cambiano layout: non serve una nuova versione client/render.

Costo strutturale, non benchmark: cache pesata a 200.000 unitÃ  (minimo 10 per utente, oppure numero override +1), massimo 256 override per utente; fino a circa 20.000 utenti senza override o circa 778 utenti con 256 override ciascuno prima dell'eviction. Le unitÃ  non sono byte: RAM/CPU reali dipendono da JVM, stringhe e carico e non sono state misurate in produzione. A cache calda i controlli non fanno query; a regime, 20.000 utenti continuamente attivi con TTL 300 secondi implicano circa 67 caricamenti SQL/secondo medi, esclusi login, errori e invalidazioni. Un solo task e nessun thread per utente. Gli eventi audit aggiungono una INSERT alle mutazioni, non ai normali controlli. La tabella audit cresce: pianificare esportazione/retention secondo esigenze operative, senza cancellazioni automatiche in questa patch.

## Attivazione

Tre nuove migrazioni: V20261011110000 (audit), V20261011110100 (override), V20261011110200 (comando/permessi). Applicarle attraverso il runner giÃ  presente prima di usare il nuovo JAR; se le migrazioni all'avvio sono disabilitate usare, con la normale configurazione del deployment:

```
java -jar Polaris-4.2.67-jar-with-dependencies.jar --migrations=apply --migrations-only
```

Poi riavviare l'emulatore. Non serve rifare migrazioni giÃ  applicate o ripristinare il catalogo. La migrazione override amplia i timestamp a BIGINT e converte i timestamp espressi in secondi della versione upstream in millisecondi; le date moderne giÃ  in millisecondi rimangono intatte. Verificare eventuali script locali che scrivono direttamente nella nuova tabella.

I controlli con solo `Rank`, privi di identitÃ  utente, restano controlli del rank: non possono applicare eccezioni personali. In particolare i vecchi percorsi offline di privacy `userinfo` e immunitÃ  al ban stanza continuano a usare il rank. I plugin che decidono da soli senza chiamare `Habbo.hasPermission` non vengono riscritti. Questo intervento non equivale a un audit completo di autenticazione, RCON o infrastruttura.

## Verifica

Esito e log finali sono riportati nel report di esecuzione sotto `D:/ducket/reports/permissions-implementation-2026-10-11`. Nessun invio webhook. Nessuna modifica al DB del deployment e nessun commit/push automatico.

Esito finale: 1889 casi unitari raccolti (1878 passati, 11 saltati), 24 nuovi casi tutti passati. I due IT MariaDB sono saltati per Docker non disponibile. JAR compilato. Spotless passato; `verify` fallisce su cinque segnalazioni SpotBugs in sorgenti WIRED preesistenti e identiche a HEAD, documentate nel report. I gate non sono stati indeboliti e questi WIRED non sono stati modificati.
