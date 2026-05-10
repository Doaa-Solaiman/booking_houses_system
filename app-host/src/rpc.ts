import { WsComm, MessageActions } from "@utils/comm";
import { exec } from "@utils/workers"; // helps for very simple stuff, WS comm was already a bit too much
import { uuid } from "@utils/misc";
import { progress } from "@utils/timing";

export let wsCallbacks = {} as Record<string,Function>;

let wsCallback = MessageActions.callFunc({
	response: (requestId,result) => {
		wsCallbacks[requestId] && wsCallbacks[requestId]({ result, error: null });
	},
	exception: (requestId,ex) => {
		console.log("exception",requestId,ex);
		wsCallbacks[requestId] && wsCallbacks[requestId]({ result: null, error: ex });
	},
	reload: () => {
		wsCallbacks["reload"]?.();
	},
})
let wsActions = MessageActions.actions({
//	title: MessageActions.documentTitle,
//	url: MessageActions.gotoUrl,
//	hash: MessageActions.setHash,
//	hpush: MessageActions.pushState,
//	hrepl: MessageActions.replaceState,
	call: wsCallback,
});
let send;

type Callback = ({ result, error, requestId }) => void;
export function call(fn: string, arg0: any, cb: Callback): void;
export function call(fn: string, arg0: any, arg1: any, cb: Callback): void;
export function call(fn: string, arg0: any, arg1: any, arg2: any, cb: Callback): void;
export function call(fn: string, arg0: any, arg1: any, arg2: any, arg3: any, cb: Callback): void;
export function call(fn: string, arg0: any): Promise<any>;
export function call(fn: string, arg0: any, arg1: any): Promise<any>;
export function call(fn: string, arg0: any, arg1: any, arg2: any): Promise<any>;
export function call(fn: string, arg0: any, arg1: any, arg2: any, arg3: any): Promise<any>;
export function call(fn: string, ...args: any[]) { return callImpl(null,fn,...args); }
export var callToListen = (fn: string, ...args: any[]) => callImpl({ mode:"listen" },fn,...args);
/**
 * call a remote function
 *
 * old style (request/response):
 * - last arg is callback function which is called on response with a object { result, error, requestId }
 * - the requestId is also returned
 *
 * old style 2 (request/response + listen):
 * - options.mode must be set to "listen"
 * - last arg is callback function which is also called on intermediate events
 * - the requestId is also returned
 *
 * new style (request/response + promise):
 * - no function is passed in args
 * - a Promise is returned
 */
var callImpl = (options: any, fn: string, ...args: any[]) => {
	if (!send) return;
	let last = args[args.length-1];
	let resolve = typeof last=="function" ? args.pop() : null;
	let listen = "listen"==options?.mode;
	let requestId = uuid();
	if (resolve) {
		wsCallbacks[requestId] = ({ result, error }) => {
			if (!listen || !result || error)
				delete wsCallbacks[requestId];
			return resolve({ result, error, requestId });
		}
		send(requestId,fn,...args);
		return requestId;
	} else {
		return new Promise((resolve,reject) => {
			wsCallbacks[requestId] = ({ result, error }) => {
				if (!listen || !result || error)
					delete wsCallbacks[requestId];
				if (error)
					reject(error);
				else resolve(result);
			}
			send(requestId,fn,...args);
		});
	}
}

export function initRpc<T extends object>(rpc:T): T {
	return new Proxy<T>(rpc,{
		get(target, property: string) {
			return (...args) => callImpl(null,property,...args);
		}
	});
}

export let session;

export function initWsComm() {
	return new Promise((resolve,reject) => {
		setTimeout(() => {
			initWsCommDelayed();
			resolve(true);
		},100);
	});
}
function initWsCommDelayed() {
	let params = new URLSearchParams(globalThis.location.search.substring(1));
	session = params.get("session") || window["name"].startsWith("UI0") && window["name"] || null;
	let wsBase = (location.origin+location.pathname).replace(/^http/,"ws").replace(/\/$/,"");
	let wsUrl = wsBase+"/ds?"+session;

	if (!false && window.Worker) {
		// somewhat tricky: esbuild mangles names. the function passed to worker using exec() has
		// also such mangled names unknown at coding time. a temp/fake function is used to know
		// the name at runtime, for the inject prop at exec()
		let wsUrl_named = (() => {}) as any;
		wsUrl_named.value = wsUrl;
		// id:"ws" opens a permanent worker, kept open for WS comm
		// fn(function): globalThis is worker's global context
		exec({ id:"ws", fn:() => {
				let forwardCalls = once => once.forEach(cmd => cmd.call && postMessage(cmd.call));
				globalThis.ws = new WsComm(wsUrl_named);
				globalThis.ws.addAction("once",forwardCalls);
			},
			inject: { [WsComm.name]: WsComm, [wsUrl_named.name]: wsUrl_named.value },
			import: ["lib/raw/inflate.min.js","lib/raw/fast-json-patch.min.js"],
		});
		// fn(function) runs in worker, globalThis is worker's global context
		// cb(callback) runs at caller, is called twice:
		// 1. result of fn (exec), 2. postMessage (see above) that forwards WS msg
		send = (...args) => (progress(args), exec({ id:"ws",
			fn:(...args) => globalThis.ws.send(...args), args:args,
			cb:(data) => data && (progress(0), wsCallback(data)) }));
	} else {
		let ws = new WsComm(wsUrl);
		ws.addAction("once",wsActions);
		send = ws.send.bind(ws);
	}
}
