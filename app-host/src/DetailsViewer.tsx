import React from 'react';
import MUI from "@material-ui/core";
const {
	Dialog, DialogTitle, DialogContent, DialogActions,
	Button
} = MUI;
import { MuiIcon, Select, Text, Date as DateInput } from "./components/formelements";

interface DetailsViewerFunc {
	booking: BookingData;
	sites: Site[];
	roomtypes: RoomType[];
	onAccept: () => void;
	onReject: () => void;
	onRejectSilently: () => void;
	onClose: () => void;
	onSaveChanges: (updated: Partial<BookingData>) => void;
}

export function DetailsViewer({
	booking,
	onAccept, onReject, onRejectSilently,
	onClose, onSaveChanges,
	sites, roomtypes, rooms
}: DetailsViewerFunc) {
	if (!booking) return null;

	//local editable state
	const [editedBooking, setEditedBooking] = React.useState({
		site_id: booking.site_id || '',
		roomTypes: booking.roomTypes || [],
		roomType_id: booking.roomType_id || '',
		room_id: booking.room_id || '',
		startDate: booking.startDate || '',
		endDate: booking.endDate || '',
		firstName: booking.firstName || '',
		lastName: booking.lastName || '',
		adults: booking.adults || '',
		telephone: booking.telephone || '',
		email: booking.email || '',
		address: booking.address || '',
		additionalWishes: booking.additionalWishes || '',
		totalPrice: booking.totalPrice,
		status: booking.status,
	});

	const isCanceled = booking.status == "storniert";
	const isPending = booking.status == "ausstehend";
	const lockFields = isCanceled || isPending; // fields that must be locked when booking is canceled OR still pending
	const pricePerNight = booking.roomTypes?.[0]?.pricePerNight || booking.price || null;
	const totalNights = Math.ceil((new Date(booking.endDate).getTime() - new Date(booking.startDate).getTime()) / (1000 * 60 * 60 * 24));

	const handleChange = (field: keyof typeof editedBooking, value: any) => {
		setEditedBooking(prev => ({ ...prev, [field]: value }));
	};
	let statusItems = 	[
		{ text: "Akzeptiert", value: "akzeptiert" },
		{ text: "Storniert", value: "storniert" }
	]
	if (editedBooking.status=="ausstehend")
		statusItems.push({ text: "Ausstehend", value: "ausstehend" })
	const DetailsRow = (props) => <div className="details-row">{props.children}</div>

	return (
		<div className="details-container dialog-content flexv gap-md">
			<div className="flexh endh pd-sm dialog-btn" style={{zIndex: 100}}>
				<button className="bg-red no-padding" onClick={onClose} aria-label="Close">
					<MuiIcon name="close" />
				</button>
			</div>
			<div className="px-md flexv gap-md">
				<div className="booking-summary form">
					<DetailsRow children={[
						<span className="details-label">Booking Id:</span>,
						<span className="details-value">{booking.id || '—'}</span>
					]}/>
					<DetailsRow children={[
						<span className="details-label">Anreisedatum:</span>,
						<DateInput
							name="startDate"
							className="details-input"
							value={editedBooking.startDate}
							onChange={handleChange}
							disabled={lockFields}
						/>
					]}/>
					<DetailsRow children={[
						<span className="details-label">Abreisedatum:</span>,
						<DateInput
							name="endDate"
							className="details-input"
							value={editedBooking.endDate}
							onChange={handleChange}
							disabled={lockFields}
						/>
					]}/>
					<DetailsRow children={[
						<span className="details-label">Anzahl der Nächte:</span>,
						<span className="details-value">{totalNights || "unbekannt"}</span>
					]}/>
					<DetailsRow children={[
						<span className="details-label">Anzahl der Gäste:</span>,
						<span className="details-value">{booking.totalGuests || "unbekannt"}</span>
					]}/>
					<DetailsRow children={[
						<span className="details-label">Gesamtkosten:</span>,
						<span className="details-value">
							{booking.totalPrice ? `${booking.totalPrice} €` : '—'}
						</span>
					]}/>
					<DetailsRow children={[
						<span className="details-label">Buchung erfolgt am:</span>,
						<span className="details-value">{new Date(booking.dateSent).toLocaleString()}</span>
					]}/>
					<DetailsRow children={[
						<span className="details-label">Buchungsquelle:</span>,
						<span className="details-value">{booking.source || 'undefiniert'}</span>
					]}/>
					<DetailsRow children={[
						<span className="details-label">Aktueller Status:</span>,
						<Select
							name="status" className="details-input"
							value={editedBooking.status}
							onChange={handleChange}
							disabled={isCanceled || booking.status == "ausstehend"}
							items={statusItems}
						/>
					]}/>
				</div>
				<div className="booking-summary form">
					<div className="details-row">
						<span className="details-label">Unterkunft:</span>
						<Select
							name="site_id" className="details-input"
							value={editedBooking.site_id}
							onChange={handleChange}
							items={sites.map((site) => ({ text: site.name, value: site.id }))}
							disabled
						/>
					</div>
					{Array.isArray(editedBooking.roomTypes) && editedBooking.roomTypes.length > 0 ?
					(<div className="details-row">
						<div className="details-label flexv fullv pt-sm">Zimmertyp:</div>
						<div className="form-group flexv gap-md">{editedBooking.roomTypes.map(rt => {
							console.log("editedBooking.roomTypes: ",editedBooking.roomTypes);
							let relatedRooms = rooms.filter(r => r.roomType_id == rt.roomType_id);
							let assignedRooms = rt.room_ids ? rt.room_ids.split(",") : [];
							return <div className="flexv gap-sm">
								<Select className="details-input"
									value={rt.roomType_id}
									items={roomtypes
									.filter(rt0 => rt0.site_id == editedBooking.site_id)
									.map(rt0 => ( { text: rt0.name, value: rt0.id } ))}
									disabled
								/>
								<div className="flexh fullv gap-sm">
									<span className="details-label pt-sm">Mieteinheit:</span>
									<div className="flexv fullh gap-sm">{Array.from(
										{ length: rt.count },
										(_, i) => <Select className="details-input"
											value={assignedRooms[i] ?? ""}
											onChange={(n,v) => {
												let newRoomIds = assignedRooms;
												if (!newRoomIds.includes(v)) newRoomIds.push(v);
												setEditedBooking(prev => ({
													...prev,
													roomTypes: prev.roomTypes.map(rt0 => {
														if (rt0.roomType_id == rt.roomType_id) {
															return { ...rt0, room_ids: newRoomIds.toString() };
														}
														return rt0;
													})
												}));
											}}
											items={relatedRooms.map(r0 => { return {text: r0.name, value: r0.id} })}
											disabled={lockFields}
										/>
									)}</div>
								</div>
							</div>
						})}</div>
					</div>)
					: (<>
						<div className="details-row">
							<span className="details-label">Zimmertyp:</span>
							<div className="flexv fullh">
								<Select
									name="roomType_id" className="details-input"
									value={editedBooking.roomType_id}
									onChange={handleChange}
									items={roomtypes
									.filter(rt => rt.site_id == editedBooking.site_id)
									.map(rt => ( { text: rt.name, value: rt.id } ))}
									disabled
								/>
							</div>
						</div>
						<div className="details-row flexh fullh between">
							<span className="details-label">Mieteinheit:</span>
							<Select
								name="room_id" className="details-input"
								value={editedBooking.room_id}
								onChange={handleChange}
								disabled={lockFields}
								items={rooms
									.filter(r =>
										r.site_id == editedBooking.site_id &&
										r.roomType_id == editedBooking.roomType_id
									)
									.map(r => ( { text: r.name, value: r.id } ))
								}
							/>
						</div>
					</>)}
				</div>
				<div className="booking-summary form">
					<div className="details-row">
						<span className="details-label">Name:</span>
						<span className="details-value">
							{booking.firstName} {booking.lastName}
						</span>
					</div>
					<div className="details-row">
						<span className="details-label">Telefonnummer:</span>
						<span className="details-value">{booking.telephone || '—'}</span>
					</div>
					<div className="details-row">
						<span className="details-label">E-Mail:</span>
						<span className="details-value">{booking.email || '—'}</span>
					</div>
					<div className="details-row">
						<span className="details-label">Adresse:</span>
						<span className="details-value">{booking.address || '—'}</span>
					</div>
					<div className="details-row">
						<span className="details-label">Zusätzliche Wünsche:</span>
						<span className="details-value">{booking.additionalWishes || 'nichts eingefügt'}</span>
					</div>
				</div>
			</div>
			<div className="modal-buttons flexh centerh gap-sm pd-md">
			{
				booking.status == "akzeptiert" &&
				<button className="save-editing" onClick={() => onSaveChanges({ ...booking, ...editedBooking })}>Speichern</button>
			}
			{
				booking.status == "ausstehend" && [
					<button className="accept-button" onClick={() => { onAccept(); }}>akzeptieren</button>,
					<button className="reject-button" onClick={onReject}>ablehnen</button>,
					<button className="reject2-button" onClick={onRejectSilently}>ablehnen ohne E-Mail</button>
				]
			}
			</div>
		</div>
	);
}
