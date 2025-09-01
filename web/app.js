// API na istom originu (8080/9090 – radi automatski)
const API = window.location.origin;

const $  = s => document.querySelector(s);
const $$ = s => document.querySelectorAll(s);
let chart;

// ---------- Auto-datum (local now) ----------
(function setDefaultDateTime(){
  const dt = document.querySelector('input[name="date"]');
  if (!dt) return;
  const isoLocal = new Date(Date.now() - new Date().getTimezoneOffset()*60000).toISOString().slice(0,16);
  if (!dt.value) dt.value = isoLocal;
})();

// ---------- Heuristika za kategorije ----------
const CATEGORY_KEYWORDS = {
  FOOD:        ["pekara","burger","pizza","restoran","kafic","kafana","fast","grill","food","bakery","market","super","maxi","idea","univer"],
  DAIRY:       ["mleko","jogurt","sir","kajmak","kefir","mozzarella","pavlaka","butter","milky"],
  DELIKATES:   ["salama","prsut","kulen","sunka","delikates","suvomesn"],
  HYGIENE:     ["dm","lilly","drog","toalet","sapun","samp","detergent","maramice","higij"],
  PHARMACY:    ["apoteka","lek","pharma","galen","hemofarm","andol","analgin","parac"],
  ENTERTAINMENT:["netflix","spotify","hbo","playstation","game","koncert","bioskop","cinema","movies"],
  BILLS:       ["eps","struja","infostan","voda","grejanje","telekom","sbb","mts","a1","telenor","internet","racun"],
  TRANSPORT:   ["gsp","bus","tramvaj","karta","taksi","uber","bolt","voz","suburban"],
  FUEL:        ["mol","omv","nis","gazprom","eurodiesel","benzin","gorivo","pumpa"],
  PARKING:     ["parking","parker","parkin","parkomat"],
  SHOPPING:    ["zara","hm","nike","adidas","shop","butik","fashion","rider","decathlon","ikea"],
  PETS:        ["pet","zoo","vet","granule","macka","pas"],
  EDUCATION:   ["kurs","udemy","coursera","katedra","prijava","skolarina","ispit"],
  HEALTH:      ["klinika","bolnica","laboratorija","ultra","stomat","zubar","pregled"],
  OTHER:       ["kiosk","prodavn","usluga","servis"]
};
function guessCategoryFromText(text) {
  const t = (text||"").toLowerCase();
  for (const [cat, words] of Object.entries(CATEGORY_KEYWORDS)) {
    if (words.some(w => t.includes(w))) return cat;
  }
  return "OTHER";
}

// ---------- Helpers ----------
function toast(m){ const el = $("#ocrHint"); if(!el) return; el.textContent = m; setTimeout(()=>el.textContent="",3000); }
async function fetchJSON(url, opts={}) {
  const r = await fetch(url,{ headers:{'Content-Type':'application/json'}, ...opts });
  if(!r.ok){ let msg = ""; try{ msg = await r.text(); }catch{} throw new Error(msg || r.statusText); }
  return r.status === 204 ? null : r.json();
}

// ---------- UI load ----------
async function loadExpenses(){
  const list = await fetchJSON(`${API}/api/expenses`);
  const q = ($("#search").value||"").toLowerCase();
  const filtered = list.filter(x => (x.name + x.category).toLowerCase().includes(q));
  const tbody = $("#table tbody"); tbody.innerHTML="";
  $("#empty").style.display = filtered.length ? "none" : "block";
  for(const e of filtered){
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${e.id}</td>
      <td>${e.name}</td>
      <td>${e.category}</td>
      <td>${(+e.amount).toFixed(2)}</td>
      <td>${new Date(e.date).toLocaleString()}</td>
      <td><button class="del" data-id="${e.id}">Obriši</button></td>`;
    tbody.appendChild(tr);
  }
  $$(".del").forEach(b => b.onclick = async ()=>{ await fetchJSON(`${API}/api/expenses/${b.dataset.id}`,{method:"DELETE"}); await loadAll(); });
}
async function loadStats(){
  const data = await fetchJSON(`${API}/api/stats`);
  const labels = Object.keys(data), values = Object.values(data);
  if(chart) chart.destroy();
  chart = new Chart($("#pie"), { type:"pie", data:{ labels, datasets:[{ data: values }] },
    options:{ plugins:{ legend:{ position:'bottom', labels:{ color:getComputedStyle(document.body).getPropertyValue('--fg') } } } } });
}
async function loadAll(){ await loadExpenses(); await loadStats(); }

// ---------- Submit ----------
$("#expenseForm").onsubmit = async (e)=>{
  e.preventDefault();
  $("#saveBtn").disabled = true;
  try{
    const fd = new FormData(e.target);
    const payload = {
      name: (fd.get("name")||"").trim(),
      category: fd.get("category"),
      amount: String(fd.get("amount")), // backend podržava i "120,50"
      date: fd.get("date") ? new Date(fd.get("date")).toISOString() : new Date().toISOString()
    };
    if(!payload.name || !payload.category || !payload.amount) { toast("Popuni sva polja."); return; }
    await fetchJSON(`${API}/api/expenses`, { method:"POST", body: JSON.stringify(payload) });
    e.target.reset(); setDefaultDateTimeAgain(); toast("Sačuvano ✔"); await loadAll();
  }catch(err){ console.error(err); toast("Greška pri čuvanju."); }
  finally{ $("#saveBtn").disabled = false; }
};
function setDefaultDateTimeAgain(){
  const dt = document.querySelector('input[name="date"]');
  if (!dt) return;
  const isoLocal = new Date(Date.now() - new Date().getTimezoneOffset()*60000).toISOString().slice(0,16);
  dt.value = isoLocal;
}

$("#search").oninput = loadExpenses;
$("#themeToggle").onclick = ()=> document.body.classList.toggle("light");

// ---------- OCR iz slike (file input) ----------
$("#receiptInput").onchange = async (ev)=>{
  const file = ev.target.files[0]; if(!file) return;
  toast("Čitam račun…");
  try{
    const { data:{ text } } = await Tesseract.recognize(file, 'eng');
    fillFromText(text);
    toast("Popunjeno iz računa ✔ (proveri)");
  }catch(e){ console.error(e); toast("Nisam uspeo da pročitam."); }
  ev.target.value = "";
};

// ---------- QR skeniranje (kamera + jsQR) ----------
let qrStream, qrTimer;
const qrModal = $("#qrModal"), qrVideo = $("#qrVideo"), qrCanvas = $("#qrCanvas");
$("#qrBtn").onclick = async ()=>{
  try{
    qrStream = await navigator.mediaDevices.getUserMedia({ video:{ facingMode: { ideal: "environment" } }, audio:false });
    qrVideo.srcObject = qrStream; qrModal.style.display = "flex";
    const ctx = qrCanvas.getContext("2d");
    qrTimer = setInterval(()=>{
      if(!qrVideo.videoWidth) return;
      qrCanvas.width = qrVideo.videoWidth; qrCanvas.height = qrVideo.videoHeight;
      ctx.drawImage(qrVideo,0,0,qrCanvas.width,qrCanvas.height);
      const imageData = ctx.getImageData(0,0,qrCanvas.width,qrCanvas.height);
      const code = jsQR(imageData.data, imageData.width, imageData.height);
      if(code && code.data){
        try{
          fillFromQR(code.data);
          toast("QR prepoznat ✔");
        }catch(e){ console.error(e); }
        closeQR();
      }
    }, 300);
  }catch(err){ console.error(err); toast("Kamera nije dostupna (dozvole?)"); }
};
$("#closeQR").onclick = closeQR;
function closeQR(){
  qrModal.style.display = "none";
  if(qrTimer) { clearInterval(qrTimer); qrTimer=null; }
  if(qrStream){ qrStream.getTracks().forEach(t=>t.stop()); qrStream=null; }
}

// ---------- Parsiranje teksta (OCR/QR) ----------
function fillFromText(text){
  // amount = poslednji broj sa 2 decimale
  const m = text.replace(',', '.').match(/\d+\.\d{2}/g);
  if(m && m.length){ $('input[name="amount"]').value = parseFloat(m[m.length-1]).toFixed(2); }
  // name = prva linija
  const first = (text.split('\n').map(s=>s.trim()).filter(Boolean)[0]||"").slice(0,40);
  $('input[name="name"]').value = first || $('input[name="name"]').value || "Račun";
  // category heuristic
  $('#category').value = guessCategoryFromText(text);
}
function fillFromQR(data){
  // Ako je URL → uzmi host kao merchant
  try{
    const u = new URL(data);
    const host = u.hostname.replace(/^www\./,'');
    $('input[name="name"]').value = host.toUpperCase();
    // pokušaj da izvučeš iznos iz query parametara
    const qvals = [...u.searchParams.values()].join(' ');
    if(qvals){
      const m = qvals.replace(',', '.').match(/\d+\.\d{2}/g);
      if(m && m.length) $('input[name="amount"]').value = parseFloat(m[m.length-1]).toFixed(2);
    }
    $('#category').value = guessCategoryFromText(host);
    return;
  }catch(_) { /* nije URL */ }

  // Nije URL, plain tekst → isto kao OCR
  fillFromText(data);
}

// Init
loadAll();
