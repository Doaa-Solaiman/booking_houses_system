import React, { useState } from "react";
import ToastNotification from './ToastNotification';
import ToastNotificationEn from "./translations/ToastNotificationEn";
import { houses } from ".";
import { HousesEn } from "./translations/HousesEn";

const BookingSummary = ({ bookingDetails, onBack, onNavigate, selectedHouse, language }) => {
	const houseData = selectedHouse
		? (language === 'de' ? houses.find(house => house.id === selectedHouse.id) : HousesEn.find(house => house.id === selectedHouse.id))
		: {};

	const {
		houseTitle,
		location,
		startDate,
		endDate,
		numberOfNights,
		costs,
		firstName,
		lastName,
		email,
		phone,
		address,
		message
	} = bookingDetails || {};

	const [showToast, setShowToast] = useState(false);

	const handleConfirm = () => {
		setShowToast(true);
	};

	const handleToastClose = () => {
		setShowToast(false);
		onNavigate('home');
	};

	const isGerman = language === 'de';

	return (
		<div className="biggerContain">
			<div className="booking-summary">
				<h2>{isGerman ? "Buchungszusammenfassung" : "Booking Summary"}</h2>
				<div className="summary-item">
					<strong>{isGerman ? "Vorname:" : "First Name:"}</strong> {firstName}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Nachname:" : "Last Name:"}</strong> {lastName}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "E-Mail:" : "Email:"}</strong> {email}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Telefon:" : "Phone:"}</strong> {phone}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Adresse:" : "Address:"}</strong> {address}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Haus Titel:" : "House Title:"}</strong> {houseData.houseTitle || ''}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Ort:" : "Location:"}</strong> {houseData.location || ''}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Checkin:" : "Check-in:"}</strong> {startDate}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Checkout:" : "Check-out:"}</strong> {endDate}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Anzahl der Nächte:" : "Number of Nights:"}</strong> {numberOfNights}
				</div>
				<div className="summary-item">
					<strong>{isGerman ? "Besondere Wünsche:" : "Special Requests:"}</strong> {message}
				</div>
				<p style={{ width: "auto" }}>---------------------------------------------------------</p>
				<div className="summary-item">
					<strong>{isGerman ? "Kosten:" : "Total Cost:"}</strong> {costs} Euros
				</div>
				<br />
				<button style={{ margin: "20px" }} onClick={onBack}>{isGerman ? "Zurück" : "Back"}</button>
				<button onClick={handleConfirm}>{isGerman ? "Buchung bestätigen!" : "Confirm Booking!"}</button>
				{showToast && (
					isGerman
					? <ToastNotification onClose={handleToastClose} />
					: <ToastNotificationEn onClose={handleToastClose} />
				)}
			</div>
		</div>
	);
};

export default BookingSummary;
