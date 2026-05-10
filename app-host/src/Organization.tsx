import React from "react";
import { Button, Text, TextArea, Number, Checkbox, Select } from "./components/formelements";
import * as types from "./types";
import { rpc } from "./index";
//import { id } from "./utils";

export function Organization(props) {
	type Item = Partial<types.Organization>;
	type ItemList = Item[];

	let [items, setItems] = React.useState<ItemList>([]);
	let [item, setItem] = React.useState<Item>(null);
	let [newItem, setNewItem] = React.useState<Item>(null);
	let loggedIn = props.loggedIn;
	let [copied, setCopied] = React.useState(false);

	React.useEffect(() => {
		const fetchData = async () => {
			if (loggedIn == "dev") {
				setItems(await rpc.loadOrganizations());
			} else if (loggedIn) {
				let org = await rpc.loadOrganization(loggedIn);
				setItems([org]);
				setItem(org);
			}
		};
		fetchData();
	}, [loggedIn]);

	let onChange = (name: string, value: string) => {
		setItem({ ...item, [name]: value });
	};
	let onCustomizeChange = (name: string, value: string) => {
		let formdata = item || {};
		let customizedata = formdata?.customizeInfos ? JSON.parse(formdata.customizeInfos) || {} : {}
		customizedata = { ...customizedata, [name]: value }
		let customizeInfos = JSON.stringify(customizedata,null,"\t")
		onChange("customizeInfos",customizeInfos);
	};

	const onSave = async () => {
		if (item && item.id) {
			if (loggedIn == "dev") {
				await rpc.saveOrganization(item);
				setItems(await rpc.loadOrganizations());
				setItem(null);
				setNewItem(null);
			} else {
				await rpc.saveOrganization(item);
				let org = await rpc.loadOrganization(loggedIn);
				setItems([org]);
				setItem(org);
			}
		} else {
			console.error("'id' is missing", item);
		}
	};
	const onRemove = async () => {
		if (item && item.id) {
			await rpc.removeOrganization(item.id);
			setItems(await rpc.loadOrganizations());
			setItem(null);
			setNewItem(null);
		}
	};

	let formdata = item || {};
	let customizedata = formdata?.customizeInfos ? JSON.parse(formdata.customizeInfos) || {} : {}

	return <div className="flexh centerh pd-lg">
		<div className="form">
			{loggedIn == "dev" && (
				<div className="form-group">
					<Select name="organization"
						value={formdata.id}
						onChange={(name, value) => {
							const selectedItem = items.find(i => i.id === value);
							setItem(selectedItem ? { ...selectedItem } : null);
							newItem && setItems(items.filter(i => i.id !== newItem.id));
						}}
						items={items.map(i => ({
							value: i.id,
							text: i.name || "New Organization added"
						}))}
					/>
				</div>
			)}
			{loggedIn == "dev" && (
				<div className="form-group">
					<Button
						name="newOrganization" label="Hinzufügen"
						onClick={() => {
							let newItem = { id: id() };
							setNewItem(newItem);
							setItems([...items, newItem]);
							setItem({ ...newItem });
						}}
					/>
				</div>
			)}
			{!props.privatePage && <>
				<h2>Anbieter</h2>
				<Text name="name" label="Name" value={formdata.name} onChange={onChange} disabled={!item} />
				<Text name="email" label="E-Mail-Adresse" value={formdata.email} onChange={onChange} disabled={!item} />
				<Text name="address" label="Adresse" value={formdata.address} onChange={onChange} disabled={!item} />
				<Text name="city" label="Stadt" value={formdata.city} onChange={onChange} disabled={!item} />
				<Text name="country" label="Land" value={formdata.country} onChange={onChange} disabled={!item} />
			</>}
			{props.privatePage && <>
				<h2>Private Seite</h2>
				<div className="form-heading">Unter Domain oder als Link</div>
				<div className="form-group">
					<TextArea name="showOn" label="Sichtbar auf Domains (eine pro Zeile)"
						value={formdata.showOn} onChange={onChange} disabled={!item} />
				</div>
				<div className="form-group">
					<label>Link zum Teilen Ihres privaten Bereichs</label>
					<input
						type="text"
						value={formdata.shareLink || ""}
						readOnly
						onClick={() => {
						if (formdata.shareLink) {
							navigator.clipboard.writeText(formdata.shareLink);
							setCopied(true);
							setTimeout(() => setCopied(false), 2000);
						}
						}}
						style={{ cursor: "pointer" }}
					/>
					{copied && <div style={{ color: "green", marginTop: "5px" }}>Link kopiert!</div>}
				</div>
				<div className="form-heading">Private Seite anpassen</div>
				<Text name="title" label="Seitentitel"value={customizedata.title} onChange={onCustomizeChange}/>
				<Checkbox name="logo" label="Logo zeigen?" value={customizedata.logo} onChange={onCustomizeChange}/>
				<Text name="logoImage" label="Logo Bild-URL" value={customizedata.logoImage} onChange={onCustomizeChange}/>
				<Checkbox name="login" label="Login zeigen?" value={customizedata.login} onChange={onCustomizeChange}/>
				<Checkbox name="filter" label="Filter zeigen?" value={customizedata.filter} onChange={onCustomizeChange}/>
				<Number name="bannerHeight" label="Banner Höhe" value={customizedata.bannerHeight} onChange={onCustomizeChange}/>
				<Text name="bannerImage" label="Banner Bild-URL" value={customizedata.bannerImage} onChange={onCustomizeChange}/>
			</>}
			<div>
				<Button name="saveOrganization" label="Speichern" onClick={onSave} disabled={!item} className="save" />
				{loggedIn == "dev" && <Button name="removeOrganization" label="Löschen" onClick={onRemove} disabled={!item} className="remove"/>}
			</div>
		</div>
	</div>
}
