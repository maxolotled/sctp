import { defineConfig } from "vitepress";

// Source lives in docs-src/ (this file's project root); the built static
// site is written to ../docs so it's served at sctp.nl/docs/ for free — the
// main site's GitHub Pages already serves the whole repo root, so anything
// under a subfolder (docs/, items/, marketplace/, ...) is reachable the same
// way with zero extra Pages/DNS configuration.
export default defineConfig({
	title: "SCTP Docs",
	description: "Documentation for the Snailcraft Trading Post",
	base: "/docs/",
	outDir: "../docs",
	cleanUrls: true,

	themeConfig: {
		nav: [
			{ text: "Home", link: "https://sctp.nl/" },
		],
		sidebar: [
			{
				text: "Guide",
				items: [
					{ text: "Introduction", link: "/" },
				],
			},
		],
		socialLinks: [],
		search: { provider: "local" },
	},
});
