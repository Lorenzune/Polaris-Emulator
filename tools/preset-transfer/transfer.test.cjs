const {test}=require('node:test');
const assert=require('node:assert/strict');
const {remap,validatePackage,preflight,importBundle,selectRooms,ownerOf,baseKey}=require('./transfer.cjs');
const bundle=require('./presets.json');
const totalItems=bundle.presets.reduce((count,preset)=>count+preset.items.length,0);
const totalWired=bundle.presets.reduce((count,preset)=>count+preset.items.filter(item=>item.wired!==null).length,0);

test('remaps only furniture references and variable tokens, preserving coincident literal values',()=>{
 const mapping=new Map([[11795,950001],[11810,950023]]);
 const actual=remap({itemIds:[11795],settings:[{item_id:11810,x:11795,y:3,z:2,state:'11795'}],antennaSource:11810,
  variableToken:'custom:11795',referenceVariableToken:'0/11810',initialValue:11795,timeWindowMs:11810,furniSource:200},mapping);
 assert.deepEqual(actual,{itemIds:[950001],settings:[{item_id:950023,x:11795,y:3,z:2,state:'11795'}],antennaSource:950023,
  variableToken:'custom:950001',referenceVariableToken:'0/950023',initialValue:11795,timeWindowMs:11810,furniSource:200});
 assert.deepEqual(remap({item_id:8,userSource:200},mapping,'','wf_act_bot_give_handitem'),{item_id:8,userSource:200});
 assert.throws(()=>remap({itemIds:[12345]},mapping),/Unresolved/);
});

test('all corrected configurations resolve within their own preset with scattered new IDs',()=>{
 validatePackage(bundle);
 let count=0;
 for(const preset of bundle.presets) {
  const mapping=new Map(preset.items.map((item,index)=>[item.oldId,9000000+index*19]));
  for(const item of preset.items) if(item.wired!==null) {
   remap(item.wired,mapping,'',item.className); count++;
  }
 }
 assert.equal(count,totalWired);
 assert.equal(totalWired,1957);
 assert.deepEqual(remap({selectedFurniIds:[11795]},new Map([[11795,700009]])),{selectedFurniIds:[700009]});
});

class FakeDatabase {
 constructor({failUpdate=false,occupied=false,failVariable=false}={}) {
  this.failUpdate=failUpdate; this.failVariable=failVariable; this.nextId=500000; this.commits=0; this.rollbacks=0;
  this.items=[{id:47,room_id:800,user_id:99,item_id:3,x:1,y:2,z:0,rot:0,extra_data:'preserve',wired_data:''}];
  if(occupied) this.items.push({...this.items[0],id:48,room_id:407});
  this.rooms=bundle.presets.map(preset=>({id:preset.roomId,owner_id:ownerOf(bundle,preset),model:'model_a',override_model:'0'}));
  this.variables=[];
  this.layouts=[];
  const names=new Map(bundle.presets.flatMap(preset=>preset.items).filter(item=>item.interaction!=='wf_cnd_anchored_instance_is').map(item=>[item.className,item.interaction]));
  this.bases=[...names].map(([item_name,interaction_type],index)=>({id:99000+index,item_name,interaction_type}));
 }
 async beginTransaction(){this.snapshot=structuredClone({items:this.items,rooms:this.rooms,layouts:this.layouts,variables:this.variables,bases:this.bases});}
 async commit(){this.commits++;}
 async rollback(){this.rollbacks++;Object.assign(this,this.snapshot);}
 async execute(sql,args=[]) {
  if(sql.startsWith('SELECT id,owner_id')) return [this.rooms.filter(room=>args.includes(room.id))];
  if(sql.startsWith('SELECT id FROM users')) return [[{id:args[0]}]];
  if(sql.startsWith('SELECT room_id,COUNT')) return [(sql.includes('FROM room_furni_wired_variables')?this.variables:this.items).filter(item=>args.includes(item.room_id)).map(item=>({room_id:item.room_id,count:1}))];
  if(sql.includes('information_schema.TABLES')) return [args.map(TABLE_NAME=>({TABLE_NAME,ENGINE:'InnoDB'}))];
  if(sql==='SHOW COLUMNS FROM items') return [[{Field:'id',Extra:'auto_increment'}]];
  if(sql==='SHOW COLUMNS FROM items_base') return [[{Field:'id',Extra:'auto_increment'},{Field:'item_name',Extra:''},{Field:'interaction_type',Extra:''}]];
  if(sql.startsWith('SELECT id,item_name')) return [this.bases];
  if(sql.startsWith('INSERT INTO items_base ')) {
   const [interaction_type,id]=args;const original=this.bases.find(base=>base.id===id);
   this.nextId+=7;this.bases.push({...original,id:this.nextId,interaction_type});
   return [{insertId:this.nextId,affectedRows:original?1:0}];
  }
  if(sql.startsWith('SELECT * FROM room_models_custom')) return [this.layouts];
  if(sql.startsWith('INSERT INTO items ')) {
   // Gaps model concurrent allocations; no MAX(id)+1 assumption is valid.
   this.nextId+=7; const [user_id,room_id,item_id,x,y,z,rot,extra_data,wired_data]=args;
   this.items.push({id:this.nextId,user_id,room_id,item_id,x,y,z,rot,extra_data,wired_data});return [{insertId:this.nextId}];
  }
  if(sql.startsWith('UPDATE items SET wired_data')) {
   if(this.failUpdate) throw Error('Simulated connection failure while updating WIRED');
   const [wired,id,room,owner]=args; const item=this.items.find(item=>item.id===id&&item.room_id===room&&item.user_id===owner);
   if(item) item.wired_data=wired; return [{affectedRows:item?1:0}];
  }
  if(sql.startsWith('INSERT INTO room_furni_wired_variables')) {
   if(this.failVariable) throw Error('Simulated assignment failure');
   const [room_id,furni_id,variable_item_id,value]=args;
   this.variables.push({room_id,furni_id,variable_item_id,value});return [{affectedRows:1}];
  }
  if(sql.startsWith('SELECT furni_id,variable_item_id')) return [this.variables.filter(row=>row.room_id===args[0]).map(({furni_id,variable_item_id,value})=>({furni_id,variable_item_id,value}))];
  if(sql.startsWith('INSERT INTO room_models_custom')) {this.layouts.push({id:args[0],heightmap:args[5]});return [{}];}
  if(sql.startsWith('UPDATE rooms SET')) {this.rooms.find(room=>room.id===args[0]).override_model='1';return [{affectedRows:1}];}
  if(sql.startsWith('SELECT id,user_id')) return [this.items.filter(item=>item.room_id===args[0])];
  throw Error(`Unexpected query: ${sql}`);
 }
}

test('imports all four rooms with separate owners using auto-increment IDs and preserves unrelated items',async()=>{
 const db=new FakeDatabase(); let backup;
 const receipt=await importBundle(db,bundle,async value=>{backup=value;});
 assert.equal(db.commits,1);assert.equal(db.rollbacks,0);assert.equal(db.items.length,totalItems+1);
 assert.equal(db.items[0].id,47);assert.equal(db.items[0].extra_data,'preserve');assert.equal(backup.rooms.length,4);
 assert.equal(Object.keys(receipt.idMap).length,totalItems);assert.deepEqual(receipt.rooms.map(room=>room.roomId),[407,408,409,410]);
 assert.deepEqual(receipt.rooms.map(room=>room.ownerId),[7,7,7,16]);
 const cake=bundle.presets[2];const original=cake.items.find(item=>item.wired?.variableToken?.startsWith('custom:'));
 const imported=db.items.find(item=>item.id===receipt.idMap[original.oldId]);
 const oldVariable=Number(original.wired.variableToken.slice(7));
 assert.equal(JSON.parse(imported.wired_data).variableToken,'custom:'+receipt.idMap[oldVariable]);
 const chess=bundle.presets.find(preset=>preset.roomId===410);
 assert.equal(db.variables.length,162);
 for(const row of chess.furniVariables) {
  const actual=db.variables.find(value=>value.furni_id===receipt.idMap[row.furniId] && value.variable_item_id===receipt.idMap[row.definitionId]);
  assert(actual);
  assert.equal(actual.room_id,410);
  assert.equal(actual.value,row.valueKind==='furniture'?receipt.idMap[row.value]:row.value);
 }
});

test('rolls back every inserted item when a WIRED update fails',async()=>{
 const db=new FakeDatabase({failUpdate:true});
 await assert.rejects(importBundle(db,bundle,async()=>{}),/Simulated connection failure/);
 assert.equal(db.commits,0);assert.equal(db.rollbacks,1);assert.equal(db.items.length,1);assert.equal(db.items[0].id,47);
});

test('refuses an occupied destination without deleting its furniture',async()=>{
 const db=new FakeDatabase({occupied:true});
 await assert.rejects(importBundle(db,bundle,async()=>{}),/must be empty/);
 assert.equal(db.commits,0);assert.equal(db.items.length,2);assert.equal(db.items[1].id,48);
});

test('does not commit an import whose ID map cannot be saved',async()=>{
 const db=new FakeDatabase();
 await assert.rejects(importBundle(db,bundle,async()=>{},async()=>{throw Error('Disk full');}),/Disk full/);
 assert.equal(db.commits,0);assert.equal(db.rollbacks,1);assert.equal(db.items.length,1);
});

test('chooses the lowest numeric base ID among duplicate names regardless of row order',async()=>{
 const db=new FakeDatabase();
 const original=db.bases[0];
 db.bases.unshift({...original,id:800000},{...original,id:12},{...original,id:3});
 const state=await preflight(db,bundle);
 assert.equal(state.baseMap.get(baseKey({className:original.item_name,interaction:original.interaction_type})),3);
});

test('validates the interaction of the lowest-ID duplicate without choosing a different duplicate',async()=>{
 const db=new FakeDatabase();
 const original=db.bases[0];
 db.bases.push({...original,id:3,interaction_type:'incorrect_interaction'});
 await assert.rejects(preflight(db,bundle),/Interaction mismatch/);
});

test('imports only chess while the previous destinations are already populated',async()=>{
 const db=new FakeDatabase({occupied:true});
 const original=structuredClone(db.items);
 const chess=selectRooms(bundle,'410');
 const receipt=await importBundle(db,chess,async()=>{});
 assert.deepEqual(receipt.rooms.map(room=>[room.roomId,room.ownerId]),[[410,16]]);
 assert.equal(Object.keys(receipt.idMap).length,1905);
 assert.deepEqual(db.items.slice(0,2),original);
 assert.equal(db.variables.length,162);
 assert.equal(db.rooms.find(room=>room.id===407).override_model,'0');
});

test('rolls back furniture and variables when assignment persistence fails',async()=>{
 const db=new FakeDatabase({failVariable:true});
 await assert.rejects(importBundle(db,selectRooms(bundle,'410'),async()=>{}),/assignment failure/);
 assert.equal(db.commits,0);assert.equal(db.items.length,1);assert.equal(db.variables.length,0);
});

test('refuses stale variable assignments in an otherwise empty destination',async()=>{
 const db=new FakeDatabase();db.variables.push({room_id:410,furni_id:999,variable_item_id:888,value:1});
 await assert.rejects(preflight(db,selectRooms(bundle,'410')),/existing WIRED variable assignments/);
});

test('checks the chess owner independently of the other rooms',async()=>{
 const db=new FakeDatabase();db.rooms.find(room=>room.id===410).owner_id=7;
 await assert.rejects(preflight(db,bundle),/410 must exist and belong to user 16/);
});

test('room selection rejects unknown, duplicate or malformed destinations',()=>{
 for(const rooms of ['999','410,410','x','410,']) assert.throws(()=>selectRooms(bundle,rooms));
});

test('package validation rejects missing variable value references',()=>{
 const chess=structuredClone(selectRooms(bundle,'410'));
 chess.presets[0].furniVariables.find(row=>row.valueKind==='furniture').value=99999999;
 assert.throws(()=>validatePackage(chess));
});

test('clones only the opt-in native base from the lowest ordinary base and reuses it on later imports',async()=>{
 const db=new FakeDatabase();const original=db.bases.find(base=>base.item_name==='wf_cnd_stuff_is');
 db.bases.push({...original,id:3});const originalCount=db.bases.length;
 const receipt=await importBundle(db,selectRooms(bundle,'410'),async()=>{});
 assert.equal(db.bases.length,originalCount+1);
 assert.deepEqual(receipt.createdBases.map(base=>[base.templateId,base.interaction]),[[3,'wf_cnd_anchored_instance_is']]);
 const variant=receipt.createdBases[0].id;
 assert.equal(db.items.filter(item=>item.item_id===variant).length,17);
 assert.equal(original.interaction_type,'wf_cnd_stuff_is');
 db.items=db.items.filter(item=>item.room_id!==410);db.variables=[];
 const state=await preflight(db,selectRooms(bundle,'410'));
 assert.equal(state.baseVariants.size,0);
 assert.equal(state.baseMap.get(baseKey({className:'wf_cnd_stuff_is',interaction:'wf_cnd_anchored_instance_is'})),variant);
});
