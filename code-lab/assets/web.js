/* HTML, CSS and JavaScript in three boxes, running in the frame beside them. */
(function () {
"use strict";

zip.configure({ useWebWorkers: false });
var $ = function (id) { return document.getElementById(id); };

/* An empty editor shows nothing, so the page opens with something that runs. */
var SAMPLE = {
  html:
'<div class="card">\n' +
'  <h1>Hello</h1>\n' +
'  <p>Change anything here and watch the preview.</p>\n' +
'  <button id="btn">Press me</button>\n' +
'  <p id="out"></p>\n' +
'</div>\n',
  css:
'body {\n' +
'  font-family: system-ui, sans-serif;\n' +
'  background: #f4f6f8;\n' +
'  display: grid;\n' +
'  place-items: center;\n' +
'  min-height: 100vh;\n' +
'  margin: 0;\n' +
'}\n' +
'.card {\n' +
'  background: white;\n' +
'  padding: 28px;\n' +
'  border-radius: 14px;\n' +
'  box-shadow: 0 8px 30px rgba(0,0,0,.08);\n' +
'  text-align: center;\n' +
'}\n' +
'button {\n' +
'  font-size: 16px;\n' +
'  padding: 10px 22px;\n' +
'  border: 0;\n' +
'  border-radius: 8px;\n' +
'  background: #2f6f5e;\n' +
'  color: white;\n' +
'}\n',
  js:
'let count = 0;\n' +
'\n' +
'document.getElementById("btn").addEventListener("click", function () {\n' +
'  count = count + 1;\n' +
'  document.getElementById("out").textContent = count + " presses";\n' +
'  console.log("pressed", count);\n' +
'});\n'
};

var state = { tab: "html", auto: true, timer: null };
var code = { html: SAMPLE.html, css: SAMPLE.css, js: SAMPLE.js };

/* ---------- helpers ---------- */
function esc(s) {
  return String(s).replace(/[&<>"']/g, function (c) {
    return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
  });
}
function fmtSize(n) {
  if (n < 1024) return n + " B";
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + " KB";
  return (n / 1024 / 1024).toFixed(1) + " MB";
}
/* A file name a computer will accept, whatever was typed. */
function safeSlug(s) {
  var v = String(s || "").trim().toLowerCase()
    .replace(/[^a-z0-9\- ]+/g, "").replace(/\s+/g, "-")
    .replace(/-+/g, "-").replace(/^-|-$/g, "");
  return v || "my-page";
}
/* If a whole page is pasted rather than just the inside of it, take the part
   between <body> and </body> — otherwise the preview holds a page in a page. */
function bodyOnly(html) {
  var m = /<body[^>]*>([\s\S]*?)<\/body>/i.exec(html);
  return m ? m[1].trim() : html;
}
function say(text, kind) {
  var el = $("note");
  el.textContent = text || "";
  el.className = "note" + (kind ? " " + kind : "");
  el.hidden = !text;
}

/* ---------- the preview ---------- */
/* The code runs in its own frame with the sandbox on, so it cannot reach this
   page, read anything here, or navigate the tab away. Errors and console lines
   are posted back so they can be shown underneath. */
var REPORTER =
  'window.onerror=function(m,s,l,c){parent.postMessage({__ck:1,kind:"error",' +
  'text:m+(l?"  (line "+l+")":"")},"*");return false};' +
  'window.addEventListener("unhandledrejection",function(e){parent.postMessage(' +
  '{__ck:1,kind:"error",text:"Promise: "+(e.reason&&e.reason.message||e.reason)},"*")});' +
  '(function(){var o={};["log","warn","error","info"].forEach(function(k){' +
  'o[k]=console[k];console[k]=function(){try{parent.postMessage({__ck:1,' +
  'kind:k==="log"||k==="info"?"log":k,text:Array.prototype.map.call(arguments,' +
  'function(a){try{return typeof a==="object"?JSON.stringify(a):String(a)}' +
  'catch(e){return String(a)}}).join(" ")},"*")}catch(e){}' +
  'o[k].apply(console,arguments)}})})();';

function buildDoc() {
  return '<!doctype html>\n<html>\n<head>\n<meta charset="utf-8">\n' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">\n' +
    "<style>\n" + code.css + "\n</style>\n</head>\n<body>\n" +
    bodyOnly(code.html) + "\n" +
    "<scr" + "ipt>" + REPORTER + "</scr" + "ipt>\n" +
    "<scr" + "ipt>\ntry{\n" + code.js + "\n}catch(e){window.onerror(e.message)}\n</scr" + "ipt>\n" +
    "</body>\n</html>";
}

var logs = [];
function renderLogs() {
  var box = $("logs");
  if (!logs.length) {
    box.innerHTML = '<p class="empty">console.log(...) shows up here.</p>';
    $("logCount").textContent = "";
    return;
  }
  box.innerHTML = logs.map(function (l) {
    return '<div class="line ' + l.kind + '">' + esc(l.text) + "</div>";
  }).join("");
  box.scrollTop = box.scrollHeight;
  var bad = logs.filter(function (l) { return l.kind === "error"; }).length;
  $("logCount").textContent = bad ? bad + (bad === 1 ? " error" : " errors") : logs.length + "";
  $("logCount").className = "count" + (bad ? " bad" : "");
}
window.addEventListener("message", function (ev) {
  var d = ev.data;
  if (!d || d.__ck !== 1) return;
  logs.push({ kind: d.kind, text: d.text });
  if (logs.length > 200) logs.shift();
  renderLogs();
});

function run() {
  logs = []; renderLogs();
  var frame = $("preview");
  // A brand new frame each time, so nothing from the last run is left behind.
  var fresh = document.createElement("iframe");
  fresh.id = "preview";
  fresh.name = "preview";
  fresh.setAttribute("sandbox", "allow-scripts allow-modals allow-forms allow-popups");
  fresh.setAttribute("title", "Preview");
  frame.parentNode.replaceChild(fresh, frame);
  fresh.srcdoc = buildDoc();
  $("ranAt").textContent = new Date().toLocaleTimeString();
}
function runSoon() {
  if (!state.auto) return;
  clearTimeout(state.timer);
  state.timer = setTimeout(run, 800);
}

/* ---------- editors ---------- */
function showTab(which) {
  state.tab = which;
  var tabs = document.querySelectorAll("[data-tab]");
  for (var i = 0; i < tabs.length; i++) {
    tabs[i].setAttribute("aria-selected", tabs[i].getAttribute("data-tab") === which);
  }
  var ed = $("editor");
  ed.value = code[which];
  ed.setAttribute("spellcheck", "false");
  $("edName").textContent = which === "html" ? "index.html"
    : which === "css" ? "css/style.css" : "js/app.js";
  updateCounts();
}
function updateCounts() {
  var v = code[state.tab];
  $("edSize").textContent = v.split("\n").length + " lines · " + fmtSize(v.length);
}
$("editor").addEventListener("input", function (e) {
  code[state.tab] = e.target.value;
  updateCounts(); save(); runSoon();
});
/* Tab should indent, not jump to the next control — this is a code box. */
$("editor").addEventListener("keydown", function (e) {
  if (e.key === "Tab") {
    e.preventDefault();
    var el = e.target, s = el.selectionStart, t = el.selectionEnd;
    el.value = el.value.slice(0, s) + "  " + el.value.slice(t);
    el.selectionStart = el.selectionEnd = s + 2;
    code[state.tab] = el.value;
    updateCounts(); save(); runSoon();
  }
  if ((e.metaKey || e.ctrlKey) && e.key === "Enter") { e.preventDefault(); run(); }
});
document.querySelector(".tabs").addEventListener("click", function (e) {
  var b = e.target.closest("[data-tab]");
  if (b) showTab(b.getAttribute("data-tab"));
});
$("runBtn").addEventListener("click", run);
$("autoBox").addEventListener("change", function (e) {
  state.auto = e.target.checked;
  if (state.auto) run();
});
$("clearLogs").addEventListener("click", function () { logs = []; renderLogs(); });

/* ---------- keeping the work ---------- */
function save() {
  try { localStorage.setItem("web", JSON.stringify(code)); } catch (e) { /* storage off */ }
}
function load() {
  try {
    var raw = localStorage.getItem("web");
    if (!raw) return;
    var o = JSON.parse(raw);
    if (o && typeof o.html === "string") { code.html = o.html; code.css = o.css; code.js = o.js; }
  } catch (e) { /* keep the sample */ }
}

/* ---------- saving the ZIP ---------- */
var dlNs = null, dlAsked = false;
function downloads() {
  if (dlAsked) return Promise.resolve(dlNs);
  dlAsked = true;
  try {
    if (window.claude && typeof window.claude.use === "function") {
      return Promise.resolve(window.claude.use("downloads")).then(
        function (ns) { dlNs = ns || null; return dlNs; }, function () { return null; });
    }
  } catch (e) { /* not that world */ }
  return Promise.resolve(null);
}
function saveFile(blob, filename) {
  return downloads().then(function (ns) {
    if (ns) return ns.save({ filename: filename, data: blob }).then(
      function () { return { ok: true }; },
      function (e) { return { ok: false, code: (e && e.code) || "unavailable" }; });
    var url = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = url; a.download = filename; a.style.display = "none";
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 40000);
    return { ok: true };
  });
}

/* The page in the ZIP is NOT the preview document. The preview has everything
   inlined; the ZIP has three separate files that point at each other. */
function zipIndexHtml(name) {
  return '<!doctype html>\n<html lang="en">\n<head>\n' +
    '<meta charset="utf-8">\n' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">\n' +
    "<title>" + esc(name) + "</title>\n" +
    '<link rel="stylesheet" href="css/style.css">\n' +
    "</head>\n<body>\n" +
    bodyOnly(code.html) + "\n\n" +
    '<script src="js/app.js"></' + "script>\n" +
    "</body>\n</html>\n";
}

$("zipBtn").addEventListener("click", function () {
  var btn = this, label = btn.textContent;
  var name = safeSlug($("projName").value);
  btn.disabled = true; btn.textContent = "…";
  say("");

  var w = new zip.ZipWriter(new zip.BlobWriter("application/zip"), { level: 9 });
  var files = [
    [name + "/index.html", zipIndexHtml(name)],
    [name + "/css/style.css", code.css],
    [name + "/js/app.js", code.js]
  ];
  var chain = Promise.resolve();
  files.forEach(function (f) {
    chain = chain.then(function () { return w.add(f[0], new zip.TextReader(f[1])); });
  });
  chain.then(function () { return w.close(); })
    .then(function (blob) {
      return saveFile(blob, name + ".zip").then(function (res) {
        if (res.ok) say(name + ".zip saved · " + fmtSize(blob.size) + " · 3 files", "ok");
        else if (res.code === "declined") say("Save cancelled.", "warn");
        else say("Could not save.", "bad");
      });
    })
    .catch(function (e) { say("Could not save: " + (e && e.message || e), "bad"); })
    .then(function () { btn.disabled = false; btn.textContent = label; });
});

/* ---------- start ---------- */
load();
showTab("html");
$("autoBox").checked = state.auto;
renderLogs();
run();
})();
