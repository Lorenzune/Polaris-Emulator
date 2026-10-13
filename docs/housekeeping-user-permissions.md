# Permessi personali nell'housekeeping

Il rank rimane la base. In **Gestione → Permessi → Utenti**, cercare un nome o `#ID` e scegliere per ogni chiave:

- **Eredita dal rank**: rimuove l'eccezione personale.
- **Abilitato**: concede il permesso a quell'utente.
- **Disabilitato**: nega il permesso anche se il rank lo concede.
- **Con diritti stanza**: disponibile per i permessi con valore massimo 2.

Due utenti rank 7 possono quindi avere eccezioni diverse, senza modificare il rank 7 o gli altri utenti. Le modifiche dall'interfaccia sono permanenti; eventuali eccezioni temporanee create con `:perm` mostrano la scadenza. Una modifica del rank non cancella le eccezioni. La colonna «Rank + eccezione» descrive questa combinazione, senza attribuirle decisioni autonome dei plugin.

## Accesso

Servono `acc_housekeeping`, `acc_hk_permissions`, `acc_hk_users` (ricerca utente) e il nuovo **`acc_hk_user_permissions`**. Quest'ultimo si configura nella matrice dei permessi dei rank. Lo schema Polaris separa i metadati in `permission_ranks` e i valori dei permessi in `permission_definitions.rank_<id>`; non serve aggiungere una colonna per ogni utente.

`cmd_update_permissions` continua a proteggere le modifiche alla matrice dei rank. La gestione personale ha un'autorizzazione separata. La migrazione inizializza `acc_hk_user_permissions` solo dove erano già concessi sia `acc_hk_permissions` sia `cmd_perm_edit`, preservando configurazioni esistenti.

Sono vietate modifiche a se stessi e a utenti protetti dalla gerarchia corrente. Il rank massimo può modificare altri utenti dello stesso rank. Una concessione non può superare i permessi o la durata delle autorizzazioni dell'operatore. Anche rimuovere una negazione verifica il permesso che tornerebbe attivo.

## Persistenza e compatibilità

Si riusano `user_permission_overrides` e l'audit transazionale `permission_audit` (origine `housekeeping`). La scrittura ricontrolla rank e autorizzazioni sulla transazione bloccata, invalida la cache dopo il commit e aggiorna permessi, perks e comandi del destinatario online.

Il pacchetto esistente per modificare i rank conserva i tre campi originali. Solo le richieste personali aggiungono `string "user" + int userId`, con `rankId=0` e `value=-1` per rimuovere l'eccezione. La lista `user.permissions` riusa il protocollo tabellare già presente. I client precedenti continuano a gestire i rank; la nuova interfaccia richiede il client aggiornato.

## Attivazione

Applicare `V20261013120000__housekeeping_user_permissions.sql` con il runner delle migrazioni di Polaris, poi avviare il JAR aggiornato e distribuire il client aggiornato. Le tabelle degli override e dell'audit devono essere già presenti attraverso le migrazioni dell'11 ottobre.

Non applicare nuovamente migrazioni già registrate da Flyway e non modificare manualmente la sua cronologia. L'intervento non cambia le credenziali o il rank degli utenti.
