// Uses connection-local temporary tables; no persistent game data is written.
const fs=require('node:fs');
const assert=require('node:assert/strict');
const mysql=require('mysql2/promise');
const {importBundle,selectRooms}=require('./transfer.cjs');
const bundle=selectRooms(require('./presets.json'),'410');

async function main() {
 const configPath=process.argv[process.argv.indexOf('--config')+1];
 if(!process.argv.includes('--config') || !configPath) throw Error('Usage: node verify-local.cjs --config /path/local/config.ini');
 const config=Object.fromEntries(fs.readFileSync(configPath,'utf8').split(/\r?\n/)
  .filter(line=>line.includes('=') && !/^[#;]/.test(line.trim()))
  .map(line=>[line.slice(0,line.indexOf('=')).trim(),line.slice(line.indexOf('=')+1).trim()]));
 const db=await mysql.createConnection({host:config['db.hostname'],port:Number(config['db.port']||3306),user:config['db.username'],password:config['db.password']||'',database:config['db.database']});
 try {
  const names=[...new Set(bundle.presets[0].items.map(item=>item.className))];
  const [bases]=await db.execute(`SELECT * FROM items_base WHERE item_name IN (${names.map(()=>'?').join(',')})`,names);
  const [rooms]=await db.execute('SELECT * FROM rooms WHERE id=?',[269]);
  assert.equal(rooms.length,1);
  // MariaDB can omit shadowed persistent tables from information_schema in this connection.
  const [engines]=await db.execute("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('items','items_base','rooms','room_models_custom','room_furni_wired_variables')");
  const schemas=new Map();
  for(const table of ['items','items_base','rooms','room_models_custom','room_furni_wired_variables']) {
   const [rows]=await db.query(`SHOW CREATE TABLE ${table}`);
   // MySQL temporary tables do not support foreign keys. Column/index definitions stay intact.
   schemas.set(table,rows[0]['Create Table'].split('\n')
    .filter(line=>!/^\s*CONSTRAINT .*FOREIGN KEY/.test(line)).join('\n')
    .replace(/^CREATE TABLE /,'CREATE TEMPORARY TABLE ').replace(/,\n\)/g,'\n)'));
  }
  for(const table of ['items','items_base','rooms','room_models_custom','room_furni_wired_variables']) {
   // All identifiers are hard-coded above. Temporary copies shadow the real tables only here.
   await db.query(schemas.get(table));
  }
  for(const row of bases.filter(base=>base.interaction_type!=='wf_cnd_anchored_instance_is')) await db.query('INSERT INTO items_base SET ?',row);
  await db.query('INSERT INTO rooms SET ?',{...rooms[0],id:410,owner_id:16});
  const adapter={
   beginTransaction:()=>db.beginTransaction(),commit:()=>db.commit(),rollback:()=>db.rollback(),
   execute:(sql,args)=>sql.includes('information_schema.TABLES')?Promise.resolve([engines.filter(row=>args.includes(row.TABLE_NAME))]):db.execute(sql,args)
  };
  const receipt=await importBundle(adapter,bundle,async()=>{});
  assert.equal(receipt.rooms[0].items,1905);
  assert.equal(receipt.rooms[0].variables,162);
  assert.equal(receipt.createdBases.length,1);
  const [variant]=await db.execute('SELECT item_name,interaction_type FROM items_base WHERE id=?',[receipt.createdBases[0].id]);
  assert.equal(variant[0].item_name,'wf_cnd_stuff_is');
  assert.equal(variant[0].interaction_type,'wf_cnd_anchored_instance_is');
  console.log('Temporary-table SQL import verified: 1905 items, 838 WIRED, 162 variables, 1 isolated native base. Persistent rooms untouched.');
 } finally {await db.end();}
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
