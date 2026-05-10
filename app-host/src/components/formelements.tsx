import React from "react";
import MUI from "@material-ui/core";
import Style from "./Style";
const { Snackbar } = MUI;

// field types:
// - text
// - textarea
// - number
// - date
// - button
// - checkbox
// - radio
// - select

export function text(
	name: string,
	label: string,
	value: string | number | null,
	onChange: (name: string, value: string) => void,
	required = false,
	props = {}
) {
	return <div className="form-group">
		<label>
			{label} {required && <span style={{ color: "red" }}>*</span>}
			<input type="text" name={name}
				value={value!=null ? value : ""}
				onChange={e => onChange(name,e.target.value)}
				required={required}
				{...props}
				/>
		</label>
	</div>
}
export function textarea(
	name: string,
	label: string,
	value: string,
	onChange: (name: string, value: string) => void,
	required = false,
	props = {}
) {
	return <div className="form-group">
		<label>
			{label} {required && <span style={{ color: "red" }}>*</span>}
			<textarea
				name={name}
				value={value !=null ? value : ""}
				onChange={e => onChange(name,e.target.value)}
				required={required}
				{...props} />
		</label>
	</div>
}
export function number(
	name: string,
	label: string,
	value: string,
	onChange: (name: string, value: string) => void,
	required = false,
	props = {}
) {
	return <div className="form-group">
		<label>
			{label} {required && <span style={{ color: "red" }}>*</span>}
			<input
				type="number"
				name={name}
				value={value!=null ? value : ""}
				onChange={e => onChange(name,e.target.valueAsNumber)}
				required={required}
				{...props} />
		</label>
	</div>
}
export function slider(
	name: string,
	label: string,
	value: string,
	onChange:(name: string, value: number) => void,
	required = false,
	props = {}
) {
	return <div className="form-group">
		<label>
			{label} {required && <span style={{ color: "red" }}>*</span>}
			<input
				type="range"
				name={name}
				value={value!=null ? value : 0}
				onChange={e => onChange(name, e.target.valueAsNumber)}
				required={required}
				{...props} />
		</label>
	</div>
}
export function date(
	name: string,
	label: string,
	value: string | null,
	onChange: (name: string, value: string) => void,
	required = false,
	props = {}
) {
	return <div className="form-group">
		<label>
			{label} {required && <span style={{ color: "red" }}>*</span>}
			<input
				type="date"
				name={name}
				value={value!=null ? value : ""}
				onChange={e => onChange(name,e.target.value)}
				required={required}
				{...props} />
		</label>
	</div>
}
export function checkbox(
	name: string,
	label: string,
	value:  boolean,
	onChange: (name: string, value:  boolean) => void,
	props = {}
) {
	return <div className="form-group">
		<label>
			<input type="checkbox"
				name={name}
				checked={value!=null ? value : false}
				onChange={e => onChange(name,e.target.checked)}
				{...props} />
			{label}
		</label>
	</div>
}
export function button(
	name: string,
	label: string,
	onClick: () => void,
	props = {}
	) {
	return <button name={name} onClick={onClick} {...props}>
		{label}
	</button>
}
export function select(
	name:string,
	label: string,
	value: string | null,
	onChange: (name: string, value: string | null) => void,
	items: { value: string, text: string, disabled?: boolean }[],
	required = false,
	props = {}
) {
	return <div className="form-group">
		<label>
			{label} {required && <span style={{ color: "red" }}>*</span>}
			<select
				name={name}
				value={value!=null ? value : ""}
				onChange={e => onChange(name,e.target.value || null)}
				required={required}
				{...props}
			>
				<option value="">{props.placeholder || "Wählen..."}</option>
				{items
					.sort((a,b) => (a.text||"").localeCompare(b.text||""))
					.map(item => <option value={item.value} disabled={item.disabled||undefined}>{item.text}</option>)}
			</select>
		</label>
	</div>
}

export function selectMany(
	name:string,
	label: string,
	value: string | null,
	onChange: (name: string, value: string | null) => void,
	items: { value: string, text: string }[],
	required = false,
	props = {}
) {
	let ids = (value||"").split(",");
	return <div className="form-group">
		<label>
			{label} {required && <span style={{ color: "red" }}>*</span>}
			<select multiple
				name={name}
				onChange={e => onChange(name,
					[...e.target.selectedOptions].map(o => o.value).join(","))}
				required={required}
				{...props}
			>
				<option value="">Wählen...</option>
				{items.map(item =>(
					<option key={item.value} value={item.value}
						selected={ids.indexOf(item.value)>=0 ? true : undefined}>
						{item.text}
					</option>
				))}
			</select>
		</label>
	</div>
}

export function radio(
	name: string,
	label: string,
	value: string,
	onChange: (name: string, value: string) => void,
	radioValue: string ,
	required = false,
	props = {}
) {
	return (
		<div className="form-group">
		<label>
			<input
			type="radio"
			name={name}
			value={radioValue}
			checked={value === radioValue}
			onChange={e => onChange(name, radioValue)}
			required={required}
			{...props}
			/>
			{label}
			{required && <span style={{ color: "red" }}>*</span>}
		</label>
		</div>
	)
}

export function file(
	name: string,
	label: string,
	value: File[] | null,
	onChange: (name: string, value: FileList | null) => void,
	required = false,
	props = {},
	buttonStyle = {},
	messageInfo = false,
) {
	let inputRef = React.useRef<HTMLInputElement>();
	return (
		<div className="form-group">
			{/*<h4 style={{textAlign: "center"}}> &#9888; Bitte beachten Sie: Das erste Foto wird als Titelbild für die Unterkunftsgalerie verwendet</h4>
			{messageInfo &&(
				<h4 style={{padding: '15px', fontweight:"normal",}}>Mit den Auf- und Ab-Pfeilen können Sie die Reihenfolge der Bildanzeige in der Galerie steuern.</h4>
			)}*/}
			<label>
				{label} {required && <span style={{ color: "red" }}>*</span>}
				<input
					type="file"
					name={name}
					onChange={(e) => onChange(name, e.target.files)}
					required={required}
					{...props}
					ref={inputRef}
					style={{display:"none"}}
				/>
				<button style={buttonStyle} onClick={e => inputRef.current?.click()}>Fotos hinzufügen</button>
			</label>

		</div>
	);
}

export function Button({ name, label, onClick, loading, ...rest }: {
	name: string, label: string, onClick: () => void
}) {
	return (
		<button name={name} onClick={onClick} {...rest}>
			<Style>{`
				& .btn-content { text-align: center; }
			`}</Style>
			<div className="btn-content">
				{loading ? <LoadingIcon /> : label}
			</div>
		</button>
	);
}

function BaseField({
	name = "", label = "", required = false, error = null, children
}:{
	name: string,
	label: string,
	error: string | null,
	required: boolean
}) {
	return <div className="form-group base-field">
			<label>
				{label} {required && <span style={{ color: "red" }}>*</span>}
				{children}
				{error && <small className="error-message">{error}</small>}
			</label>
		</div>
}
const val = v => (v ?? "");

export function Text(props) {
	const { name, value, onChange, required, error, ...rest } = props;
	return (
		<BaseField {...props}>
			<input
				type="text"
				name={name}
				value={val(value)}
				onChange={e => onChange(name, e.target.value)}
				required={required}
				className={error ? "input-error" : ""}
				{...rest}
			/>
		</BaseField>
	);
}

export function TextArea(props) {
	const { name, value, onChange, required, error, ...rest } = props;
	return (
		<BaseField {...props}>
			<textarea
				name={name}
				value={val(value)}
				onChange={e => onChange(name, e.target.value)}
				required={required}
				className={error ? "input-error" : ""}
				{...rest}
			/>
		</BaseField>
	);
}

export function Number(props) {
	const { name, value, onChange, required, error, ...rest } = props;
	return (
		<BaseField {...props}>
			<input
				type="number"
				name={name}
				value={val(value)}
				onChange={e => onChange(name, e.target.valueAsNumber)}
				required={required}
				className={error ? "input-error" : ""}
				{...rest}
			/>
		</BaseField>
	);
}

export function Date(props) {
	const { name, value, onChange, required, error, ...rest } = props;
	return (
		<BaseField {...props}>
			<input
				type="date"
				name={name}
				value={val(value)}
				onChange={e => onChange(name, e.target.value)}
				required={required}
				className={error ? "input-error" : ""}
				{...rest}
			/>
		</BaseField>
	);
}

export function Password(props) {
	const { name, label, value, onChange, required, error, ...rest } = props;
	const [show, setShow] = React.useState(false);

	return <div className="form-group anchor">
		<Style>{`
			& button {
				position: absolute;
				right: 0;
				top: ${label ? "38px" : "18px"};
				transform: translateY(-50%);
				background: none;
				color: gray;
			}
		`}</Style>
		<BaseField {...props}>
			<input
				type={show ? "text" : "password"}
				name={name}
				value={val(value)}
				onChange={e => onChange(name, e.target.value)}
				className={error ? "input-error" : ""}
				required={required}
				{...rest}
			/>
			{error && <small className="error-message">{error}</small>}
			<button
				type="button"
				onClick={() => setShow(!show)}
				title={show ? "Passwort verbergen" : "Passwort anzeigen"}
			>
				<MuiIcon name={show ? "visibility_off" : "visibility"} />
			</button>
		</BaseField>
	</div>
}

export function Slider(props) {
	const { name, value, onChange, required, ...rest } = props;
	return (
		<BaseField {...props}>
			<input
				type="range"
				name={name}
				value={value!=null ? value : 0}
				onChange={e => onChange(name, e.target.valueAsNumber)}
				required={required}
				{...rest}
			/>
		</BaseField>
	);
}

export function Checkbox({ name, label, value, onChange, ...rest }) {
	return (
		<div className="form-group">
			<label>
				<input
					type="checkbox"
					name={name}
					checked={value ?? false}
					onChange={e => onChange(name, e.target.checked)}
					{...rest}
				/>
				{label}
			</label>
		</div>
	);
}

export function Radio(props) {
	const { name, value, radioValue, onChange, required, label, ...rest } = props;
	return (
		<div className="form-group">
			<label>
				<input
					type="radio"
					name={name}
					value={radioValue}
					checked={value == radioValue}
					onChange={() => onChange(name, radioValue)}
					required={required}
					{...rest}
				/>
				{label}
				{required && <span style={{ color: "tomato" }}>*</span>}
			</label>
		</div>
	);
}

export function Select(props) {
	const { name, value, onChange, items=[], required, error, ...rest } = props;
	return (
		<BaseField {...props}>
			<select
				name={name}
				value={val(value)}
				onChange={e => onChange(name, e.target.value || null)}
				required={required}
				{...rest}
			>
				<option value="">{rest.placeholder || "Wählen..."}</option>
				{items
					.sort((a, b) => (a.text || "").localeCompare(b.text || ""))
					.map(item => <option key={item.value} value={item.value} disabled={item.disabled || undefined}>{item.text}</option>)}
			</select>
		</BaseField>
	);
}

export function SelectMany(props) {
	const { name, value, onChange, items, required, error, ...rest } = props;
	const ids = (value || "").split(",");

	return (
		<BaseField {...props}>
			<select
				multiple name={name} required={required}
				onChange={e => onChange(name, [...e.target.selectedOptions].map(o => o.value).join(","))}
				{...rest}
			>
				{items.map(item => (
					<option
						key={item.value} value={item.value}
						selected={ids.includes(item.value) || undefined}
					>
						{item.text}
					</option>
				))}
			</select>
			{error && <small className="error-message">{error}</small>}
		</BaseField>
	);
}

export function File ({ name="", label="", value=null, onChange, required=false, buttonStyle={}, messageInfo=false, error=null, ...props }:{
	name: string,
	label: string,
	value: File[] | null,
	onChange: (name: string, value: FileList | null) => void,
	required: boolean,
	buttonStyle: object,
	messageInfo: boolean
}) {
	let inputRef = React.useRef<HTMLInputElement>();
	return (
		<div className="form-group">
			{/*<h4 style={{textAlign: "center"}}> &#9888; Bitte beachten Sie: Das erste Foto wird als Titelbild für die Unterkunftsgalerie verwendet</h4>
			{messageInfo &&(
				<h4 style={{padding: '15px', fontweight:"normal",}}>Mit den Auf- und Ab-Pfeilen können Sie die Reihenfolge der Bildanzeige in der Galerie steuern.</h4>
			)}*/}
			<label>
				{label} {required && <span style={{ color: "red" }}>*</span>}
				<input
					type="file"
					name={name}
					onChange={(e) => onChange(name, e.target.files)}
					required={required}
					{...props}
					ref={inputRef}
					style={{display:"none"}}
				/>
				<button style={buttonStyle} onClick={e => inputRef.current?.click()}>Fotos hinzufügen</button>
				{error && <small className="error-message">{error}</small>}
			</label>
		</div>
	);
}

export function MuiIcon ({ name = "", size, animation = "", color = "inherit", ...props }) {
	return (
		<div className="mui-icon-wrapper">
			<Style>{`
				& span {
					font-size: ${size||20}px;
					color: ${color};
				}
				& .mui-pulse { animation: mui-pulse 2s infinite; }
				@keyframes mui-pulse {
					0%   { font-variation-settings: "wght" 200; }
					50%  { font-variation-settings: "wght" 700; }
					100% { font-variation-settings: "wght" 200; }
				}

				& .mui-spin {
					display: inline-block;
					animation: mui-spin 2s linear infinite;
				}
				@keyframes mui-spin {
					0%   { transform: rotate(0deg); }
					100% { transform: rotate(360deg); }
				}

				& .mui-shake { animation: mui-shake 0.6s infinite; }
				@keyframes mui-shake {
					0%,100% { transform: translateX(0); }
					20% { transform: translateX(-2px); }
					40% { transform: translateX(2px); }
					60% { transform: translateX(-2px); }
					80% { transform: translateX(2px); }
				}

				& .mui-bounce { animation: mui-bounce 0.8s infinite; }
				@keyframes mui-bounce {
					0%,100% { transform: translateY(0); }
					50%     { transform: translateY(-4px); }
				}
			`}</Style>

			<span className={`material-symbols-outlined mui-${animation}`}>
				{name}
			</span>
		</div>
	);
};
export function LoadingIcon () {
	return <MuiIcon name="progress_activity" animation="spin" />
}

export function Alert(props: { // copied from errorhandling
	open: boolean,
	/** hide after n milliseconds */
	autoHideDuration?: number,
	onClose: () => void,
	/** HEX | rgba */
	backgroundColor?: string,
	/** icon for action button. materials symbols & icons.  */
	actionIcon?: string | "close",
	/** main icon for the alert. materials symbols & icons. */
	icon: string,
	/** main icon color. */
	iconColor: string,
	text: string,
	textColor?: string
}) {
	const { open, autoHideDuration, onClose, backgroundColor, actionIcon, icon, iconColor, text, textColor, ...rest } = props;
	return <Snackbar open={open} key={props?.key} autoHideDuration={props?.autoHideDuration||null}
			{...rest}>
		<div className="snack-alert">
			<Style>{`
				& {
					broder-radius:4px; display:flex; align-items:center; justify-content:space-between;
					padding: 6px 16px;
					box-shadow: 0px 3px 3px -2px rgba(0, 0, 0, 0.2), 0px 3px 4px 0px rgba(0, 0, 0, 0.14), 0px 1px 8px 0px rgba(0, 0, 0, 0.12);
					background-color: ${backgroundColor};
					overflow: hidden;
				}
				& .snack-alert-message { padding: 8px 0; color: ${textColor};
					max-height: 200px; max-width: 500px; overflow: auto;
				}
				& .snack-alert-icon { display: flex; opacity: 0.9; padding: 7px 0; margin-right: 12px; }
				& .snack-alert-action {
					display: flex; align-items: center; margin-left: auto; margin-right: -8px; padding-left: 16px;
				}
				& .snack-alert-action > button { background-color: inherit; }
			`}</Style>
			<div className="snack-alert-icon">
				<MuiIcon name={icon} size={props.size||22} color={iconColor} />
			</div>
			<div className="snack-alert-message">{text}</div>
			<div className="snack-alert-action">
				<button color="inherit" size="small" onClick={onClose}>
					<MuiIcon name={actionIcon||"close"} size={22} color={"rgba(0, 0, 0, 0.54)"} />
				</button>
			</div>
		</div>
	</Snackbar>
}
