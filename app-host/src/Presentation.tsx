import React from "react";
import MUI from "@material-ui/core";
import * as types from "./types";

import { rpc, globalState } from "./index";
import { simpleState } from "./utils/utils";
import Style from "./components/Style";
import { Table } from "./components/Table";

const {
	Dialog, DialogTitle, DialogActions,
	DialogContent, DialogContentText,
} = MUI;
import { MuiIcon, Alert } from "./components/formelements";
import { AvailabilityCalendar } from "./AvailabilityCalendar";
import { GuestForm, BookingDetailsExtendedType } from "./GuestForm";
import { BookingForm, BookingDetailsType } from "./BookingForm";

export function Presentation({ ignoreDomain, isHostView = false, setPage }: { ignoreDomain?: boolean, isHostView?: boolean }) {
	let [selectedSite, setSelectedSite] = React.useState<types.Site | null>(null);

	return (
		<div className="presentation">
			<Style>{`
				& { display: flex; flex-direction: column; gap: 10px; }
				& .title { font-size: 20px; font-weight: 600; }
			`}</Style>
			{selectedSite
				? <div><button onClick={() => setSelectedSite(null)}>Zurück</button></div>
				: <div className="title">Die Präsentation Ihrer Unterkünfte</div>
			}
			{selectedSite && (
				<Detail
					site={selectedSite.site}
					roomtype={selectedSite.roomtype}
					ignoreDomain={ignoreDomain}
					isHostView={isHostView}
					displayName={selectedSite.displayName}
				/>
			)}
			{!selectedSite && <Cards
				checkInDate
				value={selectedSite}
				onChange={site => { setSelectedSite(site); }}
				ignoreDomain={ignoreDomain}
				isHostView={isHostView}
				setPage={setPage}
			/>}
		</div>
	);
}
let isValidDate = d => d instanceof Date && !isNaN(d);
let date = d => d != null && isValidDate(new Date(d)) ? new Date(d) : null;
// assumptions / declarations:
// - price per night per person is calculated on first day of booking,
//   then used for all days of booking
// - the following order of operations is used for pricing rules:
//   newDefaultPrice, addAmountEarly, addPercent, addAmountLate, newFinalPrice
export function calculatePriceForRoomtypePNPP(roomtype, room?, fromDate, priceRules, guests?) {
	if (!roomtype) return null;
	let siteId = roomtype.site_id;
	let rtId = roomtype.id;

	let applicableRules = priceRules.filter(r => {
		let match = false;
		match ||= r.site_id == siteId && r.belongsTo == "site";
		match ||= r.room_id == room.id && r.belongsTo == "room";
		match ||= r.roomType_id == rtId && r.belongsTo == "roomtype";
		//match ||= r.roomType_id == rtId && /*r.site_id==null &&*/ r.belongsTo == "roomtype";
		match ||= r.roomType_id == rtId && r.site_id == siteId && r.belongsTo == "roomtypeAndSite";
		if (match && fromDate != null) {
			if (date(r.startDate)) match &&= +date(r.startDate) <= +fromDate;
			if (date(r.endDate)) match &&= +fromDate <= +date(r.endDate);
		}
		return match;
	});
	let rank = { // order of operations
		newDefaultPrice: 1, addAmountEarly: 2, addPercent: 3, addAmountLate: 4, newFinalPrice: 5
	};
	applicableRules.sort((a, b) => {
		let ra = rank[a.adjustType];
		let rb = rank[b.adjustType];
		return ra - rb;
	});

	let price = roomtype.price ?? 0;
	applicableRules.forEach(r => {
		if (r.adjustType == "addPercent")
			price = price + (price * r.adjustValue / 100);
		else if (r.adjustType == "addAmountEarly" || r.adjustType == "addAmountLate")
			price = price + r.adjustValue;
		else if (r.adjustType == "newDefaultPrice" || r.adjustType == "newFinalPrice")
			price = r.adjustValue;
	});

	const cleanServiceFee = Number(roomtype.cleanService) || 0;
	//price = price

	return price;
}

export function Cards(props) {
	const { isHostView, privatePageToken, ignoreDomain, setPage } = props;
	let [s,sc,sx] = simpleState({
		sites: [] as types.Site[],
		roomTypes: [] as types.RoomType[],
		rooms: [] as types.Room[],
		priceRules: [] as types.PricingRule[],
		cards: [],
		visibleCount: 9,
	});

	let fromDate = props.checkInDate;
	//	if (!fromDate) {
	//		const today = new Date();
	//		today.setDate(today.getDate() + 1);
	//		const defaultCheckInDate = today.toISOString().split("T")[0];
	//		fromDate = defaultCheckInDate;
	//	}
	if (typeof fromDate == "string")
		fromDate = date(fromDate);

	React.useEffect(() => {
		const fetchData = async () => {
			const sites = await rpc.loadSites(ignoreDomain || false, privatePageToken);
			const roomtypes = await rpc.loadRoomTypes() as types.RoomType[];
			const rooms = await rpc.loadRooms() as types.Room[];
			const rules = await rpc.loadPricingRules();
			const photoUrl = "/photos/";

			s.sites = sites;
			s.roomTypes = roomtypes;
			s.rooms = rooms;
			s.priceRules = rules;

			let cards = [];
			sites.map((site) => {
				if (site.presentationType == "site") {
					site.roomTypes = roomtypes.filter(rt => rt.site_id == site.id)
					cards.push({ site });
				} else if (site.presentationType == "roomtypes") {
					let rtids = [...new Set(rooms.filter(r => r.site_id == site.id).map(r => r.roomType_id))];
					let rtViaRooms = rtids.map(rtid => roomtypes.filter(t => t.id == rtid)).flat();
					let rtViaSite = [];
					//					let rtViaSite = roomtypes
					//						.filter(t => t.site_id == site.id && !rtViaRooms.includes(t))
					//						.filter(t => rooms.filter(r => r.roomType_id == t.id).length);
					let rtFound = [...rtViaSite, ...rtViaRooms];
					let cardsFound = rtFound.map(rt => ({ site, roomtype: rt }));
					let cardsMissing = [];
					if (sites.length == 1) { // fallback only if there exists only one site
						let rtMissing = roomtypes.filter(rt => !rtFound.includes(rt));
						cardsMissing = rtMissing.map(rt => ({ site, roomtype: rt }));
					}
					cards.push(...cardsFound, ...cardsMissing);
				}
			});

			const cardsWithCover = await Promise.all(
				cards.map(async (card) => {
					let coverPhoto = null, defaultPrice = null;
					if (card.site.presentationType == "site") {
						const photos = await rpc.loadPhotos(card.site.id);
						coverPhoto = photos?.sort((a, b) => a.order - b.order)[0];
						defaultPrice = getdefaultPrice(card.site, rooms, roomtypes);
					} else if (card.site.presentationType == "roomtypes") {
						let photos = await rpc.loadPhotos(card.roomtype.id);
						if ((!photos || photos.length == 0) && card.roomtype.showSitePhotos) {
							const sitePhotos = await rpc.loadPhotos(card.site.id);
							photos = sitePhotos;
						}
						coverPhoto = photos?.sort((a, b) => a.order - b.order)[0];
						defaultPrice = getdefaultPrice(card.site, rooms, [card.roomtype]);
					}
					return {
						...card,
						coverPhoto: coverPhoto
							? { file: `${photoUrl}${coverPhoto.file}`, caption: coverPhoto.caption || "" }
							: null,
						defaultPrice: defaultPrice || null,
					};
				})
			);
			sc({ cards: cardsWithCover });
		};
		fetchData();
	}, []);


	function getdefaultPrice(site, rooms, roomtypes) {
		let room = rooms.find(x => x.site_id == site.id)
		let roomType;
		if (room) {
			roomType = roomtypes.find(x => x.id == room.roomType_id)
		}
		return roomType?.price ? roomType?.price : null;
	}

	React.useEffect(() => {
		function calculatePriceForRoomPNPP(room,fromDate,numGuests?) {
			let rt = s.roomTypes.find(t => t.id == room.roomType_id);
			if (!rt) return null;
			let site = s.sites.find(s => s.id == rt.site_id);
			if (!site) return null;
			let price = calculatePriceForRoomtypePNPP(rt,room,fromDate,s.priceRules);

			if (room.allow_extra_guests) {
				let baseGuests = (room.adults || 0) + (room.children || 0);
				let extraguests = Math.max(0, numGuests ? (numGuests - baseGuests) : baseGuests);
				if (extraguests > 0) {
					price += extraguests * (room.extra_fee_per_guest || 0);
				}
			}
			return price;
		}
		let minimalPriceBySiteId = {};
		s.rooms.forEach(r => {
			let siteId = r.site_id;
			let minPrice = minimalPriceBySiteId[siteId] || Infinity;
			minPrice = Math.min(minPrice, calculatePriceForRoomPNPP(r,fromDate));
			minimalPriceBySiteId[siteId] = minPrice;
		});
		let updatedSites = s.sites.map(site => ({
			...site,
			minimalPrice: minimalPriceBySiteId[site.id] ?? null,
		}));

		sc({ sites: updatedSites });
	},[fromDate, s.sites, s.roomTypes, s.rooms, s.priceRules]);

	React.useEffect(() => {
		sc({ visibleCount: 9 }); // reset when new data comes in
	}, [s.cards]);

	const loadMore = () => {
		sc({ visibleCount: s.visibleCount + 3 }); // to load 3 cards
	};

	return <div className="cards-container">
		<Style>{`
			& > div > span { cursor: pointer; }
		`}</Style>
		{s.cards.length == 0 && globalState.isHost &&
			<div>Es gibt noch keine Unterkunft. Klicken Sie <span className="text-primary" onClick={() => setPage?.("Sites")}>hier</span>,
			um Ihre erste Unterkunft anzulegen.</div>}
		{s.cards.length == 0 && !globalState.isHost && "Keine Unterkünfte vorhanden."}
		{s.cards.slice(0,s.visibleCount).map(card => {
			let id = card.roomtype ? card.site.id + "-" + card.roomtype.id : card.site.id;
			let name = card.site.name;
			if (card.roomtype?.name?.trim().length)
				name += " / " + card.roomtype.name;
			return <div key={id} className="card flexv"
				onClick={() => props.onChange({ ...card, displayName: name })}>
				{card.coverPhoto && (
					<img className="preview-image"
						src={card.coverPhoto.file}
						alt={card.coverPhoto.caption || "Titelbild"}
						title={card.coverPhoto.caption || "Keine Bildunterschrift verfügbar"}
					/>
				)}
				<div className="card-content flexv">
					<div className="card-title">{name}</div>
					{card.roomtype?.teaser || card.site?.teaser ? (
					<div className="card-teaser">
						{(card.roomtype?.teaser || card.site?.teaser)?.slice(0, 100)}
						{(card.roomtype?.teaser?.length > 100 || card.site?.teaser?.length > 100) && '...'}
					</div>
					) : null}
					{(card.roomtype && (card.minimalPrice || card.defaultPrice)) && (
					<div>
						<strong>Preis pro Nacht: </strong>
						{card.minimalPrice
						? `${card.minimalPrice.toFixed(2)} €`
						: card.defaultPrice
							? `${card.defaultPrice.toFixed(2)} €`
							: "Preis nicht eingegeben"}
					</div>
					)}
				</div>
			</div>
		})}
		{s.visibleCount < s.cards.length && (
			<button className="load-more" onClick={loadMore}>Mehr laden</button>
		)}
	</div>
};

export function Detail(props: { site: any, roomtype: any, displayName?: string, isHostView?: boolean, ignoreDomain?: boolean }) {
	const { isHostView } = props;
	let [s,sc,sx] = simpleState({
		sites: [] as types.Site[],
		roomTypes: [] as types.RoomType[],
		rooms: [] as types.room[],
		site: null,							// populate if !isHostView
		roomtype: null,						// populate if !isHostView
		showGuestForm: false,
		showBookingForm: false,
		numberOfSelectedRooms: {} as { [roomTypeId: string]: number },
		numberOfGuestsByRoomType: {} as { [roomTypeId: string]: number },
		images: [] as any[],
		selectedImageIndex: null as number | null,
		selectedBookingDetails: null as BookingDetailsExtendedType,
		amenities: [] as string[],
		organizationInfo: null as types.Organization,
		requestLinkStrings: {},
		showCalendar: false,
		showAmenitiesFor: null as types.RoomType | null,	// Which roomType's amenities are shown
		roomTypeAmenities: [] as string[],					// To store fetched amenities for that roomType
		guestError: "" as string,
		successSubmitNotif: null
	});

	React.useEffect(() => {
		if (s.site == null) {
			const hash = window.location.hash;
			const params = new URLSearchParams(hash.split('?')[1]);
			const siteId = params.get('id');
			rpc.loadSite(siteId)
				.then(site => {
					console.log("fetch site: ", site);
					sc({ site })
				});
		}
	},[])

	React.useEffect(() => {
		console.log("Selected site in Detail:", s.site);
		const fetchOrganization = async () => {
			if (!s.site) return;
			try {
				let orgId: string | null = null;
				if (s.site.organization_id) {
					orgId = s.site.organization_id;
				}
				// If it's a RoomType or Room and only has site_id, resolve via parent Site
				else if (s.site.site_id) {
					const parentSite = await rpc.loadSite(s.site.site_id);
					if (parentSite?.organization_id) {
						orgId = parentSite.organization_id;
					}
				}
				if (orgId) {
					const org = await rpc.loadOrganization(orgId);
					sc({ organizationInfo: org });
				} else {
					console.warn("No organization_id could be resolved for site:", s.site);
					sc({ organizationInfo: null });
				}
			} catch (err) {
				console.error("Error loading organization:", err);
				sc({ organizationInfo: null });
			}
		};
		fetchOrganization();
	},[s.site]);

	React.useEffect(() => {
		const fetchData = async () => {
			let rooms = await rpc.loadRooms();
			let roomtypes = await rpc.loadRoomTypes();
			let sites = await rpc.loadSites(props.ignoreDomain || false, null);

			s.rooms = rooms; s.roomTypes = roomtypes; s.sites = sites;

			// Fetch amenities
			if ((!s.site?.id) && (!props.site?.id)) return;
			let site0 = s.site || props.site;
			try {
				let siteRoomTypes = roomtypes.filter(rt => rt.site_id == site0.id);
				let allAmenities: string[] = [];

				for (let rt of siteRoomTypes) {
					let fetchedAmenities = await rpc.loadAmenities(rt.id);
					allAmenities.push(...fetchedAmenities);
				}
				//to avoid repeated entries
				let uniqueAmenities = [...new Set(allAmenities)];
				sc({ amenities: allAmenities.length > 0 ? uniqueAmenities : ["keine Ausstattung vergeben"] });
			} catch (error) {
				console.error("Error fetching amenities:", error);
			}
		};
		if (s.site || props.site || (props.roomtype && props.roomtype.length)) {
			fetchData();
		}
		rpc.loadStringsByKey("request:link")
			.then(strings => {
				sc({ requestLinkStrings: strings })
			});
	}, [props.site, props.roomtype, s.site]);

	React.useEffect(() => {
		if (s.site && s.roomTypes.length) {
//			let rtids = [...new Set(rooms.filter(r => r.site_id == site.id).map(r => r.roomType_id))];
//			let rtViaRooms = rtids.map(rtid => roomtypes.filter(t => t.id == rtid)).flat();
//			let rtFound = [...rtViaSite, ...rtViaRooms];
//			let cardsFound = rtFound.map(rt => ({ site, roomtype: rt }));
			let rt = s.roomTypes.find(x => x.site_id == s.site.id);
			if (rt) sc({ roomtype: rt }); // setRoomtype(rt);
		}
	},[s.roomTypes])

	React.useEffect(() => {
		const site0 = s.site || props.site || props.site?.site;
		const presentationType = site0?.presentationType || ""; const siteId = site0?.id || "";
		const loadPhotosForDetail = async () => {
			const photoUrl = "/photos/";
			try {
				if (!siteId) return;
				let photos = [];
				if (presentationType == "site") {
					photos = await rpc.loadPhotos(siteId);
					if (props.site?.showRoomtypePhotos || props.site?.site?.showRoomtypePhotos) {
						const siteRoomtypes = s.roomTypes.filter(rt => rt.site_id == siteId);
						for (const rt of siteRoomtypes) {
							const rtPhotos = await rpc.loadPhotos(rt.id);
							photos.push(...rtPhotos);
						}
					}
				} else if (presentationType == "roomtypes") {
					let roomtypeId = s.roomtype?.id || props.roomtype?.id;
					let showSitePhotos = s.roomtype?.showSitePhotos || props.roomtype?.showSitePhotos;
					let sitePhotos = [];
					if (!roomtypeId) {
						const siteRoomtypes = s.roomTypes.filter(rt => rt.site_id == siteId);
						if (siteRoomtypes.length > 0) {
							roomtypeId = siteRoomtypes[0].id;
						}
					}
					if (roomtypeId) {
						const roomtypePhotos = await rpc.loadPhotos(roomtypeId);
						photos = [...roomtypePhotos];
						//photos = await rpc.loadPhotos(roomtypeId);

						// to include site photos if checkbox was checked in RoomTypes
						if (showSitePhotos && siteId) {
							sitePhotos = await rpc.loadPhotos(siteId);
							photos = [...roomtypePhotos, ...sitePhotos];
						}
					}
				}
				sc({ images:
					photos
						.map(photo => ({ ...photo, file: `${photoUrl}${photo.file}`, }))
						.sort((a, b) => a.order - b.order)
				});
			} catch (error) {
				console.error("Error fetching photos in Detail component:", error);
			}
		};
		if (presentationType == "roomtypes" && s.roomTypes.length == 0) return;
		loadPhotosForDetail();
	}, [props.site, props.roomtype, s.roomTypes, s.roomtype]);

	let selectedSite = s.site || props.site || props.site?.site || {}; // Fallback to an empty object if site is null
	let displayName = selectedSite?.displayName || selectedSite?.site?.displayName || selectedSite?.name || selectedSite?.site?.name || "Kein Titel verfügbar";
	let roomType = s.roomtype || props.roomtype;
	const siteId = selectedSite?.site_id || selectedSite?.id;
	const fallbackSite = selectedSite || s.sites.find(s => s.id == siteId);

	let substitute = (s, propPattern, props) =>
		s.replace(propPattern, (_, key, fallback) => props[key] ?? (fallback?.trim() ?? ""));
	let propPattern = /\{\{(.+?)\s*(?:=(.+?))?\}\}/g;
	let infos = { siteName: selectedSite?.name, pageTitle: globalState.customizeInfos.title }
	let strings = { ...s.requestLinkStrings };
	Object.keys(strings).forEach(k => strings[k] = substitute(strings[k], propPattern, infos));
	let requestLink = "mailto:" + selectedSite?.email +
		"?subject=" + encodeURIComponent(strings.subject) +
		"&body=" + encodeURIComponent(strings.text);

	const hasRoomTypesWithRooms = (roomType ? [roomType] : s.roomTypes)
		.filter(rt => rt.site_id == selectedSite?.id)
		.some(rt => s.rooms.some(r => r.roomType_id == rt.id));

	const handleShowAmenities = async (roomType: types.RoomType) => {
		try {
			const fetchedAmenities = await rpc.loadAmenities(roomType.id);
			sc({
				roomTypeAmenities: fetchedAmenities.length > 0 ? fetchedAmenities : ["keine Ausstattung vergeben"],
				showAmenitiesFor: roomType
			});
		} catch (error) {
			console.error("Error loading amenities:", error);
			sc({
				roomTypeAmenities: ["Fehler beim Laden der Ausstattungen"],
				showAmenitiesFor: roomType
			});
		}
	};
	const isReservationValid = Object.keys(s.numberOfSelectedRooms).some(
		(roomtypeID) =>
			s.numberOfSelectedRooms[roomtypeID] > 0 &&
			(s.numberOfGuestsByRoomType[roomtypeID] ?? 0) > 0
	);
	const handleGuestChange = (roomType, event) => {
		const value = Number(event.target.value);
		const maxAdults = roomType.adults || 1;

		sc({ numberOfGuestsByRoomType: { ...s.numberOfGuestsByRoomType, [roomType.id]: value } });

		if (value > maxAdults) {
			sc({
				guestError: `Maximale Anzahl der Erwachsenen für ${roomType.name || "diese Unterkunft"} ist ${maxAdults}.`
			});
		} else {
			sc({ guestError: "" });
		}
	};
	const handleRequestBooking = (details: BookingDetailsType) => {
		if (isHostView) return;
		if (props.roomtype) { // for a single roomtype booking
			const selectedRoomTypesArray = s.roomTypes
				.filter(rt => s.numberOfSelectedRooms[rt.id] > 0)
				.map(rt => ({
					roomType_id: rt.id,
					name: rt.name,
					count: s.numberOfSelectedRooms[rt.id],
					pricePerNight: rt.price,
					guests: s.numberOfGuestsByRoomType[rt.id] || 0,
				}));

			sc({selectedBookingDetails: {
				...details,
				roomType_id: props.roomtype.id,
				roomTypes: selectedRoomTypesArray,
			}});
		} else { // for Multi roomtype booking (presentationType : site)
			const selectedRoomTypesArray = s.roomTypes
				.filter(rt => s.numberOfSelectedRooms[rt.id] > 0)
				.map(rt => ({
					roomType_id: rt.id,
					name: rt.name,
					count: s.numberOfSelectedRooms[rt.id],
					pricePerNight: rt.price,
					guests: s.numberOfGuestsByRoomType[rt.id] || 0,
				}));
			sc({selectedBookingDetails: {
				...details,
				roomType_id: null,
				roomTypes: selectedRoomTypesArray,
			}});
		}
		sc({ showGuestForm: true });
	};

	const openImageAlbum = (index: number) => sc({ selectedImageIndex: index });
	const closeImageAlbum = () => sc({ selectedImageIndex: null });
	const prevImage = () => {
		if (s.selectedImageIndex != null)
			sc({ selectedImageIndex: s.selectedImageIndex == 0 ? s.images.length - 1 : s.selectedImageIndex - 1 });
	};
	const nextImage = () => {
		if (s.selectedImageIndex != null)
			sc({ selectedImageIndex: s.selectedImageIndex == s.images.length - 1 ? 0 : s.selectedImageIndex + 1 });
	};
	const roomTypeColumns = [
		{
			accessor: "name", Header: "Arten der Unterbringung",
			Cell: ({ value, rowIndex }) => <a href="#">{value || `Zimmertyp ${rowIndex + 1}`}</a>
		},
		{
			id: "roomCount", Header: "Anzahl der Räume", accessor: (row) => row.id, // we only need the id for selecting
			Cell: ({ row }) => {
				const maxRooms = s.rooms.filter(r => r.roomType_id == row.id).length;
				return <select
					style={{ width: "100px" }}
					value={s.numberOfSelectedRooms[row.id] ?? 0}
					onChange={(event) => {
						const selectedRooms = Number(event.target.value);
						sc({
							numberOfSelectedRooms: {
								...s.numberOfSelectedRooms,
								[row.id]: selectedRooms,
							}
						});
						if (selectedRooms == 0) {
							sc({
								numberOfGuestsByRoomType: {
									...s.numberOfGuestsByRoomType,
									[row.id]: 0
								}
							});
						} else {
							sc({
								numberOfGuestsByRoomType: {
									...s.numberOfGuestsByRoomType,
									[row.id]: s.numberOfGuestsByRoomType[row.id] || 1
								}
							});
						}
					}}
				>
					{[...Array(maxRooms + 1)].map((_, i) => {
						const basePrice = row.price || 0;
						return <option key={i} value={i}>
							{i} {i > 0 ? `(${basePrice * i}€)` : ""}
						</option>
					})}
				</select>
			}
		},
		{
			id: "amenities", accessor: r => r.id, Header: "Ausstattungen",
			Cell: ({ row }) => <button className="showAmenities" onClick={() => handleShowAmenities(row)}>Anzeigen</button>
		},
		{
			accessor: "adults", Header: "Anzahl der Gäste",
			Cell: ({ row }) => {
				const capacity = row.adults || s.numberOfGuestsByRoomType[row.id] || 2;
				return <div className="flexh">
					{Array.from({ length: capacity }, (_, i) => <MuiIcon key={i} name="person" />)}
				</div>
			}
		},
		{
			accessor: "price", Header: "Preis pro Nacht",
			Cell: ({ row }) => {
				if (!row.price) return "n/a";
				return <>
					{Number(row.price).toFixed(2)} €
					{row.cleanService && (
						<span style={{ display: "block", fontSize: "0.85em", color: "coral" }}>
							+ Reinigungsgebühr (einmalig): {Number(row.cleanService).toFixed(2)} €
						</span>
					)}
				</>
			}
		}
	];

	return <div className="content">
		{/* Booking Form Overlay */}
		{!isHostView && s.showGuestForm && (
			<div className="overlay">
				<GuestForm
					siteId={selectedSite?.id}
					siteName={selectedSite?.name || ""}
					bookingDetails={s.selectedBookingDetails}
					onBack={() => sc({ showGuestForm: false })}
					onClose={() => sc({ showGuestForm: false })}
				/>
			</div>
		)}
		<div className="details flexv">
			{s.showCalendar && <AvailabilityCalendar onClose={() => sc({showCalendar: false})} />}
			<div className="details-title">{displayName} {(selectedSite?.presentationType == "roomtypes" && roomType?.name) ? ` / ${roomType.name}` : ""}</div>
				<div>
					<div className="flexh">
						<MuiIcon name="location_on" color="dodgerblue" size={25} />
						<div className="flexh gap-xs">
							<span>{fallbackSite?.address || "Nicht verfügbar"}</span>
							<span>|</span>
							<span>{fallbackSite?.city || "Nicht verfügbar"}</span>
							<span>|</span>
							<span>{fallbackSite?.country || "Nicht verfügbar"}</span>
						</div>
					</div>
					{/*////////////////////////////////////// Photo Gallery ///////////////////////////////////////*/}
					<div className="photo-gallery">
						{s.images.slice(0, 5).map((photo, index) => {
							let photoClass = ["main-photo", "side-photo-1", "side-photo-2", "small-photo-1", "small-photo-2"][index] || "additional-photo";
							return (
								<img
									key={photo.id}
									src={photo.file}
									alt={photo.caption || "Photo"}
									className={photoClass}
									title={photo.caption || "Keine Beschriftung wurde vergeben "}
									onClick={() => openImageAlbum(index)}
								/>
							);
						})}
						{s.images.length > 5 && (
							<div className="more-photos" onClick={() => openImageAlbum(5)}>
								+{s.images.length - 5} more
							</div>
						)}
					</div>
				</div>
				{/* Photo Album (Lightbox) */}
				{s.selectedImageIndex !== null && (
					<div className="lightbox">
						<button className="close-btn" onClick={closeImageAlbum}>&times;</button>
						<button className="prev-btn" onClick={prevImage}>&#10094;</button>
						<img src={s.images[s.selectedImageIndex].file} alt="Large Photo" className="lightbox-image" />
						<button className="next-btn" onClick={nextImage}>&#10095;</button>
					</div>
				)}
				<div className="amenities flexh wrap">
					{s.amenities.length > 0
					? (s.amenities.map((amenity, index) => <span key={index} className="amenity-item">{amenity}</span>))
					: ( <div>Keine Ausstattung vergeben.</div> )}
				</div>
				{/* Description and Booking Form */}
				<div className="details-content">
					<div className="description">
						{(() => {
							const presentationType = selectedSite?.presentationType;
							const siteDescription = selectedSite?.description;
							const roomtypeDescription = roomType?.description;
							if (presentationType == "site") {
								return siteDescription || "Keine Beschreibung verfügbar";
							} else if (presentationType == "roomtypes") {
								return roomtypeDescription || siteDescription || "Keine Beschreibung verfügbar";
							} else {
								return siteDescription || "Keine Beschreibung verfügbar";
							}
						})()}
					</div>
					{!isHostView && hasRoomTypesWithRooms && (
						<Dialog open={s.showBookingForm} >
							<BookingForm
								site={selectedSite}
								roomType={roomType}
								siteId={selectedSite.id}
								onRequestBooking={handleRequestBooking}
								rooms={s.rooms}
								roomTypes={s.roomTypes}
								numberOfSelectedRooms={s.numberOfSelectedRooms}
								numberOfGuestsByRoomType={s.numberOfGuestsByRoomType}
								isRoomType={!!roomType}
								onClose={() => sc({ showBookingForm: false })}
								onSuccessSubmit={msg => {
									sc({ successSubmitNotif: msg, showBookingForm: false, numberOfSelectedRooms: {} })
								}}
							/>
						</Dialog>
					)}
				</div>
				{/* When we are in a roomtype view and there are no multiple room types */}
				{selectedSite?.presentationType == "roomtypes" && hasRoomTypesWithRooms && !s.showBookingForm && !isHostView &&(
					<div style={{ display: "flex", justifyContent: "flex-end", margin: "20px 0" }}>
						<button className="booking-open-btn" onClick={() => sc({ showBookingForm: true })}>
							Buchung diese Einheit
						</button>
					</div>
				)}
				{selectedSite?.presentationType == "roomtypes" && hasRoomTypesWithRooms && (
					<Dialog open={s.showBookingForm} >
						<BookingForm
							site={selectedSite}
							roomType={roomType}
							siteId={selectedSite.id}
							onRequestBooking={handleRequestBooking}
							rooms={s.rooms}
							roomTypes={s.roomTypes}
							numberOfSelectedRooms={s.numberOfSelectedRooms}
							numberOfGuestsByRoomType={s.numberOfGuestsByRoomType}
							onClose={() => sc({ showBookingForm: false })}
							onSuccessSubmit={msg => {
								sc({ successSubmitNotif: msg, showBookingForm: false, numberOfSelectedRooms: {} })
							}}
							isRoomType={!!roomType}
						/>
					</Dialog>
				)}
				{<Alert backgroundColor="#77c47b" textColor="white"
					open={s.successSubmitNotif} onClose={() => sc({ successSubmitNotif: null })}
					anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
					text={s.successSubmitNotif}
				/>}
				{/* to create the table at the presentation page when the presentationtype is "site"*/}
				{selectedSite?.presentationType=="site" && hasRoomTypesWithRooms && (<>
					{!isHostView && (
						<div style={{ display: "flex", justifyContent: "flex-end" }}>
							<button
								disabled={!isReservationValid || !!s.guestError}
								onClick={() => sc({ showBookingForm: true })}
							>
								Reservieren
							</button>
						</div>
					)}
					<div className="roomtype-table">
						{s.guestError && (
							<div className="errorMessage">{s.guestError}</div>
						)}
						<Table
							columns={roomTypeColumns}
							items={s.roomTypes
								.filter(rt => rt.site_id == selectedSite?.id)
								.filter(rt => s.rooms.some(r => r.roomType_id == rt.id))
							}
						/>
						{s.showAmenitiesFor && (
							<Dialog open={s.showAmenitiesFor}>
								<div className="dialog-content">
									<Style>{`& .amenities { width: 400px; }`}</Style>
									<div className="close-button"><button
										onClick={() => sc({showAmenitiesFor: null})}
									><MuiIcon name="close"/></button></div>
									<div className="px-md amenities">
										<div className="title">Ausstattungen – {s.showAmenitiesFor.name}</div>
										<ul>
											{s.roomTypeAmenities.map((amenity, index) => <li key={index}>{amenity}</li>)}
										</ul>
									</div>
								</div>
							</Dialog>
						)}
					</div>
				</>)}
				{/* Organization Details */}
				<h3 className="contact-info">Kontaktinformationen zur Unterkunft</h3>
				{selectedSite && (
					<div className="organization-details">
						{selectedSite?.email && <div style={{ float: "right" }}>
							<a href={requestLink}>
								<button>Anfrage via Email</button>
							</a>
						</div>}
						<div className="flexv gap-sm">
							<p>Anbieter: {s.organizationInfo?.name || "Nicht verfügbar"}</p>
							<p>Telefon: {selectedSite?.phoneNumber || "-"}</p>
							<p>Email: {selectedSite?.email || "-"}</p>
							<div className="flexh">
								<span>{s.organizationInfo?.address || " "}</span>
								<span>|</span>
								<span>{s.organizationInfo?.city || " "}</span>
								<span>|</span>
								<span>{s.organizationInfo?.country || " "}</span>
							</div>
						</div>
					</div>
				)}
		</div>
	</div>
}
