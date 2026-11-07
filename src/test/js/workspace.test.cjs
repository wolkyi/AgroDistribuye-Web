const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const data = JSON.parse(fs.readFileSync('data/ejemplo-planificacion.json', 'utf8').replace(/^\uFEFF/, ''));
const context = vm.createContext({window:{addEventListener(){}}, Intl});
vm.runInContext(fs.readFileSync('src/main/resources/static/js/workspace.js','utf8') + '\nplanningDefaults=' + JSON.stringify(data), context);
const normalize = value => JSON.parse(JSON.stringify(value));
const csv = value => context.rowsCSV(context.inputRows(value));
test('CSV round trip preserves quantities, fleet, units and multiline quoted provenance', () => {
  const input=structuredClone(data); input.provenance='Datos; simulados "para prueba"\nSegunda línea'; input.actualPlanCostCop=800000;
  assert.deepEqual(normalize(context.csvData(csv(input))),input);
});
test('CSV formula protection does not change imported source metadata',()=>{
  const input=structuredClone(data);input.provenance='=texto de prueba';
  assert.ok(csv(input).includes("'=texto de prueba"));
  assert.equal(context.csvData(csv(input)).provenance,input.provenance);
});
test('CSV rejects missing cells, duplicated rows, incompatible units and negative numbers',()=>{
  const text=csv(data),row=text.split('\r\n')[1];
  assert.throws(()=>context.csvData(text.replace(row+'\r\n','')),/todos los datos/);
  assert.throws(()=>context.csvData(text+'\r\n'+row),/duplicada/);
  assert.throws(()=>context.csvData(text.replace(';t\r\n',';kg\r\n')),/unidad/);
  assert.throws(()=>context.csvData(text.replace(';2.2;t',';-2.2;t')),/no negativo/);
});
test('Savings are undefined with a zero baseline and preserve increases as negative',()=>{
  const savings=vm.runInContext('savings',context);
  assert.equal(savings(0,20),null);
  assert.equal(savings(100,120),-20);
  assert.equal(savings(100,80),20);
});
test('CSV exports remain rectangular for failed and feasible scenarios',()=>{
  const downloads=[];
  context.downloadFile=(name,text)=>downloads.push({name,text});
  const experiment={scenario:'A_BASELINE',category:'REFERENCE',label:'Referencia',validated:true,status:'OPTIMAL',totalCostCop:100,objectiveValue:100,totalDistanceKm:1,totalDepotWaitingMinutes:0,totalProducerWaitingMinutes:0,totalDrivingMinutes:1,totalServiceMinutes:1,totalOperationMinutes:2,usedVehicles:1,servedCustomers:1,solvingTimeMs:2};
  vm.runInContext('activeOptimizationResponse='+JSON.stringify({experiments:[experiment,{...experiment,scenario:'FAIL',validated:false,status:'INFEASIBLE'}],executedAt:'2026-11-07T00:00:00Z',run:{activeCustomers:1,request:{data},matrixMode:'LOCAL_SNAPSHOT'}}),context);
  context.exportComparison();
  const rows=normalize(context.parseCSV(downloads[0].text));
  assert.equal(rows.length,3);assert.ok(rows.every(row=>row.length===rows[0].length));
  assert.equal(rows[2][5],'');assert.equal(rows[2][8],'');
});
test('A double click during asynchronous validation submits only one job',async()=>{
  const isolated=vm.createContext({window:{addEventListener(){}},localStorage:{setItem(){},removeItem(){}},document:{getElementById(){return {value:'30',checked:true,reportValidity(){return true;}};}}});
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/workspace.js','utf8'),isolated);
  vm.runInContext(`let submitted=0; collectData=()=>({vehicleCapacity:[5]}); validateData=async d=>d;
    readOptions=()=>({}); storedValue=async()=>null; setBusy=value=>{workspaceBusy=value;};
    jsonApi=async()=>{submitted++;return {id:'test'};}; pollStudy=async()=>({}); showStudy=()=>{};`,isolated);
  await Promise.all([isolated.startStudy(),isolated.startStudy()]);
  assert.equal(vm.runInContext('submitted',isolated),1);
});
