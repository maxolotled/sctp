import { defineConfig } from "vitepress";

// Source lives in docs-src/ (this file's project root); the built static
// site is written to ../docs so it's served at sctp.nl/docs/ for free — the
// main site's GitHub Pages already serves the whole repo root, so anything
// under a subfolder (docs/, items/, marketplace/, ...) is reachable the same
// way with zero extra Pages/DNS configuration.
export default defineConfig({
	title: "SCTP Info",
	description: "Documentation for the Snailcraft Trading Post",
	base: "/docs/",
	outDir: "../docs",
	cleanUrls: true,

	// The main site has no light mode at all, so match it exactly instead of
	// theming both a light and a dark palette.
	appearance: "force-dark",

	head: [
		["link", { rel: "preconnect", href: "https://fonts.googleapis.com" }],
		["link", { rel: "preconnect", href: "https://fonts.gstatic.com", crossorigin: "" }],
		["link", {
			rel: "stylesheet",
			href: "https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,600;9..144,700&family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap",
		}],
		["style", {}, `
			:root, .dark {
				/* SCTP brand palette (matches sctp.nl) */
				--vp-c-bg: #101B14;
				--vp-c-bg-alt: #0C140F;
				--vp-c-bg-elv: #1B2A20;
				--vp-c-bg-soft: #22332A;

				--vp-c-text-1: #EAEFE7;
				--vp-c-text-2: #C7D4C5;
				--vp-c-text-3: #8FA593;

				--vp-c-border: #33453A;
				--vp-c-divider: #33453A;
				--vp-c-gutter: #101B14;

				--vp-c-brand-1: #B7E23D;
				--vp-c-brand-2: #A0D62E;
				--vp-c-brand-3: #87AE29;
				--vp-c-brand-soft: rgba(183, 226, 61, 0.16);

				--vp-button-brand-border: transparent;
				--vp-button-brand-text: #16210F;
				--vp-button-brand-bg: #B7E23D;
				--vp-button-brand-hover-border: transparent;
				--vp-button-brand-hover-text: #16210F;
				--vp-button-brand-hover-bg: #A0D62E;
				--vp-button-brand-active-border: transparent;
				--vp-button-brand-active-text: #16210F;
				--vp-button-brand-active-bg: #87AE29;

				--vp-nav-bg-color: #101B14;
				--vp-sidebar-bg-color: #101B14;
				--vp-local-nav-bg-color: #101B14;

				--vp-code-bg: #1B2A20;
				--vp-code-block-bg: #1B2A20;

				--vp-custom-block-tip-border: transparent;
				--vp-custom-block-tip-text: #EAEFE7;
				--vp-custom-block-tip-bg: rgba(183, 226, 61, 0.12);
				--vp-custom-block-warning-border: transparent;
				--vp-custom-block-warning-text: #EAEFE7;
				--vp-custom-block-warning-bg: rgba(226, 163, 61, 0.14);
				--vp-custom-block-danger-border: transparent;
				--vp-custom-block-danger-text: #EAEFE7;
				--vp-custom-block-danger-bg: rgba(226, 100, 61, 0.14);

				--vp-home-hero-name-color: #B7E23D;
				--vp-home-hero-name-background: transparent;

				--vp-font-family-base: 'Inter', sans-serif;
				--vp-font-family-mono: 'JetBrains Mono', monospace;
			}

			.VPHero .name, .VPHero .text, h1, h2, h3 {
				font-family: 'Fraunces', serif;
			}
		`],
	],

	themeConfig: {
		nav: [
			{ text: "SCTP", link: "https://sctp.nl/" },
			{ text: "Features", link: "/features" },
			{ text: "FAQ", link: "/faq" },
			{ text: "Installation", link: "/installation" },
		],
		sidebar: [
		],
		socialLinks: [],
		search: { provider: "local" },
	},
});
