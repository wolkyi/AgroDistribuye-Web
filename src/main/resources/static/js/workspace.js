/* Edición, experimentos reproducibles y vista local. Sin dependencias externas. */
let planningDefaults, workspaceGeo, currentExperiment, tileLayer;
let workspaceReady = false, workspaceBusy = false;
const DEPOT_LABELS = ['Distribuidora Norte', 'Distribuidora Centro', 'Distribuidora Sur'];
const PRODUCTS = ['Fertilizantes', 'Cal agrícola', 'Abonos orgánicos', 'Semillas'];
const byId = id => document.getElementById(id);
const cloneData = x => JSON.parse(JSON.stringify(x));
const minutesText = n => `${String(Math.floor(n / 60)).padStart(2, '0')}:${String(n % 60).padStart(2, '0')}`;
function field(value, label, path, type = 'number', positive = false) {
  return `<input aria-label="${escapeHtml(label)}" data-path="${path}" type="${type}" value="${value}" ${type === 'time' ? 'min="07:00" max="18:00" step="60"' : `min="${path.startsWith('serviceMinutes') ? '1' : positive ? '.001' : '0'}" max="1000000000" step="${path.startsWith('serviceMinutes') ? '1' : 'any'}"`} required>`;
}
function renderDataEditor(data) {
  const definitions = [
    ['demand', 'Demanda por productor', 8, 'P', PRODUCTS.map(x => x + ' (t)')],
    ['stock', 'Inventario por depósito', 3, 'C', PRODUCTS.map(x => x + ' (t)')],
    ['depotCapacity', 'Capacidad de almacenamiento', 3, 'C', ['Capacidad (t)']],
    ['timeWindows', 'Ventanas de servicio', 8, 'P', ['Inicio', 'Fin', 'Servicio (min)']],
    ['vehicles', 'Flota y costos', data.vehicleCapacity.length, 'V', ['Capacidad (t)', 'Costo fijo (COP/jornada usada)', 'Costo por km (COP/km)']]
  ];
  byId('data-editor').innerHTML = definitions.map(([key, title, n, prefix, columns], idx) => {
    const rows = Array.from({length: n}, (_, i) => {
      const id = prefix + (i + 1); let cells;
      if (key === 'timeWindows') cells = [field(minutesText(data.timeWindows[i][0]), id + ' inicio', `timeWindows.${i}.0`, 'time'), field(minutesText(data.timeWindows[i][1]), id + ' fin', `timeWindows.${i}.1`, 'time'), field(data.serviceMinutes[i], id + ' servicio (min)', `serviceMinutes.${i}`, 'number', true)];
      else if (key === 'vehicles') cells = ['vehicleCapacity', 'fixedCost', 'costPerKm'].map((k, j) => field(data[k][i], id + ' ' + columns[j], `${k}.${i}`, 'number', j === 0));
      else if (key === 'depotCapacity') cells = [field(data[key][i], id + ' capacidad (t)', `${key}.${i}`, 'number', true)];
      else cells = columns.map((c, j) => field(data[key][i][j], id + ' ' + c, `${key}.${i}.${j}`));
      return `<tr><th scope="row">${prefix === 'C' ? DEPOT_LABELS[i] + ' · ' + id : prefix === 'P' ? 'Productor ' + id : id}</th>${cells.map(x => `<td>${x}</td>`).join('')}</tr>`;
    }).join('');
    return `<details ${idx === 0 ? 'open' : ''}><summary>${title}</summary><div class="input-table-wrap"><table class="input-table"><thead><tr><th>Punto / ID</th>${columns.map(c => `<th>${c}</th>`).join('')}</tr></thead><tbody>${rows}</tbody></table></div>${key === 'vehicles' ? '<div class="editor-actions"><button type="button" id="add-vehicle" class="secondary-button">Añadir vehículo</button><button type="button" id="remove-vehicle" class="secondary-button">Retirar último vehículo</button></div>' : ''}</details>`;
  }).join('');
  byId('data-provenance').value = data.provenance;
  byId('actual-plan-cost').value = data.actualPlanCostCop ?? '';
  byId('add-vehicle').onclick = () => resizeFleet(1);
  byId('remove-vehicle').onclick = () => resizeFleet(-1);
  syncCatalog(data);
}
function collectData(quiet = false) {
  const invalid = byId('planning-form').querySelector('input:invalid, textarea:invalid');
  if (invalid) {
    if (!quiet) { window.revealPlanningField?.(invalid); const section = invalid.closest('details'); if (section) section.open = true; invalid.reportValidity(); }
    throw new Error('Completa los campos resaltados con valores válidos.');
  }
  const n = byId('data-editor').querySelectorAll('[data-path^="vehicleCapacity."]').length;
  const data = {demand: Array.from({length:8},()=>Array(4)), stock:Array.from({length:3},()=>Array(4)), depotCapacity:Array(3), timeWindows:Array.from({length:8},()=>Array(2)), serviceMinutes:Array(8), vehicleCapacity:Array(n), fixedCost:Array(n), costPerKm:Array(n), provenance:byId('data-provenance').value.trim(), actualPlanCostCop:byId('actual-plan-cost').value === '' ? null : Number(byId('actual-plan-cost').value)};
  byId('data-editor').querySelectorAll('[data-path]').forEach(input => {
    const path = input.dataset.path.split('.'); let target = data;
    for (let i=0; i<path.length-1; i++) target=target[path[i]];
    const value = input.type === 'time' ? Number(input.value.slice(0,2))*60+Number(input.value.slice(3)) : Number(input.value);
    if (!Number.isFinite(value)) throw new Error('Número inválido: ' + input.getAttribute('aria-label'));
    target[path.at(-1)] = value;
  });
  for (let i=0;i<8;i++) if (data.timeWindows[i][0]>=data.timeWindows[i][1] || data.serviceMinutes[i]>data.timeWindows[i][1]-data.timeWindows[i][0]) throw new Error(`P${i+1}: revisa inicio, fin y duración del servicio.`);
  return data;
}
function syncCatalog(d) {
  byId('depot-count').textContent=3; byId('customer-count').textContent=8; byId('product-count').textContent=4;
  byId('vehicle-count').textContent=d.vehicleCapacity.length;
  byId('fleet-caption').textContent=d.vehicleCapacity.length+' unidades · configuración actual';
  byId('vehicle-list').innerHTML=d.vehicleCapacity.map((x,i)=>`<span class="vehicle-pill">V${i+1} · ${x} t · ${formatCop(d.fixedCost[i])} fijo</span>`).join('');
  byId('depot-list').innerHTML=d.depotCapacity.map((x,i)=>`<div class="data-row"><strong>${DEPOT_LABELS[i]} · C${i+1}</strong><span>${x} t</span></div>`).join('');
  byId('customer-list').innerHTML=d.timeWindows.map((x,i)=>`<div class="producer"><strong>Productor P${i+1}</strong><small>${minutesText(x[0])}–${minutesText(x[1])}</small></div>`).join('');
}
function resizeFleet(delta) {
  try { const d=collectData(), n=d.vehicleCapacity.length; if(n+delta<1||n+delta>8)throw new Error('La flota admite entre 1 y 8 vehículos.');
    for(const k of ['vehicleCapacity','fixedCost','costPerKm']) delta>0?d[k].push(d[k].at(-1)):d[k].pop();
    renderDataEditor(d); persistDraft();
  } catch(e){editorMessage(e.message,true);}
}
function editorMessage(text,error=false){byId('editor-message').textContent=text;byId('editor-message').classList.toggle('error-text',error);}
async function jsonApi(url,options={}) {
  const response=await fetch(url,options);let body;
  try{body=await response.json();}catch{throw new Error('El servidor no devolvió una respuesta válida.');}
  if(!response.ok){const e=new Error(body.message||body.detail||'Solicitud fallida (HTTP '+response.status+').');e.status=response.status;throw e;}return body;
}
function validateData(data){return jsonApi('/api/optimizaciones/datos/validar',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});}
async function storedValue(key,value,write=false) {
  const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('agrodistribuye-study',1);r.onupgradeneeded=()=>r.result.createObjectStore('workspace');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
  try{return await new Promise((resolve,reject)=>{const tx=db.transaction('workspace',write?'readwrite':'readonly'),store=tx.objectStore('workspace');const r=write?store.put(value,key):store.get(key);let result;r.onsuccess=()=>{result=r.result;};tx.oncomplete=()=>resolve(result);tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error);});}finally{db.close();}
}
async function persistDraft(){if(workspaceBusy||!workspaceReady)return;try{const d=collectData(true);syncCatalog(d);await storedValue('draft',d,true);editorMessage('Borrador guardado en este navegador. Los resultados anteriores conservan los datos de su ejecución.');}catch(e){editorMessage(e.message,true);}}
function csvCell(value){if(value==null)return '';let s=String(value);if(typeof value==='string'&&/^[=+\-@\t\r]/.test(s))s="'"+s;return /[;"\r\n]/.test(s)?'"'+s.replaceAll('"','""')+'"':s;}
function rowsCSV(rows){return rows.map(r=>r.map(csvCell).join(';')).join('\r\n');}
function downloadFile(name,text,type){const url=URL.createObjectURL(new Blob([type.includes('csv')?'\uFEFF':'',text],{type})),a=document.createElement('a');a.href=url;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function inputRows(d){const rows=[['section','id','field','value','unit']];
  for(let i=0;i<8;i++)for(let j=0;j<4;j++)rows.push(['DEMAND','P'+(i+1),'PR'+(j+1),d.demand[i][j],'t']);
  for(let i=0;i<3;i++)for(let j=0;j<4;j++)rows.push(['STOCK','C'+(i+1),'PR'+(j+1),d.stock[i][j],'t']);
  d.depotCapacity.forEach((x,i)=>rows.push(['DEPOT','C'+(i+1),'capacity',x,'t']));
  d.timeWindows.forEach((x,i)=>rows.push(['WINDOW','P'+(i+1),'start',minutesText(x[0]),'HH:mm'],['WINDOW','P'+(i+1),'end',minutesText(x[1]),'HH:mm'],['SERVICE','P'+(i+1),'minutes',d.serviceMinutes[i],'min']));
  d.vehicleCapacity.forEach((x,i)=>rows.push(['VEHICLE','V'+(i+1),'capacity',x,'t'],['VEHICLE','V'+(i+1),'fixedCost',d.fixedCost[i],'COP'],['VEHICLE','V'+(i+1),'costPerKm',d.costPerKm[i],'COP/km']));
  rows.push(['META','study','provenance',d.provenance,'text'],['META','study','actualPlanCostCop',d.actualPlanCostCop,'COP']);return rows;
}
function parseCSV(text){text=text.replace(/^\uFEFF/,'');const delimiter=text.split(/\r?\n/)[0].includes(';')?';':',';const rows=[];let row=[],cell='',quoted=false;
  for(let i=0;i<text.length;i++){const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){cell+='"';i++;}else if(!quoted&&cell==='')quoted=true;else if(quoted)quoted=false;else throw new Error('CSV: comillas inesperadas.');}else if(c===delimiter&&!quoted){row.push(cell);cell='';}else if((c==='\n'||c==='\r')&&!quoted){if(c==='\r'&&text[i+1]==='\n')i++;row.push(cell);if(row.some(x=>x!==''))rows.push(row);row=[];cell='';}else cell+=c;}
  if(quoted)throw new Error('CSV: comillas sin cerrar.');row.push(cell);if(row.some(x=>x!==''))rows.push(row);return rows;
}
function csvData(text){const rows=parseCSV(text);if(rows.shift()?.join(';')!=='section;id;field;value;unit')throw new Error('Usa la plantilla section;id;field;value;unit, con punto decimal.');const cells=new Map();
  for(const r of rows){if(r.length!==5)throw new Error('Cada fila CSV debe tener cinco columnas.');const k=r.slice(0,3).join('|');if(cells.has(k))throw new Error('Fila duplicada: '+k);cells.set(k,r);}
  const n=new Set(rows.filter(r=>r[0]==='VEHICLE').map(r=>r[1])).size;if(n<1||n>8)throw new Error('Se requieren de 1 a 8 vehículos consecutivos V1…V8.');
  const d=cloneData(planningDefaults);d.vehicleCapacity=Array(n).fill(5);d.fixedCost=Array(n).fill(0);d.costPerKm=Array(n).fill(0);
  if(cells.size!==inputRows(d).length-1)throw new Error('Incluye todos los datos de la plantilla, sin filas adicionales.');
  const read=(s,id,f,u)=>{const k=[s,id,f].join('|'),r=cells.get(k);if(!r||r[4]!==u)throw new Error('Falta '+k+' o su unidad debe ser '+u);return r[3];};
  const num=(s,id,f,u)=>{const v=read(s,id,f,u);if(!/^(\d+(\.\d+)?|\.\d+)([eE][+-]?\d+)?$/.test(v))throw new Error('Número no negativo inválido: '+id+' '+f);return Number(v);};
  for(const [section,key,prefix,count]of[['DEMAND','demand','P',8],['STOCK','stock','C',3]])for(let i=0;i<count;i++)for(let j=0;j<4;j++)d[key][i][j]=num(section,prefix+(i+1),'PR'+(j+1),'t');
  for(let i=0;i<3;i++)d.depotCapacity[i]=num('DEPOT','C'+(i+1),'capacity','t');
  for(let i=0;i<8;i++){for(let j=0;j<2;j++){const v=read('WINDOW','P'+(i+1),j?'end':'start','HH:mm');if(!/^\d{2}:\d{2}$/.test(v)||Number(v.slice(3))>59)throw new Error('Horario inválido en P'+(i+1));d.timeWindows[i][j]=Number(v.slice(0,2))*60+Number(v.slice(3));}d.serviceMinutes[i]=num('SERVICE','P'+(i+1),'minutes','min');}
  for(let i=0;i<n;i++){d.vehicleCapacity[i]=num('VEHICLE','V'+(i+1),'capacity','t');d.fixedCost[i]=num('VEHICLE','V'+(i+1),'fixedCost','COP');d.costPerKm[i]=num('VEHICLE','V'+(i+1),'costPerKm','COP/km');}
  let source=read('META','study','provenance','text');if(/^'[=+\-@\t\r]/.test(source))source=source.slice(1);d.provenance=source;d.actualPlanCostCop=read('META','study','actualPlanCostCop','COP')===''?null:num('META','study','actualPlanCostCop','COP');return d;
}
async function importInputs(file){if(!file)return;if(file.size>20*1024*1024)throw new Error('El archivo debe ocupar menos de 20 MB.');const text=await file.text();let data;
  if(file.name.toLowerCase().endsWith('.csv'))data=csvData(text);else{const p=JSON.parse(text.replace(/^\uFEFF/,''));data=p.run?.request?.data||p.data||p;}
  const checked=await validateData(data);renderDataEditor(checked);await storedValue('draft',checked,true);editorMessage('Archivo validado e importado. Ejecuta el modelo para calcular nuevos resultados.');
}
function readOptions(){const num=id=>{const e=byId(id);if(!e.reportValidity())throw new Error('Revisa los supuestos de las alternativas.');return Number(e.value);};return {extraVehicleCapacity:num('extra-capacity'),extraVehicleFixedCost:num('extra-fixed'),extraVehicleCostPerKm:num('extra-km'),capacityIncreasePercent:num('upgrade-percent'),upgradeFixedCostPerVehicle:num('upgrade-fixed')};}
function setBusy(value){workspaceBusy=value;byId('planning-fields').disabled=value;byId('study-settings').disabled=value;byId('run-optimization').disabled=value;byId('run-optimization').textContent=value?'Calculando escenarios…':'Ejecutar optimización';for(const id of ['import-inputs','reset-inputs','validate-inputs','download-inputs','export-comparison','export-routes','export-run'])byId(id).disabled=value;byId('study-progress').classList.toggle('hidden',!value);byId('progress-text').classList.toggle('hidden',!value);}
async function pollStudy(id){let failures=0;while(true){let p;try{p=await jsonApi('/api/optimizaciones/trabajos/'+encodeURIComponent(id));failures=0;}catch(e){if(e.status===404)localStorage.removeItem('agro-active-job');if(e.status===404||++failures>=4)throw new Error(e.message+' Recarga para recuperar el cálculo si el servidor sigue activo.');await new Promise(r=>setTimeout(r,2000));continue;}
  byId('study-progress').max=p.total||1;byId('study-progress').value=p.completed;byId('progress-text').textContent=p.total?`${p.completed} de ${p.total} escenarios completados. El límite se aplica a cada escenario; algunos esperan un turno de cálculo.`:'Preparando matriz vial…';if(p.state==='FAILED')throw new Error(p.error||'El cálculo falló.');if(p.state==='COMPLETED')return p.result;await new Promise(r=>setTimeout(r,1500));}}
function showStudy(result){if(!Array.isArray(result.experiments)||!result.experiments.length)throw new Error(result.message||'Sin resultados.');if(result.experiments.some(e=>(e.routes||[]).some(r=>!DEPOT_LABELS.includes(r.depot))))throw new Error('La ejecución guardada usa la nomenclatura anterior. Vuelve a ejecutar el estudio para obtener resultados con nombres genéricos.');activeOptimizationResponse=result;ensureExperimentTabs(result.experiments);renderResult(result.experiments[0],result);}
async function startStudy(existingId=null){if(workspaceBusy)return;workspaceBusy=true;try{let request;if(!existingId){const data=await validateData(collectData());if(!byId('solver-limit').reportValidity())throw new Error('El límite debe estar entre 1 y 60 segundos.');request={data,offline:byId('offline-mode').checked,alternatives:byId('include-alternatives').checked,sensitivity:byId('include-sensitivity').checked,timeLimitMs:Number(byId('solver-limit').value)*1000,options:readOptions()};if(request.alternatives&&data.vehicleCapacity.length>7)throw new Error('Usa hasta 7 vehículos para evaluar uno adicional.');await storedValue('draft',data,true);}
  setBusy(true);byId('optimization-message').textContent=existingId?'Recuperando ejecución en curso…':'Estudio en curso. Los resultados visibles corresponden a la última ejecución terminada.';const id=existingId||(await jsonApi('/api/optimizaciones/trabajos',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)})).id;localStorage.setItem('agro-active-job',id);const result=await pollStudy(id);localStorage.removeItem('agro-active-job');showStudy(result);window.onStudyFinished?.();try{await storedValue('lastRun',result,true);}catch{editorMessage('No se pudo conservar el resultado en el navegador. Descarga la ejecución JSON.',true);}
  }catch(e){byId('optimization-message').textContent='No se pudo completar: '+e.message;}finally{setBusy(false);}}
const referenceOf = r => r.experiments?.find(x=>x.scenario==='A_BASELINE');
const savings = (base,value) => base>0?(base-value)/base*100:null;
const displayNumber = (value,decimals=1) => Number.isFinite(value)?new Intl.NumberFormat('es-CO',{maximumFractionDigits:decimals,minimumFractionDigits:decimals}).format(value):'—';
function enrichResults(result,response){currentExperiment=result;const b=referenceOf(response),ok=result.validated,compare=ok&&b?.validated,delta=compare?b.totalCostCop-result.totalCostCop:null,percentage=compare?savings(b.totalCostCop,result.totalCostCop):null,actual=response.run?.request?.data?.actualPlanCostCop;
  byId('economic-comparison').textContent=ok?`Costo logístico: ${formatCop(result.totalCostCop)}. `+(delta===null?'Referencia A sin solución validada.':`${delta>=0?'Ahorro':'Sobrecosto'} frente a A: ${formatCop(Math.abs(delta))}${percentage===null?'':` (${displayNumber(Math.abs(percentage))} %)`}.`)+(actual?` Frente al costo observado declarado (${formatCop(actual)}): ${displayNumber(savings(actual,result.totalCostCop))} % de ahorro.`:' No se declaró un costo observado del plan actual.'):'Sin solución validada: no se calculan ahorros ni se muestran costos cero como resultados.';
  const m=result.solverMetrics;byId('solver-details').textContent=m?`Objetivo: ${ok?displayNumber(result.objectiveValue,0)+' COP-equivalentes':'—'} · Límite: ${m.timeLimitMs/1000} s · Solución: ${displayNumber(result.solvingTimeMs/1000,2)} s · Gap: ${m.relativeGap==null?'—':displayNumber(100*m.relativeGap,2)+' %'} · Cota: ${m.bestBound==null?'—':displayNumber(m.bestBound,0)} · Variables: ${m.variables} (${m.binaryVariables} binarias, ${m.variables-m.binaryVariables} continuas) · Restricciones: ${m.constraints}. ${m.solverVersion}. Objetivos con coeficientes distintos no son comparables directamente.`:(result.message||'Sin métricas de solución.');
  const run=response.run;byId('run-provenance').textContent=run?`${new Date(response.executedAt).toLocaleString('es-CO')} · ${run.matrixMode==='LOCAL_SNAPSHOT'?'Matriz local guardada':'Matriz consultada en línea'} · Fecha matriz: ${run.matrixGeneratedAt} · ${displayNumber(response.solvingTimeMs/1000)} s de ejecución completa. Origen declarado: ${run.request.data.provenance}`:'Resultado anterior sin metadatos completos.';
  const table=byId('experiment-comparison-body').closest('table');table.querySelector('thead').innerHTML='<tr>'+['Escenario','Estado SCIP','Costo COP','Cambio de costo vs A','Espera depósito (min)','Espera productor (min)','Operación (min)','Desde 07:00 (min-vehículo)','Solución (s)','Mejora operación vs A','Servicio','Gap (%)','Vehículos usados','Utilización (%)'].map(x=>`<th>${x}</th>`).join('')+'</tr>';
  byId('experiment-comparison-body').innerHTML=response.experiments.map(e=>{const valid=e.validated,comparison=valid&&b?.validated,change=comparison?e.totalCostCop-b.totalCostCop:null,improvement=comparison?savings(b.totalOperationMinutes,e.totalOperationMinutes):null;const category=e.category==='ALTERNATIVE'?'Alternativa':e.category==='SENSITIVITY'?'Sensibilidad':e.category==='TEMPORAL'?'Penalización temporal':'Referencia del modelo';
    const capacity=(e.routes||[]).reduce((sum,r)=>sum+r.capacityTonnes,0),load=(e.routes||[]).reduce((sum,r)=>sum+r.loadTonnes,0);
    const cells=[`<span class="scenario-name">${escapeHtml(scenarioTitle(e))}</span><small class="scenario-code">${category}</small>`,`<span class="table-status" data-validated="${valid}">${escapeHtml(e.status)}</span>`,valid?formatCop(e.totalCostCop):'—',change===null?'—':`${change>0?'+':''}${formatCop(change)}${b.totalCostCop>0?` (${displayNumber(100*change/b.totalCostCop)} %)` :''}`,...[e.totalDepotWaitingMinutes,e.totalProducerWaitingMinutes,e.totalOperationMinutes,e.totalDepotWaitingMinutes+e.totalOperationMinutes].map(x=>valid?displayNumber(x):'—'),displayNumber(e.solvingTimeMs/1000,2),improvement===null?'—':displayNumber(improvement)+' %',valid?(run?.activeCustomers===0?'Sin demanda':`${e.servedCustomers}/${run?.activeCustomers??'—'}`):'—',e.solverMetrics?.relativeGap==null?'—':displayNumber(100*e.solverMetrics.relativeGap,2),valid?String(e.usedVehicles):'—',valid&&capacity>0?displayNumber(100*load/capacity):'—'];return `<tr class="${e.scenario===result.scenario?'active':''}" title="${escapeHtml(e.label)}">${cells.map(x=>`<td>${x}</td>`).join('')}</tr>`;}).join('');
  if(!ok)byId('result-summary').querySelectorAll('strong').forEach(x=>x.textContent='—');renderSensitivity(response);
}
function alternativeChart(response) {
  const alternatives=response.experiments.filter(e=>e.category==='ALTERNATIVE'); if(!alternatives.length)return '';
  const values=[referenceOf(response),...alternatives].filter(Boolean),max=Math.max(1,...values.filter(e=>e.validated).map(e=>e.totalCostCop));
  return '<h4>Evaluación económica de alternativas</h4><p class="small muted">Costo por jornada con costos adicionales incluidos. Los escenarios comparten la salida fija de A. La utilización se calcula sobre la capacidad de los vehículos usados.</p><div class="alternative-chart">'+values.map(e=>`<div class="alternative-bar"><div><span>${escapeHtml(scenarioTitle(e))}</span><strong>${e.validated?formatCop(e.totalCostCop):escapeHtml(e.status)}</strong></div><div class="bar-track" aria-hidden="true"><span style="width:${e.validated?100*e.totalCostCop/max:0}%"></span></div><small>${escapeHtml(e.status)} · ${e.validated?e.usedVehicles+' vehículos usados':'Sin solución validada'}</small></div>`).join('')+'</div>';
}
function renderSensitivity(response){const base=referenceOf(response),container=byId('sensitivity-charts'),alternatives=alternativeChart(response);if(!response.experiments.some(e=>e.category==='SENSITIVITY')){container.innerHTML=alternatives+'<p class="small muted">Activa sensibilidad para analizar demanda, capacidad y costo/km.</p>';return;}
  const names={demand:'Demanda',capacity:'Capacidad vehicular',costPerKm:'Costo por kilómetro'};container.innerHTML=alternatives+'<h4>Sensibilidad paramétrica</h4><p class="small muted">Política de referencia y parámetros restantes constantes. Cada punto resuelve nuevamente el MILP. Las líneas conectan resultados obtenidos; no son precios sombra. Consulta el estado y el gap en la tabla.</p><div class="charts-grid">'+Object.entries(names).map(([key,name])=>{
    const p=[response.experiments.find(e=>e.parameter===key&&e.parameterValue===.8),base,response.experiments.find(e=>e.parameter===key&&e.parameterValue===1.2)],max=Math.max(1,...p.filter(e=>e?.validated).map(e=>e.totalCostCop))*1.12,y=v=>175-130*v/max,x=i=>55+i*100;
    let svg=`<svg viewBox="0 0 320 230" role="img" aria-label="${name}: costo logístico para 80, 100 y 120 por ciento"><text x="20" y="20">Costo logístico (COP)</text><path d="M45 35V175H290" stroke="#b6c8bd" fill="none"/>`;
    for(let t=0;t<=2;t++){const v=max*t/2;svg+=`<text x="40" y="${y(v)+4}" text-anchor="end" class="axis-label">${Math.round(v/1000)}k</text>`;}
    for(let i=1;i<3;i++)if(p[i-1]?.validated&&p[i]?.validated)svg+=`<path d="M${x(i-1)} ${y(p[i-1].totalCostCop)}L${x(i)} ${y(p[i].totalCostCop)}" stroke="#287653" stroke-width="2" fill="none"/>`;
    p.forEach((e,i)=>{svg+=`<text x="${x(i)}" y="201" text-anchor="middle">${[80,100,120][i]} %</text>`;svg+=e?.validated?`<circle cx="${x(i)}" cy="${y(e.totalCostCop)}" r="5" fill="#287653"><title>${escapeHtml(e.status)} · ${formatCop(e.totalCostCop)}</title></circle>`:`<text x="${x(i)}" y="160" text-anchor="middle" class="chart-error">${e?.status==='INFEASIBLE'?'Infactible':'Sin solución'}</text>`;});svg+='</svg>';
    return `<article class="chart-card"><h5>${name}</h5>${svg}<ul class="chart-values">${p.map((e,i)=>`<li>${[80,100,120][i]} %: ${e?.validated?formatCop(e.totalCostCop)+' · '+escapeHtml(e.status):escapeHtml(e?.status||'Sin resultado')}</li>`).join('')}</ul></article>`;}).join('')+'</div>';
}
function exportComparison(){const r=activeOptimizationResponse;if(!r)return;const b=referenceOf(r);const rows=[['scenario','category','label','status','validated','cost_COP','objective_COP_equivalent','distance_km','cost_change_vs_A_COP','cost_improvement_vs_A_pct','operation_improvement_vs_A_pct','depot_wait_min','producer_wait_min','driving_min','service_min','operation_min','since_0700_vehicle_min','vehicles','served_customers','active_customers','solve_ms','gap_pct','best_bound','variables','binary_variables','constraints','parameter','parameter_value','executed_at','matrix_date','matrix_mode','provenance','actual_plan_COP','actual_plan_improvement_pct','message','capacity_utilization_pct']];
  r.experiments.forEach(e=>{const ok=e.validated,m=e.solverMetrics,compare=ok&&b?.validated;rows.push([e.scenario,e.category,e.label,e.status,ok,ok?e.totalCostCop:null,ok?e.objectiveValue:null,ok?e.totalDistanceKm:null,compare?e.totalCostCop-b.totalCostCop:null,compare?savings(b.totalCostCop,e.totalCostCop):null,compare?savings(b.totalOperationMinutes,e.totalOperationMinutes):null,...[e.totalDepotWaitingMinutes,e.totalProducerWaitingMinutes,e.totalDrivingMinutes,e.totalServiceMinutes,e.totalOperationMinutes,e.totalDepotWaitingMinutes+e.totalOperationMinutes,e.usedVehicles,e.servedCustomers].map(x=>ok?x:null),r.run?.activeCustomers,e.solvingTimeMs,m?.relativeGap==null?null:m.relativeGap*100,m?.bestBound,m?.variables,m?.binaryVariables,m?.constraints,e.parameter,e.parameterValue,r.executedAt,r.run?.matrixGeneratedAt,r.run?.matrixMode,r.run?.request?.data?.provenance,r.run?.request?.data?.actualPlanCostCop,ok&&r.run?.request?.data?.actualPlanCostCop?savings(r.run.request.data.actualPlanCostCop,e.totalCostCop):null,e.message,ok&&e.routes?.reduce((n,r)=>n+r.capacityTonnes,0)>0?100*e.routes.reduce((n,r)=>n+r.loadTonnes,0)/e.routes.reduce((n,r)=>n+r.capacityTonnes,0):null]);});downloadFile('agrodistribuye-comparacion.csv',rowsCSV(rows),'text/csv;charset=utf-8');
}
function exportRoutes(){const r=activeOptimizationResponse;if(!r)return;const rows=[['scenario','status','depot','vehicle','capacity_t','load_t','route_km','route_cost_COP','departure','return','depot_wait_min','route_wait_min','driving_min','service_min','operation_min','stop_order','producer','arrival','service_start','service_end','stop_demand_t','stop_wait_min','products_t_JSON','executed_at','matrix_date']];for(const e of r.experiments)if(e.validated)for(const route of e.routes)route.stops.forEach((s,i)=>rows.push([e.scenario,e.status,route.depot,route.vehicle,route.capacityTonnes,route.loadTonnes,route.distanceKm,route.routeCostCop,route.departureTime,route.returnTime,route.depotWaitingMinutes,route.waitingMinutes,route.drivingMinutes,route.serviceMinutes,route.operationMinutes,i+1,s.producerId,s.arrivalTime,s.serviceStart,s.serviceEnd,s.demandTonnes,s.waitingMinutes,JSON.stringify(route.productLoadsTonnes),r.executedAt,r.run?.matrixGeneratedAt]));downloadFile('agrodistribuye-rutas.csv',rowsCSV(rows),'text/csv;charset=utf-8');}
function renderOfflineMap(){if(!workspaceGeo)return;const geo=workspaceGeo,refs=[...geo.depots,...geo.producers],routes=currentExperiment?.routes||[],all=refs.map(p=>[p.latitude,p.longitude]);routes.forEach(r=>(r.geometry||[]).forEach(p=>all.push(p)));const lats=all.map(p=>p[0]),lons=all.map(p=>p[1]),minLat=Math.min(...lats),maxLat=Math.max(...lats),minLon=Math.min(...lons),maxLon=Math.max(...lons),scale=Math.min(750/Math.max(.001,maxLon-minLon),330/Math.max(.001,maxLat-minLat)),project=p=>[50+(p[1]-minLon)*scale,390-(p[0]-minLat)*scale];
  let svg='<svg viewBox="0 0 880 440" role="img" aria-label="Ubicaciones y rutas sin mapa base"><defs><pattern id="offline-grid" width="40" height="40" patternUnits="userSpaceOnUse"><path d="M40 0H0V40" fill="none" stroke="#e3ece4"/></pattern></defs><rect width="880" height="440" fill="#f6f9f4"/><rect width="880" height="440" fill="url(#offline-grid)"/><text x="830" y="40" text-anchor="end" fill="#61736b">N ↑</text>';
  routes.forEach((r,i)=>{const depot=geo.depots.find(d=>r.depot===d.id||r.depot===d.name);const sequence=[depot,...r.stops.map(s=>geo.producers.find(p=>p.id===s.producerId)),depot].filter(Boolean).map(p=>[p.latitude,p.longitude]),geometry=r.geometry?.length>=2?r.geometry:sequence,stride=Math.max(1,Math.ceil(geometry.length/1200)),points=geometry.filter((_,idx)=>idx%stride===0||idx===geometry.length-1).map(p=>project(p).map(v=>v.toFixed(1)).join(',')).join(' ');svg+=`<polyline points="${points}" fill="none" stroke="${ROUTE_COLORS[i%ROUTE_COLORS.length]}" stroke-width="3" ${r.geometry?.length>=2?'':'stroke-dasharray="7 5"'}><title>${escapeHtml(r.vehicle)} · ${r.geometry?.length>=2?'Geometría OSRM guardada':'Secuencia esquemática, no carretera'}</title></polyline>`;});
  refs.forEach(p=>{const[x,y]=project([p.latitude,p.longitude]);svg+=`<circle cx="${x}" cy="${y}" r="7" fill="${p.id.startsWith('C')?'#194b39':'#3979b8'}" stroke="#fff" stroke-width="2"/><text x="${x+10}" y="${y-10}" fill="#22352e" font-size="12">${p.id}</text>`;});svg+='<text x="24" y="423" font-size="11" fill="#61736b">Esquema WGS84 · línea discontinua = secuencia sin geometría vial disponible</text></svg>';byId('offline-route-map').innerHTML=svg;
}
function isLocalMapView(){return byId('map-view-mode')?.value === 'local';}
function setMapMode(){const offline=isLocalMapView();byId('real-map').classList.toggle('hidden',offline);byId('offline-route-map').classList.toggle('hidden',!offline);byId('map-mode-caption').textContent=offline?'Esquema local sin internet. Las líneas discontinuas representan secuencias, no carreteras. Esta vista no cambia la matriz del cálculo.':'Mapa geográfico de OpenStreetMap (requiere internet). La vista del mapa es independiente de la matriz local o en línea usada para calcular las rutas.';
  if(geographicMap){if(offline&&tileLayer){tileLayer.remove();tileLayer=null;}if(!offline&&byId('real-map').getClientRects().length){geographicMap.invalidateSize();if(workspaceGeo)geographicMap.fitBounds([...workspaceGeo.depots,...workspaceGeo.producers].map(p=>[p.latitude,p.longitude]),{padding:[25,25]});if(!tileLayer)tileLayer=L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(geographicMap);}}
  if(currentExperiment)renderRouteMap(experimentRouteGeometries(currentExperiment));else renderOfflineMap();
}
async function initializeWorkspace(){try{planningDefaults=await jsonApi('/api/optimizaciones/datos');let draft;try{draft=await storedValue('draft');}catch{}let data=planningDefaults;if(draft)try{data=await validateData(draft);}catch{}renderDataEditor(data);byId('planning-fields').disabled=false;byId('run-optimization').disabled=false;workspaceReady=true;editorMessage(draft?'Borrador recuperado de este navegador.':'Ejemplo simulado listo para editar. Descarga el CSV para ver el formato.');
  byId('planning-form').addEventListener('submit',e=>e.preventDefault());byId('planning-form').addEventListener('change',persistDraft);
  byId('download-inputs').onclick=()=>{try{downloadFile('agrodistribuye-datos.csv',rowsCSV(inputRows(collectData())),'text/csv;charset=utf-8');}catch(e){editorMessage(e.message,true);}};
  byId('validate-inputs').onclick=async()=>{try{await validateData(collectData());editorMessage('Estructura y unidades válidas. La factibilidad se determina al ejecutar el modelo.');}catch(e){editorMessage(e.message,true);}};
  byId('reset-inputs').onclick=async()=>{renderDataEditor(cloneData(planningDefaults));await persistDraft();};byId('import-inputs').onchange=async e=>{try{await importInputs(e.target.files[0]);}catch(err){editorMessage(err.message,true);}finally{e.target.value='';}};
  byId('map-view-mode').onchange=setMapMode;byId('export-comparison').onclick=exportComparison;byId('export-routes').onclick=exportRoutes;byId('export-run').onclick=()=>{if(activeOptimizationResponse)downloadFile('agrodistribuye-ejecucion.json',JSON.stringify(activeOptimizationResponse,null,2),'application/json');};
  try{const last=await storedValue('lastRun');if(last){showStudy(last);byId('optimization-message').textContent='Última ejecución recuperada: corresponde a los datos y fecha indicados, no a cambios posteriores del editor.';}}catch(e){byId('optimization-message').textContent=e.message;}
  const job=localStorage.getItem('agro-active-job');if(job)startStudy(job);
}catch(e){editorMessage('No se pudo inicializar el editor: '+e.message,true);}}
window.addEventListener('DOMContentLoaded',initializeWorkspace);
