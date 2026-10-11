// Export corrected native settings, with the saved initial board rather than an active match.
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {validatePackage}=require('./transfer.cjs');

async function main() {
 const args=process.argv.slice(2);
 const option=name=>{const index=args.indexOf(name);return index<0?null:args[index+1];};
 if(!option('--source-root') || !option('--config')) throw Error('Usage: node export-local.cjs --source-root D:/ducket --config /path/config.ini [--output /path]');
 const root=path.resolve(option('--source-root'));
 const outputDirectory=path.resolve(option('--output') || __dirname);
 const config=Object.fromEntries(fs.readFileSync(option('--config'),'utf8').split(/\r?\n/)
  .filter(line=>line.includes('=') && !/^[#;]/.test(line.trim()))
  .map(line=>[line.slice(0,line.indexOf('=')).trim(),line.slice(line.indexOf('=')+1).trim()]));
 let mysql;
 try {mysql=require('mysql2/promise');} catch(error) {if(error.code!=='MODULE_NOT_FOUND') throw error;mysql=require(path.join(root,'imager/node_modules/mysql2/promise'));}
 const db=await mysql.createConnection({host:config['db.hostname'],port:Number(config['db.port']||3306),user:config['db.username'],password:config['db.password']||'',database:config['db.database']});
 try {
  const previous=JSON.parse(fs.readFileSync(path.join(root,'reports/server-presets-2026-10-06/presets.json')));
  const chessReceipt=JSON.parse(fs.readFileSync(path.join(root,'reports/preset-import-269/receipt.json')));
  const bootstrap=JSON.parse(fs.readFileSync(path.join(root,'reports/preset-import-269/permanent-bootstrap.json')));
  const bundle={version:2,exportedOn:'2026-10-08',presets:[]};
  for(const [sourceRoomId,roomId,ownerId,name] of [[266,407,7,'Katso Merta'],[267,408,7,'Dama'],[268,409,7,'Chocolate Cake Crush'],[269,410,16,'Scacchi']]) {
   const baseline=sourceRoomId===269
    ? chessReceipt.expected.map(item=>({oldId:item.id,className:item.className,x:item.values[3],y:item.values[4],z:item.values[5],rot:item.values[6],state:item.values[7]}))
    : previous.presets.find(preset=>preset.sourceRoomId===sourceRoomId).items;
   const originalMap=new Map(baseline.map(item=>[item.oldId,item]));
   const [rows]=await db.execute('SELECT i.id,i.x,i.y,i.z,i.rot,i.extra_data,i.wired_data,b.item_name,b.interaction_type FROM items i JOIN items_base b ON b.id=i.item_id WHERE i.room_id=? ORDER BY i.id',[sourceRoomId]);
   const present=new Set(rows.map(row=>row.id));
   for(const item of baseline.filter(item=>!present.has(item.oldId))) {
    if(sourceRoomId!==267 || ![12368,12369].includes(item.oldId) || !item.className.startsWith('highscore_')) throw Error('Unexpected missing original item '+item.oldId);
    rows.push({id:item.oldId,item_name:item.className,interaction_type:item.interaction,wired_data:item.wired?JSON.stringify(item.wired):'',...item});
   }
   const items=rows.sort((a,b)=>a.id-b.id).map(row=>{
    const original=originalMap.get(row.id);
    if(original) assert.equal(original.className.toLowerCase(),row.item_name.toLowerCase());
    else assert(row.item_name.startsWith('wf_'),'Unexpected added non-WIRED furniture '+row.id);
    const wired=row.wired_data?JSON.parse(row.wired_data):null;
    // Retain changed WIRED stack positions; gameplay objects use their saved starting position.
    const position=row.item_name.startsWith('wf_') || !original?row:original;
    return {oldId:row.id,className:row.item_name,interaction:row.interaction_type,
     x:Number(position.x),y:Number(position.y),z:Number(position.z),rot:Number(position.rot),
     state:String(original?.state ?? row.extra_data ?? ''),wired};
   });
   const [layouts]=await db.execute('SELECT name,door_x,door_y,door_dir,heightmap FROM room_models_custom WHERE id=?',[sourceRoomId]);
   assert.equal(layouts.length,1);
   const furniVariables=[];
   if(sourceRoomId===269) {
    const [persisted]=await db.execute('SELECT furni_id,variable_item_id,value FROM room_furni_wired_variables WHERE room_id=?',[sourceRoomId]);
    for(const row of bootstrap.rows) {
     assert(persisted.some(value=>value.furni_id===row.furniId && value.variable_item_id===row.definitionId && value.value===row.value),'Missing permanent chess assignment '+row.furniId+'/'+row.definitionId);
     // callback values are item IDs; colors and clock ownership remain literal 1/2.
     furniVariables.push({...row,valueKind:row.definitionId===14431?'furniture':'literal'});
    }
    assert.equal(furniVariables.length,162);
   }
   bundle.presets.push({name,sourceRoomId,roomId,ownerId,layout:layouts[0],items,furniVariables});
  }
  validatePackage(bundle);
  fs.mkdirSync(outputDirectory,{recursive:true});
  fs.writeFileSync(path.join(outputDirectory,'presets.json'),JSON.stringify(bundle));
  console.log(JSON.stringify(bundle.presets.map(preset=>({name:preset.name,roomId:preset.roomId,ownerId:preset.ownerId,items:preset.items.length,wired:preset.items.filter(item=>item.wired!==null).length,variables:preset.furniVariables.length})),null,2));
 } finally {await db.end();}
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
