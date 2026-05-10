/// <reference lib="es2017"/>

const esbuild = require('esbuild');
const fs = require('fs');
const { sassPlugin } = require('esbuild-sass-plugin');
const { globalExternals } = require('@fal-works/esbuild-plugin-global-externals');

/** Mapping from module paths to global variables */
const globals = {
	"react": "React",
	"react": {
		varName: "React",
		namedExports: [
			"Fragment",
			"useCallback",
			"createElement",
			"cloneElement",
			"isValidElement",
			"createContext",
			"useContext",
			"useRef",
			"createRef",
			"forwardRef",
			"useState",
			"PureComponent",
			"Component",
			"useEffect",
			"useLayoutEffect",
			"Children",
		],
	},
	"react-dom": "ReactDOM",
	"react-dom": {
		varName: "ReactDOM",
		namedExports: [
			"createPortal",
			"flushSync",
		],
	},
	"@material-ui/core": "MaterialUI",
};

const onRebuild = {
	name: 'onRebuild',
	setup(build) {
		let count = 0;
		build.onEnd(result => {
			console.log('watch/build succeeded:', result)
			fs.cpSync("./target-lib","./target/lib",{ recursive: true });
			fs.cpSync("./src/index.html","./target/index.html");
			fs.cpSync("./src/img","./target/img",{ recursive: true });
			fs.cpSync("./target","../target/classes/app-host",{ recursive: true });
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
		loader: { '.jpg': 'file', '.png': 'file' },
		platform: "browser",
		bundle: true,
		minify: true,
		sourcemap: true,
		outdir: "./target",
	})
	await ctx.watch();
	await ctx.rebuild();
})();

function sleep() { setTimeout(sleep,1000); }
sleep();
