# Guida: aggiungere ricette di crafting

Il catalogo attuale usa una sola tabella `crafting_recipes`, alimentata dal JSON delle ricette. Le vecchie tabelle `crafting_altars_recipes` e `crafting_recipes_ingredients` non devono più essere usate.

## Struttura attuale

Le colonne importanti sono:

| Colonna | Valore |
| --- | --- |
| `id` | ID numerico univoco della ricetta |
| `product_name` | classname del prodotto creato; deve esistere nel productdata |
| `reward_name` | classname dell'item consegnato; deve esistere negli item caricati |
| `station_ids` | array JSON con gli ID degli altari compatibili |
| `ingredients` | array JSON di `{classname, amount}` |
| `enabled` | `'1'` per abilitarla, `'0'` per disabilitarla |
| `achievement` | nome dell'achievement opzionale |
| `secret` | `'1'` per ricetta segreta, altrimenti `'0'` |
| `limited` | `'1'` se ha quantità limitata |
| `remaining` | quantità iniziale disponibile per una ricetta limitata |

`source_page` e `source_url` sono metadati d'importazione rimossi dalla migration di normalizzazione: non inserirli più.

## 1. Aggiungere una ricetta normale

Creare una nuova migration Flyway, senza modificare le migration già applicate:

`V20260811120000__add_my_crafting_recipe.sql`

Esempio:

```sql
INSERT INTO `crafting_recipes`
    (`id`, `product_name`, `reward_name`, `station_ids`, `ingredients`,
     `enabled`, `achievement`, `secret`, `limited`, `remaining`)
VALUES
    (
        600,
        'my_product',
        'my_product',
        '[8178]',
        '[{"classname":"ingredient_one","amount":2},{"classname":"ingredient_two","amount":1}]',
        '1',
        '',
        '0',
        '0',
        0
    );
```

Controlli necessari prima di applicare la migration:

- `product_name` e `reward_name` devono essere classnames realmente caricati;
- ogni `classname` in `ingredients` deve esistere;
- `amount` deve essere maggiore di zero;
- `station_ids` deve contenere l'ID numerico dell'altare corretto;
- non riutilizzare un `id` o un `product_name` già presente;
- `[]` o `[0]` non collegano la ricetta a un altare utilizzabile e la ricetta verrà saltata dal manager.

Per una ricetta segreta impostare `secret = '1'`. Il runtime incrementa automaticamente `AtcgSecret` quando l'utente scopre per la prima volta quella ricetta. Per una ricetta normale incrementa automaticamente `Atcg` dopo la creazione riuscita del prodotto.

## 2. Aggiungere un achievement alla ricetta

Il nome va inserito direttamente nella colonna `achievement` e deve corrispondere esattamente al nome del catalogo achievement:

```sql
INSERT INTO `crafting_recipes`
    (`id`, `product_name`, `reward_name`, `station_ids`, `ingredients`,
     `enabled`, `achievement`, `secret`, `limited`, `remaining`)
VALUES
    (
        601,
        'designer_chair',
        'designer_chair',
        '[9226]',
        '[{"classname":"wood_piece","amount":3}]',
        '1',
        'Chef',
        '0',
        '0',
        0
    );
```

Per collegare un achievement a una ricetta già presente:

```sql
UPDATE `crafting_recipes`
SET `achievement` = 'Chef'
WHERE `product_name` = 'designer_chair';
```

Quando il craft termina correttamente, il server esegue automaticamente:

1. progresso dell'achievement indicato in `achievement`, se non vuoto;
2. progresso di `Atcg` per il crafting normale;
3. oppure, per una ricetta segreta, progresso di `AtcgSecret` alla prima scoperta.

Non serve aggiungere codice Java per questo collegamento singolo.

## 3. Collegare molte ricette allo stesso achievement

È possibile usare una migration con un solo `UPDATE`:

```sql
UPDATE `crafting_recipes`
SET `achievement` = 'Chef'
WHERE `product_name` IN (
    'designer_chair',
    'designer_table',
    'designer_lamp'
);
```

Se il collegamento dipende dall'altare, usare `station_ids` come JSON e una condizione `REGEXP`, come nelle migration SWF già presenti. In quel caso il nome achievement deve comunque esistere nel catalogo.

## 4. Nuovo achievement usato dal crafting

Prima aggiungere l'achievement seguendo [guida-aggiungere-achievement.md](guida-aggiungere-achievement.md), cioè:

1. definizione in `gamedata/achievements.json`;
2. righe corrispondenti nella tabella `achievements`;
3. migration crafting che valorizza `crafting_recipes.achievement`.

Se manca il catalogo, il craft può completarsi ma il progresso non verrà registrato perché il server non trova l'achievement.

## 5. Verifica finale

```sql
SELECT `id`, `product_name`, `station_ids`, `achievement`, `secret`, `enabled`
FROM `crafting_recipes`
WHERE `product_name` = 'designer_chair';
```

Poi ricaricare il crafting o riavviare l'emulatore, verificare che l'altare sia presente nella stanza e provare un craft reale. Il conteggio achievement deve avvenire solo dopo la consegna effettiva dell'item.

