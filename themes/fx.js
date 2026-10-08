/*
 * SCTP — shared seasonal-theme effects engine.
 *
 * Every themes/<name>.js calls window.SCTPThemeFX(config) with its own
 * artwork and settings; this file does the work. Everything it adds is
 * decoration only: aria-hidden, ignores the mouse, never touches page content,
 * and motion is off by default for people who ask their system for reduced
 * motion (the effects button can still turn it on). See themes/README.md.
 *
 * Config (all optional):
 *   id            theme name, used for the saved effects on/off choice
 *   titlePrefix   emoji put in front of the tab title
 *   favicon       full <svg> markup for the tab icon
 *   icons         { "🐛": "🕷️", ... } page-badge emoji swaps (looked up without U+FE0F)
 *   kickerIcons   { "Rare-dle": "🔮" } icon for home-page card labels that have none
 *   logo          { svg, width, top, left|right, rotate } accessory on the snail logo
 *   props         { items: [svg...], side: "left"|"right" } cluster on the header's bottom edge (wide screens)
 *   garland       { spacing, sag, wire, colors, anim, item(color, i, n) -> svg } string across the top of the page
 *   footer        { left, text, right, font } banner above the footer text ("{year}" = the coming new year)
 *   particles     [{ type: "fall"|"rise", shapes, viewBox, colors, count, mobileCount, size, dur,
 *                    sway, swayDur, spin, opacity, glow, blink }]
 *   bursts        { every: [min,max] ms, colors, sparks, shape: "dot"|"rect", first }
 *   flyers        [{ svg, width, height, path: "fly"|"hop"|"streak", every: [min,max] ms, first, dur, flap }]
 *   toggle        { icon, noun }  the corner button that switches particles + bursts on/off
 *   flyerToggle   { icon, noun }  optional second corner button that switches the flyers on/off
 *   extra         function(helpers) run once after everything else, for theme-specific tricks
 *                 (helpers: { reduceMotion, el, rand, pick })
 */
(function () {
	"use strict";
	if (window.SCTPThemeFX) return;

	var reduceMotion = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);

	function rand(a, b) { return a + Math.random() * (b - a); }
	function pick(list) { return list[Math.floor(Math.random() * list.length)]; }
	function el(tag, cls, html) {
		var e = document.createElement(tag);
		if (cls) e.className = cls;
		if (html) e.innerHTML = html;
		e.setAttribute("aria-hidden", "true");
		return e;
	}
	function comingYear() {
		var d = new Date();
		return d.getMonth() >= 9 ? d.getFullYear() + 1 : d.getFullYear(); // Oct–Dec: next year
	}
	function fillTokens(s) { return String(s).replace(/\{year\}/g, comingYear()); }

	var BASE_CSS = [
		"header.site{position:relative;}",
		/* particles */
		".fx-layer{position:fixed;inset:0;overflow:hidden;pointer-events:none;z-index:60;}",
		".fx-p{position:absolute;animation-timing-function:linear;animation-iteration-count:infinite;will-change:transform;}",
		".fx-p.fall{top:-60px;animation-name:fxFall;}",
		".fx-p.rise{bottom:-80px;animation-name:fxRise;}",
		".fx-sway{animation-name:fxSway;animation-timing-function:ease-in-out;animation-iteration-count:infinite;animation-direction:alternate;}",
		".fx-p svg{display:block;overflow:visible;}",
		".fx-blink{animation:fxBlink 1.6s ease-in-out infinite alternate;}",
		"@keyframes fxFall{from{transform:translateY(0);}to{transform:translateY(calc(100vh + 120px));}}",
		"@keyframes fxRise{from{transform:translateY(0);}to{transform:translateY(calc(-100vh - 160px));}}",
		"@keyframes fxSway{from{transform:translateX(calc(var(--fx-d,40px) * -0.5)) rotate(calc(var(--fx-r,20deg) * -1));}to{transform:translateX(calc(var(--fx-d,40px) * 0.5)) rotate(var(--fx-r,20deg));}}",
		"@keyframes fxBlink{from{opacity:1;}to{opacity:0.15;}}",
		/* bursts */
		".fx-burst{position:fixed;width:0;height:0;pointer-events:none;z-index:60;}",
		".fx-spark{position:absolute;left:-2px;top:-2px;width:4px;height:4px;border-radius:50%;animation:fxSpark 1.5s cubic-bezier(.15,.7,.3,1) forwards;}",
		".fx-spark.rect{width:5px;height:9px;border-radius:1px;}",
		".fx-flash{position:absolute;left:-14px;top:-14px;width:28px;height:28px;border-radius:50%;animation:fxFlash .5s ease-out forwards;}",
		"@keyframes fxSpark{0%{transform:translate(0,0) rotate(0) scale(1);opacity:1;}70%{opacity:1;}100%{transform:translate(var(--fx-x),calc(var(--fx-y) + 40px)) rotate(var(--fx-rot,0deg)) scale(.4);opacity:0;}}",
		"@keyframes fxFlash{from{transform:scale(.2);opacity:.9;}to{transform:scale(2.4);opacity:0;}}",
		/* flyers */
		".fx-flyer{position:fixed;left:0;z-index:60;pointer-events:none;}",
		".fx-flyer .fx-mirror{display:block;}",
		".fx-flyer.rtl .fx-mirror{transform:scaleX(-1);}",
		".fx-flyer svg{display:block;overflow:visible;}",
		".fx-flap{animation:fxFlap .22s ease-in-out infinite alternate;transform-origin:50% 60%;}",
		".fx-flyer.fly{animation:fxFly var(--fx-dur,10s) linear forwards;}",
		".fx-flyer.fly.rtl{animation-name:fxFlyBack;}",
		".fx-flyer.hop{top:auto;bottom:calc(6px + env(safe-area-inset-bottom,0px));animation:fxWalk var(--fx-dur,12s) linear forwards;}",
		".fx-flyer.hop.rtl{animation-name:fxWalkBack;}",
		".fx-flyer.hop .fx-hopper{display:block;animation:fxHop .7s cubic-bezier(.3,0,.7,1) infinite;}",
		".fx-flyer.streak{animation:fxStreak var(--fx-dur,1.4s) ease-in forwards;}",
		"@keyframes fxFly{0%{transform:translate(-160px,0);}25%{transform:translate(25vw,-4vh);}50%{transform:translate(50vw,3vh);}75%{transform:translate(75vw,-3vh);}100%{transform:translate(calc(100vw + 160px),1vh);}}",
		"@keyframes fxFlyBack{0%{transform:translate(calc(100vw + 160px),0);}25%{transform:translate(75vw,4vh);}50%{transform:translate(50vw,-3vh);}75%{transform:translate(25vw,3vh);}100%{transform:translate(-160px,-1vh);}}",
		"@keyframes fxWalk{from{transform:translateX(-80px);}to{transform:translateX(calc(100vw + 80px));}}",
		"@keyframes fxWalkBack{from{transform:translateX(calc(100vw + 80px));}to{transform:translateX(-80px);}}",
		"@keyframes fxHop{0%,100%{transform:translateY(0);}45%{transform:translateY(-26px);}}",
		"@keyframes fxStreak{0%{transform:translate(0,0);opacity:0;}10%{opacity:1;}100%{transform:translate(55vw,32vh);opacity:0;}}",
		"@keyframes fxFlap{from{transform:scaleY(1);}to{transform:scaleY(0.35);}}",
		/* header props + garland */
		".fx-props{position:absolute;bottom:-3px;display:flex;align-items:flex-end;gap:3px;pointer-events:none;}",
		".fx-props.left{left:clamp(8px, calc(50% - 690px), 60px);}",
		".fx-props.right{right:clamp(8px, calc(50% - 690px), 60px);}",
		".fx-props svg{display:block;overflow:visible;filter:drop-shadow(0 3px 4px rgba(0,0,0,0.5));}",
		"@media (max-width:1100px){.fx-props{display:none;}}",
		".fx-garland{position:absolute;top:0;left:0;right:0;z-index:-1;pointer-events:none;overflow:hidden;}",
		".fx-garland > svg{position:absolute;top:0;left:0;}",
		".fx-g-item{position:absolute;top:0;transform-origin:50% 0;}",
		".fx-g-item svg{display:block;overflow:visible;}",
		/* footer banner */
		".fx-footer{display:flex;align-items:center;justify-content:center;gap:10px;margin:0 0 10px;color:var(--shell);font-size:17px;letter-spacing:0.04em;flex-wrap:wrap;}",
		".fx-footer svg{display:block;overflow:visible;}",
		/* logo accessory */
		".fx-logo-wrap{position:relative;display:inline-flex;flex:0 0 auto;}",
		".fx-logo-acc{position:absolute;pointer-events:none;filter:drop-shadow(0 2px 3px rgba(0,0,0,0.5));}",
		".fx-logo-acc svg{display:block;width:100%;height:auto;overflow:visible;}",
		/* the effects on/off button */
		".fx-toggle{position:fixed;left:14px;bottom:calc(14px + env(safe-area-inset-bottom,0px));z-index:70;width:38px;height:38px;border-radius:50%;border:1px solid var(--line);background:var(--panel);font-size:18px;line-height:1;cursor:pointer;box-shadow:0 4px 14px rgba(0,0,0,0.4);transition:transform .15s ease,opacity .15s ease;padding:0;}",
		".fx-toggle:hover{transform:scale(1.08);border-color:var(--accent-dim);}",
		".fx-toggle:focus-visible{outline:2px solid var(--accent);outline-offset:2px;}",
		".fx-toggle.off{opacity:0.55;filter:grayscale(0.7);}",
		/* the optional flyers on/off button sits just above it */
		".fx-toggle.fx-toggle-2{bottom:calc(60px + env(safe-area-inset-bottom,0px));}",
		/* reusable little animations for artwork */
		".fx-flicker{animation:fxFlicker 3.2s infinite;}",
		".fx-twinkle{animation:fxTwinkle 2.4s ease-in-out infinite;}",
		".fx-wobble{animation:fxWobble 2.8s ease-in-out infinite;transform-origin:50% 100%;transform-box:fill-box;}",
		".fx-lamp{animation:fxLamp 3.6s ease-in-out infinite;}",
		".fx-bob{animation:fxBob 2.4s ease-in-out infinite;transform-box:fill-box;}",
		"@keyframes fxFlicker{0%,100%{opacity:1;}42%{opacity:.8;}46%{opacity:1;}71%{opacity:.9;}74%{opacity:.7;}77%{opacity:1;}}",
		"@keyframes fxTwinkle{0%,100%{opacity:1;filter:brightness(1.15);}50%{opacity:.45;filter:brightness(.8);}}",
		"@keyframes fxWobble{0%,100%{transform:rotate(-5deg);}50%{transform:rotate(5deg);}}",
		"@keyframes fxLamp{0%,100%{transform:rotate(-4deg);}50%{transform:rotate(4deg);}}",
		"@keyframes fxBob{0%,100%{transform:translateY(0);}50%{transform:translateY(-3px);}}",
		"@media (prefers-reduced-motion: reduce){.fx-flicker,.fx-twinkle,.fx-wobble,.fx-lamp,.fx-bob,.fx-blink{animation:none;}}"
	].join("\n");

	function injectCss() {
		if (document.getElementById("fx-base-css")) return;
		var s = document.createElement("style");
		s.id = "fx-base-css";
		s.textContent = BASE_CSS;
		// first in <head>, so the theme's own stylesheet can override any of it
		document.head.insertBefore(s, document.head.firstChild);
	}

	// ---- icons, title, favicon, logo ----------------------------------------

	function swapEmojiIn(node, map) {
		var walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT, null);
		var t;
		while ((t = walker.nextNode())) {
			var s = t.nodeValue, out = s;
			for (var k in map) if (out.indexOf(k) !== -1) out = out.split(k + "️").join(map[k]).split(k).join(map[k]);
			if (out !== s) t.nodeValue = out;
		}
	}

	function applyIcons(cfg) {
		if (cfg.icons) {
			var marks = document.querySelectorAll("div.brand-mark, span.brand-mark, .promo-kicker");
			for (var i = 0; i < marks.length; i++) swapEmojiIn(marks[i], cfg.icons);
		}
		if (cfg.kickerIcons) {
			var kickers = document.querySelectorAll(".promo-kicker");
			for (var j = 0; j < kickers.length; j++) {
				var label = kickers[j].textContent.trim();
				if (cfg.kickerIcons[label]) kickers[j].textContent = cfg.kickerIcons[label] + " " + label;
			}
		}
		if (cfg.titlePrefix && document.title.indexOf(cfg.titlePrefix) !== 0) document.title = cfg.titlePrefix + " " + document.title;
		if (cfg.favicon) {
			var href = "data:image/svg+xml," + encodeURIComponent(cfg.favicon);
			var links = document.querySelectorAll('link[rel~="icon"]');
			if (links.length) for (var l = 0; l < links.length; l++) links[l].href = href;
			else { var ln = document.createElement("link"); ln.rel = "icon"; ln.href = href; document.head.appendChild(ln); }
		}
		if (cfg.logo) {
			var snails = document.querySelectorAll("svg.brand-mark");
			for (var n = 0; n < snails.length; n++) {
				var svg = snails[n];
				if (svg.parentNode.classList && svg.parentNode.classList.contains("fx-logo-wrap")) continue;
				var wrap = el("span", "fx-logo-wrap");
				svg.parentNode.insertBefore(wrap, svg);
				wrap.appendChild(svg);
				var acc = el("span", "fx-logo-acc", cfg.logo.svg);
				acc.style.width = cfg.logo.width || "70%";
				acc.style.top = cfg.logo.top || "-45%";
				if (cfg.logo.left != null) acc.style.left = cfg.logo.left; else acc.style.right = cfg.logo.right || "-15%";
				if (cfg.logo.rotate) acc.style.transform = "rotate(" + cfg.logo.rotate + "deg)";
				wrap.appendChild(acc);
			}
		}
	}

	// ---- header props, garland, footer ------------------------------------

	function applyProps(cfg) {
		var header = document.querySelector("header.site");
		if (header && cfg.props && !header.querySelector(".fx-props")) {
			header.appendChild(el("div", "fx-props " + (cfg.props.side || "left"), cfg.props.items.join("")));
		}
		var footer = document.querySelector("footer.site");
		if (footer && cfg.footer && !footer.querySelector(".fx-footer")) {
			var f = el("div", "fx-footer", (cfg.footer.left || "") + "<span>" + fillTokens(cfg.footer.text || "") + "</span>" + (cfg.footer.right || ""));
			if (cfg.footer.font) f.style.fontFamily = cfg.footer.font;
			footer.insertBefore(f, footer.firstChild);
		}
		if (header && cfg.garland) garland(header, cfg.garland);
	}

	function garland(header, g) {
		var box = el("div", "fx-garland");
		header.appendChild(box);
		var spacing = g.spacing || 44, sag = g.sag || 10, lastW = 0;
		function build() {
			var w = header.clientWidth;
			if (!w || w === lastW) return;
			lastW = w;
			var n = Math.max(3, Math.round(w / spacing));
			var step = w / n;
			var h = sag + (g.height || 30);
			box.style.height = h + "px";
			var d = "M0 2";
			for (var i = 0; i < n; i++) d += " Q" + (i * step + step / 2).toFixed(1) + " " + (sag * 2 + 2) + " " + ((i + 1) * step).toFixed(1) + " 2";
			var html = '<svg width="' + w + '" height="' + h + '" viewBox="0 0 ' + w + " " + h + '"><path d="' + d + '" fill="none" stroke="' + (g.wire || "rgba(255,255,255,.35)") + '" stroke-width="1.5"/></svg>';
			for (var k = 0; k < n; k++) {
				var color = g.colors ? g.colors[k % g.colors.length] : "#fff";
				var x = k * step + step / 2;
				var delay = (-rand(0, 3)).toFixed(2);
				html += '<span class="fx-g-item' + (g.anim ? " fx-" + g.anim : "") + '" style="left:' + (x - (g.itemWidth || 12) / 2).toFixed(1) + "px;top:" + (sag + 1) + "px;animation-delay:" + delay + 's">' + g.item(color, k, n) + "</span>";
			}
			box.innerHTML = html;
		}
		build();
		var t = null;
		window.addEventListener("resize", function () { clearTimeout(t); t = setTimeout(build, 200); });
	}

	// ---- particles + bursts -------------------------------------------------

	var layer = null, burstTimer = null;

	function startEffects(cfg) {
		if (layer) return;
		layer = el("div", "fx-layer");
		var narrow = window.innerWidth < 700;
		(cfg.particles || []).forEach(function (g) {
			var count = narrow ? (g.mobileCount != null ? g.mobileCount : Math.ceil(g.count / 2)) : g.count;
			for (var i = 0; i < count; i++) {
				var dur = rand(g.dur[0], g.dur[1]);
				var size = rand(g.size[0], g.size[1]);
				var p = el("div", "fx-p " + (g.type || "fall"));
				p.style.left = rand(-3, 97).toFixed(1) + "vw";
				p.style.animationDuration = dur.toFixed(1) + "s";
				p.style.animationDelay = (-rand(0, dur)).toFixed(1) + "s"; // already mid-air on load
				var inner = el("div", g.sway === 0 ? "" : "fx-sway");
				if (g.sway !== 0) {
					var sw = g.sway || [20, 60];
					inner.style.animationDuration = rand((g.swayDur || [2.6, 4.6])[0], (g.swayDur || [2.6, 4.6])[1]).toFixed(1) + "s";
					inner.style.setProperty("--fx-d", rand(sw[0], sw[1]).toFixed(0) + "px");
					inner.style.setProperty("--fx-r", (g.spin != null ? g.spin : 30) + "deg");
				}
				var color = g.colors[i % g.colors.length];
				var vb = g.viewBox || "0 0 24 24";
				var parts = vb.split(" ");
				var hgt = size * (+parts[3] / +parts[2]);
				var style = "color:" + color + ";opacity:" + (g.opacity != null ? g.opacity : 0.85) + ";";
				if (g.glow) style += "filter:drop-shadow(0 0 " + Math.max(3, size / 3).toFixed(0) + "px " + color + ");";
				if (g.spin && g.type !== "rise") style += "transform:rotate(" + rand(0, 360).toFixed(0) + "deg);";
				inner.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="' + vb + '" width="' + size.toFixed(0) + '" height="' + hgt.toFixed(0) +
					'" fill="currentColor" class="' + (g.blink ? "fx-blink" : "") + '" style="' + style + (g.blink ? "animation-delay:" + (-rand(0, 2)).toFixed(1) + "s;" : "") + '">' +
					pick(g.shapes) + "</svg>";
				p.appendChild(inner);
				layer.appendChild(p);
			}
		});
		document.body.appendChild(layer);
		if (cfg.bursts && !burstTimer) scheduleBurst(cfg.bursts, cfg.bursts.first || [1200, 3000]);
	}

	function stopEffects() {
		if (layer && layer.parentNode) layer.parentNode.removeChild(layer);
		layer = null;
		clearTimeout(burstTimer);
		burstTimer = null;
	}

	function scheduleBurst(b, range) {
		burstTimer = setTimeout(function () {
			if (!layer) return;
			if (!document.hidden) burst(b);
			scheduleBurst(b, b.every);
		}, rand(range[0], range[1]));
	}

	function burst(b) {
		var box = el("div", "fx-burst");
		box.style.left = rand(8, 92).toFixed(1) + "vw";
		box.style.top = rand(8, 42).toFixed(1) + "vh";
		var main = pick(b.colors);
		var flash = el("span", "fx-flash");
		flash.style.background = "radial-gradient(circle," + main + " 0%, transparent 70%)";
		box.appendChild(flash);
		var n = b.sparks || 24;
		for (var i = 0; i < n; i++) {
			var s = el("span", "fx-spark" + (b.shape === "rect" ? " rect" : ""));
			var a = (i / n) * Math.PI * 2 + rand(-0.12, 0.12);
			var r = rand(60, 130);
			var c = Math.random() < 0.7 ? main : pick(b.colors);
			s.style.setProperty("--fx-x", (Math.cos(a) * r).toFixed(0) + "px");
			s.style.setProperty("--fx-y", (Math.sin(a) * r).toFixed(0) + "px");
			s.style.setProperty("--fx-rot", rand(-400, 400).toFixed(0) + "deg");
			s.style.background = c;
			if (b.shape !== "rect") s.style.boxShadow = "0 0 6px 1px " + c;
			s.style.animationDelay = rand(0, 0.08).toFixed(2) + "s";
			box.appendChild(s);
		}
		document.body.appendChild(box);
		setTimeout(function () { if (box.parentNode) box.parentNode.removeChild(box); }, 1900);
	}

	function effectsToggle(cfg) {
		if (!cfg.particles && !cfg.bursts) return;
		var key = "sctp_fx_" + (cfg.id || "theme");
		var want = !reduceMotion;
		try {
			var v = localStorage.getItem(key);
			if (v === "on") want = true;
			if (v === "off") want = false;
		} catch (e) {}
		var noun = (cfg.toggle && cfg.toggle.noun) || "effects";
		var btn = document.createElement("button");
		btn.type = "button";
		btn.className = "fx-toggle";
		btn.textContent = (cfg.toggle && cfg.toggle.icon) || "✨";
		function sync() {
			var on = !!layer;
			btn.setAttribute("aria-pressed", on ? "true" : "false");
			btn.title = (on ? "Turn " + noun + " off" : "Turn " + noun + " on");
			btn.setAttribute("aria-label", btn.title);
			btn.classList.toggle("off", !on);
		}
		btn.addEventListener("click", function () {
			if (layer) stopEffects(); else startEffects(cfg);
			try { localStorage.setItem(key, layer ? "on" : "off"); } catch (e) {}
			sync();
		});
		if (want) startEffects(cfg);
		sync();
		document.body.appendChild(btn);
	}

	// ---- flyers -------------------------------------------------------------

	// Flyers (e.g. Halloween's bats) can be switched off on their own with an
	// optional second corner button (cfg.flyerToggle); the choice is remembered.
	var flyersOn = true;
	function flyerToggle(cfg) {
		if (!cfg.flyers || !cfg.flyerToggle || reduceMotion) return;
		var key = "sctp_fx_" + (cfg.id || "theme") + "_flyers";
		try { if (localStorage.getItem(key) === "off") flyersOn = false; } catch (e) {}
		var noun = cfg.flyerToggle.noun || "flyers";
		var btn = document.createElement("button");
		btn.type = "button";
		btn.className = "fx-toggle fx-toggle-2";
		btn.textContent = cfg.flyerToggle.icon || "🕊";
		function sync() {
			btn.setAttribute("aria-pressed", flyersOn ? "true" : "false");
			btn.title = (flyersOn ? "Turn " + noun + " off" : "Turn " + noun + " on");
			btn.setAttribute("aria-label", btn.title);
			btn.classList.toggle("off", !flyersOn);
		}
		btn.addEventListener("click", function () {
			flyersOn = !flyersOn;
			if (!flyersOn) document.querySelectorAll(".fx-flyer").forEach(function (n) { n.remove(); }); // gone right away
			try { localStorage.setItem(key, flyersOn ? "on" : "off"); } catch (e) {}
			sync();
		});
		sync();
		document.body.appendChild(btn);
	}

	function flyers(cfg) {
		if (reduceMotion || !cfg.flyers) return;
		cfg.flyers.forEach(function (f) {
			function go() {
				if (!document.hidden && flyersOn) launch(f);
				setTimeout(go, rand(f.every[0], f.every[1]));
			}
			setTimeout(go, rand((f.first || [5000, 10000])[0], (f.first || [5000, 10000])[1]));
		});
	}

	function launch(f) {
		var path = f.path || "fly";
		var rtl = path !== "streak" && Math.random() < 0.5;
		var box = el("div", "fx-flyer " + path + (rtl ? " rtl" : ""));
		box.style.setProperty("--fx-dur", (f.dur || (path === "streak" ? 1.4 : 10)) + "s");
		var svg = f.svg.replace("<svg", '<svg width="' + f.width + '" height="' + f.height + '"' + (f.flap ? ' class="fx-flap"' : ""));
		if (path === "hop") svg = '<span class="fx-hopper">' + svg + "</span>";
		box.innerHTML = '<span class="fx-mirror">' + svg + "</span>";
		if (path === "fly") box.style.top = rand(6, 30).toFixed(0) + "vh";
		if (path === "streak") { box.style.top = rand(2, 22).toFixed(0) + "vh"; box.style.left = rand(0, 45).toFixed(0) + "vw"; }
		document.body.appendChild(box);
		box.addEventListener("animationend", function (e) { if (e.target === box && box.parentNode) box.parentNode.removeChild(box); });
	}

	// ---- entry point --------------------------------------------------------

	window.SCTPThemeFX = function (cfg) {
		function init() {
			injectCss();
			applyIcons(cfg);
			applyProps(cfg);
			effectsToggle(cfg);
			flyerToggle(cfg);
			flyers(cfg);
			if (typeof cfg.extra === "function") {
				try { cfg.extra({ reduceMotion: reduceMotion, el: el, rand: rand, pick: pick }); } catch (e) {}
			}
		}
		if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
		else init();
	};
})();
