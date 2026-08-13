# Guida: aggiungere un achievement normale

Questa guida descrive il flusso usato dal server Polaris per aggiungere un achievement e farlo progredire durante un'interazione.

## Come sono divisi i dati

- `gamedata/achievements.json` contiene la definizione SWF: nome, categoria, stato e soglie dei livelli.
- La tabella `achievements` contiene le righe DB dello stesso achievement: una riga per livello, con punti e ricompensa.
- Il codice Java deve chiamare il progresso nel punto esatto in cui avviene l'azione.

Il nome è la chiave comune. Deve essere identico in JSON, DB e codice, inclusi maiuscole e minuscole.

## 1. Aggiungere la definizione nel JSON

Inserire un elemento nell'array di `gamedata/achievements.json`:

```json
{
  "achievement": {
    "id": 900,
    "name": "MyAchievement",
    "creationTime": "2026-08-11",
    "state": "ENABLED",
    "category": "explore"
  },
  "levelRequirements": [
    { "level": 1, "requiredScore": 1 },
    { "level": 2, "requiredScore": 5 },
    { "level": 3, "requiredScore": 10 }
  ]
}
```

`requiredScore` è la soglia cumulativa del livello. Non usare soglie relative: il client e il server si aspettano il valore totale raggiunto.

L'`id` deve essere stabile e non duplicato nel catalogo SWF. La categoria deve essere coerente con quelle già presenti, ad esempio `identity`, `explore`, `social`, `games`, `room_builder`, `pets` o `crackables`.

## 2. Aggiungere le righe DB con una migration

Non modificare una migration già applicata. Creare una nuova migration Flyway, con una versione successiva all'ultima presente, ad esempio:

`V20260811110000__add_my_achievement.sql`

Esempio per le tre soglie sopra:

```sql
INSERT INTO `achievements`
    (`id`, `name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
VALUES
    (900000, 'MyAchievement', 'explore', 1, 5, 0, 5, 1),
    (900001, 'MyAchievement', 'explore', 2, 10, 0, 10, 5),
    (900002, 'MyAchievement', 'explore', 3, 15, 0, 15, 10)
ON DUPLICATE KEY UPDATE
    `category` = VALUES(`category`),
    `reward_amount` = VALUES(`reward_amount`),
    `reward_type` = VALUES(`reward_type`),
    `points` = VALUES(`points`),
    `progress_needed` = VALUES(`progress_needed`);
```

Gli ID DB devono essere univoci. Prima della migration verificare il range disponibile con:

```sql
SELECT MAX(`id`) AS `max_id` FROM `achievements`;
```

I valori `progress_needed` devono corrispondere alle soglie `requiredScore` del JSON. `reward_amount`, `reward_type` e `points` sono i valori server-side della ricompensa.

## 3. Collegare l'evento che deve farlo avanzare

Nel punto in cui l'azione viene completata usare il bridge null-safe:

```java
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;

AchievementInteractions.progress(habbo, "MyAchievement");
```

Per più unità in una sola azione:

```java
AchievementInteractions.progress(habbo, "MyAchievement", amount);
```

Per un contatore già calcolato, usare `sync`: incrementa solo la differenza e non duplica il progresso.

```java
AchievementInteractions.sync(habbo, "MyAchievement", observedValue);
```

La chiamata va fatta solo dopo che l'azione è riuscita. Non inserirla prima di una validazione, di un acquisto o di una transazione che potrebbe fallire.

## 4. Ricarica e verifica

1. Applicare la migration con l'avvio dell'emulatore.
2. Riavviare oppure usare il comando di reload achievement disponibile nell'emulatore.
3. Controllare che `getAchievement("MyAchievement")` non restituisca `null`.
4. Eseguire l'azione e verificare progressione, cambio livello, punti e ricompensa.

Se il JSON contiene l'achievement ma manca la tabella DB, il runtime può caricarlo come voce di compatibilità, ma reward e punti possono risultare a zero. Per un achievement nuovo vanno quindi aggiornati entrambi.

