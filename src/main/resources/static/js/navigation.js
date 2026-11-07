/* Navegación del estudio: mueve los controles existentes sin duplicar datos ni resultados. */
(() => {
  const views = [
    {id:'overview', hash:'resumen', title:'Resumen', subtitle:'El territorio, los recursos y tu próximo estudio.', icon:'grid'},
    {id:'data', hash:'datos', title:'Datos', subtitle:'Edita o importa los datos de la jornada antes de ejecutar.', icon:'box'},
    {id:'execute', hash:'ejecutar', title:'Ejecutar', subtitle:'Configura los escenarios y sigue el progreso del estudio.', icon:'route'},
    {id:'results', hash:'resultados', title:'Resultados', subtitle:'Compara escenarios y consulta la evidencia de cada solución.', icon:'grid'},
    {id:'map', hash:'mapa', title:'Mapa', subtitle:'Consulta el territorio y las rutas del escenario seleccionado.', icon:'pin'}
  ];
  const subviews = [
    {id:'summary', hash:'resumen', title:'Resumen'},
    {id:'comparison', hash:'comparacion', title:'Comparación'},
    {id:'analysis', hash:'analisis', title:'Análisis'},
    {id:'routes', hash:'rutas', title:'Rutas'},
    {id:'method', hash:'metodo', title:'Método y datos'}
  ];
  let activeView = 'overview', activeResult = 'summary', activeData = 0;
  const panels = new Map(), resultPanels = new Map();
  let primaryTabs, resultTabs, runState, resultEmpty;
  const aliases = {inicio:'overview', geografia:'map', catalogo:'data', configuracion:'data', optimizacion:'execute', 'optimization-result':'results', 'sensitivity-charts':'results'};

  function makeTabs(items, prefix, label, onSelect) {
    const list = document.createElement('div');
    list.className = prefix === 'workspace' ? 'workspace-tabs' : 'section-tabs';
    list.setAttribute('role', 'tablist'); list.setAttribute('aria-label', label);
    items.forEach(item => {
      const button = document.createElement('button'); button.type = 'button';
      button.id = `${prefix}-tab-${item.id}`; button.dataset.tabKey = item.id;
      button.setAttribute('role', 'tab'); button.setAttribute('aria-controls', `${prefix}-panel-${item.id}`);
      button.setAttribute('aria-selected', 'false'); button.tabIndex = -1;
      if (item.icon) button.innerHTML = `<svg class="icon" aria-hidden="true"><use href="#icon-${item.icon}"/></svg>`;
      button.append(document.createTextNode(item.title));
      button.addEventListener('click', () => onSelect(item.id)); list.append(button);
    });
    list.addEventListener('keydown', event => {
      const buttons = [...list.querySelectorAll('[role="tab"]')], index = buttons.indexOf(event.target);
      if (index < 0 || !['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
      event.preventDefault();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1
        : (index + (event.key === 'ArrowRight' ? 1 : -1) + buttons.length) % buttons.length;
      buttons[next].click(); buttons[next].focus();
    });
    return list;
  }
  function makePanel(prefix, id) {
    const section = document.createElement('section'); section.id = `${prefix}-panel-${id}`;
    section.className = prefix === 'workspace' ? 'workspace-panel' : 'section-panel';
    section.setAttribute('role', 'tabpanel'); section.setAttribute('aria-labelledby', `${prefix}-tab-${id}`);
    section.tabIndex = 0; section.hidden = true; return section;
  }
  function select(items, container, id) {
    items.forEach((panel, key) => { panel.hidden = key !== id; });
    container.querySelectorAll('[role="tab"]').forEach(button => {
      const selected = button.dataset.tabKey === id;
      button.setAttribute('aria-selected', String(selected)); button.tabIndex = selected ? 0 : -1;
    });
  }
  function writeHash() {
    const view = views.find(v => v.id === activeView);
    const hash = '#' + view.hash + (activeView === 'results' ? '/' + subviews.find(v => v.id === activeResult).hash : '');
    if (location.hash !== hash) history.pushState(null, '', hash);
  }
  function refreshMap() {
    if (activeView !== 'map') return;
    requestAnimationFrame(() => { if (typeof setMapMode === 'function') setMapMode(); });
  }
  function showView(id, updateURL = true) {
    if (!panels.has(id)) return;
    activeView = id; select(panels, primaryTabs, id);
    const view = views.find(v => v.id === id);
    document.querySelector('.topbar h1').textContent = view.title;
    document.querySelector('.topbar .muted').textContent = view.subtitle;
    document.title = `AgroDistribuye | ${view.title}`;
    document.querySelectorAll('.sidebar .nav-link').forEach(link => {
      const selected = link.dataset.view === id; link.classList.toggle('active', selected);
      if (selected) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
    });
    if (updateURL) writeHash();
    refreshMap();
  }
  function showResult(id, updateURL = true) {
    if (!resultPanels.has(id)) return;
    activeResult = id; select(resultPanels, resultTabs, id);
    if (updateURL && activeView === 'results') writeHash();
  }
  function readHash() {
    const [hash, subhash] = decodeURIComponent(location.hash.slice(1)).split('/');
    const id = views.find(v => v.hash === hash)?.id || aliases[hash] || 'overview';
    if (id === 'results') showResult(subviews.find(v => v.hash === subhash)?.id || activeResult, false);
    showView(id, false);
  }
  function openView(id) {
    showView(id);
    document.querySelector('.workspace-navigation').scrollIntoView({block:'start', behavior:'auto'});
  }
  function prepareResults(result) {
    resultTabs = makeTabs(subviews, 'result-view', 'Vistas de resultados', id => showResult(id));
    const summary = makePanel('result-view', 'summary');
    summary.append(byId('result-summary'), byId('economic-comparison'), result.querySelector('.savings-kpi'));
    const comparison = makePanel('result-view', 'comparison');
    const oldComparison = result.querySelector('.comparison-wrap');
    const tableWrapper = document.createElement('div'); tableWrapper.className = 'comparison-wrap';
    tableWrapper.append(oldComparison.querySelector('table')); oldComparison.remove(); comparison.append(tableWrapper);
    const analysis = makePanel('result-view', 'analysis'); analysis.append(byId('sensitivity-charts'));
    const routes = makePanel('result-view', 'routes');
    const routeDetails = byId('result-output').closest('details'); routeDetails.open = true;
    routes.append(result.querySelector('.route-map-panel'), routeDetails);
    const method = makePanel('result-view', 'method');
    const explanation = result.querySelector(':scope > p.small.muted');
    method.append(byId('run-provenance'), byId('solver-details')); if (explanation) method.append(explanation);
    [summary, comparison, analysis, routes, method].forEach((panel, i) => resultPanels.set(subviews[i].id, panel));
    const exports = result.querySelector(':scope > .editor-actions');
    result.insertBefore(resultTabs, exports);
    resultPanels.forEach(panel => result.append(panel));
    showResult(activeResult, false);
    new MutationObserver(updateState).observe(result, {attributes:true, attributeFilter:['class']});
  }
  function selectData(index) {
    activeData = index;
    const editor = byId('data-editor'), tablist = editor.querySelector('[role="tablist"]'); if (!tablist) return;
    editor.querySelectorAll('[data-editor-panel]').forEach((panel, i) => { panel.hidden = i !== index; });
    tablist.querySelectorAll('[role="tab"]').forEach((button, i) => {
      button.setAttribute('aria-selected', String(i === index)); button.tabIndex = i === index ? 0 : -1;
    });
  }
  function prepareDataTabs() {
    const editor = byId('data-editor');
    const groups = [...editor.children].filter(el => el.tagName === 'DETAILS'); if (!groups.length) return;
    const titles = ['Demanda','Inventario','Depósitos','Horarios','Flota'];
    const tabs = makeTabs(groups.map((_, i) => ({id:String(i), title:titles[i]})), 'data-view', 'Tablas de entrada', key => selectData(Number(key)));
    editor.prepend(tabs);
    groups.forEach((group, i) => {
      const panel = makePanel('data-view', String(i)); panel.dataset.editorPanel = String(i);
      const summary = group.querySelector('summary'), heading = document.createElement('h3');
      heading.textContent = summary.textContent; summary.remove(); panel.append(heading);
      while (group.firstChild) panel.append(group.firstChild);
      group.replaceWith(panel);
    });
    selectData(activeData);
  }
  function updateState() {
    const hasResult = !byId('optimization-result').classList.contains('hidden');
    resultEmpty.hidden = hasResult;
    if (typeof workspaceBusy !== 'undefined' && workspaceBusy) {
      const progress = byId('study-progress');
      runState.hidden = false; runState.textContent = `Calculando · ${progress.value}/${progress.max}`;
      runState.title = byId('progress-text').textContent || 'Abrir ejecución en curso';
      runState.dataset.running = 'true'; runState.onclick = () => openView('execute');
    } else {
      runState.hidden = !hasResult; runState.textContent = 'Ver última ejecución';
      runState.title = 'Consultar los resultados guardados'; runState.dataset.running = 'false'; runState.onclick = () => openView('results');
    }
    const caption = byId('map-current-scenario');
    if (caption) caption.textContent = typeof currentExperiment !== 'undefined' && currentExperiment
      ? `Escenario: ${scenarioTitle(currentExperiment)} · ${currentExperiment.label}` : 'Puntos de referencia · ejecuta el estudio para ver rutas.';
  }
  function initializeNavigation() {
    const main = document.querySelector('main'), header = main.querySelector('.topbar'), footer = main.querySelector('footer');
    const welcome = main.querySelector('.welcome'), metrics = main.querySelector('.metrics');
    const geography = byId('geografia'), catalog = byId('catalogo'), data = byId('configuracion');
    const execute = byId('optimizacion'), result = byId('optimization-result');
    views.forEach(view => {
      const panel = makePanel('workspace', view.id);
      panels.set(view.id, panel); main.insertBefore(panel, footer);
    });
    panels.get('overview').append(welcome, metrics);
    const guide = document.createElement('div'); guide.className = 'overview-workflow';
    guide.innerHTML = '<a href="#datos"><span>01</span><div><strong>Prepara los datos</strong><small>Importa o edita la jornada</small></div></a><a href="#ejecutar"><span>02</span><div><strong>Ejecuta el estudio</strong><small>Elige alternativas y sensibilidad</small></div></a><a href="#resultados"><span>03</span><div><strong>Compara y exporta</strong><small>Consulta costos, tiempos y rutas</small></div></a>';
    panels.get('overview').append(guide, catalog);
    panels.get('data').classList.add('data-workspace'); panels.get('data').append(data);
    panels.get('execute').append(execute);
    const resultsView = panels.get('results'); resultsView.classList.add('panel', 'results-workspace');
    resultEmpty = document.createElement('div'); resultEmpty.className = 'results-empty';
    resultEmpty.innerHTML = '<p class="eyebrow">RESULTADOS DEL ESTUDIO</p><h2>Aún no hay una ejecución guardada</h2><p>Prepara tus datos y ejecuta el modelo. Aquí aparecerán la comparación, las gráficas y el detalle de rutas.</p><a class="welcome-link" href="#ejecutar">Configurar y ejecutar</a>';
    resultsView.append(resultEmpty, result); prepareResults(result);
    const mapContext = document.createElement('div'); mapContext.className = 'map-context';
    mapContext.innerHTML = '<p id="map-current-scenario"></p><a href="#resultados/rutas">Volver al detalle de rutas</a>';
    panels.get('map').append(mapContext, geography);
    main.querySelector('.content-grid')?.remove();
    const navigation = document.createElement('div'); navigation.className = 'workspace-navigation';
    primaryTabs = makeTabs(views, 'workspace', 'Secciones de AgroDistribuye', id => showView(id));
    runState = document.createElement('button'); runState.type = 'button'; runState.className = 'workspace-state'; runState.hidden = true;
    navigation.append(primaryTabs, runState); header.after(navigation);
    panels.forEach(panel => main.insertBefore(panel, footer));
    const sidebar = document.querySelector('.sidebar nav');
    sidebar.innerHTML = views.map(view => `<a class="nav-link" data-view="${view.id}" href="#${view.hash}"><svg class="icon" aria-hidden="true"><use href="#icon-${view.icon}"/></svg><span>${view.title}</span></a>`).join('');
    document.addEventListener('click', event => {
      const link = event.target.closest('a[href^="#"]');
      if (!link || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      const hash = link.getAttribute('href').slice(1), [key, sub] = hash.split('/');
      const view = views.find(v => v.hash === key)?.id || aliases[key]; if (!view) return;
      event.preventDefault();
      if (view === 'results' && sub) showResult(subviews.find(v => v.hash === sub)?.id || activeResult, false);
      openView(view);
    });
    window.addEventListener('hashchange', readHash); window.addEventListener('popstate', readHash);
    const observer = new MutationObserver(updateState);
    observer.observe(byId('progress-text'), {childList:true, characterData:true, subtree:true});
    observer.observe(byId('run-optimization'), {attributes:true, attributeFilter:['disabled']});
    new MutationObserver(prepareDataTabs).observe(byId('data-editor'), {childList:true});
    prepareDataTabs();
    window.openWorkspaceView = openView;
    window.onStudyFinished = () => { updateState(); if (activeView === 'execute') openView('results'); };
    window.revealPlanningField = input => {
      const panel = input.closest('[data-editor-panel]'); if (panel) selectData(Number(panel.dataset.editorPanel));
      openView('data');
    };
    window.addEventListener('study-rendered', updateState);
    readHash(); updateState(); document.body.classList.add('tabbed-workspace');
  }
  window.addEventListener('DOMContentLoaded', initializeNavigation);
})();
