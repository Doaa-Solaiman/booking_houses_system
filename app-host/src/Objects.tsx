import React from "react";
import MUI from "@material-ui/core";
const { Dialog } = MUI;
import {
	Button, Text, TextArea, Number as InputNumber, Select, Checkbox, File, Date as InputDate,
	MuiIcon
} from "./components/formelements";
import * as types from "./types";
import { rpc, globalState } from "./index";
import Style from "./components/Style";
import { MuiIcon } from "./components/formelements";
import { Amenities } from "./Amenities";
import { createValidator, validator, useFormValidation } from "./utils/utils";

let id = () => Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 10);

export function Sites({ loggedIn, organizationName }) {
	type Item = Partial<types.Site>;
	type ItemList = Item[];

	let [items, setItems] = React.useState<ItemList>([]);
	let [item, setItem] = React.useState<Item>(null);
	let [newItem, setNewItem] = React.useState<Item>(null);
	let [itemPhotos, setItemPhotos] = React.useState([]);
	let [photosEnabled, setPhotosEnabled] = React.useState(false);
	let [showMessage, setShowMessage] = React.useState(false);
	let [notification, setNotification] = React.useState<{ message: string, type: "success" | "error" } | null>(null);
	let [roomTypes, setRoomTypes] = React.useState<types.RoomType[]>([]); // to load room types in site section...
	let [showInfo, setShowInfo] = React.useState(false);
	let [loading, setLoading] = React.useState({
		save: false, remove: false
	})

	const val = validator;
	const schema = {
		name: [
			val.string().required("Bitte geben Sie einen Name ein"),
			val.string().max(255,"Maximal 255 Zeichen"),
		],
		shortName: [ val.string().max(50,"Maximal 50 Zeichen"), ],
		presentationType: [ val.string().required("Bitte wählen Sie den Präsentationstyp") ],
		teaser: [ val.string().max(255,"Maximal 255 Zeichen") ],
		email: [
			val.string().email("Ungültige E-Mail-Adresse"),
			val.string().max(50,"Maximal 50 Zeichen"),
		],
		address: [ val.string().max(50,"Maximal 50 Zeichen") ],
		city: [ val.string().max(50,"Maximal 50 Zeichen") ],
		state: [ val.string().max(50,"Maximal 50 Zeichen") ],
		country: [ val.string().max(50,"Maximal 50 Zeichen") ],
		phoneNumber: [ val.string().max(50,"Maximal 50 Zeichen") ],
	};
	const { validateField, validateAll } = createValidator({
		schema,
		cross: () => {}
	});
	const { formData, setFormData, resetForm, errors, handleChange, isFormValid } = useFormValidation(
		(item || {}),
		validateField,
		validateAll
	);
	let formdata = formData || {}; // item || {};

	React.useEffect(() => {
		(async () => {
			if (globalState.siteSubPage == "Sites") {
				await loadRoomTypes();
			}
			const sites = await rpc.loadSites(true, null);
			setItems(sites);
			resetForm(null); // setItem(null);
			setNewItem(null);
		})();
	}, [loggedIn, globalState.siteSubPage]);

	let onChange = async (name: string, value: any) => {
		handleChange(name,value);
		// let updatedItem = { ...(item || {}), [name]: value };
		let updatedItem = { ...(formdata || {}), [name]: value };
		if (name == "availabilityType") {
			if (value == "rooms") {
				const types = await rpc.loadRoomTypes();
				setRoomTypes(types);
			} else {
				updatedItem.roomType_ids = [];
				setRoomTypes([]);
			}
		}
		// setItem(updatedItem);
	};
	const onSave = async () => {
		if (!formdata) return;
		setLoading((l) => ({ ...l, save: true }));

		try {
			console.log("sending item to the backend:", formdata);

			await rpc.saveSite(formdata);
			localStorage.removeItem("siteDraft");

			const [photosResult, updatedItems] = await Promise.allSettled([
				rpc.loadPhotos(formdata.id),
				rpc.loadSites(true, null),
			]);
			if (photosResult.status == "fulfilled") {
				const sortedPhotos = [...photosResult.value].sort((a, b) => a.order - b.order);
				setItemPhotos(sortedPhotos);
			} else {
				console.warn(
					`Could not load photos for site ID ${formdata.id}:`,
					photosResult.reason
				);
				setItemPhotos([]);
			}
			if (updatedItems.status == "fulfilled") {
				setItems(updatedItems.value);
			}
			setNewItem(null); // resetForm(null);
			setPhotosEnabled(true);
			setNotification({ message: "Dateien erfolgreich gespeichert!", type: "success" });
			setTimeout(() => setNotification(null), 3000);
		} catch (err) {
			console.error("Error saving item:", err);
			setNotification({ message: "Fehler beim Speichern: " + (err?.message || "Unbekannter Fehler"), type: "error" });
			setTimeout(() => setNotification(null), 4000);
		} finally {
			setLoading((l) => ({ ...l, save: false }));
		}
	};

	const onRemove = async () => {
		if (!formdata?.id) {
			alert("Kein Element ausgewählt zum Löschen.");
			return;
		}
		setLoading((l) => ({ ...l, remove: true }));
		try {
			await rpc.removeSite(formdata.id);

			const [updatedItems] = await Promise.allSettled([
				rpc.loadSites(true, null),
			]);
			if (updatedItems.status == "fulfilled") {
				setItems(updatedItems.value);
			} else {
				console.warn(`Could not load sites:`, updatedItems.reason);
			}
			resetForm(null);
			setNewItem(null);
			setPhotosEnabled(false);
			setItemPhotos([]);
			setShowMessage(true);
			setNotification({ message: "Dateien erfolgreich gelöscht!", type: "success" });
			setTimeout(() => setNotification(null), 3000);

		} catch (err) {
			console.error("Error removing item:", err);
			setNotification({ message: "Fehler beim Löschen: " + (err?.message || "Unbekannter Fehler"), type: "error" });
			setTimeout(() => setNotification(null), 4000);

		} finally {
			setLoading((l) => ({ ...l, remove: false }));
		}
	};

	return <div className="site-container flexh wrap centerh pd-md">
		<Style>{`
			& { align-items: start; }
			& .form-info {
				background-color: #f9f9f9;
				border: 1px solid #ddd;
				border-radius: 6px;
				max-width: 600px;
			}
			& .form-info > img {
				width: 24px; height: 24px; margin-top: 2px;
			}
		`}</Style>
		<InfoDialog
			open={showInfo}
			onClose={() => setShowInfo(false)}
			content={<div className="pd-sm">
				<strong>Was ist eine Unterkunft?</strong>
				<div>
					Eine Unterkunft ist das übergeordnete Objekt, das Sie verwalten. Dazu gehören Standort, Adresse, Hausregeln, Ausstattung und das gesamte Inventar.<br/>
					Eine Unterkunft entspricht immer einem physisch klar abgegrenzten Betrieb: ein Hotel, ein Apartmenthaus, eine Ferienanlage oder eine einzelne Ferienwohnung.
				</div>
			</div>}
		/>
		<div className="info-btn"><button onClick={() => setShowInfo(!showInfo)}><MuiIcon name="info"/></button></div>
		<div className="flexh wrap gap-lg" style={{placeSelf: "center", position: "relative"}}>
			<div className="form">
				<div className="form-info flexh gap-sm pd-sm">
					<img src="img/info.png" alt="Info" />
					<div style={{ margin: 0, fontSize: "15px", lineHeight: "1.5", color: "#333" }}>
						Im ersten Abschnitt können Sie die Standortinformationen Ihrer Unterkunft
						sowie Außenfotos hochladen. Danach wechseln Sie zum Bereich „Art der Unterbringung“,
						um festzulegen, welche Zimmerarten Sie für Ihre Unterkunft anbieten.
					</div>
				</div>
				<Select
					name="site" placeholder="Immobilie / Unterkunft wählen..."
					value={formdata.id}
					onChange={async (name,value) => {
						const selectedItem = items.find(i => i.id == value);
						// setItem(value && { ...selectedItem });
						setFormData(value && { ...selectedItem });

						let [photos] = await Promise.allSettled([rpc.loadPhotos(selectedItem.id)]);
						if (photos.status == "fulfilled") {
							let sorted = photos.value.sort((a, b) => a.order - b.order);
							setItemPhotos(sorted);
							setPhotosEnabled(true);
						} else {
							console.warn("failed to load photos: ",photos.reason);
							setItemPhotos([]);
						}

						if (selectedItem?.availabilityType == "rooms") {
							const types = await rpc.loadRoomTypes();
							setRoomTypes(types);
						} else {
							setRoomTypes([]);
						}
						//setItem(value && { ...items.find(i => i.id == value) });
						//newItem && setItems(items.filter(i => i.id != newItem.id));
						if (newItem) {
							setItems(items.map(i =>
								i.id == newItem.id ? { ...newItem } : i));
						}
					}}
					items={items.map(i => ({ value: i.id, text: i.name || "New Site" }))}
				/>
				{showMessage && (
					<center style={{ color: 'red', padding: '10px', backgroundColor: 'khaki', }}>
						<b>Um eine neue Unterkunft mit Fotos zu erstellen, klicken Sie zunächst auf 'Hinzufügen'</b>
					</center>
				)}
				<Button
					name="newSite"
					label="Immobilie hinzufügen +"
					onClick={() => {
						let newItem = { id: id(), organization_id: loggedIn, showRoomtypePhotos: false };
						setNewItem(newItem);
						setItems([...items, newItem]);
						setFormData({ ...newItem }); // setItem({ ...newItem });
						setItemPhotos([]);
						setShowMessage(false);
						setPhotosEnabled(false);
					}}
				/>
				{loggedIn=="dev" && <Text name="organization_id" label="Organisation" value={organizationName} disabled={true} />}
				<Text name="name" label="Name" value={formdata.name} onChange={onChange} disabled={!formdata} required error={errors.name} />
				<Text name="shortName" label="Kurzname" value={formdata.shortName} onChange={onChange}
					disabled={!formdata} placeholder="z.B. DHH" error={errors.shortName} />
				<Select
					name="presentationType" label="Präsentation" disabled={!formdata}
					value={formdata.presentationType}
					onChange={onChange}
					items={[
						{ value: "site", text: "Unterkunft" },
						{ value: "roomtypes", text: "Arten der Unterbringung" },
					]}
					required error={errors.presentationType}
				/>
				<Select
					name="availabilityType" label="Art der Verfügbarkeitsprüfung" disabled={!formdata}
					value={formdata.availabilityType}
					onChange={onChange}
					items={[
						{ value: "none", text: "Keine Prüfung" },
						{ value: "capacity", text: "Nach Kapazität (Einheitenzahl)" },
						{ value: "rooms", text: "Nach Räumen (Wenn konkrete Zimmer definiert sind)" },
					]}
				/>
				{formdata?.availabilityType == "capacity" &&
					<InputNumber
						name="unitCount" label="Anzahl der Einheiten"
						value={formdata.unitCount} onChange={onChange}
						disabled={!formdata} placeholder="z.B. 3" min={1}
					/>
				}
				{formdata?.availabilityType=="rooms" && roomTypes.length==0 &&
					<div style={{ marginTop: 10, backgroundColor: '#dedede', padding: 10, color: 'rgb(246 0 0)',
							borderRadius: 5, marginBottom: '20px' }}>
						<strong>&#9888;&#65039; Kein Zimmertyp vorhanden!</strong><br/>
						<p>Bitte fügen Sie einen Zimmertyp hinzu, bevor Sie fortfahren.</p>
					</div>
				}
				<TextArea
					name="teaser" label="Teaser" disabled={!formdata}
					value={formdata.teaser}
					onChange={onChange}
					placeholder="Kurzbeschreibung auf der Hauptseite der Unterkünfte"
					error={errors.teaser}
				/>
				<TextArea name="description" label="Beschreibung" value={formdata.description} onChange={onChange} disabled={!formdata} />
				{formdata?.id && <Checkbox
					name="showRoomtypePhotos"
					label="Fotos aus dem Zimmertyp Bereich enthalten"
					value={!!formdata.showRoomtypePhotos}
					onChange={(name, value) => {
						if (formdata) {
							const updatedItem = { ...formdata, [name]: value };
							handleChange(name,value);
						}
					}}
				/>}
				<Text name="address" label="Straße" value={formdata.address} onChange={onChange} disabled={!formdata} error={errors.address} />
				<Text name="city" label="Stadt" value={formdata.city} onChange={onChange} disabled={!formdata} placeholder="Stadt + Postleitzahl" error={errors.city} />
				<Text name="state" label="Staat oder Provinz" value={formdata.state} onChange={onChange} disabled={!formdata} error={errors.state} />
				<Text name="country" label="Land" value={formdata.country} onChange={onChange} disabled={!formdata} error={errors.country} />
				<Text name="phoneNumber" label="Telefonnummer des Ansprechpartners" value={formdata.phoneNumber} onChange={onChange} disabled={!formdata} error={errors.phoneNumber} />
				<Text name="email" label="E-Mail-Adresse" value={formdata.email} onChange={onChange} disabled={!formdata} error={errors.email} />
				<div className="flexh gap-md endh">
					<Button name="save" label="Speichern" onClick={onSave} loading={loading.save}
						disabled={!formdata || !formdata.organization_id || !isFormValid} className="bg-primary" style={{flex: 1}} />
					<Button name="remove" label="Löschen" onClick={onRemove} loading={loading.remove}
						disabled={!formdata} style={{flex: 1}} className="bg-transparent text-primary border-primary" />
				</div>
			</div>
			{photosEnabled && <div className="photos">
				<Photos
					value={itemPhotos}
					formdata={formdata}
					context="site"
					onSave={(photosToSave) => {
						let site_id = formdata.id;
						if (!site_id) {
							console.error("no property selected");
							return;
						}
						photosToSave.forEach(p => p.site_id = site_id);
						rpc.savePhotos(site_id, photosToSave)
							.then(savedPhotos => {
								savedPhotos.sort((a, b) => a.order - b.order);
								setItemPhotos(savedPhotos);
							})
							.catch((error) => {
								console.error("Failed to save photos:", error);
								setNotification({ message: "Fehler beim Speichern der Fotos: " + (error?.message || "Unbekannter Fehler"), type: "error" });
								setTimeout(() => setNotification(null), 4000);
							});
					}}
					enabled={photosEnabled}
				/>
			</div>}
			{notification && <Notification type={notification.type} message={notification.message} />}
		</div>
	</div>
}

function Photos({ value, onChange, onSave, enabled, formdata, context }) {
	const [photos, setPhotos] = React.useState([]);
	const [messageInfo, setMessageInfo] = React.useState(false);
	const [notification, setNotification] = React.useState<{ message: string, type: "success" | "error" } | null>(null);

	React.useEffect(() => {
		setPhotos((value || []).map((v, i) => ({
			id: v.id,
			file: typeof v == "string" ? v : v.file,
			caption: v.caption || "",
			order: i + 1,
		})));
	}, [value]);

	// photos section and functions
	React.useEffect(() => {
		setMessageInfo(photos.length > 0);
		//const currentPhotos = photos;
		return () => {
			photos.forEach((photo) => {
				if (typeof photo.file !== "string") {
					URL.revokeObjectURL(photo.file);
				}
			});
		};

	}, [photos]);

	const handleAddPhotos = (name: string, files: FileList | null) => {
		if (!enabled || !files) return;

		const newPhotos = Array.from(files).map((file) => ({
			file: file,
			caption: "",
			order: photos.length, // to set the order based on the current length of the photos array
		}));
		setPhotos((previous) => {
			//const existingFiles = new Set (previous.map(p => p.file.name));
			//const checkedNewPhotos = newPhotos.filter(newPhotos => !existingFiles.has(newPhotos.file.name));

			const updatedPhotos = [...previous, ...newPhotos];
			onChange && onChange(updatedPhotos);
			return updatedPhotos;
		});
		setMessageInfo(newPhotos.length > 0);
	};
	const handleRemovePhoto = (index: number) => {
		setPhotos((previous) => {
			const removedPhoto = previous[index];
			if (typeof removedPhoto.file !== "string") {
				URL.revokeObjectURL(removedPhoto.file); // cleanup object URL for temp files
			}
			const updatedPhotos = previous.filter((_, i) => i !== index);
			onChange && onChange(updatedPhotos);
			return updatedPhotos;
		});
	};
	const movePhoto = (index, direction) => {
		setPhotos((prev) => {
			const newPhotos = [...prev];
			const targetIndex = index + direction;
			if (targetIndex < 0 || targetIndex >= newPhotos.length) return prev;

			// swap the photos
			[newPhotos[index], newPhotos[targetIndex]] = [newPhotos[targetIndex], newPhotos[index]];

			//const temporary = newPhotos[index];
			//newPhotos[index] = newPhotos[targetIndex];
			//newPhotos[targetIndex] = temporary;
			const orderedPhotos = newPhotos.map((p, i) => ({ ...p, order: i + 1 }));
			onChange && onChange(orderedPhotos);
			return orderedPhotos;
		});
	};

	// This function is for preparing photo Metadata
	const save = async () => {
		// TODO implement photo upload
		// - upload photos (for all added photos)
		// - save photo list with desired order and metadata, e.g. captions
		// - call onChange with a list of URLs that works with the server (no blob: URLs anymore)
		//   (maybe also a list of JS objects with URL + metadata)

		let savePhotoMetadata = async (nameByFileObject?) => {
			const processedPhotos = photos.map((photo, index) => {
				const file = typeof photo.file == "string" ? photo.file : nameByFileObject?.get(photo.file);
				if (!file) {
					console.warn(`photo filename missed at index ${index}`)
				}
				return {
					id: photo.id,
					file: file,
					caption: photo.caption,
					order: index + 1,
				};
			});
			console.log("Processed Photos:", processedPhotos);
			onSave && onSave(processedPhotos);
		};

		// uploading new files to the server
		let toUpload = photos.map(p => p.file).filter(f => typeof f != "string");
		if (toUpload.length) {
			const formData = new FormData();
			for (let i = 0; i < toUpload.length; i++)
				if (typeof toUpload[i] != "string")
					formData.append("photos", toUpload[i]);
			const xhr = new XMLHttpRequest(); //upload new files through it to the backend (/uploadPhotos)
			xhr.open("POST", "/uploadPhotos", true);
			xhr.onreadystatechange = async function() { //event handler is set up to process the server's response.
				if (xhr.readyState == XMLHttpRequest.DONE) {
					if (xhr.status == 200) {
						let nameByFileObject = new Map();
						let names = JSON.parse(xhr.responseText);
						for (let i = 0; i < toUpload.length; i++)
							nameByFileObject.set(toUpload[i], names[i]);
						await savePhotoMetadata(nameByFileObject);
						// Handle successful response from the server
						console.log('Files uploaded successfully! ', names);
						setNotification({ message: "Dateien erfolgreich hochgeladen!", type: "success" });
						setTimeout(() => setNotification(null), 3000);
						//alert("Dateien erfolgreich hochgeladen!");
					} else {
						// Handle error response from the server
						console.error('Failed to upload files.');
						setNotification({ message: "Fehler beim Hochladen der Datei. Versuchen Sie es erneut.", type: "error" });
						setTimeout(() => setNotification(null), 4000);
						//alert("Fehler beim Hochladen der Datei. Versuchen Sie es erneut.");
					}
				}
			};
			xhr.send(formData);
		} else {
			await savePhotoMetadata();
		}
	};

	const buttonStyle = {
		enabled: {
			backgroundColor: 'rgb(0, 112, 191)',
			color: 'white',
		},
		disabled: {
			backgroundColor: '#ccc',
			color: '#666',
			cursor: 'not-allowed',
		},
	}

	return <div style={{position: "relative"}}>
		{notification && <Notification type={notification.type} message={notification.message} />}
		<File
			name="photos" label="Fotos hochladen"
			value={photos.map((p) => p.file)}
			onChange={handleAddPhotos}
			required messageInfo multiple
			buttonStyle={enabled ? buttonStyle.enabled : buttonStyle.disabled}
			disabled={!enabled}
		/>
		{photos && photos.length > 0 && (
			<div className="uploaded-photos flexv gap-md mt-md">
				<h4>Das Fotocover ist das erste:</h4>
				{photos
					//.sort((a,b) => a.order - b.order) // to sort the photos by the order
					.map((photo, index) => (
						<div
							key={photo.id || index}
							className={`photo-preview${index == 0 ? " cover-photo" : " photohere"} flexh gap-md`}>
							<div className="photo-img">
								<img
									src={!photo.file ? "#" :
										typeof photo.file == "string"
											? "photos/" + photo.file // from server (permanently / after upload)
											: URL.createObjectURL(photo.file) // from local machine (temporary while/before upload)
									}
									alt={`Photo ${index + 1}`}
									style={{ width: "100px" }}
								/>
							</div>
							<div className="form-group">
								<input
									type="text"
									name="caption"
									value={photo.caption}
									placeholder="Enter a caption"
									onChange={(e) => {
										//console.log(`Updating caption for photo of index ${index}: ${e.target.value}`);
										setPhotos((prev) => {
											const updatedPhotos = prev.map((p,i) =>
												i == index ? { ...p, caption: e.target.value } : p
											);
											onChange && onChange(updatedPhotos);
											return updatedPhotos;
										});
									}}
								/>
							</div>
							<div className="photo-actions flexh gap-sm wrap">
								<button onClick={() => movePhoto(index, -1)} disabled={index == 0}>
									<MuiIcon name="arrow_upward"/></button>
								<button onClick={() => movePhoto(index, 1)} disabled={index == photos.length - 1}>
									<MuiIcon name="arrow_downward"/>
								</button>
								<button onClick={() => handleRemovePhoto(index)}>
									<MuiIcon name="delete"/>
								</button>
							</div>
						</div>
					))}
			</div>
		)}
		<Button
			name="save" label="Speichern"
			onClick={save}
			disabled={!photos.length || !enabled}
			className="my-md"
		/>
		{(!formdata?.id) && context != "roomType" && (
			<div className="text-red">
				Bitte speichern Sie zuerst die Unterkunft, bevor Sie Fotos speichern.
			</div>
		)}
	</div>
}

export function Notification(props) {
	return <div
		style={{
			backgroundColor: props.type == "success" ? "#4caf50" : "#f44336",
			color: "white",
			padding: "12px",
			borderRadius: "4px",
			marginBottom: "1rem",
			textAlign: "center",
			fontWeight: "bold",
			position: "absolute",
			bottom: 0,
			transform: "translate(40%)"
		}}
	>
		{props.message}
	</div>
}

export function RoomTypes({ loggedIn, organizationName }) {
	type Item = Partial<types.RoomType>;
	type ItemList = Item[];

	let [items, setItems] = React.useState<ItemList>([]);
	let [item, setItem] = React.useState<Item>(null);
	let [newItem, setNewItem] = React.useState<Item>(null);
	let [itemPhotos, setItemPhotos] = React.useState([]);
	let [amenities, setAmenities] = React.useState<string[]>([]);
	let [photosEnabled, setPhotosEnabled] = React.useState(false);
	let [sites, setSites] = React.useState<types.Site[]>([]);
	let [showInfo, setShowInfo] = React.useState(false);
	let [notification, setNotification] = React.useState<{ message: string, type: "success" | "error" } | null>(null);
	//const [draftLoaded, setDraftLoaded] = React.useState(false);
	let [loading, setLoading] = React.useState({
		save: false, remove: false
	})
	let formdata = item || {
		name: "", shortName: "", site_id: "", teaser: "", description: "",
		adults: null, price: null, cleanService: null
	};

	React.useEffect(async () => {
		setSites(await rpc.loadSites(true, null));
		const roomTypes = await rpc.loadRoomTypes();
		console.log("Loaded room types:", roomTypes);
		setItems(roomTypes);
	}, [loggedIn]);

	React.useEffect(() => {
		if (item?.id) {
			rpc.loadPhotos(item.id).then((photos) => {
				photos.sort((a, b) => a.order - b.order);
				setItemPhotos(photos);
			});
		}
	}, [item?.id]);

	let onChange = (name: string, value: string | number | boolean) => {
		if (item) {
			setItem({ ...item, [name]: value });
		}
	};

	const isValidPrice = (price: any, cleanService?: any) => {
		if (price != undefined && price != null && price != "") {
			const priceNum = Number(price);
			if (isNaN(priceNum) || priceNum <= 0) return false;
		}
		if (cleanService != undefined && cleanService != null && cleanService != "") {
			const cleanNum = Number(cleanService);
			if (isNaN(cleanNum) || cleanNum < 0) return false;
		}
		return true;
	};

	const resetState = (excludeItem?) => {
		if (!excludeItem) setItem(null);
		setNewItem(null);
		setItemPhotos([]);
		setAmenities([]);
	}
	const onSaveRoomType = async () => {
		if (item) {
			setLoading((l) => ({ ...l, save: true }));
			try {
				if (!item.cleanService) item.cleanService = null; // temporary solution
				await rpc.saveRoomType(item);
				await rpc.saveAmenities(item.id, amenities);

				// reload the list after the save operation
				const updatedItems = await rpc.loadRoomTypes();
				updatedItems.sort((a, b) => (a.name || "").localeCompare(b.name || "", "de", { sensitivity: "base" }));

				setItems(updatedItems);
				console.log(" The roomtype data are:", updatedItems);
				/*const savedItem = updatedItems.find(i => i.id == item.id);
				if (savedItem) {
					setItem(savedItem);
				}*/
				setPhotosEnabled(true);
				const photos = await rpc.loadPhotos(item.id);
				photos.sort((a, b) => a.order - b.order);
				setItemPhotos(photos);
				resetState(true);
				setNotification({ message: "Dateien erfolgreich gespeichert!", type: "success" });
				setTimeout(() => setNotification(null), 3000);
			} catch(err) {
				console.error("error onSaveRoomType: ",err);
				setNotification({ message: "Fehler beim Speichern. Versuchen Sie es erneut.", type: "error" });
				setTimeout(() => setNotification(null), 4000);

			} finally {
				setLoading((l) => ({ ...l, save: false }));
			}
		}
	};
	const onSave = async () => {
		if (!formdata) return;
		setLoadingSave(true);

		try {
			console.log("sending item to the backend:", formdata);

			await rpc.saveSite(formdata);
			localStorage.removeItem("siteDraft");

			const [photosResult, updatedItems] = await Promise.allSettled([
				rpc.loadPhotos(formdata.id),
				rpc.loadSites(true, null),
			]);
			if (photosResult.status == "fulfilled") {
				const sortedPhotos = [...photosResult.value].sort((a, b) => a.order - b.order);
				setItemPhotos(sortedPhotos);
			} else {
				console.warn(
					`Could not load photos for site ID ${formdata.id}:`,
					photosResult.reason
				);
				setItemPhotos([]);
			}
			if (updatedItems.status == "fulfilled") {
				setItems(updatedItems.value);
			}
			setNewItem(null);
			resetForm(null);
		} catch (err) {
			console.error("Error saving item:", err);
			alert("Fehler beim Speichern: " + err.message);
		} finally {
			setLoadingSave(false);
		}
	};

	const onRemoveRoomType = async () => {
		if (item) {
			setLoading((l) => ({ ...l, remove: true }));
			try {
				await rpc.removeRoomType(item.id);
				setItems(await rpc.loadRoomTypes());
				resetState();
				setNotification({ message: "Dateien erfolgreich gespeichert!", type: "success" });
				setTimeout(() => setNotification(null), 3000);
				setLoading((l) => ({ ...l, remove: false }));
			} catch(err) {
				console.error("error onRemoveRoomType: ",err);
				setNotification({ message: "Fehler beim Entfernen. Versuchen Sie es erneut.", type: "error" });
				setTimeout(() => setNotification(null), 4000);
				setLoading((l) => ({ ...l, remove: false }));
			}
		}
	};

	return (
		<div className="site-container flexh wrap centerh pd-md">
			<Style>{`
				& { align-items: start; }
			`}</Style>
			<InfoDialog
				open={showInfo}
				onClose={() => setShowInfo(false)}
				content={<div className="pd-sm">
					<strong>Was ist eine Art der Unterbringung?</strong>
					<div>
						Dies ist die strukturelle Kategorie eines Zimmers, z. B. „Doppelzimmer Standard“ oder „Studio Apartment“.<br/>
						Die Art der Unterbringung beschreibt die Eigenschaften. Die physischen Zimmer werden separat als einzelne Einheiten erfasst.
					</div>
				</div>}
			/>
			<div className="info-btn"><button onClick={() => setShowInfo(!showInfo)}><MuiIcon name="info"/></button></div>
			<div className="flexh wrap gap-lg" style={{position: "relative"}}>
				<div id="roomTypes" className="form">
					<div
						style={{
							display: "flex",
							alignItems: "flex-start",
							gap: "12px",
							backgroundColor: "#f9f9f9",
							border: "1px solid #ddd",
							borderRadius: "6px",
							padding: "12px",
							marginBottom: "10px",
							maxWidth: "600px",
						}}
					>
						<img
							src="img/info.png"
							alt="Info"
							style={{ width: "24px", height: "24px", marginTop: "2px", }}
						/>
						<div style={{ margin: 0, fontSize: "15px", lineHeight: "1.5", color: "#333" }}>
							In diesem Abschnitt legen Sie fest, wie Ihre Unterkunft dargestellt wird – z. B. als komplette Wohnung oder als einzelnes Zimmer.<br />
							Bitte wechseln Sie anschließend in den Bereich „Mieteinheit“, um jede Unterkunft mit den entsprechenden Zimmertypen zu verknüpfen, damit sie korrekt auf der Präsentationsseite angezeigt werden.
						</div>
					</div>
					<Select
						name="roomType"
						value={formdata.id}
						onChange={async (name, value) => {
							const selectedItem = items.find(i => i.id == value);
							if (selectedItem) {
								setItem({ ...selectedItem });
								setAmenities(await rpc.loadAmenities(selectedItem.id))
								setPhotosEnabled(true);
							} else resetState();
							if (newItem) {
								setItems(items.filter(i => i.id != newItem.id));
							}
						}}
						items={items.map(t => {
							let site = sites.find(s => s.id == t.site_id);
							let name = (site ? site.name : "Shared Type") + " / " + t.name;
							return { value: t.id, text: t.name ? name : "New Room Type" };
						})}
						placeholder="Art der Unterbringung / Zimmertyp wählen..."
					/>
					<Button
						name="newRoomType" label="Art der Unterbringung hinzufügen +"
						onClick={() => {
							const newItem = { id: id(), organization_id: loggedIn, showSitePhotos: false };
							setNewItem(newItem);
							setItems([...items, newItem]);
							setItem({ ...newItem });
							setItemPhotos([]);
							setPhotosEnabled(false);
						}}
						// style={{ backgroundColor: "orange" }}
					/>
					<hr />
					{loggedIn == "dev" && <Text name="organization_id" label="Organisation" value={organizationName} disabled />}
					<Text
						name="name" label="Name"
						value={formdata.name} onChange={onChange}
						disabled={!item} style={{ color: '#1f0467' }} />
					<Text
						name="shortName" label="Kurzname(optional)"
						value={formdata.shortName} onChange={onChange}
						disabled={!item} placeholder="z.B. DZ" />
					<Select
						name="site_id" label="Unterkunft"
						value={formdata.site_id} onChange={onChange}
						items={sites.map(r => ({ value: r.id, text: r.name }))} disabled={!item} />
					{/*<SelectMany name="supertype_ids" label="Obertyp" value={formdata.supertype_ids} onChange={onChange}
							items={items.map(i => ({ value: i.id, text: i.name }))} disabled={!item} />*/}
					{/*<InputNumber name="unitCount" label="Anzahl der Zimmer" value={item.unitCount} onChange={onChange}
						disabled={!item} placeholder="z.B. 3" min={1} />*/}
					<TextArea
						name="teaser" label="Teaser"
						value={formdata.teaser} onChange={onChange}
						disabled={!item}
						placeholder="Kurzbeschreibung auf der Hauptseite der Unterkünfte" />
					<TextArea
						name="description" label="Beschreibung"
						value={formdata.description} onChange={onChange}
						disabled={!item} />
					<InputNumber
						name="adults" label="Max. Anzahl Erwachsene"
						value={formdata.adults} onChange={onChange}
						disabled={!item} />
					<div className="form-group flexv gap-md">
						<Amenities room={item} amenities={amenities} setAmenities={setAmenities} />
					</div>
					<Text
						name="price" label="Standardpreis pro Nacht"
						value={formdata.price} onChange={onChange}
						disabled={!item}
						placeholder="die erforderliche Eingabe Beispiel 40.70"
						style={{ color: 'black' }} />
					<Text
						name="cleanService" label="Reinigungsgebühren (optional)"
						value={formdata.cleanService} onChange={onChange}
						disabled={!item} style={{ color: 'black' }} />
					{(!isValidPrice(formdata.price, formdata.cleanService))
						&& <div style={{ color: "red", fontSize: "0.8em" }}>Bitte geben Sie einen gültigen Preis ein (z.B. 40.70)</div>
					}
					<div className="helper-text">Um weitere Preiseinstellungen vorzunehmen, gehen Sie bitte zum Preisregeln Abschnitt.</div>
					{formdata?.id && <Checkbox
						name="showSitePhotos" label="Fotos aus dem Unterkunftsbereich enthalten"
						value={!!formdata.showSitePhotos}
						onChange={(name, value) => {
							if (item) {
								const updatedItem = { ...item, [name]: value };
								setItem(updatedItem);
							}
						}}
					/>}
					<div className="flexh gap-md endh">
						<Button
							name="saveRoomType" label="Speichern" onClick={onSaveRoomType} loading={loading.save}
							disabled={!formdata.name || !formdata.id || !formdata.organization_id || !isValidPrice(formdata.price, formdata.cleanService)}
							style={{ flex: 1 }} className="bg-primary"
						/>
						<Button
							name="removeRoomType" label="Löschen" onClick={onRemoveRoomType} loading={loading.remove}
							disabled={!formdata.id} style={{ flex: 1 }} className="bg-transparent text-primary border-primary" />
					</div>
					{!formdata?.id && (
						<div style={{ color: "#999", fontStyle: "italic", marginTop: 10 }}>
							Bitte wählen oder erstellen Sie zuerst einen Zimmertyp, um Fotos hinzuzufügen.
						</div>
					)}
				</div>
				{photosEnabled && <div className="photos">
					<Photos
						value={itemPhotos}
						onChange={setItemPhotos}
						onSave={async (savedPhotos) => {
							let roomType_id = formdata.id;
							if (!roomType_id) {
								console.error("no room type selected"); return;
							}
							const updatedPhotos = savedPhotos.map(photo => ({
								...photo,
								roomType_id
							}));
							await rpc.savePhotos(roomType_id, updatedPhotos);
							setItemPhotos(updatedPhotos);
						}}
						enabled={photosEnabled}
						formdata={formdata}
						context="roomType"
					/>
				</div>}
				{notification && <Notification type={notification.type} message={notification.message} />}
			</div>
		</div>
	);
}

export function Rooms({ loggedIn, organizationName }) {
	type Item = Partial<types.Room>;
	type ItemList = Item[];

	let [rooms, setRooms] = React.useState<ItemList>([]);
	let [room, setRoom] = React.useState<Item>(null);
	let [newRoom, setNewRoom] = React.useState<Item>(null);
	let [sites, setSites] = React.useState<types.Site[]>([]);
	let [roomTypes, setRoomTypes] = React.useState<types.RoomType[]>([]);
	let [showInfo, setShowInfo] = React.useState(false);
	let [notification, setNotification] = React.useState<{ message: string, type: "success" | "error" } | null>(null);
	let [loading, setLoading] = React.useState({
		save: false, remove: false
	})

	React.useEffect(() => {
		(async () => {
			setSites(await rpc.loadSites(true, null));
			setRoomTypes(await rpc.loadRoomTypes());
			setRooms(await rpc.loadRooms());
		})();
	}, [loggedIn]);

	const onChange = (name: string, value: string | number | boolean) => {
		setRoom({ ...(room || {}), [name]: value });
	};

	const onSaveRoom = async () => {
		if (room) {
			setLoading((l) => ({ ...l, save: true }));
			try {
				const savedRoom = await rpc.saveRoom(room);
				console.log(" saved room result:", savedRoom);
				const [updatedRooms] = await Promise.allSettled([
					rpc.loadRooms()
				])
				if (updatedRooms.status == "fulfilled") {
					setRooms(updatedRooms.value);
				} else console.warn(`Could not load rooms: ${updatedRooms.reason}`)

				setNewRoom(null);
				setNotification({ message: "Datei erfolgreich gespeichert!", type: "success" });
				setTimeout(() => setNotification(null), 3000);
			} catch (error) {
				console.error("error saving room:", JSON.stringify(error, Object.getOwnPropertyNames(error)));
				setNotification({ message: "Fehler beim Speichern: " + (error?.message || "Unbekannter Fehler"), type: "error" });
				setTimeout(() => setNotification(null), 4000);
			} finally {
				setLoading((l) => ({ ...l, save: false }));
			}
		}
	};
	const onRemoveRoom = async () => {
		if (room) {
			setLoading((l) => ({ ...l, remove: true }));
			try {
				await rpc.removeRoom(room.id);
				const [updatedRooms] = await Promise.allSettled([
					rpc.loadRooms()
				])
				if (updatedRooms.status == "fulfilled") {
					setRooms(updatedRooms.value);
				} else console.warn(`Could not load rooms: ${updatedRooms.reason}`)
				setRoom(null); setNewRoom(null);
				setNotification({ message: "Datei erfolgreich gelöscht!", type: "success" });
				setTimeout(() => setNotification(null), 3000);
			} catch(err) {
				console.error("onRemoveRoom error: ",err);
				setNotification({ message: "Fehler beim Löschen: " + (error?.message || "Unbekannter Fehler"), type: "error" });
				setTimeout(() => setNotification(null), 4000);
			} finally{
				setLoading((l) => ({ ...l, remove: false }));
			}
		}
	};
	let formdata = room || {};

	return <div className="site-container flexh centerh pd-md">
		<Style>{`
			& { align-items: start; }
		`}</Style>
		<InfoDialog
			open={showInfo}
			onClose={() => setShowInfo(false)}
			content={<div className="pd-sm">
				<strong>Was ist eine Mieteinheit?</strong>
				<div>
					Die Mieteinheit ist die konkrete, buchbare Einheit, die in Kalendern und auf Plattformen erscheint. Sie basiert auf einem physischen Zimmer.
					<br/>Die Mieteinheit ist keine Tarif- oder Servicevariante. Pro physischem Zimmer existiert genau eine Mieteinheit.
				</div>
			</div>}
		/>
		<div className="form" style={{position: "relative"}}>
			<div className="info-btn"><button onClick={() => setShowInfo(!showInfo)}><MuiIcon name="info"/></button></div>
			<Select
				name="room"
				value={formdata.id}
				onChange={async (name, value) => {
					const selectedRoom = rooms.find(r => r.id == value);
					if (selectedRoom) {
						setRoom({ ...selectedRoom });
						//setAmenities(await rpc.loadAmenities(selectedRoom.id) || []);
					}
				}}
				items={rooms.map(r => {
					let site = sites.find(s => s.id == r.site_id);
					let type = roomTypes.find(t => t.id == r.roomType_id);
					let name = (site ? site.name + " / " : "") + (type ? type.name + " / " : "") + r.name;
					return { value: r.id, text: r.name ? name : "Room added" };
				})}
				placeholder="Mieteinheit / Zimmer wählen..."
			/>
			<Button
				name="newRooms" label="Mieteinheit / Zimmer hinzufügen +"
				onClick={e => {
					const newRoom = { id: id(), organization_id: loggedIn };
					setNewRoom(newRoom);
					setRooms([...rooms, newRoom]);
					setRoom({ ...newRoom });
					//setAmenities([]);
				}}
			/>
			<hr />
			<Text
				name="name" label="Name"
				value={formdata.name} onChange={onChange}
				disabled={!room}
			/>
			{loggedIn == "dev" && <Text
				name="organization_id" label="Organization"
				value={organizationName} disabled />}
			{/*<Select name="organization_id" label="Organization" value={formdata.organization_id} onChange={onChange}
				items={organizations.map(o => ({ value: o.id, text: o.name }))} disabled={!room} />*/}
			<Select
				name="site_id" label="Unterkunft" value={formdata.site_id} onChange={onChange}
				items={sites.map(r => ({ value: r.id, text: r.name }))} disabled={!room} />
			<Select
				name="roomType_id" label="Art der Unterbringung" value={formdata.roomType_id} onChange={onChange}
				items={roomTypes.map(i => ({ value: i.id, text: i.name.trim() || i.shortName }))} disabled={!room} />
			<Checkbox
				name="available" label="Verfügbar"
				value={formdata.available} onChange={onChange}
				disabled={!room}
			/>
			<div className="flexh gap-md endh">
				<Button
					name="saveRoom" label="Speichern" onClick={onSaveRoom} loading={loading.save}
					disabled={!formdata.id || !formdata.site_id || !formdata.roomType_id || !formdata.organization_id}
					className="bg-primary" style={{flex: 1}}
				/>
				<Button
					name="removeRoom" label="Löschen" onClick={onRemoveRoom}
					disabled={!formdata.id} loading={loading.remove}
					className="bg-transparent text-primary border-primary" style={{flex: 1}}
				/>
			</div>
			{notification && <Notification type={notification.type} message={notification.message} />}
		</div>
	</div>
}

export function PricingRules({ loggedIn, organizationName }) {
	type Item = Partial<types.PricingRule>;
	type ItemList = Item[];

	let [data, setData] = React.useState({
		pricingRules: [] as Item[],
		sites: [] as types.Site[],
		roomTypes: [] as types.RoomType[],
		rooms: [] as types.Room[],
	});
	let [item, setItem] = React.useState<Item | null>(null);
	let [newItem, setNewItem] = React.useState<Item | null>(null);
	let [selectedRoom, setSelectedRoom] = React.useState<types.Room | null>(null);
	let [base, setBase] = React.useState({
		defaultPrice: "" as number | "",
		finalPrice: null as number | null,
	});
	let [adjust, setAdjust] = React.useState({
		type: "addPercent" as
			| "addPercent"
			| "addAmountEarly"
			| "addAmountLate"
			| "newDefaultPrice"
			| "newFinalPrice",
		percent: "" as number | "",
		amount: "" as number | "",
		new: "" as number | "",
	});
	let [loading, setLoading] = React.useState({
		save: false,
		remove: false,
	});
	let [showInfo, setShowInfo] = React.useState(false);
	let [notification, setNotification] = React.useState<{ message: string, type: "success" | "error" } | null>(null);

	let belongsSet = React.useMemo(
		() => new Set(["room", "roomtype", "roomtypeAndSite", "site"]),[]
	);

	React.useEffect(() => {
		(async () => {
			const [pricingRules, sites, roomTypes, rooms] = await Promise.all([
				rpc.loadPricingRules(),
				rpc.loadSites(true, null),
				rpc.loadRoomTypes(),
				rpc.loadRooms(),
			]);
			setData({ pricingRules, sites, roomTypes, rooms });
		})();
	}, [loggedIn]);

	let onChange = (name: string, value: any) => {
		setItem(i => {
			let next = { ...(i || {}) };
			if (name == "belongsTo" && (value == "site"||value == "room")) {
				delete next.roomType_id; delete next.room_id;
			}
			next[name] = value;
			return next;
		});
	};

	const adjustKey = {
		addPercent: "percent",
		addAmountEarly: "amount",
		addAmountLate: "amount",
		newDefaultPrice: "new",
		newFinalPrice: "new",
	}[adjust.type] || "amount";
	let getAdjustValue = () => adjust[adjustKey];
	let setAdjustValue = (v: any) =>
		setAdjust((a) => ({ ...a, [adjustKey]: v }));

	const updateFinalPrice = (type: string, raw: any) => {
		const basePrice = Number(base.defaultPrice) || 0;
		const adjustVal = Number(raw);

		const extras =
			(item?.allow_extra_guests
				? Number(item.extra_guests || 0) * Number(item.extra_fee_per_guest || 0)
				: 0) + Number(item?.cleanService || 0);

		const strategies = {
			addPercent: (b: number, v: number) => b + (b * v) / 100,
			addAmountEarly: (b: number, v: number) => b + v,
			addAmountLate: (b: number, v: number) => b + v,
			newFinalPrice: (_: number, v: number) => v,
			newDefaultPrice: (_: number, v: number) => v,
			default: (b: number) => b,
		};

		const fn = (strategies as any)[type] || strategies.default;
		const final = fn(basePrice, adjustVal) + extras;
		setBase((x) => ({ ...x, finalPrice: final }));
	};
	const savePricingRule = async () => {
		if (!item) return;
		setLoading((l) => ({ ...l, save: true }));
		try {
			await rpc.savePricingRule(item);

			const [refreshed] = await Promise.allSettled([rpc.loadPricingRules()]);
			if (refreshed.status == "fulfilled") {
				setData((d) => ({ ...d, pricingRules: refreshed.value }));
			} else console.warn("could not load pricing rules: ",refreshed.reason);

			setNewItem(null);
			setNotification({ message: "Datei erfolgreich gespeichert!", type: "success" });
			setTimeout(() => setNotification(null), 3000);
		} catch(err) {
			console.error("savePricingRule error: ",err);
			setNotification({ message: "Fehler beim Speichern: " + (err?.message || "Unbekannter Fehler"), type: "error" });
			setTimeout(() => setNotification(null), 4000);
		} finally {
			setLoading((l) => ({ ...l, save: false }));
		}
	};

	const removePricingRule = async () => {
		if (!item) return;
		setLoading((l) => ({ ...l, remove: true }));
		try {
			await rpc.removePricingRule(item.id);

			const [refreshed] = await Promise.allSettled([rpc.loadPricingRules()]);
			if (refreshed.status == "fulfilled") {
				setData((d) => ({ ...d, pricingRules: refreshed.value }));
			} else console.warn("could not load pricing rules: ",refreshed.reason);

			setItem(null); setNewItem(null);
			setNotification({ message: "Datei erfolgreich gelöscht!", type: "success" });
			setTimeout(() => setNotification(null), 3000);
		} catch(err) {
			console.error("removePricingRule error: ",err);
			setNotification({ message: "Fehler beim Löschen: " + (err?.message || "Unbekannter Fehler"), type: "error" });
			setTimeout(() => setNotification(null), 4000);
		} finally {
			setLoading((l) => ({ ...l, remove: false }));
		}
	};

	const emptyRule = () => ({
		id: id(),
		name: "",
		active: false,
		defaultPrice: 0,
		conditions: "",
		belongsTo: "",
		adjustType: "addPercent",
		adjustValue: 0,
		finalPrice: 0,
		startDate: new Date(),
		endDate: new Date(),
	});

	let formdata = item || {};
	console.log("sites: ",data.sites);
	console.log("roomTypes: ",data.roomTypes);
	console.log("rooms: ",data.rooms);
	console.log("formdata: ",formdata);

	return <div className="site-container flexv wrap pd-md">
		<Style>{`
		`}</Style>
		<InfoDialog
			open={showInfo}
			onClose={() => setShowInfo(false)}
			content={<div className="pd-sm">
				<strong>Was ist eine Preisregel?</strong>
				<div>
					Preisregeln steuern, wie sich der Grundpreis unter bestimmten Bedingungen verändert. Sie können auf drei Ebenen angewendet werden:
					<ul>
						<li>auf die gesamte Unterkunft</li>
						<li>auf eine Art der Unterbringung</li>
						<li>auf eine einzelne Mieteinheit</li>
					</ul>
					Preisregeln definieren:
					<ul>
						<li>Zeitraum, in dem die Regel gilt</li>
						<li>Bedingungen wie Wochentage, Feiertage oder saisonale Zeiten</li>
						<li>Art der Anpassung: prozentuale Erhöhung/Reduzierung oder ein fester Preis</li>
						<li>Reihenfolge und Priorität, falls mehrere Regeln gleichzeitig greifen</li>
					</ul>
					So lassen sich saisonale Preise, Wochenendaufschläge, Sondertarife oder zeitlich begrenzte Aktionen steuern, ohne die Grundpreise manuell zu verändern.
				</div>
			</div>}
		/>
		<div className="info-btn"><button onClick={() => setShowInfo(!showInfo)}><MuiIcon name="info"/></button></div>
		<div className="form" style={{position: "relative"}}>
			<Select
				name="pricingRule"
				value={formdata.id}
				onChange={(name, value) => {
					const found = data.pricingRules.find((i) => i.id == value);
					if (found) setItem({ ...found });
				}}
				items={data.pricingRules.map((i) => ({ value: i.id, text: i.name || "New Pricing Rule" }))}
				placeholder="Preisregel wählen..."
			/>
			<Button
				label="Preisregel hinzufügen +"
				onClick={(e) => {
					const r = emptyRule();
					setNewItem(r);
					setData((d) => ({ ...d, pricingRules: [...d.pricingRules, r] }));
					setItem(r);
				}}
			/>
			<Checkbox
				name="active" label="Aktiv" value={formdata.active} onChange={onChange} disabled={!item} />
			<Text
				name="name" label="Titel"
				value={formdata.name} onChange={onChange}
				disabled={!item}
			/>
			<Select
				name="belongsTo" label="Regel bezieht sich auf"
				value={formdata.belongsTo} onChange={onChange}
				items={[
					// I should add "Unterkunft", but in this case should we remove Zimmertyp und Unterkunft because it would be useless repeated
					{ value: "site", text: "Unterkünfte" },
					{ value: "roomtype", text: "Zimmertyp" },
					{ value: "roomtypeAndSite", text: "Zimmertyp und Unterkunft" },
					{ value: "room", text: "Einzelnes Zimmer" },
				]}
				disabled={!item}
			/>
			{formdata.belongsTo == "room" && <Select
				name="room_id" label="Zimmer"
				value={formdata.room_id}
				onChange={(name, value) => {
					const room = data.rooms.find((r) => r.id == value);
					onChange(name,value);
					if (room?.roomType_id) {
						const rt = data.roomTypes.find((x) => x.id == room.roomType_id);
					setBase((b) => ({ ...b, defaultPrice: rt?.price || "" }));
					}
					if (room?.allow_extra_guests) {
						setItem((p) => ({
							...p,
							allow_extra_guests: true,
							extra_guests: room.extra_guests || 0,
							extra_fee_per_guest: room.extra_fee_per_guest || 0,
						}));
					}
				}}
				items={data.rooms.map((room) => ({ value: room.id, text: room.name }))}
				disabled={!item}
			/>}
			{formdata.belongsTo?.includes("site") && <Select
				name="site_id" label="Unterkunft"
				value={formdata.site_id} onChange={onChange}
				items={data.sites.map((s) => ({ value: s.id, text: s.name }))}
				disabled={!item}
			/>}
			{formdata.belongsTo?.includes("roomtype") && <Select
				name="roomType_id" label="Zimmertyp"
				value={formdata.roomType_id}
				onChange={(name, value) => {
					onChange(name, value);
					const rt = data.roomTypes.find((r) => r.id == value);
					setBase((b) => ({ ...b, defaultPrice: rt?.price || "" }));
				}}
				items={data.roomTypes.map((rt) => ({ value: rt.id, text: rt.name }))}
				disabled={!item}
			/>}
			{belongsSet.has(formdata.belongsTo) && (
				<div className="form-group guest-limit pd-sm">
					<strong >Grundlimit der Gäste:</strong>
					<p>{data.roomTypes.find(r => {
						let rtId = formdata.roomType_id;
						if (formdata.belongsTo == "room") {
							let room = data.rooms.find(x => x.id == formdata.room_id)
							if (room) rtId = room.roomType_id;
						}
						return r.id == rtId;
					})?.adults ?? "-"}</p>
				</div>
			)}
			{belongsSet.has(formdata.belongsTo) && (
				<Checkbox
					name="allow_extra_guests"
					label="Extra Gäste?"
					value={formdata.allow_extra_guests}
					onChange={onChange}
				/>
			)}
			{formdata.allow_extra_guests && <>
				<InputNumber
					name="extra_guests" label="Max. Extra Gäste"
					value={formdata.belongsTo == "room"
						? selectedRoom?.extra_guests ?? ""
						: formdata.extra_guests}
					onChange={onChange}
					disabled={formdata.belongsTo == "room" && !!selectedRoom?.extra_guests}
				/>
				<InputNumber
					name="extra_fee_per_guest" label="Zusätzliche Gebühr für Gäste €"
					value={formdata.extra_fee_per_guest}
					onChange={onChange}
					disabled={!formdata.allow_extra_guests}
				/>
			</>}
			<div className="form-group">
				<label>Standardpreis:</label>
				<span style={{ fontSize: "17", color: "#000" }}>
					{base.defaultPrice ? `${base.defaultPrice} €` : "  "}
				</span>
			</div>
			<Select
				name="adjustType" label="Preis Anpassung"
				value={adjust.type}
				onChange={(name, value) => {
					setAdjust((a) => ({ ...a, type: value }));
					updateFinalPrice(value, getAdjustValue());
					setItem((x) => ({ ...(x || {}), adjustmentName: "" }));
				}}
				items={[
					{ value: "addAmountEarly", text: "Auf-/Abschlag (Festbetrag vor Prozenten)" },
					{ value: "addAmountLate", text: "Auf-/Abschlag (Festbetrag nach Prozenten)" },
					{ value: "addPercent", text: "Auf-/Abschlag (Prozentsatz)" },
					{ value: "newDefaultPrice", text: "Neuer/Anderer Standardpreis" },
					{ value: "newFinalPrice", text: "Neuer/Anderer Endpreis" },
				]}
				disabled={!item}
			/>
			<InputNumber
				name="adjustValue"
				label={
					adjust.type == "addPercent"
						? "Anpassung %"
						: ["addAmountEarly", "addAmountLate"].includes(adjust.type)
						? "Anpassung Betrag"
						: "Neuer Betrag"
				}
				value={getAdjustValue()}
				onChange={(name, value) => {
					if (!isNaN(value)) setAdjustValue(value);
					updateFinalPrice(adjustType,value);
				}}
				disabled={!item}
			/>
			{base.finalPrice != null && (
				<div className="form-group">
					<p>Endpreis: <strong>{base.finalPrice} €</strong></p>
				</div>
			)}
			<Text
				name="conditions" label="Bedingungen"
				value={formdata.conditions} onChange={onChange}
				disabled={!item} placeholder="z.B. Besetzungsgrad, Wochentage, ..."
			/>
			<div className="flexv gap-sm">
				<label>Gültigkeitszeitraum</label>
				<InputDate
					name="startDate" label="Beginn"
					value={formdata.startDate} onChange={onChange}
					disabled={!item}
				/>
				<InputDate
					name="endDate" label="Ende"
					value={formdata.endDate} onChange={onChange}
					disabled={!item}
				/>
				<span>Bitte beachten Sie, dass dieser Preis für den eingegebenen Zeitraum gilt und danach
				automatisch der Standardpreis wieder angewendet wird.</span>
			</div>
			<div className="flexh gap-md endh">
				<Button
					name="savePricingRule" label="Speichern" onClick={savePricingRule} loading={loading.save}
					disabled={!formdata} className="bg-primary" style={{flex: 1}} />
				<Button
					name="removePricingRule" label="Löschen" onClick={removePricingRule} loading={loading.remove}
					disabled={!formdata.id} className="bg-transparent text-primary border-primary" style={{flex: 1}} />
			</div>
			{notification && <Notification type={notification.type} message={notification.message} />}
		</div>
	</div>
}

function InfoDialog(props) {
	return <Dialog open={props.open} onClose={props.onClose}>
		<div className="flexh endh pd-sm">
			<button className="bg-red no-padding" onClick={props.onClose}><MuiIcon name="close" /></button>
		</div>
		<div>{props.content}</div>
	</Dialog>
}
