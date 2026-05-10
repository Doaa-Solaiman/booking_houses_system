// copied from platform-iot / iot-webui
import React from "react";

type SimpleStateC<T> = ((changes: Partial<T> | Record<string,any>) => void) & { [K in keyof T]: (value: any) => void }
type SimpleStateX<T> = Readonly<{
	state: T, set: SimpleStateC<T>,
	update: () => void, mod: number, key?: string,
	bind?: { [K in keyof T]: { value: T[K]; onChange: (value: any) => void } };
}>;

export function simpleState<T extends object>(initial: T | (() => T)): [T, SimpleStateC<T>, SimpleStateX<T>, ()=>void, number] {
	let [meta] = React.useState({ stopOnUpdate: false, onChange: null });
	let [state] = React.useState(initial);
	let [mod,setMod] = React.useState(0);
	let update = (c) => { if (meta.stopOnUpdate) debugger; if (meta.onChange?.(c)) return; setMod(m => m+1); }
	let updateBreak = () => meta.stopOnUpdate = true;
	let set = createSetSetters(state,(c: Partial<T> = {}) => { Object.assign(state,c); update(c); });
	let bind = React.useMemo(() => createBindObject(state,set),[]);
	let onChange = onChange => meta.onChange = onChange;
	return [ state, set, { state, set, update, updateBreak, mod, bind, onChange }, update, mod ]; // decompose to [s,sc,sx,<legacy>]
}

export function createBindObject<T extends object>(state: T, set: SimpleStateC<T>): SimpleStateX<T>["bind"] {
	let bind = {} as any;
	for (let key in state)
		bind[key] = {
			get value() { return state[key]; },
			onChange(value: any) { set({ [key]: value } as Partial<T>); }
		};
	return bind;
}
export function createSetSetters<T extends object>(state: T, set: SimpleStateC<T>): SimpleStateC<T> {
	for (let key in state)
		Object.defineProperty(set,key,{
			get: () => (value: any) => set({ [key]: value } as Partial<T>),
			set(value: any) { set({ [key]: value } as Partial<T>); },
			enumerable: true,
			configurable: true,
		});
	return set;
}

export function undentStr(str,tablen = 4) {
	let lines = str.replace(/^\n|\n\s*$/g,"").split("\n");
	let expand = l => l.replace(/\t/g," ".repeat(tablen));
	let indent = Math.min(...lines.filter(l => l.trim()).map(l => expand(l).match(/^ */)[0].length));
	return lines.map(l => expand(l).slice(indent)).join("\n");
}
export const tl2str = (strings, ...values) => strings.reduce((r,s,i) => r+(values[i-1]??"")+s);
export const undent = (strings, ...values) =>
	undentStr(tl2str(strings,values),typeof values.at(-1)=="number" ? values.pop() : 4);

let window2 = (parent||window);
let debounceTimer = null;
let lastPathRef = window2.location.pathname;
let segments = window2.location.pathname.split("/").filter(Boolean);
let stateWasSet = false;
let setSegment = (index: number, value: string, replaceHistory) => {
	if (segments[index]==value) return;
	segments[index] = value;

	if (debounceTimer) clearTimeout(debounceTimer);
	debounceTimer = setTimeout(() => {
		let newPath = "/" + segments.filter(Boolean).join("/");
		if (stateWasSet) {
			segments[index] = value;
			newPath = "/" + segments.filter(Boolean).slice(0,index+1).join("/");
			if (newPath==lastPathRef) return;
		}
		let method = replaceHistory ? "replaceState" : "pushState";
		window2.history[method]({ url: newPath },"",newPath);
		setTimeout(() => window2.dispatchEvent(new PopStateEvent("popstate",{ state: { url: newPath } })));
		lastPathRef = newPath;
		stateWasSet = true;
	});
};

export function usePathSegmentState({ value, onChange },{ index, replaceHistory = false }) {
//	debugger;
	let lastValueRef = React.useRef<string | null>(null);

	// Sync path -> external state
	React.useEffect(() => {
		let updateFromPath = () => {
			let segments = window2.location.pathname.split("/").filter(Boolean);
			let segment = segments[index];
			if (segment!=lastValueRef.current) {
				lastValueRef.current = segment;
				onChange(segment);
			}
		};
		updateFromPath(); // run once on mount
		return on(window2,"popstate",updateFromPath);
	},[index,onChange]);

	// Sync external state -> path
//	React.useEffect(() => setSegment(index,value,replaceHistory),[value,index]);
	setSegment(index,value,replaceHistory);
}

export function useLocalStorage(key, defaultValue?) {
	let [state,setState] = React.useState(() => {
		let stored = localStorage.getItem(key);
		try {
			return stored!=null ? JSON.parse(stored) : defaultValue;
		} catch (e) {
			return defaultValue;
		}
	});
	React.useEffect(() => localStorage.setItem(key,JSON.stringify(state)),[key,state]);
	return [state,setState];
}

export function useSessionStorage(key, defaultValue?) {
	let [state,setState] = React.useState(() => {
		let stored = sessionStorage.getItem(key);
		try {
			return stored!=null ? JSON.parse(stored) : defaultValue;
		} catch (e) {
			return defaultValue;
		}
	});
	React.useEffect(() => sessionStorage.setItem(key,JSON.stringify(state)),[key,state]);
	return [state,setState];
}

export let idTimestampRandom = () => Date.now().toString(36)+"-"+Math.random().toString(36).slice(2);

export function labelsFromSchema(schema) {
	return schema.filter(f => firstString(f.show,f.type).startsWith("label:")).reduce((r,f) =>
		(r[f.name] = firstString(f.show,f.type).slice("label:".length), r),{});
}

export function firstString(...args) { return args.find(a => typeof a=="string"); }

export function idset(x) { return new Set(ids(x)); }
export function ids(x) {
	if (!Array.isArray(x)) x = [x];
	return x.filter(Boolean).map(x => typeof x=="string" ? x : x.id).filter(Boolean);
}

///////////////// form utils /////////////////////
// minimal primitive builder (Yup/Zod inspired)
export const validator = {
	string: () => ({
		required: (msg) => (v) => (!v?.trim() || v?.length==0) ? msg : null,
		min: (len, msg) => (v) => (!v || v.length < len ? msg : null),
		max: (len, msg) => (v) => (!v || v.length > len ? msg : null),
		email: (msg) => (v) => !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v) ? msg : null,
	}),
};
export function createValidator({ schema, cross }) {
	const validateField = (n, v) => {
		const rules = schema[n]; if (!rules) return null;
		for (const r of rules) { const m = r(v); if (m) return m; }
		return null;
	};

	const validateAll = fd => {
		const errors = {};
		if (fd)
			for (const n in schema) {
				const e = validateField(n, fd[n]);
				if (e) errors[n] = e;
			}
		return Object.assign(errors, cross(fd) || {});
	};

	return { validateField, validateAll };
}
export function useFormValidation(initialData, validateField, validateAll) {
	const [formData, setFormData] = React.useState(initialData);
	const [errors, setErrors] = React.useState({});
	const [touched, setTouched] = React.useState({});
	const timers = React.useRef({});

	const handleChange = (n, v) => {
		setFormData(p => ({ ...p, [n]: v }));
		if (!touched[n]) setTouched(p => ({ ...p, [n]: true }));
	};

	React.useEffect(() => {
		if (!formData) return;
		for (const n of Object.keys(formData)) {
			if (!touched[n]) continue;
			if (timers.current[n]) clearTimeout(timers.current[n]);

			timers.current[n] = setTimeout(() => {
				const fieldErr = validateField(n, formData[n]);
				const crossErrs = validateAll(formData);

				setErrors(prev => {
					const next = { ...prev };
					delete next[n]; // clear previous errors for this field
					for (const key in crossErrs) delete next[key]; // clear cross-field names

					if (fieldErr) next[n] = fieldErr; // apply field error

					// apply cross-field errors (only if dependent fields touched)
					Object.keys(crossErrs).forEach((k) => {
						if (touched[k]) next[k] = crossErrs[k];
					});

					return next;
				});
			}, 300);
		}
	},[formData, touched]);

	const checkSubmit = () => {
		const errs = validateAll(formData);
		setErrors(errs);
		return Object.keys(errs).length == 0;
	};
	const resetForm = (initialData) => {
		setFormData(initialData);
		setErrors({});
		setTouched({});
	}

	const allTouched = Object.keys(initialData).every(key => touched[key]);
	const isFormValid = /* allTouched &&*/ Object.keys(validateAll(formData)).length == 0;
	// console.log("validateAll: ",validateAll(formData));

	return { formData, setFormData, errors, handleChange, checkSubmit, isFormValid, resetForm };
}
