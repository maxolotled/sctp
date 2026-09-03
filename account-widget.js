// Shared account widget — login/logout button + session storage, used on
// every page. This is the one deliberate exception to the site's usual
// "every page is fully self-contained" convention: every page needs
// identical login behavior, so duplicating this ~150 lines seven times over
// would be pure risk with no upside. Any page that wants it just needs
// <div id="accountWidgetMount"></div> in its header plus
// <script src="/account-widget.js" defer></script>.
(function () {
	"use strict";
	var API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	var SESSION_KEY = "sctp_session";

	var style = document.createElement("style");
	style.textContent =
		".acct-widget{position:relative;font-family:inherit;}" +
		".acct-btn{background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:8px 14px;font-size:13px;font-weight:600;cursor:pointer;font-family:inherit;}" +
		".acct-btn:hover{border-color:var(--accent-dim,#87AE29);color:var(--accent,#B7E23D);}" +
		".acct-verified{color:var(--accent,#B7E23D);}" +
		".acct-menu{position:absolute;right:0;top:calc(100% + 6px);background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:10px;padding:6px;min-width:170px;z-index:80;box-shadow:0 8px 24px rgba(0,0,0,0.35);}" +
		".acct-menu a,.acct-menu button{display:block;width:100%;text-align:left;background:transparent;border:none;color:var(--text,#EAEFE7);padding:8px 10px;border-radius:6px;font-size:13px;cursor:pointer;text-decoration:none;font-family:inherit;box-sizing:border-box;}" +
		".acct-menu a:hover,.acct-menu button:hover{background:var(--panel-alt,#22332A);}" +
		".acct-modal-bg{position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;align-items:center;justify-content:center;z-index:200;}" +
		".acct-modal{background:var(--panel,#1B2A20);border:1px solid var(--line,#33453A);border-radius:14px;padding:22px;width:300px;font-family:inherit;}" +
		".acct-modal h2{margin:0 0 14px;font-size:17px;color:var(--text,#EAEFE7);font-family:inherit;}" +
		".acct-modal input{width:100%;box-sizing:border-box;margin-bottom:10px;background:var(--panel-alt,#22332A);border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:8px;padding:9px 12px;font-size:14px;font-family:inherit;}" +
		".acct-modal .err{color:#E2643D;font-size:12.5px;margin:0 0 10px;}" +
		".acct-modal button.primary{width:100%;background:var(--accent,#B7E23D);color:var(--accent-ink,#16210F);border:none;border-radius:9px;padding:10px;font-weight:700;font-size:14px;cursor:pointer;font-family:inherit;}" +
		".acct-modal button.ghost{width:100%;margin-top:8px;background:transparent;border:1px solid var(--line,#33453A);color:var(--text,#EAEFE7);border-radius:9px;padding:9px;font-size:13px;cursor:pointer;font-family:inherit;}";
	document.head.appendChild(style);

	function getSession() {
		try {
			var raw = localStorage.getItem(SESSION_KEY);
			if (!raw) return null;
			var s = JSON.parse(raw);
			if (!s.token || Date.parse(s.expiresAt) < Date.now()) { localStorage.removeItem(SESSION_KEY); return null; }
			return s;
		} catch (e) { return null; }
	}
	function setSession(s) { try { localStorage.setItem(SESSION_KEY, JSON.stringify(s)); } catch (e) {} }
	function clearSession() { try { localStorage.removeItem(SESSION_KEY); } catch (e) {} }

	// Small public surface so a page's own script can read login state (the
	// marketplace page and admin.html both need this) and react to changes.
	window.sctpAccount = { getSession: getSession, API_BASE: API_BASE, clearSession: clearSession };

	function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

	function render() {
		var btn = document.getElementById("acctBtn");
		var menu = document.getElementById("acctMenu");
		if (!btn || !menu) return;
		var session = getSession();
		if (!session) {
			btn.textContent = "Log in";
			btn.onclick = openLogin;
			menu.hidden = true;
			return;
		}
		btn.innerHTML = esc(session.username) + (session.mcVerified ? ' <span class="acct-verified">&#10003;</span>' : "");
		btn.onclick = function () { menu.hidden = !menu.hidden; };
		var isAdmin = session.isHeadAdmin || (session.permissions && session.permissions.length > 0);
		var html = '<a href="/marketplace/#mine">My Marketplace</a>';
		if (isAdmin) html += '<a href="/admin.html">Admin Panel</a>';
		html += '<button type="button" id="acctLogout">Log out</button>';
		menu.innerHTML = html;
		document.getElementById("acctLogout").onclick = function () {
			clearSession();
			render();
			if (window.sctpOnAccountChange) window.sctpOnAccountChange();
		};
	}

	function openLogin() {
		var wrap = document.createElement("div");
		wrap.className = "acct-modal-bg";
		wrap.innerHTML =
			'<div class="acct-modal">' +
				"<h2>Log in</h2>" +
				'<p class="err" id="acctErr" hidden></p>' +
				'<input type="text" id="acctUser" placeholder="Username" autocomplete="username">' +
				'<input type="password" id="acctPass" placeholder="Password" autocomplete="current-password">' +
				'<button type="button" class="primary" id="acctSubmit">Log in</button>' +
				'<button type="button" class="ghost" id="acctCancel">Cancel</button>' +
			"</div>";
		document.body.appendChild(wrap);
		document.getElementById("acctCancel").onclick = function () { wrap.remove(); };
		wrap.addEventListener("click", function (e) { if (e.target === wrap) wrap.remove(); });

		function submit() {
			var username = document.getElementById("acctUser").value.trim();
			var password = document.getElementById("acctPass").value;
			var errEl = document.getElementById("acctErr");
			errEl.hidden = true;
			if (!username || !password) return;
			fetch(API_BASE + "/admin/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username: username, password: password }) })
				.then(function (r) { return r.json().then(function (d) { return { ok: r.ok, data: d }; }); })
				.then(function (res) {
					if (!res.ok) { errEl.textContent = res.data.error || "Login failed"; errEl.hidden = false; return; }
					setSession(res.data);
					wrap.remove();
					render();
					if (window.sctpOnAccountChange) window.sctpOnAccountChange();
				})
				.catch(function () { errEl.textContent = "Network error"; errEl.hidden = false; });
		}
		document.getElementById("acctSubmit").onclick = submit;
		document.getElementById("acctPass").addEventListener("keydown", function (e) { if (e.key === "Enter") submit(); });
	}

	function mount() {
		var host = document.getElementById("accountWidgetMount");
		if (!host) return;
		host.className = "acct-widget";
		host.innerHTML = '<button type="button" class="acct-btn" id="acctBtn"></button><div class="acct-menu" id="acctMenu" hidden></div>';
		render();
		document.addEventListener("click", function (e) {
			var menu = document.getElementById("acctMenu");
			if (menu && !host.contains(e.target)) menu.hidden = true;
		});
	}

	if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount);
	else mount();
})();
