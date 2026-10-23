// Lógica del Gestor de Clips: llama a la API y pinta los resultados
const $ = s => document.querySelector(s);
let archivo = null, actuales = [];

// ---------- Pestañas ----------
document.querySelectorAll('.tab:not(.modo)').forEach(t => t.onclick = () => {
  document.querySelectorAll('.tab:not(.modo)').forEach(x => x.classList.toggle('on', x === t));
  $('#detectar').hidden = t.dataset.tab !== 'detectar';
  $('#guardados').hidden = t.dataset.tab !== 'guardados';
  if (t.dataset.tab === 'guardados') cargarGuardados();
});

// ---------- Modo: link o archivo ----------
let modo = 'link';
document.querySelectorAll('.modo').forEach(b => b.onclick = () => {
  modo = b.dataset.modo;
  document.querySelectorAll('.modo').forEach(x => x.classList.toggle('on', x === b));
  $('#modoLink').hidden = modo !== 'link';
  $('#drop').hidden = modo !== 'archivo';
});

// ---------- Archivo ----------
const drop = $('#drop');
drop.onclick = () => $('#file').click();
$('#file').onchange = e => elegir(e.target.files[0]);
drop.ondragover = e => { e.preventDefault(); drop.classList.add('over'); };
drop.ondragleave = () => drop.classList.remove('over');
drop.ondrop = e => { e.preventDefault(); drop.classList.remove('over'); elegir(e.dataTransfer.files[0]); };
function elegir(f){
  if(!f) return;
  archivo = f;
  $('#dropText').innerHTML = `📄 <b>${esc(f.name)}</b> · ${(f.size/1024/1024).toFixed(2)} MB — clic para cambiar`;
}
$('#sens').oninput = e => $('#sensVal').textContent = Number(e.target.value).toFixed(1);

// ---------- Analizar ----------
$('#btn').onclick = async () => {
  const url = $('#vodUrl').value.trim();
  if(modo === 'link' && !url){ estado('Pega el link del VOD', true); return; }
  if(modo === 'archivo' && !archivo){ estado('Primero elige el chat.json', true); return; }
  const q = new URLSearchParams({
    streamer: $('#streamer').value, sensibilidad: $('#sens').value,
    maxClips: $('#max').value, guardar: $('#guardar').checked
  });
  $('#btn').disabled = true;
  estado(modo === 'link' ? 'Descargando el chat de Twitch y analizando… esto puede tardar un par de minutos ⏳'
                         : 'Analizando el chat… (en streams largos tarda unos segundos)');
  $('#resultados').innerHTML = '';
  try{
    let r;
    if(modo === 'link'){
      q.set('url', url);
      r = await fetch('/api/clips/detectar-vod?' + q, { method:'POST' });
    }else{
      const fd = new FormData(); fd.append('chat', archivo);
      r = await fetch('/api/clips/detectar?' + q, { method:'POST', body: fd });
    }
    const data = await r.json();
    if(!r.ok) throw new Error(data.message || data.error || ('Error ' + r.status));
    actuales = data;
    if(!data.length){ estado('No se encontraron momentos. Prueba bajando la sensibilidad.'); return; }
    estado(`Se encontraron ${data.length} momentos. Haz clic en un video para verlo en ese minuto.`);
    pintar(data, $('#resultados'));
  }catch(e){ estado('Error: ' + e.message, true); }
  finally{ $('#btn').disabled = false; }
};

// ---------- Guardados ----------
async function cargarGuardados(){
  $('#status2').textContent = 'Cargando…';
  try{
    const r = await fetch('/api/clips'); const data = await r.json();
    const conVod = data.filter(c => c.inicioSegundos != null);
    $('#status2').textContent = conVod.length ? `${conVod.length} clips guardados` : '';
    if(!conVod.length){ $('#lista').innerHTML = '<div class="empty">Aún no hay clips guardados.</div>'; return; }
    pintar(conVod.sort((a,b)=>(b.puntaje||0)-(a.puntaje||0)), $('#lista'), true);
  }catch(e){ $('#status2').textContent = 'Error: ' + e.message; }
}

// ---------- Tarjetas ----------
function pintar(clips, cont, guardados=false){
  const max = Math.max(...clips.map(c => c.puntaje || 0), 1);
  cont.innerHTML = '';
  clips.forEach((c, i) => {
    const el = document.createElement('div');
    el.className = 'clip ' + (c.estado === 'aprobado' || c.estado === 'descartado' ? c.estado : '');
    const dur = (c.finSegundos ?? 0) - (c.inicioSegundos ?? 0);
    el.innerHTML = `
      <div class="player"><div class="play"><big>▶</big>Ver desde ${hms(c.inicioSegundos)}</div></div>
      <div class="body">
        <div class="top">
          <span class="time">${hms(c.inicioSegundos)} – ${hms(c.finSegundos)}</span>
          ${c.estado==='aprobado'||c.estado==='descartado' ? `<span class="badge ${c.estado}">${c.estado}</span>` : `<span class="score">🔥 ${c.puntaje ?? '-'}</span>`}
        </div>
        <div class="bar"><i style="width:${Math.round((c.puntaje||0)/max*100)}%"></i></div>
        <div class="meta">${c.mensajesEnPico ?? 0} mensajes · ${c.metadatos?.autoresUnicos ?? '?'} personas · ${dur}s</div>
        <div class="chat">${(c.mensajesDestacados||[]).map(m=>`<div>${esc(m)}</div>`).join('') || '<div>—</div>'}</div>
        <div class="actions">
          <button class="ok">✔ Aprobar</button>
          <button class="no">✖ Descartar</button>
          <a href="${c.urlVideo}" target="_blank" rel="noopener">Twitch ↗</a>
        </div>
      </div>`;
    el.querySelector('.player').onclick = function(){
      if(this.querySelector('iframe')) return;
      const t = c.inicioSegundos||0;
      const time = `${Math.floor(t/3600)}h${Math.floor(t%3600/60)}m${t%60}s`;
      this.innerHTML = `<iframe allowfullscreen src="https://player.twitch.tv/?video=v${c.vodId}&time=${time}&parent=${location.hostname}&autoplay=true"></iframe>`;
    };
    el.querySelector('.ok').onclick = () => cambiar(c, 'aprobado', el, guardados);
    el.querySelector('.no').onclick = () => cambiar(c, 'descartado', el, guardados);
    cont.appendChild(el);
  });
}

async function cambiar(c, estado, el, guardados){
  try{
    let r;
    if(c.id){
      r = await fetch(`/api/clips/${c.id}/estado`, { method:'PATCH', headers:{'Content-Type':'application/json'}, body: JSON.stringify({estado}) });
    }else if(estado === 'aprobado'){
      // No estaba guardado: se guarda ahora como aprobado
      r = await fetch('/api/clips', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({...c, estado}) });
    }else{
      el.classList.add('descartado'); return; // descartar sin guardar: solo se oculta
    }
    const data = await r.json();
    if(!r.ok) throw new Error(data.message || ('Error ' + r.status));
    Object.assign(c, data);
    el.classList.remove('aprobado','descartado'); el.classList.add(estado);
    el.querySelector('.top span:last-child').outerHTML = `<span class="badge ${estado}">${estado}</span>`;
  }catch(e){ alert('No se pudo guardar: ' + e.message); }
}

// ---------- Utilidades ----------
function hms(s){ s = s||0; return `${Math.floor(s/3600)}:${String(Math.floor(s%3600/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`; }
function esc(t){ return String(t).replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m])); }
function estado(t, err=false){ const s=$('#status'); s.textContent=t; s.classList.toggle('err', err); }
