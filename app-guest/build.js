/// <reference lib="es2017"/>

const esbuild = require('esbuild');
const fs = require('fs');
const { sassPlugin } = require('esbuild-sass-plugin');
const { globalExternals } = require('@fal-works/esbuild-plugin-global-externals');

/** Mapping from module paths to global variables */
const globals = {
	"react": {
		varName: "React",
		namedExports: [
			"Fragment",
			"createElement",
			"cloneElement",
			"isValidElement",
			"createContext",
			"useContext",
			"useRef",
			"forwardRef",
			"useState",
		],
	},
	"react-dom": "ReactDOM",
};

const onRebuild = {
	name: 'onRebuild',
	setup(build) {
		let count = 0;
		build.onEnd(result => {
			console.log('watch/build succeeded:', result)
			fs.cpSync("./target-lib","./target/lib",{ recursive: true });
			fs.cpSync("./src/index.html","./target/index.html");
			fs.cpSync("./target","../target/classes/app-guest",{ recursive: true });
		});
	},
};

(async function() {
	const ctx = await esbuild.context({
		entryPoints: [ './src/index.jsx'/*, './src/index.scss'*/ ],
		entryNames: 'index',
		plugins: [
			sassPlugin(),
			globalExternals(globals),
			onRebuild,
		],
		platform: "browser",
		bundle: true,
		minify: !true,
		sourcemap: true,
		outdir: "./target",
	})
	await ctx.watch();
	await ctx.rebuild();
})();

function sleep() { setTimeout(sleep,1000); }
sleep();
