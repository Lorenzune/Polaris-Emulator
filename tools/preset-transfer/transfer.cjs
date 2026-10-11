const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const ID_FIELDS = new Set(['item_id','itemId','itemIds','pickedFurniIds','primaryItemIds','secondaryItemIds','targetItemIds',
 'forwardItemIds','variableItemId','referenceVariableItemId','destinationVariableItemId','selectedItemIds','selectedFurniIds']);
const SOURCE_FIELDS = new Set(['antennaSource','furniSource','compareFurniSource','destinationFurniSource',
 'referenceFurniSource','moveSource','targetSource','moveThroughFurniSource','blockByFurniSource','channel']);
const TOKEN_FIELDS = new Set(['variableToken','referenceVariableToken','destinationVariableToken']);
// A separate base preserves the ordinary condition used by other presets.
const BASE_VARIANTS=new Map([['wf_cnd_anchored_instance_is',{className:'wf_cnd_stuff_is',originalInteraction:'wf_cnd_stuff_is'}]]);
const baseKey=item=>item.className.toLowerCase()+'\0'+item.interaction;

function mappedId(value, mapping, label) {
 if(value===0 || value<0) return value;
 if(!Number.isSafeInteger(value) || !mapping.has(value)) throw Error(`Unresolved furniture reference ${label}=${value}`);
 return mapping.get(value);
}

function remap(value, mapping, key='', interaction='') {
 if(Array.isArray(value)) return value.map(child=>remap(child,mapping,key,interaction));
 if(value && typeof value==='object') return Object.fromEntries(Object.entries(value).map(([name,child])=>[name,remap(child,mapping,name,interaction)]));
 // This effect stores a hand-item type, rather than an instance from items.
 if(key==='item_id' && interaction==='wf_act_bot_give_handitem') return value;
 if(ID_FIELDS.has(key) && typeof value==='number') return mappedId(value,mapping,key);
 if(SOURCE_FIELDS.has(key) && typeof value==='number' && value>1000) return mappedId(value,mapping,key);
 if(TOKEN_FIELDS.has(key) && typeof value==='string') {
  const custom=/^custom:(\d+)$/.exec(value);
  if(custom) return 'custom:'+mappedId(Number(custom[1]),mapping,key);
  const legacy=/^(?:(-?\d+)\/)?(\d+)$/.exec(value);
  if(legacy && Number(legacy[2])>0) return (legacy[1]===undefined ? '' : legacy[1]+'/')+mappedId(Number(legacy[2]),mapping,key);
 }
 return value;
}

function validatePackage(bundle) {
 assert([1,2].includes(bundle.version));
 assert(Array.isArray(bundle.presets) && bundle.presets.length>0);
 assert.equal(new Set(bundle.presets.map(preset=>preset.roomId)).size,bundle.presets.length);
 const allIds=new Set();
 for(const preset of bundle.presets) {
  assert(Number.isSafeInteger(preset.roomId) && preset.roomId>0);
  assert(Number.isSafeInteger(ownerOf(bundle,preset)) && ownerOf(bundle,preset)>0);
  assert(preset.items.length>0 && preset.layout && typeof preset.layout.heightmap==='string');
  const mapping=new Map();
  for(const item of preset.items) {
   assert(Number.isSafeInteger(item.oldId) && item.oldId>0 && !allIds.has(item.oldId)); allIds.add(item.oldId);
   mapping.set(item.oldId,100000000+item.oldId);
   assert([item.x,item.y,item.z,item.rot].every(Number.isFinite));
   assert(typeof item.className==='string' && typeof item.interaction==='string');
  }
  for(const item of preset.items) if(item.wired!==null) remap(item.wired,mapping,'',item.className);
  const assignments=new Set();
  for(const row of preset.furniVariables || []) {
   assert(mapping.has(row.furniId) && mapping.has(row.definitionId));
   assert(['literal','furniture'].includes(row.valueKind));
   assert(row.value===null || Number.isSafeInteger(row.value));
   assert(row.valueKind!=='furniture' || mapping.has(row.value));
   const key=row.furniId+'/'+row.definitionId;
   assert(!assignments.has(key)); assignments.add(key);
  }
 }
}

function ownerOf(bundle,preset) {return preset.ownerId ?? bundle.ownerId;}
function selectRooms(bundle,rooms) {
 if(!rooms) return bundle;
 const ids=rooms.split(',').map(Number);
 if(ids.some(id=>!Number.isSafeInteger(id)||id<=0) || new Set(ids).size!==ids.length) throw Error('Invalid --rooms list');
 const presets=ids.map(id=>bundle.presets.find(preset=>preset.roomId===id));
 if(presets.some(preset=>!preset)) throw Error('Unknown destination in --rooms');
 return {...bundle,presets};
}

async function preflight(db,bundle,lock=false) {
 validatePackage(bundle);
 const ids=bundle.presets.map(preset=>preset.roomId);
 const placeholders=ids.map(()=>'?').join(',');
 const [rooms]=await db.execute(`SELECT id,owner_id,model,override_model FROM rooms WHERE id IN (${placeholders})${lock?' FOR UPDATE':''}`,ids);
 for(const preset of bundle.presets) {
  const room=rooms.find(row=>Number(row.id)===preset.roomId);
  if(!room || Number(room.owner_id)!==ownerOf(bundle,preset)) throw Error(`Room ${preset.roomId} must exist and belong to user ${ownerOf(bundle,preset)}`);
 }
 for(const id of new Set(bundle.presets.map(preset=>ownerOf(bundle,preset)))) {
  const [owner]=await db.execute('SELECT id FROM users WHERE id=?',[id]);
  if(owner.length!==1) throw Error(`Owner user ${id} does not exist`);
 }
 const [counts]=await db.execute(`SELECT room_id,COUNT(*) AS count FROM items WHERE room_id IN (${placeholders}) GROUP BY room_id`,ids);
 if(counts.some(row=>Number(row.count)>0)) throw Error('Destination rooms must be empty; existing items will not be deleted');
 const withVariables=bundle.presets.filter(preset=>preset.furniVariables?.length).map(preset=>preset.roomId);
 const tables=['items','rooms','room_models_custom',...(withVariables.length?['room_furni_wired_variables']:[]),
  ...(bundle.presets.some(preset=>preset.items.some(item=>BASE_VARIANTS.has(item.interaction)))?['items_base']:[])];
 const [engines]=await db.execute(`SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (${tables.map(()=>'?').join(',')})`,tables);
 if(engines.length!==tables.length || engines.some(row=>String(row.ENGINE).toLowerCase()!=='innodb')) throw Error(tables.join(', ')+' must use InnoDB for atomic import');
 if(withVariables.length) {
  const [variables]=await db.execute(`SELECT room_id,COUNT(*) AS count FROM room_furni_wired_variables WHERE room_id IN (${withVariables.map(()=>'?').join(',')}) GROUP BY room_id`,withVariables);
  if(variables.some(row=>Number(row.count)>0)) throw Error('Destination rooms contain existing WIRED variable assignments');
 }
 const [columns]=await db.execute('SHOW COLUMNS FROM items');
 if(!columns.some(column=>column.Field==='id' && /auto_increment/.test(column.Extra))) throw Error('items.id must be AUTO_INCREMENT');
 const [bases]=await db.execute('SELECT id,item_name,interaction_type FROM items_base');
 const baseMap=new Map();
 const baseVariants=new Map();
 for(const item of bundle.presets.flatMap(preset=>preset.items)) {
  const matches=bases.filter(base=>base.item_name.toLowerCase()===item.className.toLowerCase())
   .sort((a,b)=>Number(a.id)-Number(b.id));
  if(matches.length===0) throw Error(`Missing items_base: ${item.className}`);
  if(matches[0].interaction_type!==item.interaction) {
   const variant=BASE_VARIANTS.get(item.interaction);
   if(!variant || variant.className!==item.className || matches[0].interaction_type!==variant.originalInteraction)
    throw Error(`Interaction mismatch for ${item.className}: expected ${item.interaction}, found ${matches[0].interaction_type}`);
   const existing=matches.find(base=>base.interaction_type===item.interaction);
   if(existing) baseMap.set(baseKey(item),existing.id);
   else baseVariants.set(baseKey(item),{templateId:matches[0].id,interaction:item.interaction});
  } else baseMap.set(baseKey(item),matches[0].id);
 }
 if(baseVariants.size) {
  const [baseColumns]=await db.execute('SHOW COLUMNS FROM items_base');
  if(!baseColumns.some(column=>column.Field==='id' && /auto_increment/.test(column.Extra))) throw Error('items_base.id must be AUTO_INCREMENT for native variants');
 }
 const [layouts]=await db.execute(`SELECT * FROM room_models_custom WHERE id IN (${placeholders})`,ids);
 return {baseMap,baseVariants,rooms,layouts};
}

async function importBundle(db,bundle,beforeWrite,beforeCommit=async()=>{}) {
 await db.beginTransaction();
 try {
  const state=await preflight(db,bundle,true);
  await beforeWrite({at:new Date().toISOString(),rooms:state.rooms,layouts:state.layouts});
  const receipt={at:new Date().toISOString(),rooms:[],idMap:{},createdBases:[]};
  if(state.baseVariants.size) {
   const [columns]=await db.execute('SHOW COLUMNS FROM items_base');
   const names=columns.filter(column=>column.Field!=='id' && !/GENERATED/i.test(column.Extra)).map(column=>column.Field);
   const quoted=names.map(name=>'`'+name.replace(/`/g,'``')+'`');
   for(const [key,variant] of state.baseVariants) {
    const select=names.map((name,index)=>name==='interaction_type'?'?':quoted[index]);
    const [insert]=await db.execute(`INSERT INTO items_base (${quoted.join(',')}) SELECT ${select.join(',')} FROM items_base WHERE id=?`,[variant.interaction,variant.templateId]);
    if(insert.affectedRows!==1 || !Number.isSafeInteger(Number(insert.insertId)) || Number(insert.insertId)<=0) throw Error('Could not clone native condition base');
    state.baseMap.set(key,Number(insert.insertId));
    receipt.createdBases.push({...variant,id:Number(insert.insertId)});
   }
  }
  for(const preset of bundle.presets) {
   const ownerId=ownerOf(bundle,preset);
   const mapping=new Map();
   for(const item of preset.items) {
    const [insert]=await db.execute('INSERT INTO items (user_id,room_id,item_id,x,y,z,rot,extra_data,wired_data) VALUES (?,?,?,?,?,?,?,?,?)',
     [ownerId,preset.roomId,state.baseMap.get(baseKey(item)),item.x,item.y,item.z,item.rot,item.state,'']);
    if(!Number.isSafeInteger(Number(insert.insertId)) || Number(insert.insertId)<=0) throw Error('Invalid AUTO_INCREMENT ID');
    mapping.set(item.oldId,Number(insert.insertId));
   }
   for(const item of preset.items) if(item.wired!==null) {
    const wired=JSON.stringify(remap(item.wired,mapping,'',item.className));
    const [update]=await db.execute('UPDATE items SET wired_data=? WHERE id=? AND room_id=? AND user_id=?',
     [wired,mapping.get(item.oldId),preset.roomId,ownerId]);
    if(update.affectedRows!==1) throw Error('WIRED update affected an unexpected number of items');
   }
   const expectedVariables=(preset.furniVariables || []).map(row=>({
    furni_id:mapping.get(row.furniId),variable_item_id:mapping.get(row.definitionId),
    value:row.valueKind==='furniture'?mapping.get(row.value):row.value
   }));
   const now=Math.floor(Date.now()/1000);
   for(const row of expectedVariables) await db.execute(
    'INSERT INTO room_furni_wired_variables (room_id,furni_id,variable_item_id,value,created_at,updated_at) VALUES (?,?,?,?,?,?)',
    [preset.roomId,row.furni_id,row.variable_item_id,row.value,now,now]);
   if(expectedVariables.length) {
    const [actualVariables]=await db.execute('SELECT furni_id,variable_item_id,value FROM room_furni_wired_variables WHERE room_id=? ORDER BY furni_id,variable_item_id',[preset.roomId]);
    const normalize=rows=>rows.map(row=>({furni_id:Number(row.furni_id),variable_item_id:Number(row.variable_item_id),value:row.value===null?null:Number(row.value)}))
     .sort((a,b)=>a.furni_id-b.furni_id || a.variable_item_id-b.variable_item_id);
    assert.deepEqual(normalize(actualVariables),normalize(expectedVariables));
   }
   const layout=preset.layout;
   await db.execute('INSERT INTO room_models_custom (id,name,door_x,door_y,door_dir,heightmap) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),door_x=VALUES(door_x),door_y=VALUES(door_y),door_dir=VALUES(door_dir),heightmap=VALUES(heightmap)',
    [preset.roomId,`custom_${preset.roomId}`,layout.door_x,layout.door_y,layout.door_dir,layout.heightmap]);
   await db.execute("UPDATE rooms SET override_model='1' WHERE id=? AND owner_id=?",[preset.roomId,ownerId]);
   const [actual]=await db.execute('SELECT id,user_id,item_id,x,y,z,rot,extra_data,wired_data FROM items WHERE room_id=?',[preset.roomId]);
   assert.equal(actual.length,preset.items.length);
   const actualMap=new Map(actual.map(item=>[Number(item.id),item]));
   for(const item of preset.items) {
    const row=actualMap.get(mapping.get(item.oldId)); assert(row);
    assert.equal(Number(row.user_id),ownerId); assert.equal(Number(row.item_id),Number(state.baseMap.get(baseKey(item))));
    for(const key of ['x','y','z','rot']) assert(Math.abs(Number(row[key])-item[key])<0.0001,`Position mismatch ${row.id}/${key}`);
    assert.equal(row.extra_data,item.state);
    assert.deepEqual(row.wired_data ? JSON.parse(row.wired_data) : null,item.wired===null?null:remap(item.wired,mapping,'',item.className));
   }
   receipt.rooms.push({roomId:preset.roomId,ownerId,name:preset.name,items:preset.items.length,wired:preset.items.filter(item=>item.wired!==null).length,variables:expectedVariables.length});
   Object.assign(receipt.idMap,Object.fromEntries(mapping));
  }
  await beforeCommit(receipt);
  await db.commit();
  return receipt;
 } catch(error) {await db.rollback(); throw error;}
}

async function main() {
 const args=process.argv.slice(2); const configIndex=args.indexOf('--config');
 if(configIndex<0 || !args[configIndex+1] || (args.includes('--apply') && args.includes('--check'))) throw Error('Usage: node transfer.cjs --config /path/to/config.ini [--rooms 410 | --rooms 407,408,409,410] [--check | --apply]');
 const roomsIndex=args.indexOf('--rooms');
 if(roomsIndex>=0 && (!args[roomsIndex+1] || args[roomsIndex+1].startsWith('--'))) throw Error('Missing --rooms list');
 const config=Object.fromEntries(fs.readFileSync(args[configIndex+1],'utf8').split(/\r?\n/).filter(line=>line.trim() && !/^[#;]/.test(line.trim()) && line.includes('='))
  .map(line=>[line.slice(0,line.indexOf('=')).trim(),line.slice(line.indexOf('=')+1).trim()]));
 for(const key of ['db.hostname','db.username','db.database']) if(!config[key]) throw Error(`Missing setting ${key}`);
 const bundle=selectRooms(JSON.parse(fs.readFileSync(path.join(__dirname,'presets.json'))),roomsIndex>=0?args[roomsIndex+1]:null); validatePackage(bundle);
 const mysql=require('mysql2/promise');
 const db=await mysql.createConnection({host:config['db.hostname'],port:Number(config['db.port']||3306),user:config['db.username'],password:config['db.password']||'',database:config['db.database']});
 try {
  await preflight(db,bundle);
  console.log('Verified:',bundle.presets.map(preset=>({roomId:preset.roomId,ownerId:ownerOf(bundle,preset),items:preset.items.length,wired:preset.items.filter(item=>item.wired!==null).length,variables:preset.furniVariables?.length || 0})));
  if(!args.includes('--apply')) {console.log('Read-only check completed. Use --apply to import.'); return;}
  const stamp=new Date().toISOString().replace(/[:.]/g,'-');
  const file=path.join(__dirname,`imported-${stamp}.json`);
  const receipt=await importBundle(db,bundle,
   backup=>fs.writeFileSync(path.join(__dirname,`before-${stamp}.json`),JSON.stringify(backup,null,2),{flag:'wx'}),
   receipt=>fs.writeFileSync(file,JSON.stringify({...receipt,status:'pending_commit'},null,2),{flag:'wx'}));
  try {fs.writeFileSync(file,JSON.stringify({...receipt,status:'committed'},null,2));}
  catch(error) {console.warn('Database committed; ID map is saved but its status could not be updated:',error.message);}
  console.log('COMMITTED:',receipt.rooms); console.log('ID mapping:',file);
 } finally {await db.end();}
}

module.exports={remap,validatePackage,preflight,importBundle,selectRooms,ownerOf,baseKey,ID_FIELDS,SOURCE_FIELDS,TOKEN_FIELDS};
if(require.main===module) main().catch(error=>{console.error('Import failed:',error.message);process.exitCode=1;});
