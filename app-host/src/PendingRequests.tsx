import React from "react";
import MUI from "@material-ui/core";
import { rpc } from "./index";
import * as types from "./types";
import { isBookingOutdated } from "./OutdatedBookings";
const {
	Dialog, DialogTitle, DialogActions,
	DialogContent, DialogContentText,
} = MUI;

interface BookingData {
	id: string;
	site_id: string,
	//roomType_id: string;
	firstName: string;
	lastName: string;
	startDate: string | Date;
	endDate: string | Date;
	email: string;
	address: string;
	telephone: string;
	additionalWishes: string;
	dateSent:string | Date;
	status: "ausstehend" | "akzeptiert" | "abgelehnt" | "veraltet";
	statusDecisionTime: Date;
}

export function PendingRequests() {
	const [pendingRequests, setPendingRequests] = React.useState<BookingData[]>([]);
	const [selectedBooking, setSelectedBooking] = React.useState<BookingData | null>(null);
	const [successNotification, setSuccessNotification] = React.useState<string | null>(null);
	const [confirmAction, setConfirmAction] = React.useState<"accept" | "reject" | "rejectSilently" | null>(null);
	const [notification, setNotification] = React.useState<string | null>(null);
	const [notificationStyle, setNotificationStyle] = React.useState<"accept" | "reject" | "rejectSilently" | "error" | null>(null);

	React.useEffect(() => {
		(async function fetchPendingBookings() {
			try {
				const data = await rpc.loadAllBookingData();
				const ausstehend = data.filter(record =>
					(record.status === "ausstehend" || record.status == null) &&
					!isBookingOutdated(record)
				);
				setPendingRequests(ausstehend || []);
			} catch (error) {
				console.error("Failed to load pending bookings:", error);
			}
		})();
	}, []);

	React.useEffect(() => {
		if (selectedBooking || confirmAction){
			document.body.style.overflow = 'hidden';
		} else {
			document.body.style.overflow = 'auto';
		}

		return () => {
			document.body.style.overflow = 'auto';
		};
	}, [selectedBooking, confirmAction]);


	const openModal = (booking: BookingData) => setSelectedBooking(booking);
	const closeModal = () => {
		setSelectedBooking(null);
		setConfirmAction(null);
	};

	const isOverlappingBooking = (booking: BookingData, allBookings: BookingData[])=>{
		const bookingStart = new Date(booking.startDate);
		const bookingEnd = new Date(booking.endDate);

		// some method is used to check if at least one element in an array satisfies a given condition
		return allBookings.some(existing => {
			if (
				existing.id === booking.id ||
				existing.site_id !== booking.site_id ||
				existing.status !== "akzeptiert"
			) {
				return false;
			}

			const existingStart = new Date(existing.startDate);
			const existingEnd = new Date(existing.endDate);

			const overlap = bookingStart < existingEnd && existingStart < bookingEnd;

			if (overlap) {
				console.log("overlap detected: ",{
					existingId: existing.id,
					existingStart,
					existingEnd,
					bookingStart,
					bookingEnd
				});
			}

			return overlap;
		});
	};

	/*const isOverlappingBooking = (booking: BookingData, allBookings: BookingData[])=>{
		const bookingStart = new Date(booking.startDate);
		const bookingEnd = new Date(booking.endDate);

		// some method is used to check if at least one element in an array satisfies a given condition
		return allBookings.some(existing => {

			// to ignore the current booking itself and any that are not accepted,
			// because only accepted bookings should block availability
			if (
				existing.id === booking.id || existing.status !== "accepted") {
					return false
				}

			const useRoomType = booking.roomType_id && existing.roomType_id;
			const isSameHouse = useRoomType
				? booking.roomType_id === existing.roomType_id
				: booking.site_id === existing.site_id;

			if (!isSameHouse)return false;

			const existingStart = new Date(existing.startDate);
			const existingEnd = new Date(existing.endDate);

			const overlap = bookingStart < existingEnd && existingStart < bookingEnd;

			if (overlap) {
				console.log("Overlap detected with booking ID: ", existing.id);
			}
			//return bookingStart < existingEnd && existingStart < bookingEnd;
			return overlap;
		});
	};*/

	const handleConfirm = async () => {
		if (!selectedBooking || !confirmAction) return;

		try {
			if (confirmAction == "accept"){
				/*
				const allBookings = await rpc.loadAllBookingData();
				console.log("allBookings", allBookings);
				const isOverlapping = isOverlappingBooking(selectedBooking, allBookings);

				if (isOverlapping) {
					alert("Dieser Zeitraum überschneidet sich mit einer bereits bestätigten Buchung. Bitte kontaktieren Sie den Kunden, um ein anderes Datum zu vereinbaren.");
					setConfirmAction(null);
					return;
				}*/
				console.log("INFO:the overlap check temporarily disabled.");
			}

			selectedBooking.status = confirmAction == "accept" ? "akzeptiert" : "abgelehnt";
			selectedBooking.statusDecisionTime = new Date();

			// to exclude sening mails to the customer when "ablehnen ohne E-Mail" is chosen.
			if (confirmAction == "rejectSilently"){
				await rpc.saveBookingData(selectedBooking); // just save, no email
			} else {
				await rpc.requestStatusUpdate(selectedBooking); // save + email
			}

			const updatedBookings = await rpc.loadAllBookingData();
			const ausstehend = updatedBookings.filter(record => record.status == "ausstehend" || record.status == null);
			setPendingRequests(ausstehend || []);
			//const pending = pendingRequests.filter(record => record.status == "pending" || record.status == null);
			//setPendingRequests(pending || []);

			let message = "";
			let style: typeof notificationStyle = null;

			if (confirmAction == "accept"){
				message = "Die Buchungsanfrage wurde erfolgreich akzeptiert!";
				style = "accept";
			} else if (confirmAction == "reject") {
				message = "Die Buchungsanfrage wurde erfolgreich abgelehnt!";
				style = "reject";
			} else if (confirmAction == "rejectSilently") {
				message = "Die Anfrage wurde abgelehnt, ohne dem Kunden eine E-Mail zu senden.";
				style = "rejectSilently";
			}

			setNotification(message);
			setNotificationStyle(style);
			closeModal();

		} catch (error) {
			console.error("Error processing booking request:", error);
			setNotification("Fehler beim Bearbeiten der Buchungsanfrage");
			setNotificationStyle("error");
			closeModal();
		}

		setTimeout(() => {
			setNotification(null);
			setNotificationStyle(null);
		}, 3000);
	};

	return (<>
		{notification && (
			<div className={`notification-bar ${notificationStyle}`}>{notification}</div>
		)}
		<div className="booking-requests">
			<h3 style={{fontWeight: "normal", color:"#0080c0"}}>
				Hier sind die Buchungsanfragen, die noch auf eine Entscheidung warten. Eine Antwort geht dann ggf. an den Gast raus.
			</h3>
			{pendingRequests.length == 0 ? (
				<h3 style={{fontWeight:"normal"}}>Keine ausstehenden Buchungsanfragen anzuzeigen</h3>
			) : (
				<div className="table-wrapper">
					<table>
						<thead>
							<tr>
								<th>Unterkunfts-ID</th>
								<th>Vorname</th>
								<th>Nachname</th>
								<th>Check-in-Datum</th>
								<th>Check-out-Datum</th>
								<th>Email</th>
								<th>Benutzeradresse</th>
								<th>Telefon</th>
								<th>Zusätzliche Wünsche</th>
								<th>Pries pro Nacht</th>
								<th>Versanddatum</th>
							</tr>
						</thead>
						<tbody>
							{pendingRequests.map((request) => (
								<tr key={request.id} onClick={() => openModal(request)}>
									<td>{request.site_id}</td>
									<td>{request.firstName}</td>
									<td>{request.lastName}</td>
									<td>{new Date(request.startDate).toLocaleDateString()}</td>
									<td>{new Date(request.endDate).toLocaleDateString()}</td>
									<td>{request.email}</td>
									<td>{request.address}</td>
									<td>{request.telephone}</td>
									<td>{(request.additionalWishes || "").slice(0, 20)}...</td>
									<td>{request.price}</td>
									<td>{new Date(request.dateSent).toLocaleString()}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
			{selectedBooking && <Dialog open={selectedBooking!=null}>
				<div className="details-container">
					<br/>
					<div className="close-btn"><button onClick={closeModal}>X</button></div>
					<div className="title">Eine Buchungsanfrage eines Kunden</div>
					<hr/>
					<div className="flexh gap-md"><strong>ausgewählte Unterkunft:</strong> {selectedBooking.site_id}</div>
					<div className="flexh gap-md"><strong>Vorname:</strong> {selectedBooking.firstName}</div>
					<div className="flexh gap-md"><strong>Nachname:</strong> {selectedBooking.lastName}</div>
					<div className="flexh gap-md"><strong>Check-in-Datum:</strong> {new Date(selectedBooking.startDate).toLocaleDateString()}</div>
					<div className="flexh gap-md"><strong>Check-out Date:</strong> {new Date(selectedBooking.endDate).toLocaleDateString()}</div>
					<div className="flexh gap-md"><strong>E-Mail:</strong> {selectedBooking.email}</div>
					<div className="flexh gap-md"><strong>Adresse:</strong> {selectedBooking.address}</div>
					<div className="flexh gap-md"><strong>Telefon:</strong> {selectedBooking.telephone}</div>
					<div className="flexh gap-md"><strong>Zusätzliche Wünsche:</strong> {selectedBooking.additionalWishes}</div>
					<div className="flexh gap-md"><strong>Versanddatum:</strong> {new Date(selectedBooking.dateSent).toLocaleString()}</div>
					<div className="modal-buttons flexh gap-sm">
						<button className="accept-button" onClick={() => setConfirmAction("accept")}>akzeptieren</button>
						<button className="reject-button" onClick={() => setConfirmAction ("reject")}>ablehnen</button>
						<button className="reject2-button" onClick={() => setConfirmAction ("rejectSilently")}>ablehnen ohne E-Mail</button>
					</div>
				</div>
			</Dialog>}
			<Dialog open={confirmAction}>
				<div className="details-container">
					<div>Sind Sie sicher, dass Sie die Buchungsanfrage {confirmAction == "accept" ? "akzeptieren" : "ablehnen"} möchten?</div>
					<div className="modal-buttons flexh gap-sm centerh">
						<button className="confirm-button" onClick={handleConfirm}>Ja</button>
						<button className="cancel-button" onClick={() => setConfirmAction(null)}>Abbrechen</button>
					</div>
				</div>
			</Dialog>
		</div>
	</>);
}

