# Guida: aggiungere un crackable e collegare un achievement

Un crackable è definito nella tabella `items_crackable`. La riga identifica il furni di base e descrive quanti utilizzi servono, quali premi può dare e quando incrementare gli achievement.

## Prerequisiti

Prima di creare la riga devono già esistere:

- il furni di base in `items_base`/productdata;
- tutti gli item premio indicati in `prizes`;
- l'achievement nel catalogo (`gamedata/achievements.json` e tabella `achievements`).

`item_id` è l'ID del furni di base, non l'ID dell'istanza posseduta da un utente. `item_name` è il suo classname.

Verifica rapida:

```sql
SELECT `id`, `classname`
FROM `items_base`
WHERE `id` = 12000 OR `classname` = 'my_crackable';
```

## Struttura di `items_crackable`

| Colonna | Significato |
| --- | --- |
| `item_id` | ID del furni di base, chiave primaria |
| `item_name` | classname del furni |
| `count` | numero di tick/utilizzi necessari per completare il crackable |
| `prizes` | premi nel formato `itemId:chance;itemId:chance` |
| `achievement_tick` | achievement incrementato a ogni tick |
| `achievement_cracked` | achievement incrementato una volta alla rottura completa |
| `required_effect` | effetto richiesto; `0` significa nessun effetto |
| `subscription_duration` | durata opzionale dell'abbonamento in giorni |
| `subscription_type` | `hc` o `bc`, se è previsto un abbonamento |

Le colonne achievement accettano un solo nome ciascuna. Lasciare `''` quando non serve alcun collegamento.

## 1. Aggiungere un crackable senza achievement

Creare una nuova migration Flyway, senza modificare una migration già applicata:

`V20260811130000__add_my_crackable.sql`

```sql
INSERT INTO `items_crackable`
    (`item_id`, `item_name`, `count`, `prizes`,
     `achievement_tick`, `achievement_cracked`, `required_effect`,
     `subscription_duration`, `subscription_type`)
VALUES
    (
        12000,
        'my_crackable',
        10,
        '12001:60;12002:30;12003:10',
        '',
        '',
        0,
        NULL,
        NULL
    )
ON DUPLICATE KEY UPDATE
    `item_name` = VALUES(`item_name`),
    `count` = VALUES(`count`),
    `prizes` = VALUES(`prizes`),
    `required_effect` = VALUES(`required_effect`),
    `subscription_duration` = VALUES(`subscription_duration`),
    `subscription_type` = VALUES(`subscription_type`);
```

Nel campo `prizes`, le probabilità non devono per forza sommare esattamente a 100: il server usa la somma dei valori come peso totale. Il formato deve però essere valido e ogni item ID deve esistere.

## 2. Collegare un achievement a ogni utilizzo

Usare `achievement_tick` quando ogni click/tick deve contare:

```sql
UPDATE `items_crackable`
SET `achievement_tick` = 'CrackableUses',
    `achievement_cracked` = ''
WHERE `item_id` = 12000;
```

Con `count = 10`, l'achievement riceverà un incremento per ciascuno dei dieci utilizzi. Il nome deve corrispondere esattamente a quello del catalogo achievement.

## 3. Collegare un achievement alla rottura completa

Usare `achievement_cracked` quando deve contare solo il completamento del crackable:

```sql
UPDATE `items_crackable`
SET `achievement_tick` = '',
    `achievement_cracked` = 'CrackableCompleted'
WHERE `item_id` = 12000;
```

Il server incrementa questo achievement una sola volta, quando il numero di tick raggiunge `count`, prima dell'esplosione/consegna del premio.

## 4. Usare entrambi i collegamenti

È possibile valorizzare entrambe le colonne:

```sql
UPDATE `items_crackable`
SET `achievement_tick` = 'CrackableUses',
    `achievement_cracked` = 'CrackableCompleted'
WHERE `item_id` = 12000;
```

In questo caso il primo achievement cresce a ogni utilizzo e il secondo cresce alla conclusione. Non usare lo stesso nome in entrambe le colonne se non si vuole conteggiare due volte lo stesso evento finale: l'ultimo tick incrementa `achievement_tick` e la rottura incrementa anche `achievement_cracked`.

## 5. Effetto obbligatorio e premi speciali

Per richiedere un effetto avatar durante l'utilizzo:

```sql
UPDATE `items_crackable`
SET `required_effect` = 33
WHERE `item_id` = 12000;
```

Per una ricompensa abbonamento, impostare durata e tipo:

```sql
UPDATE `items_crackable`
SET `subscription_duration` = 31,
    `subscription_type` = 'hc'
WHERE `item_id` = 12000;
```

## 6. Ricaricare e verificare

Dopo aver applicato la migration:

1. riavviare l'emulatore oppure usare il comando `updateitems`/`Update Items`, che ricarica item e crackable;
2. verificare la configurazione:

```sql
SELECT `item_id`, `item_name`, `count`, `prizes`,
       `achievement_tick`, `achievement_cracked`, `required_effect`
FROM `items_crackable`
WHERE `item_id` = 12000;
```

3. posizionare il furni in una stanza;
4. eseguire esattamente `count` utilizzi;
5. controllare che il premio venga creato e che il progresso achievement aumenti nel momento previsto.

Se il nome achievement non esiste, il crackable continua a funzionare ma quel progresso viene ignorato. Se un premio o il furni di base non è caricato, il crackable può caricarsi parzialmente ma la consegna del premio fallirà: verificare sempre gli ID in `items_base`.

