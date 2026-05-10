import React from "react";
import { Button, Text, TextArea, Select } from "./components/formelements";
import { rpc, globalState } from "./index";

export function StringsUI(props) {
	type Item = Partial<types.Strings>;
	type ItemList = Item[];

	let loggedIn = props.loggedIn;
	let [context, setContext] = React.useState(loggedIn=="dev" ? "*" : props.context);
	let [items, setItems] = React.useState<ItemList>([]);
	let [item, setItem] = React.useState<Item>(null);
	let [itemRaw, setItemRaw] = React.useState(null);
	let [previous, setPrevious] = React.useState<Item>(null);
	let [recent, setRecent] = React.useState<Item>(null);
	let [newItem, setNewItem] = React.useState<Item>(null);
	let [copied, setCopied] = React.useState(false);
	let [purposeTexts, setPurposeTexts] = React.useState([]);
	let [hintsTexts, setHintsTexts] = React.useState([]);

	const fetchData = async () => {
		setItems(await rpc.loadStrings());
		setItem(null);
		setItemRaw(null);
		setPurposeTexts(await rpc.loadStringsByKey("strings:purpose"));
		setHintsTexts(await rpc.loadStringsByKey("strings:hints"));
	};
	React.useEffect(() => { fetchData(); },[loggedIn]);

	let onChange = (name: string, value: string) => {
		setItem({ ...item, [name]: value });
	};

	let formdata = item || {}; let purpose = formdata.purpose; let emailtextdata = {};
	if (context=="email") {
		let item2 = !itemRaw ? formdata : {
			lang: itemRaw.split(/[\s/]+/)[0],
			purpose: purpose = itemRaw.split(/[\s/]+/)[1],
			context: context,
		};
		let data = items.filter(i => i.context==item2.context && i.purpose==item2.purpose && i.lang==item2.lang);
		emailtextdata.maxVersion = data.reduce((r,s) => Math.max(r,s.version),0);
		(data||[]).forEach(i => {
			if (i.organization_id==globalState.loggedIn) { // org-private string (other orgs ignored)
				emailtextdata[i.skey] = formdata[i.skey] || i.svalue; // set definitively
				emailtextdata[i.skey+"_id"] = i.id;
			} else if (i.organization_id==null) { // public/global string
				if (emailtextdata[i.skey]==null) { // only set if still unset
					emailtextdata[i.skey] = formdata[i.skey] || i.svalue;
					emailtextdata[i.skey+"_id"] = i.id;
				}
			}
		});
	}
	let onSave = async () => {
		if (context=="email") {
			let toSave = [{
				id: emailtextdata.subject_id,
				svalue: emailtextdata.subject,
				comment: formdata.comment,
				version: emailtextdata.maxVersion,
			},{
				id: emailtextdata.text_id,
				svalue: emailtextdata.text,
				comment: formdata.comment,
				version: emailtextdata.maxVersion,
			},{
				id: emailtextdata.html_id,
				svalue: emailtextdata.html,
				comment: formdata.comment,
				version: emailtextdata.maxVersion,
			}];
			await Promise.all(toSave.map(s => rpc.saveString(s))); // we manage all other fields on server-side
			fetchData().then(() => setItemRaw(itemRaw));
		} else {
			let stringId = await rpc.saveString(item);
			fetchData().then(() => setItemRaw(stringId));
		}
	}
	let onRemove = () => {
		alert("you're dev. use db.");
	}

	let contextfilter = i => context=="*" || context==i.context;

	return <div className="flexh centerh pd-lg">
		<div className="form">
			<div className="flexh">
				{loggedIn=="dev" && (
					<Select name="strings_context"
						value={context}
						onChange={(name, value) => { setContext(value); }}
						items={["*", ...new Set(items.map(i => i.context))]
							.map(p => ({ value: p, text: p }
						))}
					/>
				)}
				<Select name="strings_item"
					value={formdata.id || itemRaw}
					onChange={async (name, value) => {
						setItemRaw(value);
						const selectedItem = items.find(i => i.id==value);
						setItem(selectedItem ? { ...selectedItem } : null);
						setPrevious(null); setRecent(null);
						if (selectedItem && selectedItem.previous_id) {
							setPrevious(await rpc.loadString(selectedItem.previous_id));
						}
						newItem && setItems(items.filter(i => i.id !== newItem.id));
					}}
					items={context=="email"
						? [...new Set(items.filter(contextfilter).map(i => i.lang+" / "+i.purpose))]
						.map(p => ({ value: p, text: purposeTexts[p.split(/[\s/]+/)[1]] || p }))
						: items.filter(contextfilter)
						.map(i => ({ value: i.id, text: i.lang+" / "+i.context+" / "+i.purpose+" / "+i.skey+
							(i.organization_id ? " @ "+i.organization_id : "") }))}
				/>
			</div>

			{context=="email" && <h2>E-Mail Texte</h2>}
			{context!="email" && <h2>Programm Texte</h2>}
			{purposeTexts[purpose] && <h3>{purposeTexts[purpose]}</h3>}
			{hintsTexts[purpose] && <aside>{hintsTexts[purpose]}</aside>}

			{context=="email" && <>
				<Text name="subject" label="Betreff" value={emailtextdata.subject} onChange={onChange} />
				<TextArea name="text" label="Text Inhalt" value={emailtextdata.text} onChange={onChange} />
				<TextArea name="html" label="HTML Inhalt" value={emailtextdata.html} onChange={onChange} />
			</>}
			{context!="email" && <>
				<div style={{display:"flex"}}>
					<div style={{display:"flex",width:"30%"}}>
						<Text name="lang" label="Sprache" value={formdata.lang} onChange={onChange} disabled={!item}/>
						<Text name={"context"} label={"Kontext"} value={formdata.context} onChange={onChange} disabled={true} />
					</div>
					<Text name="purpose" label="Zweck" value={formdata.purpose} onChange={onChange} disabled={true} />
					<Text name="skey" label={"Schlüssel"} value={formdata.skey} onChange={onChange} disabled={true} />
				</div>
				<div className="flexh">
					Text
					{item && ", Version "+item.version}
					{previous && <Button onClick={async () => {
							setRecent(recent || item);
							setItem(previous);
							if (previous.previous_id)
								setPrevious(await rpc.loadString(previous.previous_id));
							else setPrevious(null);
						}}
						label="Show previous version"
					/>}
					{recent && <Button onClick={async () => {
							setRecent(null); setItem(recent);
							if (recent.previous_id)
								setPrevious(await rpc.loadString(recent.previous_id));
							else setPrevious(null);
						}}
						label="Back to recent version"
					/>}
				</div>
				<TextArea name="svalue" value={formdata.svalue} onChange={onChange} disabled={recent && recent!=item} />
			</>}
			<Text name="comment" label="Kommentar (Was wurde geändert? Warum?)" value={formdata.comment} onChange={onChange} />
			<div>
				<Button name="saveStrings" label="Speichern" onClick={onSave} disabled={!item || recent && recent!=item} className="save" />
				{loggedIn=="dev" &&
				<Button name="removeStrings" label="Löschen" onClick={onRemove} disabled={!item || recent && recent!=item} className="remove"/>}
			</div>
		</div>
	</div>
}
