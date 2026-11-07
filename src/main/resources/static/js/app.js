const $ = selector => document.querySelector(selector);
function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
}
function formatCop(value) {
  return new Intl.NumberFormat('es-CO', {style:'currency', currency:'COP', maximumFractionDigits:0}).format(value || 0);
}

const ROUTE_COLORS = ['#24764a','#3979b8','#b16b27','#8256a8','#bd5260','#648b35'];
const SCENARIO_TITLES = {
  A_BASELINE: 'A · Referencia', B_DEPOT_50: 'B1 · Esperas',
  B_DEPOT_100: 'B2 · Esperas', B_DEPOT_300: 'B3 · Esperas',
  C_OPERATION_50: 'C1 · Operación', C_OPERATION_100: 'C2 · Operación',
  C_OPERATION_200: 'C3 · Operación'
  , ALT_EXTRA_VEHICLE: 'Alt. 1 · Vehículo', ALT_CAPACITY: 'Alt. 2 · Capacidad',
  SENS_demand_80: 'Demanda 80 %', SENS_demand_120: 'Demanda 120 %',
  SENS_capacity_80: 'Capacidad 80 %', SENS_capacity_120: 'Capacidad 120 %',
  SENS_costPerKm_80: 'Costo/km 80 %', SENS_costPerKm_120: 'Costo/km 120 %'
};
function scenarioTitle(experiment) {
  return SCENARIO_TITLES[experiment.scenario] || experiment.label || experiment.scenario || 'Resultado';
}
let geographicMap = null;
let optimizationRouteLayer = null;
let activeOptimizationResponse = null;
function renderRouteMap(routeGeometries) {
  const status = $('#route-map-status');
  if (isLocalMapView()) {
    renderOfflineMap();
    const count = (routeGeometries || []).filter(r => r.coordinates?.length >= 2).length;
    if (status) status.textContent = `Vista local: ${count} geometrías OSRM guardadas de ${(routeGeometries || []).length} rutas. Las restantes se muestran como secuencias discontinuas, no carreteras.`;
    const legend = $('#route-map-legend');
    if (legend) legend.innerHTML = (routeGeometries || []).map((r,i) => `<span><i class="legend-line" style="background:${ROUTE_COLORS[i % ROUTE_COLORS.length]}"></i>${escapeHtml(r.vehicle)}</span>`).join('');
    return;
  }
  if (!geographicMap) {
    if (status) status.textContent = 'El mapa geográfico aún no está disponible. Verifica la conexión a OpenStreetMap y vuelve a ejecutar.';
    return;
  }
  if (!$('#real-map').getClientRects().length) {
    if (status) status.textContent = 'Abre la pestaña Mapa para consultar los trazados del escenario seleccionado.';
    return;
  }
  if (optimizationRouteLayer) optimizationRouteLayer.remove();
  optimizationRouteLayer = L.layerGroup().addTo(geographicMap);
  const bounds = [];
  let drawnRoutes = 0;
  const legend = $('#route-map-legend');
  if (legend) legend.innerHTML = '<span><i class="legend-dot depot-dot"></i> Depósitos</span><span><i class="legend-dot producer-dot"></i> Productores</span>' + (routeGeometries || []).map((r,i) => `<span><i class="legend-line" style="background:${ROUTE_COLORS[i % ROUTE_COLORS.length]}"></i>${escapeHtml(r.vehicle || `Ruta ${i+1}`)}</span>`).join('');
  (routeGeometries || []).forEach((route, index) => {
    const color = ROUTE_COLORS[index % ROUTE_COLORS.length];
    const points = (route.coordinates || []).map(pair => [Number(pair[0]), Number(pair[1])])
      .filter(pair => Number.isFinite(pair[0]) && Number.isFinite(pair[1]));
    if (points.length < 2) return;
    drawnRoutes++;
    const line = L.polyline(points, {color, weight:6, opacity:.88, lineCap:'round', lineJoin:'round'}).addTo(optimizationRouteLayer);
    line.bindTooltip(`${escapeHtml(route.vehicle || `Ruta ${index+1}`)} · ${escapeHtml(route.depot || '')}`, {sticky:true});
    points.forEach(point => bounds.push(point));
  });
  if (bounds.length) {
    geographicMap.fitBounds(bounds, {padding:[35,35], maxZoom:13});
    if (status) status.textContent = `${drawnRoutes} de ${routeGeometries.length} geometrías OSRM dibujadas. Las secuencias sin geometría vial se pueden consultar en el esquema local.`;
  } else if (status) {
    status.textContent = 'La optimización terminó, pero OSRM no devolvió geometrías para dibujar.';
  }
}
function experimentRouteGeometries(experiment) {
  return (experiment.routes || []).map(route => ({
    vehicle: route.vehicle,
    depot: route.depot,
    coordinates: route.geometry || route.pathCoordinates || []
  }));
}
function ensureExperimentTabs(experiments) {
  let tabs = $('#experiment-tabs');
  if (!tabs) {
    tabs = document.createElement('div');
    tabs.id = 'experiment-tabs';
    tabs.className = 'experiment-tabs';
    tabs.setAttribute('role', 'tablist');
    $('#result-summary').before(tabs);
  }
  tabs.innerHTML = experiments.map(experiment =>
    `<button type="button" role="tab" class="experiment-tab" title="${escapeHtml(experiment.label || experiment.scenario)}" aria-label="${escapeHtml(experiment.label || experiment.scenario)}" data-scenario="${escapeHtml(experiment.scenario)}">${escapeHtml(scenarioTitle(experiment))}</button>`
  ).join('');
  tabs.onclick = event => {
    const button = event.target.closest('[data-scenario]');
    if (!button || !activeOptimizationResponse) return;
    const selected = (activeOptimizationResponse.experiments || []).find(item => item.scenario === button.dataset.scenario);
    if (selected) renderResult(selected, activeOptimizationResponse);
  };
  tabs.onkeydown = event => {
    const buttons = [...tabs.querySelectorAll('[role="tab"]')];
    const index = buttons.indexOf(event.target);
    if (index < 0 || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1
      : (index + (event.key === 'ArrowRight' ? 1 : -1) + buttons.length) % buttons.length;
    buttons[next].click();
    buttons[next].focus();
  };
}
function renderResult(result, response = result) {
  currentExperiment = result;
  $('#result-status').textContent = result.status || 'EJECUTADO';
  $('#result-status').dataset.validated = String(Boolean(result.validated));
  if ($('#result-scenario')) $('#result-scenario').textContent = result.label || 'Resultado de la optimización';
  const items = [
    ['Costo total', formatCop(result.totalCostCop)],
    ['Distancia total', `${Number(result.totalDistanceKm || 0).toFixed(1)} km`],
    ['Espera en depósito', `${Number(result.totalDepotWaitingMinutes || 0).toFixed(1)} min`],
    ['Espera en productor', `${Number(result.totalProducerWaitingMinutes || 0).toFixed(1)} min`],
    ['Tiempo de operación', `${Number(result.totalOperationMinutes || 0).toFixed(1)} min`]
  ];
  $('#result-summary').innerHTML = items.map(([label,value]) => `<div class="result-stat"><span>${label}</span><strong>${escapeHtml(value)}</strong></div>`).join('');
  const experiments = Array.isArray(response.experiments) ? response.experiments : [];
  const baseline = experiments.find(experiment => experiment.scenario === 'A_BASELINE');
  const savingsElement = $('#operation-savings');
  if (savingsElement) {
    const baselineMinutes = Number(baseline?.totalOperationMinutes || 0);
    const currentMinutes = Number(result.totalOperationMinutes || 0);
    const savingsPercent = baseline?.validated && result.validated && baselineMinutes > 0
      ? ((baselineMinutes - currentMinutes) / baselineMinutes) * 100
      : null;
    savingsElement.textContent = savingsPercent === null ? '—' : `${savingsPercent.toFixed(1)}%`;
    savingsElement.title = savingsPercent === null ? 'No hay tiempo base para comparar.'
      : savingsPercent >= 0 ? 'Reducción respecto al tiempo de operación base.'
      : 'El escenario requiere más tiempo que A_BASELINE.';
  }
  const comparisonBody = $('#experiment-comparison-body');
  if (comparisonBody) {
    comparisonBody.innerHTML = experiments.length ? experiments.map(experiment => `
      <tr class="${experiment.scenario === result.scenario ? 'active' : ''}">
        <td><span class="scenario-name">${escapeHtml(scenarioTitle(experiment))}</span><small class="scenario-code">${escapeHtml(experiment.scenario || '—')}</small></td>
        <td><span class="table-status" data-validated="${Boolean(experiment.validated)}">${escapeHtml(experiment.status || '—')}</span></td>
        <td>${formatCop(experiment.totalCostCop)}</td>
        <td>${Number(experiment.totalDepotWaitingMinutes || 0).toFixed(1)} min</td>
        <td>${Number(experiment.totalProducerWaitingMinutes || 0).toFixed(1)} min</td>
        <td>${Number(experiment.totalOperationMinutes || 0).toFixed(1)} min</td>
      </tr>`).join('') : '<tr><td colspan="6">No hay resultados experimentales disponibles.</td></tr>';
  }
  const tabs = $('#experiment-tabs');
  if (tabs) tabs.querySelectorAll('.experiment-tab').forEach(tab => {
    tab.classList.toggle('active', tab.dataset.scenario === result.scenario);
    const selected = tab.dataset.scenario === result.scenario;
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });
  const routes = (result.routes || []).map(route => `
    <article class="route-card">
      <h4>${escapeHtml(route.depot)} · ${escapeHtml(route.vehicle)}</h4>
      <p><b>Carga:</b> ${Number(route.loadTonnes).toFixed(1)} / ${Number(route.capacityTonnes).toFixed(1)} t
      &nbsp; <b>Distancia:</b> ${Number(route.distanceKm).toFixed(1)} km
      &nbsp; <b>Costo:</b> ${formatCop(route.routeCostCop)}</p>
      <p><b>Ruta:</b> ${escapeHtml(route.depot)} → ${(route.stops || []).map(s => `${escapeHtml(s.producerId)} (${escapeHtml(s.serviceStart)}–${escapeHtml(s.serviceEnd)})`).join(' → ')} → ${escapeHtml(route.depot)}</p>
      <p><b>Productos:</b> ${Object.entries(route.productLoadsTonnes || {}).map(([name,qty]) => `${escapeHtml(name)}: ${Number(qty).toFixed(1)} t`).join(' · ')}</p>
      <p><b>Salida:</b> ${escapeHtml(route.departureTime)} &nbsp; <b>Regreso al depósito:</b> ${escapeHtml(route.returnTime)}</p>
      <p><b>Espera en depósito:</b> ${Number(route.depotWaitingMinutes || 0).toFixed(1)} min &nbsp; <b>Espera en productores:</b> ${Number(route.waitingMinutes || 0).toFixed(1)} min</p>
      <p><b>Conducción:</b> ${Number(route.drivingMinutes || 0).toFixed(1)} min &nbsp; <b>Servicio:</b> ${Number(route.serviceMinutes || 0).toFixed(1)} min &nbsp; <b>Operación:</b> ${Number(route.operationMinutes || 0).toFixed(1)} min</p>
      <p><b>Capacidad disponible:</b> ${(Number(route.capacityTonnes)-Number(route.loadTonnes)).toFixed(2)} t (${(100*Number(route.loadTonnes)/Number(route.capacityTonnes)).toFixed(1)} % utilizada)</p>
    </article>`).join('');
  $('#result-output').innerHTML = routes || '<p>No hay rutas para mostrar.</p>';
  renderRouteMap(result.scenario ? experimentRouteGeometries(result) : (result.routeGeometries || []));
  $('#optimization-result').classList.remove('hidden');
  $('#optimization-message').textContent = result.validated
    ? `Escenario ${result.scenario || 'seleccionado'}: validación independiente superada. Tiempo de solución: ${result.solvingTimeMs} ms.${result.message ? ` ${result.message}` : ''}`
    : (result.message || 'No se obtuvo una solución validada.');
  enrichResults(result, response);
  window.dispatchEvent(new Event('study-rendered'));
}
async function runOptimization() { if (workspaceReady) await startStudy(); }
$('#run-optimization').addEventListener('click', runOptimization);
async function loadGeographicBase(){
  const response=await fetch('/data/geografia-popayan.json');
  if(!response.ok)throw new Error('No se pudo cargar la base geográfica.');
  const geo=await response.json();
  workspaceGeo = geo;
  renderOfflineMap();
  const depotStatus=d=>d.dataStatus==='USER_PROVIDED_COORDINATE'
    ? 'Coordenada proporcionada por el usuario; acceso operativo pendiente de validar.'
    : 'Coordenada pendiente de validación.';
  $('#geo-depot-list').innerHTML=geo.depots.map(d=>`<div class="geo-depot-item"><strong>${escapeHtml(d.id)} · ${escapeHtml(d.name)}</strong><small>${escapeHtml(d.address)}<br>Estado: ${escapeHtml(depotStatus(d))}<br>${escapeHtml(d.role||'')}</small></div>`).join('');
  if(!window.L){$('#real-map').textContent='No se pudo cargar la librería cartográfica. Verifica la conexión a internet.';return;}
  const map=L.map('real-map').setView([2.475,-76.61],12);
  geographicMap = map;
  const bounds=[];
  geo.producers.forEach(p=>{
    const point=[p.latitude,p.longitude];bounds.push(point);
    L.circleMarker(point,{radius:7,color:'#17633a',weight:2,fillColor:'#2f9b5d',fillOpacity:.9}).addTo(map)
      .bindPopup(`<strong>${escapeHtml(p.id)} · ${escapeHtml(p.name)}</strong><br>Centro poblado/vereda de referencia (SGC)<br><small>No representa la ubicación exacta de una finca.</small>`);
  });
  geo.depots.forEach(d=>{
    if(!Number.isFinite(d.latitude)||!Number.isFinite(d.longitude))return;
    const point=[d.latitude,d.longitude];bounds.push(point);
    const icon=L.divIcon({className:'geo-depot-marker',html:`<span>${escapeHtml(d.id)}</span>`,iconSize:[30,30],iconAnchor:[15,15]});
    L.marker(point,{icon}).addTo(map).bindPopup(`<strong>${escapeHtml(d.id)} · ${escapeHtml(d.name)}</strong><br>${escapeHtml(d.address)}<br><small>Coordenada proporcionada por el usuario. Validar acceso operativo y condición de depósito.</small>`);
  });
  if(bounds.length && !isLocalMapView() && $('#real-map').getClientRects().length)map.fitBounds(bounds,{padding:[25,25]});
  setMapMode();
}
loadGeographicBase().catch(error=>{const el=$('#real-map');if(el)el.textContent=error.message;});
$('#show-geographic-map')?.addEventListener('click',()=>{if(window.openWorkspaceView)window.openWorkspaceView('map');else (isLocalMapView() ? $('#offline-route-map') : $('#real-map'))?.scrollIntoView({block:'center'});});
