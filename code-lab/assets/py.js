/* Python notebook. Every cell runs on its own, and they all share one
   namespace — a name made in the first cell is still there in the fifth,
   which is the whole point of working in cells. */
(function () {
"use strict";

zip.configure({ useWebWorkers: false });
var $ = function (id) { return document.getElementById(id); };

/* ---------- the wrapper each cell is run inside ----------
   Two things it has to get right.

   The namespace must survive between runs, and each call to runPythonSource
   gets a fresh module of its own. So the dict is parked in sys.modules, which
   is one object for the whole interpreter and outlives any single run.

   The value of the last line has to be shown, the way a Python prompt shows
   it: somebody who writes `1 + 2` in a cell expects to see 3, and run as a
   plain script they would see nothing at all. The value is reached by slicing
   the source with the line numbers the parser reports — NOT by compiling an
   ast.Expression node, which Brython cannot do (it looks for a `.body` that a
   bare expression does not have).

   Names bridged from JavaScript must not start with two underscores: inside a
   class body Python rewrites them, and `window.__out` quietly becomes
   `window._Cap__out`, which does not exist. */
var BOOT = [
  "import sys, ast, traceback, types",
  "from browser import window",
  "",
  "st = sys.modules.get('_nb')",
  "if st is None:",
  "    st = types.ModuleType('_nb')",
  "    st.g = {'__name__': '__main__'}",
  "    sys.modules['_nb'] = st",
  "",
  "class Cap:",
  "    def __init__(self, kind): self.kind = kind",
  "    def write(self, s): window.pyOut(str(s), self.kind)",
  "    def flush(self): pass",
  "",
  "sys.stdout = Cap('out')",
  "sys.stderr = Cap('err')",
  "g = st.g",
  "g['input'] = window.pyIn",
  "",
  "def show(v):",
  "    if v is not None:",
  "        print(repr(v))",
  "",
  "try:",
  "    src = str(window.pySrc)",
  "    try:",
  "        show(eval(compile(src, '<cell>', 'eval'), g))",
  "    except SyntaxError:",
  "        tree = ast.parse(src)",
  "        lines = src.split('\\n')",
  "        head, tail = src, None",
  "        if tree.body and isinstance(tree.body[-1], ast.Expr):",
  "            node = tree.body[-1]",
  "            start = node.lineno - 1",
  "            end = getattr(node, 'end_lineno', node.lineno)",
  "            tail = '\\n'.join(lines[start:end])",
  "            head = '\\n'.join(lines[:start])",
  "        if head.strip():",
  "            exec(compile(head, '<cell>', 'exec'), g)",
  "        if tail is not None:",
  "            show(eval(compile(tail, '<cell>', 'eval'), g))",
  "except SystemExit:",
  "    pass",
  "except BaseException:",
  "    traceback.print_exc()",
  ""
].join("\n");

var WIPE = "import sys\nsys.modules.pop('_nb', None)\n";

/* ---------- what the notebook opens with ---------- */
var START = [
  "name = \"student\"\nprint(\"hello,\", name)",
  "# this cell still knows the name made above\nprint(name.upper())\n\nfor i in range(1, 6):\n    print(7, \"x\", i, \"=\", 7 * i)",
  "import math\n\nmarks = [72, 88, 95, 61, 79]\nprint(\"total  :\", sum(marks))\nprint(\"highest:\", max(marks))\nround(math.sqrt(2), 6)"
];

/* ---------- state ---------- */
var cells = [], seq = 0, execCount = 0;
var state = { ready: false, busy: false, active: null };

function mk(src) {
  return { id: ++seq, src: src || "", out: [], count: null, ms: null, stdin: "" };
}
function find(id) {
  for (var i = 0; i < cells.length; i++) if (cells[i].id === id) return cells[i];
  return null;
}
function indexOf(id) {
  for (var i = 0; i < cells.length; i++) if (cells[i].id === id) return i;
  return -1;
}

/* ---------- the bridge into Python ---------- */
var sink = null;                       // the cell being run right now
window.pySrc = "";
window.pyOut = function (text, kind) {
  if (sink) sink.out.push({ kind: String(kind), text: String(text) });
};
var stdinLines = [], stdinAt = 0, stdinShort = false;
window.pyIn = function (prompt) {
  if (sink && prompt) sink.out.push({ kind: "out", text: String(prompt) });
  if (stdinAt < stdinLines.length) {
    var line = stdinLines[stdinAt++];
    if (sink) sink.out.push({ kind: "in", text: line + "\n" });
    return line;
  }
  stdinShort = true;
  if (sink) sink.out.push({ kind: "in", text: "\n" });
  return "";
};

/* ---------- helpers ---------- */
function esc(s) {
  return String(s).replace(/[&<>"']/g, function (c) {
    return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
  });
}
/* A traceback names the machinery that ran the code as well as the code
   itself. Only the second part is the reader's business. */
function tidy(text) {
  var keep = [], lines = text.split("\n");
  for (var i = 0; i < lines.length; i++) {
    var l = lines[i];
    if (/^\s*File "[^"]*#nb"/.test(l) || /^\s*File "[^"]*\.html[^"]*"/.test(l)) { i++; continue; }
    keep.push(l.replace(/File "<cell>"/g, "cell"));
  }
  return keep.join("\n").replace(/\n{3,}/g, "\n\n");
}
function wantsInput(src) { return /(^|[^\w.])input\s*\(/.test(src); }
function say(text, kind) {
  var n = $("note");
  n.textContent = text || ""; n.className = "note " + (kind || ""); n.hidden = !text;
}

/* ---------- drawing ---------- */
var PLAY = '<svg viewBox="0 0 12 12" aria-hidden="true"><path d="M2 1l9 5-9 5z"/></svg>';

function cellHtml(c, i) {
  var tag = c.count == null ? "[ ]" : "[" + c.count + "]";
  var body = "";
  if (c.out.length) {
    var html = "", buf = "", kind = c.out[0].kind;
    function flush() {
      if (buf) html += '<span class="' + kind + '">' +
        esc(kind === "err" ? tidy(buf) : buf) + "</span>";
      buf = "";
    }
    c.out.forEach(function (p) {
      if (p.kind !== kind) { flush(); kind = p.kind; }
      buf += p.text;
    });
    flush();
    var bad = c.out.some(function (p) { return p.kind === "err"; });
    body = '<div class="cell-out"><pre>' + html + "</pre>" +
      '<div class="meta"><span>' + (bad ? "error" : "ok") + "</span>" +
      (c.ms == null ? "" : "<span>" + c.ms + " ms</span>") + "</div></div>";
  }
  var inbox = wantsInput(c.src)
    ? '<div class="cell-in"><label for="in' + c.id + '">Input</label>' +
      '<textarea id="in' + c.id + '" data-in="' + c.id + '" spellcheck="false" ' +
      'autocapitalize="off" autocorrect="off">' + esc(c.stdin) + "</textarea>" +
      "<p>one line per input() call</p></div>"
    : "";

  return '<section class="cell' + (state.active === c.id ? " active" : "") + '" data-cell="' + c.id + '">' +
    '<div class="cell-head">' +
      '<button class="play" type="button" data-act="run" data-id="' + c.id + '" ' +
        'aria-label="Run cell ' + (i + 1) + '">' + PLAY + "</button>" +
      '<span class="tag">' + tag + "</span>" +
      '<div class="spacer"></div>' +
      '<button class="icobtn" type="button" data-act="up" data-id="' + c.id + '" aria-label="Move up"' +
        (i === 0 ? " disabled" : "") + ">&#8593;</button>" +
      '<button class="icobtn" type="button" data-act="down" data-id="' + c.id + '" aria-label="Move down"' +
        (i === cells.length - 1 ? " disabled" : "") + ">&#8595;</button>" +
      '<button class="icobtn" type="button" data-act="add" data-id="' + c.id + '" aria-label="Add cell below">+</button>' +
      '<button class="icobtn" type="button" data-act="del" data-id="' + c.id + '" aria-label="Delete cell"' +
        (cells.length === 1 ? " disabled" : "") + ">&#215;</button>" +
    "</div>" +
    '<textarea class="src" data-src="' + c.id + '" spellcheck="false" autocapitalize="off" ' +
      'autocorrect="off" aria-label="Cell ' + (i + 1) + '">' + esc(c.src) + "</textarea>" +
    inbox + body +
  "</section>";
}

/* Redrawing the whole sheet would take the keyboard away mid-typing, so the
   caret is put back where it was. */
function draw() {
  var el = document.activeElement;
  var keepId = el && el.getAttribute && el.getAttribute("data-src");
  var at = keepId ? el.selectionStart : null;

  $("sheet").innerHTML = cells.map(cellHtml).join("");
  $("cellCount").textContent = cells.length + (cells.length === 1 ? " cell" : " cells");
  cells.forEach(function (c) { grow($("sheet").querySelector('[data-src="' + c.id + '"]')); });

  if (keepId) {
    var back = $("sheet").querySelector('[data-src="' + keepId + '"]');
    if (back) { back.focus(); try { back.setSelectionRange(at, at); } catch (e) {} }
  }
  save();
}
function grow(ta) {
  if (!ta) return;
  ta.style.height = "auto";
  ta.style.height = Math.max(64, ta.scrollHeight) + "px";
}

/* ---------- running ---------- */
function runCell(id) {
  var c = find(id);
  if (!c || !state.ready || state.busy) return false;
  state.busy = true;

  c.out = [];
  sink = c;
  stdinLines = c.stdin.length ? c.stdin.replace(/\n$/, "").split("\n") : [];
  stdinAt = 0; stdinShort = false;
  window.pySrc = c.src;

  var t0 = performance.now();
  try {
    window.__BRYTHON__.runPythonSource(BOOT, "nb");
  } catch (e) {
    c.out.push({ kind: "err", text: "Python could not run: " + (e && e.message || e) + "\n" });
  }
  c.ms = Math.round(performance.now() - t0);
  c.count = ++execCount;
  sink = null;
  state.busy = false;

  if (stdinShort) say("This cell called input() more times than the Input box had lines. Empty answers were used.", "warn");
  else say("");

  var bad = c.out.some(function (p) { return p.kind === "err"; });
  draw();
  return !bad;
}

function runAll() {
  if (!state.ready || state.busy) return;
  var i = 0;
  (function step() {
    if (i >= cells.length) return;
    var ok = runCell(cells[i].id);
    i++;
    if (ok) setTimeout(step, 0);        // stop at the first cell that fails
  })();
}

function restart() {
  try { window.__BRYTHON__.runPythonSource(WIPE, "nb"); } catch (e) {}
  execCount = 0;
  cells.forEach(function (c) { c.out = []; c.count = null; c.ms = null; });
  say("Restarted. Every name the cells made is gone.", "ok");
  draw();
}

/* ---------- events ---------- */
$("sheet").addEventListener("click", function (e) {
  var b = e.target.closest("[data-act]");
  if (!b) return;
  var id = parseInt(b.getAttribute("data-id"), 10), i = indexOf(id);
  var act = b.getAttribute("data-act");
  if (act === "run") { state.active = id; runCell(id); }
  else if (act === "add") { cells.splice(i + 1, 0, mk("")); draw(); }
  else if (act === "del") { if (cells.length > 1) { cells.splice(i, 1); draw(); } }
  else if (act === "up") { if (i > 0) { cells.splice(i - 1, 0, cells.splice(i, 1)[0]); draw(); } }
  else if (act === "down") { if (i < cells.length - 1) { cells.splice(i + 1, 0, cells.splice(i, 1)[0]); draw(); } }
});

$("sheet").addEventListener("input", function (e) {
  var el = e.target;
  var sid = el.getAttribute("data-src");
  if (sid) {
    var c = find(parseInt(sid, 10));
    if (!c) return;
    var had = wantsInput(c.src);
    c.src = el.value;
    grow(el);
    save();
    if (had !== wantsInput(c.src)) draw();   // the Input box appears or goes
    return;
  }
  var iid = el.getAttribute("data-in");
  if (iid) { var d = find(parseInt(iid, 10)); if (d) { d.stdin = el.value; save(); } }
});

$("sheet").addEventListener("focusin", function (e) {
  var sid = e.target.getAttribute && e.target.getAttribute("data-src");
  if (!sid) return;
  var id = parseInt(sid, 10);
  if (state.active === id) return;
  state.active = id;
  var old = $("sheet").querySelector(".cell.active");
  if (old) old.classList.remove("active");
  var now = $("sheet").querySelector('[data-cell="' + id + '"]');
  if (now) now.classList.add("active");
});

/* Python lives on indentation, so Tab inserts spaces rather than jumping to
   the next control. */
$("sheet").addEventListener("keydown", function (e) {
  var el = e.target;
  if (!el.getAttribute || !el.getAttribute("data-src")) return;
  if (e.key === "Tab") {
    e.preventDefault();
    var s = el.selectionStart, t = el.selectionEnd;
    el.value = el.value.slice(0, s) + "    " + el.value.slice(t);
    el.selectionStart = el.selectionEnd = s + 4;
    var c = find(parseInt(el.getAttribute("data-src"), 10));
    if (c) c.src = el.value;
    grow(el); save();
  }
  if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
    e.preventDefault();
    runCell(parseInt(el.getAttribute("data-src"), 10));
  }
});

$("addBtn").addEventListener("click", function () { cells.push(mk("")); draw(); });
$("runAllBtn").addEventListener("click", runAll);
$("restartBtn").addEventListener("click", restart);

/* ---------- keeping the work ---------- */
function save() {
  try {
    localStorage.setItem("nb", JSON.stringify(cells.map(function (c) {
      return { src: c.src, stdin: c.stdin };
    })));
  } catch (e) { /* private window, or storage switched off */ }
}
function load() {
  try {
    var raw = localStorage.getItem("nb");
    if (!raw) return null;
    var a = JSON.parse(raw);
    if (!a || !a.length) return null;
    return a.map(function (o) { var c = mk(String(o.src || "")); c.stdin = String(o.stdin || ""); return c; });
  } catch (e) { return null; }
}

/* ---------- taking it away ---------- */
/* A .py file on its own cannot be saved from inside a preview — only a short
   list of file types can — so it travels in a ZIP, which can. The `# %%`
   markers are what every editor reads back as cell boundaries. */
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
$("saveBtn").addEventListener("click", function () {
  var btn = this, label = btn.textContent;
  btn.disabled = true; btn.textContent = "…";
  var name = ($("fileName").value || "notebook").replace(/[^A-Za-z0-9_\-]/g, "") || "notebook";
  var text = cells.map(function (c) { return "# %%\n" + c.src.replace(/\s*$/, "") + "\n"; }).join("\n");
  var w = new zip.ZipWriter(new zip.BlobWriter("application/zip"), { level: 9 });
  w.add(name + ".py", new zip.TextReader(text))
    .then(function () { return w.close(); })
    .then(function (blob) { return saveFile(blob, name + ".zip"); })
    .then(function (res) {
      if (res.ok) say(name + ".zip saved.", "ok");
      else if (res.code === "declined") say("Save cancelled.", "warn");
      else say("Could not save.", "bad");
    })
    .catch(function (e) { say("Could not save: " + (e && e.message || e), "bad"); })
    .then(function () { btn.disabled = false; btn.textContent = label; });
});

/* ---------- start ---------- */
cells = load() || START.map(mk);
draw();

try {
  window.brython({ debug: 0, indexedDB: false });
  state.ready = true;
  $("ready").textContent = "ready";
  $("ready").className = "pill on";
} catch (e) {
  $("ready").textContent = "not running";
  $("ready").className = "pill off";
  say("Python could not start: " + (e && e.message || e), "bad");
}
})();
