/* ZipKaro — every tool runs in the browser. Nothing is uploaded, ever. */
(function () {
"use strict";

/* zip.js builds Web Workers out of blob: URLs. Those are blocked when the page
   is opened from a phone's Files app (file://) and inside sandboxed previews —
   the two places this file most needs to work. So: main thread. zip.js still
   awaits between chunks, and every loop below yields, so the screen keeps
   responding while it works. */
zip.configure({ useWebWorkers: false });

/* ---------- limits. A hostile ZIP must not be able to fill up the phone. --------- */
var LIMIT = {
  entries: 5000,                      // more files than any real archive holds
  totalOut: 2 * 1024 * 1024 * 1024,   // 2 GB once opened — refuse
  warnOut: 400 * 1024 * 1024,         // 400 MB — warn, but carry on
  ratio: 150                          // 150x its own size is a bomb, not a file
};

/* ---------- every word the page says ---------- */
var WORDS = {
en: {
  choose: "Choose files", chooseOne: "Choose a ZIP file", orDrag: "or drag them here",
  working: "Working…", packing: "Packing", opening: "Opening", done: "Done",
  remove: "Remove", download: "Download", downloadAll: "Download all as one ZIP",
  pickFirst: "Choose a file first.",
  size: "Size", ratio: "Smaller by", was: "Was", now: "Now",
  parts: "Parts", locked: "locked", entriesN: "Files inside",
  contents: "What is inside", encrypted: "Needs a password", modified: "Changed",
  andMore: "more files not listed",

  t_create: "Create ZIP", t_extract: "Extract ZIP", t_compress: "Compress ZIP",
  t_merge: "Merge ZIPs", t_split: "Split ZIP", t_protect: "Password ZIP", t_info: "ZIP info",
  d_create: "Pick your files and get one ZIP back.",
  d_extract: "See what is inside a ZIP, and save the files you want.",
  d_compress: "Try to shrink a ZIP you already have.",
  d_merge: "Turn several ZIPs into one.",
  d_split: "Break a big ZIP into pieces small enough to send.",
  d_protect: "Make a ZIP that needs a password to open.",
  d_info: "Check what a ZIP holds before you trust it — sizes, dates, and whether it is locked.",


  zipName: "Name for the ZIP", level: "How hard to squeeze",
  lvFast: "Fast", lvNormal: "Normal", lvSmall: "Smallest",
  password: "Password", passwordAgain: "Type it again",
  passwordHelp: "This password never leaves your device. If you forget it, nobody can open the file — not us, not anyone.",
  passwordNeed: "Enter a password.", passwordShort: "Use at least 4 characters.",
  passwordMatch: "The two passwords are not the same.",
  passwordFor: "This ZIP needs a password",
  unlock: "Open it",
  passwordWrong: "That password is not right. Try again.",

  dupes: "If two files have the same name",
  dupeRename: "Keep both", dupeReplace: "Keep the newer", dupeSkip: "Keep the first",
  renamedN: "Renamed", skippedN: "Skipped",

  splitBy: "How to split", splitFiles: "Into working ZIPs", splitBytes: "Into raw pieces",
  splitFilesHelp: "Each piece is a normal ZIP you can open on its own. Best choice.",
  splitBytesHelp: "Cuts the file into equal pieces. They must be joined back before opening.",
  partSize: "Size of each piece (MB)",
  joinTitle: "How to join the pieces back",
  joinWin: "On Windows, in the folder holding the pieces:",
  joinMac: "On Mac or Linux:",
  tooBigEntry: "One file inside is bigger than the piece size, so it cannot be split this way. Use a bigger piece size, or choose “Into raw pieces”.",

  errNotZip: "This does not look like a ZIP file, or it is damaged.",
  errEmpty: "This ZIP has nothing in it.",
  errBomb: "This ZIP is unsafe. It is small, but it would open into something enormous — a known trick for filling up a device. Nothing was opened.",
  errTooMany: "This ZIP holds too many files to open safely here.",
  errTooBig: "This ZIP would open into more than 2 GB. That is too much for a phone.",
  warnBig: "This is large. Opening a file may take a while and use a lot of memory.",
  errMem: "Your device ran out of memory. Try a smaller file, or close some other apps and tabs.",
  errGeneric: "Something went wrong while working on this file.",
  errLocked: "This ZIP is password protected. Open it with the Extract tool, which will ask for the password.",
  slipCleaned: "Some names inside this ZIP tried to write outside their own folder — a trick called ZIP Slip. Those names have been cleaned, and the files are listed below under safe names.",

  noSmaller: "This ZIP did not get smaller. What is inside it is already compressed — photos, video and music cannot be squeezed much further. Your original is still the best version.",
  gotSmaller: "Saved space.",
  declined: "Save cancelled.",
  badExt: "This preview can only save certain file types. Use “Download all as one ZIP” instead, or open this page on the real site.",
  listOnly: "Tap Download beside any file to save just that one. Nothing is unpacked until you ask, so even a very large ZIP opens straight away."
}
};
var lang = "en";
var S = WORDS.en;
function t(k) { return S[k] || WORDS.en[k] || k; }

/* ---------- tools ---------- */
var TOOLS = [
  { id: "create",   multi: true,  accept: "" },
  { id: "extract",  multi: false, accept: ".zip" },
  { id: "compress", multi: false, accept: ".zip" },
  { id: "merge",    multi: true,  accept: ".zip" },
  { id: "split",    multi: false, accept: ".zip" },
  { id: "protect",  multi: true,  accept: "" },
  { id: "info",     multi: false, accept: ".zip" }
];
/* One drawn shape per tool, defined once at the top of the page. Emoji were a
   different picture on every phone — the clamp for "compress" came out as a
   grey blob on most Androids — and they could not take the page's colours. */
function icon(id, cls) {
  return '<svg class="ic' + (cls ? " " + cls : "") + '" aria-hidden="true">' +
    '<use href="#i-' + id + '"/></svg>';
}

var $ = function (id) { return document.getElementById(id); };
var state = { tool: "create", files: [], busy: false };

/* ---------- small helpers ---------- */
function fmtSize(n) {
  if (n === 0) return "0 B";
  if (n == null || isNaN(n)) return "—";
  var u = ["B", "KB", "MB", "GB"], i = 0, v = n;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return (v >= 100 || i === 0 ? Math.round(v) : v.toFixed(1)) + " " + u[i];
}
function esc(s) {
  return String(s).replace(/[&<>"']/g, function (c) {
    return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
  });
}
/* A name inside a ZIP is written by whoever made the ZIP. It can say
   "../../something" and climb out of the folder it belongs in — the ZIP Slip
   trick. Nothing that comes from inside an archive is trusted as a path. */
function safeName(raw) {
  var n = String(raw == null ? "" : raw).replace(/\\/g, "/");
  n = n.replace(/^[A-Za-z]:/, "").replace(/^\/+/, "");
  n = n.split("/").filter(function (p) {
    return p && p !== "." && p !== "..";
  }).join("/");
  n = n.replace(/[\u0000-\u001F\u007F]/g, "");   // nulls and control characters
  return n || "file";
}
function baseName(p) { var a = safeName(p).split("/"); return a[a.length - 1] || "file"; }
function stripZip(name) { return String(name).replace(/\.zip$/i, "") || "archive"; }
/* Let the browser paint between heavy steps, so the screen never looks frozen. */
function breathe() { return new Promise(function (r) { setTimeout(r, 0); }); }

/* ---------- saving a file ---------- */
/* Two worlds. On a real site — or this page saved onto the phone — an ordinary
   link does it. Inside the claude.ai preview those links are inert, and the
   host hands the file over instead. Ask once, then remember the answer. */
var dlNs = null, dlAsked = false;
function downloads() {
  if (dlAsked) return Promise.resolve(dlNs);
  dlAsked = true;
  try {
    if (window.claude && typeof window.claude.use === "function") {
      return Promise.resolve(window.claude.use("downloads")).then(function (ns) {
        dlNs = ns || null; return dlNs;
      }, function () { dlNs = null; return null; });
    }
  } catch (e) { /* not that world */ }
  return Promise.resolve(null);
}
function saveFile(blob, filename) {
  return downloads().then(function (ns) {
    if (ns) {
      return ns.save({ filename: filename, data: blob }).then(
        function () { return { ok: true }; },
        function (e) { return { ok: false, code: (e && e.code) || "unavailable" }; }
      );
    }
    var url = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = url; a.download = filename; a.style.display = "none";
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 40000);
    return { ok: true };
  });
}
function saveNote(res) {
  if (res.ok) return "";
  if (res.code === "declined") return t("declined");
  if (res.code === "rejected_extension" || res.code === "extension_not_enabled") return t("badExt");
  return t("errGeneric");
}

/* ---------- screen ---------- */
function showErr(msg) { var e = $("err"); e.textContent = msg; e.hidden = false; }
function clearErr() { $("err").hidden = true; }
function setProg(on, label, pct) {
  $("prog").hidden = !on;
  if (!on) return;
  $("progLabel").textContent = label || t("working");
  var p = Math.max(0, Math.min(100, Math.round(pct || 0)));
  $("progPct").textContent = p + "%";
  $("progFill").style.width = p + "%";
}
function busy(on) {
  state.busy = on;
  $("goBtn").disabled = on || state.files.length === 0;
  $("pickBtn").disabled = on;
}

function renderTools() {
  $("tools").innerHTML = TOOLS.map(function (tool) {
    return '<button class="tool" type="button" data-tool="' + tool.id + '" aria-pressed="' +
      (tool.id === state.tool) + '">' + icon(tool.id) +
      '<span class="nm">' + esc(t("t_" + tool.id)) + "</span></button>";
  }).join("");
}
function renderFiles() {
  $("fileList").innerHTML = state.files.map(function (f, i) {
    return '<li><span class="fname">' + esc(f.name) + '</span><span class="fsize">' +
      fmtSize(f.size) + '</span><button class="fx" type="button" data-rm="' + i +
      '" aria-label="' + esc(t("remove")) + '">×</button></li>';
  }).join("");
  $("goBtn").disabled = state.busy || state.files.length === 0;
}

function field(label, inner, help) {
  return '<div class="field"><label>' + esc(label) + "</label>" + inner +
    (help ? '<span class="help">' + esc(help) + "</span>" : "") + "</div>";
}
function renderOpts() {
  var o = $("opts"), tl = state.tool, h = "";
  if (tl === "create" || tl === "protect") {
    h += field(t("zipName"), '<input type="text" id="optName" value="archive.zip" spellcheck="false">');
  }
  if (tl === "create" || tl === "compress" || tl === "protect") {
    h += field(t("level"),
      '<div class="radios">' +
      '<label><input type="radio" name="lv" value="1"><span>' + esc(t("lvFast")) + "</span></label>" +
      '<label><input type="radio" name="lv" value="6" checked><span>' + esc(t("lvNormal")) + "</span></label>" +
      '<label><input type="radio" name="lv" value="9"><span>' + esc(t("lvSmall")) + "</span></label>" +
      "</div>");
  }
  if (tl === "protect") {
    h += field(t("password"), '<input type="password" id="optPw" autocomplete="new-password">', t("passwordHelp"));
    h += field(t("passwordAgain"), '<input type="password" id="optPw2" autocomplete="new-password">');
  }
  if (tl === "merge") {
    h += field(t("zipName"), '<input type="text" id="optName" value="merged.zip" spellcheck="false">');
    h += field(t("dupes"),
      '<div class="radios">' +
      '<label><input type="radio" name="dup" value="rename" checked><span>' + esc(t("dupeRename")) + "</span></label>" +
      '<label><input type="radio" name="dup" value="replace"><span>' + esc(t("dupeReplace")) + "</span></label>" +
      '<label><input type="radio" name="dup" value="skip"><span>' + esc(t("dupeSkip")) + "</span></label>" +
      "</div>");
  }
  if (tl === "split") {
    h += field(t("splitBy"),
      '<div class="radios">' +
      '<label><input type="radio" name="sm" value="files" checked><span>' + esc(t("splitFiles")) + "</span></label>" +
      '<label><input type="radio" name="sm" value="bytes"><span>' + esc(t("splitBytes")) + "</span></label>" +
      "</div>", t("splitFilesHelp"));
    h += field(t("partSize"), '<input type="number" id="optPart" value="25" min="1" max="2000" step="1">');
  }
  o.innerHTML = h;
  var sm = o.querySelectorAll('input[name="sm"]');
  for (var i = 0; i < sm.length; i++) {
    sm[i].addEventListener("change", function () {
      var help = o.querySelector(".field .help");
      if (help) help.textContent = radio("sm") === "files" ? t("splitFilesHelp") : t("splitBytesHelp");
    });
  }
}
function radio(name) {
  var el = document.querySelector('input[name="' + name + '"]:checked');
  return el ? el.value : null;
}
function val(id, dflt) { var el = $(id); return el ? el.value : dflt; }

function renderTool() {
  var tool = TOOLS.filter(function (x) { return x.id === state.tool; })[0];
  $("toolTitle").textContent = t("t_" + tool.id);
  $("toolDesc").textContent = t("d_" + tool.id);
  $("goBtn").textContent = t("t_" + tool.id);
  $("fileInput").multiple = tool.multi;
  $("fileInput").accept = tool.accept;
  $("pickBtn").textContent = tool.multi ? t("choose") : t("chooseOne");
  $("dropHint").textContent = t("orDrag");
  renderOpts(); renderFiles(); clearErr();
  $("result").hidden = true; setProg(false);
}

/* ---------- reading a ZIP safely ---------- */
/* zip.js refuses outright to list an archive holding a name like "../../thing".
   Refusing is right, but on its own it would also mean the reader cannot open a
   file that 7-Zip opens fine. So the name is cleaned first, through the
   library's own hook, and the library's check then passes by itself. The
   cleaning is ours, the enforcement stays the library's, and the reader is told
   their archive carried names like that. */
function openZip(file, password) {
  var box = { cleaned: 0 };
  var opts = {
    filenameValidation: "strict",
    normalizeFilename: function (name) {
      var s = safeName(name);
      if (s !== name) box.cleaned++;
      return s;
    }
  };
  if (password) opts.password = password;
  var reader = new zip.ZipReader(new zip.BlobReader(file), opts);
  return reader.getEntries().then(function (entries) {
    return { reader: reader, entries: entries, cleaned: box.cleaned };
  });
}
/* Whether a ZIP is safe to open is decided BEFORE anything is opened. A ZIP
   bomb is 42 KB on disk and gigabytes once unpacked; the only defence is to
   read the list of contents first and refuse. */
function guard(entries, fileSize) {
  if (!entries.length) return { stop: t("errEmpty") };
  if (entries.length > LIMIT.entries) return { stop: t("errTooMany") };
  var total = 0;
  for (var i = 0; i < entries.length; i++) total += (entries[i].uncompressedSize || 0);
  if (total > LIMIT.totalOut) return { stop: t("errTooBig") };
  if (fileSize > 0 && total / fileSize > LIMIT.ratio && total > 100 * 1024 * 1024) {
    return { stop: t("errBomb") };
  }
  return { total: total, warn: total > LIMIT.warnOut ? t("warnBig") : null };
}
function friendly(e) {
  var m = String((e && e.message) || e || "");
  if (/memory|allocation/i.test(m)) return t("errMem");
  if (/password|encrypted/i.test(m)) return t("passwordWrong");
  if (/zip|signature|central|format|eocd/i.test(m)) return t("errNotZip");
  return t("errGeneric");
}

/* ---------- results ---------- */
function statBlock(rows) {
  return '<dl class="stats">' + rows.map(function (r) {
    return '<div class="stat"><dt>' + esc(r[0]) + "</dt><dd" +
      (r[2] ? ' class="good"' : "") + ">" + esc(r[1]) + "</dd></div>";
  }).join("") + "</dl>";
}
function showResult(html) {
  var r = $("result"); r.innerHTML = html; r.hidden = false;
}
var pending = {};   // download id -> {blob, filename}
function dlButton(blob, filename, label, cls) {
  var id = "d" + Math.random().toString(36).slice(2);
  pending[id] = { blob: blob, filename: filename };
  return '<button class="' + (cls || "btn") + '" type="button" data-dl="' + id + '">' +
    esc(label || t("download")) + "</button>";
}

/* ---------- an opened ZIP, kept for on-demand extraction ---------- */
/* Nothing inside is unpacked until the reader asks for it. A 1 GB archive
   costs a list and no memory until a file is actually tapped. */
var session = null;   // {reader, entries, password, name}
function endSession() {
  if (session && session.reader) { try { session.reader.close(); } catch (e) {} }
  session = null;
}

/* ---------- the seven jobs ---------- */
function runCreate(pw) {
  var name = baseName(val("optName", "archive.zip")) || "archive.zip";
  if (!/\.zip$/i.test(name)) name += ".zip";
  var level = parseInt(radio("lv") || "6", 10);
  var opts = { level: level };
  if (pw) { opts.password = pw; opts.encryptionStrength = 3; }   // AES-256
  var writer = new zip.ZipWriter(new zip.BlobWriter("application/zip"), opts);
  var total = state.files.reduce(function (a, f) { return a + f.size; }, 0) || 1;
  var done = 0, chain = Promise.resolve();
  state.files.forEach(function (f) {
    chain = chain.then(function () {
      return writer.add(baseName(f.name), new zip.BlobReader(f), {
        onprogress: function (i) {
          setProg(true, t("packing") + " — " + baseName(f.name), (done + i) / total * 100);
        }
      }).then(function () { done += f.size; return breathe(); });
    });
  });
  return chain.then(function () { return writer.close(); }).then(function (blob) {
    var pct = total > 0 ? Math.round((1 - blob.size / total) * 100) : 0;
    showResult(
      statBlock([
        [t("entriesN"), String(state.files.length)],
        [t("was"), fmtSize(total)],
        [t("now"), fmtSize(blob.size)],
        [t("ratio"), (pct > 0 ? pct : 0) + "%", pct > 0]
      ]) +
      (pw ? '<div class="msg msg-warn">' + esc(t("passwordHelp")) + "</div>" : "") +
      '<div class="dlrow">' + dlButton(blob, name, t("download") + " " + name, "btn-go") + "</div>"
    );
  });
}

/* Extract now only LISTS. Opening a file happens when its button is tapped,
   which is what lets a very large archive be browsed on a phone at all. */
function runExtract() {
  var file = state.files[0];
  function attempt(password) {
    return openZip(file, password).then(function (z) {
      var g = guard(z.entries, file.size);
      if (g.stop) return z.reader.close().then(function () { throw { soft: g.stop }; });
      var real = z.entries.filter(function (e) { return !e.directory; });
      if (!real.length) return z.reader.close().then(function () { throw { soft: t("errEmpty") }; });
      if (!password && real.some(function (e) { return e.encrypted; })) {
        return z.reader.close().then(function () { throw { needPw: true }; });
      }
      /* A ZIP's list of contents is NOT encrypted, so listing one succeeds with
         any password at all. Since nothing else is unpacked now, a wrong
         password would sail through and show a perfectly normal file list —
         the reader would only find out much later. So one entry, the smallest,
         is decrypted here and thrown away, purely to make the password prove
         itself. Cheap, and it keeps the error where it belongs. */
      var proven = Promise.resolve();
      if (password) {
        var locked = real.filter(function (e) { return e.encrypted; });
        if (locked.length) {
          var smallest = locked.reduce(function (a, b) {
            return (a.uncompressedSize || 0) <= (b.uncompressedSize || 0) ? a : b;
          });
          setProg(true, t("opening"), 50);
          proven = smallest.getData(new zip.BlobWriter(), { password: password })
            .then(function () { });
        }
      }
      return proven.catch(function (e) {
        var msg = /password|encrypted/i.test(String((e && e.message) || e))
          ? t("passwordWrong") : friendly(e);
        return z.reader.close().then(function () { throw { soft: msg }; });
      }).then(function () {
      endSession();
      session = { reader: z.reader, entries: real, password: password || null,
                  name: stripZip(baseName(file.name)) };
      var rows = real.map(function (e, i) {
        return '<li><span class="fname">' + esc(safeName(e.filename)) + '</span><span class="fsize">' +
          fmtSize(e.uncompressedSize) + '</span><button class="mini" type="button" data-ex="' + i +
          '">' + esc(t("download")) + "</button></li>";
      }).join("");
      showResult(
        statBlock([[t("entriesN"), String(real.length)], [t("contents"), fmtSize(g.total)]]) +
        (z.cleaned ? '<div class="msg msg-warn">' + esc(t("slipCleaned")) + "</div>" : "") +
        (g.warn ? '<div class="msg msg-warn">' + esc(g.warn) + "</div>" : "") +
        '<div class="dlrow"><button class="btn-go" type="button" data-exall="1">' +
          esc(t("downloadAll")) + "</button></div>" +
        '<div class="msg msg-ok">' + esc(t("listOnly")) + "</div>" +
        "<h3>" + esc(t("contents")) + '</h3><div class="entries"><ul class="files">' + rows + "</ul></div>"
      );
      });
    });
  }
  /* The password is asked for with a field on the page, never window.prompt().
     A sandboxed frame — which is what a preview or an embed is — silently
     ignores prompt() and hands back nothing, so the page would sit there
     insisting on a password it had never asked for. A field also beats a
     browser popup on a phone, and lets a wrong password be retried without
     picking the file again. */
  extractAgain = attempt;
  return attempt(null).catch(function (e) {
    if (e && e.needPw) { askPassword(); return; }
    throw e;
  });
}

var extractAgain = null;   // re-runs the listing once a password is typed

function askPassword() {
  showResult(
    '<div class="msg msg-warn">' + esc(t("passwordFor")) + "</div>" +
    '<div class="field"><label for="unlockPw">' + esc(t("password")) + "</label>" +
    '<input type="password" id="unlockPw" autocomplete="current-password"></div>' +
    '<div class="dlrow"><button class="btn-go" type="button" id="unlockBtn">' +
      esc(t("unlock")) + "</button></div>"
  );
  var box = $("unlockPw");
  if (box) {
    box.focus();
    box.addEventListener("keydown", function (ev) {
      if (ev.key === "Enter") { ev.preventDefault(); tryUnlock(); }
    });
  }
  var btn = $("unlockBtn");
  if (btn) btn.addEventListener("click", tryUnlock);
}

function tryUnlock() {
  if (state.busy || !extractAgain) return;
  var pw = val("unlockPw", "");
  if (!pw) { showErr(t("passwordNeed")); return; }
  clearErr();
  busy(true); setProg(true, t("opening"), 0);
  Promise.resolve(extractAgain(pw)).then(function () {
    setProg(true, t("done"), 100);
    setTimeout(function () { setProg(false); }, 700);
  }, function (e) {
    setProg(false);
    showErr(e && e.soft ? e.soft : friendly(e));
    askPassword();          // leave the field there so it can be tried again
  }).then(function () { busy(false); });
}

function runCompress() {
  var file = state.files[0];
  var level = parseInt(radio("lv") || "6", 10);
  return openZip(file).then(function (z) {
    var g = guard(z.entries, file.size);
    if (g.stop) return z.reader.close().then(function () { throw { soft: g.stop }; });
    var real = z.entries.filter(function (e) { return !e.directory; });
    if (!real.length) return z.reader.close().then(function () { throw { soft: t("errEmpty") }; });
    if (real.some(function (e) { return e.encrypted; })) {
      return z.reader.close().then(function () { throw { soft: t("errLocked") }; });
    }
    var w = new zip.ZipWriter(new zip.BlobWriter("application/zip"), { level: level });
    var i = 0;
    function next() {
      if (i >= real.length) return Promise.resolve();
      var e = real[i++];
      setProg(true, t("packing") + " — " + baseName(e.filename), i / real.length * 100);
      return e.getData(new zip.BlobWriter()).then(function (blob) {
        return w.add(safeName(e.filename), new zip.BlobReader(blob));
      }).then(function () { return breathe().then(next); });
    }
    return next().then(function () { return w.close(); }).then(function (blob) {
      return z.reader.close().then(function () {
        var smaller = blob.size < file.size;
        var pct = Math.round((1 - blob.size / file.size) * 100);
        showResult(
          statBlock([[t("was"), fmtSize(file.size)], [t("now"), fmtSize(blob.size)],
                     [t("ratio"), (smaller ? pct : 0) + "%", smaller]]) +
          '<div class="msg ' + (smaller ? "msg-ok" : "msg-warn") + '">' +
            esc(smaller ? t("gotSmaller") : t("noSmaller")) + "</div>" +
          (smaller ? '<div class="dlrow">' +
            dlButton(blob, stripZip(baseName(file.name)) + "-smaller.zip", t("download"), "btn-go") + "</div>" : "")
        );
      });
    });
  });
}

function runMerge() {
  var name = baseName(val("optName", "merged.zip")) || "merged.zip";
  if (!/\.zip$/i.test(name)) name += ".zip";
  var policy = radio("dup") || "rename";
  var w = new zip.ZipWriter(new zip.BlobWriter("application/zip"), { level: 6 });
  var seen = {}, count = 0, skipped = 0, renamed = 0, fi = 0;
  function nextFile() {
    if (fi >= state.files.length) return Promise.resolve();
    var file = state.files[fi++];
    return openZip(file).then(function (z) {
      var g = guard(z.entries, file.size);
      if (g.stop) return z.reader.close().then(function () { throw { soft: g.stop }; });
      var real = z.entries.filter(function (e) { return !e.directory && !e.encrypted; });
      var i = 0;
      function nextEntry() {
        if (i >= real.length) return z.reader.close();
        var e = real[i++];
        setProg(true, t("packing") + " — " + baseName(file.name),
          (fi - 1 + i / Math.max(real.length, 1)) / state.files.length * 100);
        var nm = safeName(e.filename);
        if (seen[nm]) {
          if (policy === "skip") { skipped++; return breathe().then(nextEntry); }
          if (policy === "rename") {
            var dot = nm.lastIndexOf("."), n = 2, cand;
            do {
              cand = dot > 0 ? nm.slice(0, dot) + " (" + n + ")" + nm.slice(dot) : nm + " (" + n + ")";
              n++;
            } while (seen[cand]);
            nm = cand; renamed++;
          }
          /* "replace": the same name is written again, and every unpacker takes
             the later one — which is exactly what "keep the newer" means. */
        }
        seen[nm] = true; count++;
        return e.getData(new zip.BlobWriter()).then(function (blob) {
          return w.add(nm, new zip.BlobReader(blob));
        }).then(function () { return breathe().then(nextEntry); });
      }
      return nextEntry();
    }).then(nextFile);
  }
  return nextFile().then(function () { return w.close(); }).then(function (blob) {
    var rows = [[t("entriesN"), String(count)], [t("size"), fmtSize(blob.size)]];
    if (renamed) rows.push([t("renamedN"), String(renamed)]);
    if (skipped) rows.push([t("skippedN"), String(skipped)]);
    showResult(statBlock(rows) + '<div class="dlrow">' +
      dlButton(blob, name, t("download") + " " + name, "btn-go") + "</div>");
  });
}

function runSplit() {
  var file = state.files[0];
  var mb = Math.max(1, parseInt(val("optPart", "25"), 10) || 25);
  var partBytes = mb * 1024 * 1024;
  var mode = radio("sm") || "files";
  var base = stripZip(baseName(file.name));

  if (mode === "bytes") {
    /* file.slice() hands back a lazy view, not a copy, so this costs no memory
       whatever the size of the archive. */
    var parts = [], n = Math.ceil(file.size / partBytes);
    for (var i = 0; i < n; i++) {
      var slice = file.slice(i * partBytes, Math.min((i + 1) * partBytes, file.size));
      var num = String(i + 1); while (num.length < 3) num = "0" + num;
      parts.push({ name: base + ".zip." + num, blob: slice });
    }
    showResult(
      statBlock([[t("parts"), String(parts.length)], [t("size"), fmtSize(file.size)]]) +
      '<div class="dlrow">' + parts.map(function (p) {
        return dlButton(p.blob, p.name, p.name, "mini");
      }).join("") + "</div>" +
      '<div class="howto"><h3>' + esc(t("joinTitle")) + "</h3>" +
      "<span>" + esc(t("joinWin")) + "</span><code>copy /b " + esc(base) + ".zip.* " + esc(base) + ".zip</code>" +
      "<span>" + esc(t("joinMac")) + "</span><code>cat " + esc(base) + ".zip.* &gt; " + esc(base) + ".zip</code></div>"
    );
    return Promise.resolve();
  }

  return openZip(file).then(function (z) {
    var g = guard(z.entries, file.size);
    if (g.stop) return z.reader.close().then(function () { throw { soft: g.stop }; });
    var real = z.entries.filter(function (e) { return !e.directory && !e.encrypted; });
    if (!real.length) return z.reader.close().then(function () { throw { soft: t("errEmpty") }; });
    if (real.some(function (e) { return (e.uncompressedSize || 0) > partBytes; })) {
      return z.reader.close().then(function () { throw { soft: t("tooBigEntry") }; });
    }
    var out = [], cur = null, curSize = 0, i = 0;
    function endPart() {
      if (!cur) return Promise.resolve();
      var w = cur; cur = null;
      return w.close().then(function (blob) {
        var num = String(out.length + 1); while (num.length < 2) num = "0" + num;
        out.push({ name: base + "-part" + num + ".zip", blob: blob });
      });
    }
    function nextEntry() {
      if (i >= real.length) return endPart();
      var e = real[i++];
      setProg(true, t("packing"), i / real.length * 100);
      var sz = e.uncompressedSize || 0;
      var pre = (cur && curSize + sz > partBytes) ? endPart() : Promise.resolve();
      return pre.then(function () {
        if (!cur) {
          cur = new zip.ZipWriter(new zip.BlobWriter("application/zip"), { level: 6 });
          curSize = 0;
        }
        return e.getData(new zip.BlobWriter());
      }).then(function (blob) {
        curSize += sz;
        return cur.add(safeName(e.filename), new zip.BlobReader(blob));
      }).then(function () { return breathe().then(nextEntry); });
    }
    return nextEntry().then(function () {
      return z.reader.close().then(function () {
        showResult(
          statBlock([[t("parts"), String(out.length)], [t("entriesN"), String(real.length)]]) +
          '<div class="msg msg-ok">' + esc(t("splitFilesHelp")) + "</div>" +
          '<div class="dlrow">' + out.map(function (p) {
            return dlButton(p.blob, p.name, p.name + " · " + fmtSize(p.blob.size), "mini");
          }).join("") + "</div>"
        );
      });
    });
  });
}

function runProtect() {
  var pw = val("optPw", ""), pw2 = val("optPw2", "");
  if (!pw) return Promise.reject({ soft: t("passwordNeed") });
  if (pw.length < 4) return Promise.reject({ soft: t("passwordShort") });
  if (pw !== pw2) return Promise.reject({ soft: t("passwordMatch") });
  return runCreate(pw);
}

function runInfo() {
  var file = state.files[0];
  return openZip(file).then(function (z) {
    if (!z.entries.length) return z.reader.close().then(function () { throw { soft: t("errEmpty") }; });
    var real = z.entries.filter(function (e) { return !e.directory; });
    var out = 0, enc = 0, newest = null;
    real.forEach(function (e) {
      out += (e.uncompressedSize || 0);
      if (e.encrypted) enc++;
      if (e.lastModDate && (!newest || e.lastModDate > newest)) newest = e.lastModDate;
    });
    var pct = out > 0 ? Math.round((1 - file.size / out) * 100) : 0;
    var rows = real.slice(0, 500).map(function (e) {
      return '<li><span class="fname">' + esc(safeName(e.filename)) + "</span>" +
        (e.encrypted ? '<span class="lock">' + icon("protect", "ic-sm") + esc(t("locked")) + "</span>" : "") +
        '<span class="fsize">' + fmtSize(e.uncompressedSize) + "</span></li>";
    }).join("");
    var stats = [
      [t("entriesN"), String(real.length)],
      [t("size"), fmtSize(file.size)],
      [t("contents"), fmtSize(out)],
      [t("ratio"), (pct > 0 ? pct : 0) + "%", pct > 0]
    ];
    if (enc) stats.push([t("encrypted"), String(enc)]);
    if (newest) stats.push([t("modified"), newest.toLocaleDateString()]);
    return z.reader.close().then(function () {
      showResult(
        statBlock(stats) +
        "<h3>" + esc(t("contents")) + '</h3><div class="entries"><ul class="files">' + rows + "</ul></div>" +
        (real.length > 500 ? '<p class="help">+' + (real.length - 500) + " " + esc(t("andMore")) + "</p>" : "")
      );
    });
  });
}

var RUN = {
  create: function () { return runCreate(null); },
  extract: runExtract, compress: runCompress, merge: runMerge,
  split: runSplit, protect: runProtect, info: runInfo
};

/* ---------- clicks on results ---------- */
document.addEventListener("click", function (ev) {
  var el = ev.target.closest ? ev.target.closest("[data-dl],[data-ex],[data-exall]") : null;
  if (!el || state.busy) return;

  // an already-made file, waiting to be saved
  if (el.hasAttribute("data-dl")) {
    var item = pending[el.getAttribute("data-dl")];
    if (!item) return;
    var label = el.textContent;
    el.disabled = true; el.textContent = "…";
    saveFile(item.blob, item.filename).then(function (res) {
      el.disabled = false; el.textContent = label;
      var note = saveNote(res);
      if (note) showErr(note); else clearErr();
    });
    return;
  }

  if (!session) return;

  // one file out of an opened ZIP — unpacked only now
  if (el.hasAttribute("data-ex")) {
    var entry = session.entries[parseInt(el.getAttribute("data-ex"), 10)];
    if (!entry) return;
    var lbl = el.textContent;
    el.disabled = true; el.textContent = "…";
    clearErr();
    entry.getData(new zip.BlobWriter(), { password: session.password || undefined })
      .then(function (blob) { return saveFile(blob, baseName(entry.filename)); })
      .then(function (res) {
        var note = saveNote(res);
        if (note) showErr(note);
      })
      .catch(function (e) { showErr(friendly(e)); })
      .then(function () { el.disabled = false; el.textContent = lbl; });
    return;
  }

  // everything at once, packed into one ZIP
  if (el.hasAttribute("data-exall")) {
    var btn = el, blbl = btn.textContent;
    btn.disabled = true;
    busy(true); clearErr();
    var w = new zip.ZipWriter(new zip.BlobWriter("application/zip"), { level: 6 });
    var list = session.entries, pw = session.password, i = 0;
    (function next() {
      if (i >= list.length) return w.close().then(function (blob) {
        setProg(true, t("done"), 100);
        return saveFile(blob, session.name + "-files.zip").then(function (res) {
          var note = saveNote(res);
          if (note) showErr(note);
        });
      });
      var e = list[i++];
      setProg(true, t("opening") + " — " + baseName(e.filename), i / list.length * 100);
      return e.getData(new zip.BlobWriter(), { password: pw || undefined })
        .then(function (blob) { return w.add(safeName(e.filename), new zip.BlobReader(blob)); })
        .then(function () { return breathe().then(next); });
    })().catch(function (e) { showErr(friendly(e)); })
      .then(function () {
        btn.disabled = false; btn.textContent = blbl;
        busy(false); setTimeout(function () { setProg(false); }, 700);
      });
  }
});

/* ---------- wiring ---------- */
function addFiles(list) {
  var tool = TOOLS.filter(function (x) { return x.id === state.tool; })[0];
  var incoming = Array.prototype.slice.call(list);
  state.files = tool.multi ? state.files.concat(incoming) : incoming.slice(0, 1);
  clearErr(); $("result").hidden = true; endSession(); extractAgain = null; renderFiles();
}
$("pickBtn").addEventListener("click", function () { $("fileInput").click(); });
$("fileInput").addEventListener("change", function (e) {
  if (e.target.files && e.target.files.length) addFiles(e.target.files);
  e.target.value = "";
});
var drop = $("drop");
["dragenter", "dragover"].forEach(function (n) {
  drop.addEventListener(n, function (e) { e.preventDefault(); drop.classList.add("over"); });
});
["dragleave", "drop"].forEach(function (n) {
  drop.addEventListener(n, function (e) { e.preventDefault(); drop.classList.remove("over"); });
});
drop.addEventListener("drop", function (e) {
  if (e.dataTransfer && e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
});
$("fileList").addEventListener("click", function (e) {
  var b = e.target.closest("[data-rm]");
  if (!b) return;
  state.files.splice(parseInt(b.getAttribute("data-rm"), 10), 1);
  renderFiles();
});
$("tools").addEventListener("click", function (e) {
  var b = e.target.closest("[data-tool]");
  if (!b || state.busy) return;
  state.tool = b.getAttribute("data-tool");
  state.files = [];
  endSession(); extractAgain = null;
  renderTools(); renderTool();
});
$("goBtn").addEventListener("click", function () {
  if (state.busy) return;
  if (!state.files.length) { showErr(t("pickFirst")); return; }
  clearErr(); $("result").hidden = true; endSession();
  busy(true); setProg(true, t("working"), 0);
  var job;
  try { job = RUN[state.tool](); } catch (e) { job = Promise.reject(e); }
  Promise.resolve(job).then(function () {
    setProg(true, t("done"), 100);
    setTimeout(function () { setProg(false); }, 700);
  }, function (e) {
    setProg(false);
    showErr(e && e.soft ? e.soft : friendly(e));
  }).then(function () { busy(false); });
});

/* ---------- wording ---------- */
/* The page speaks one language. applyLang still fills every [data-t] node from
   WORDS, which is where the tool names and button labels live. */
function applyLang() {
  lang = "en";
  S = WORDS.en;
  document.documentElement.setAttribute("lang", "en");
  var nodes = document.querySelectorAll("[data-t]");
  for (var i = 0; i < nodes.length; i++) {
    nodes[i].textContent = t(nodes[i].getAttribute("data-t"));
  }
  renderTools(); renderTool();
}
applyLang();
})();
