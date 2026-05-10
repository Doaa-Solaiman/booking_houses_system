import React from 'react';

export function BookingSummary({ bookingInfo, onEditClick, onConfirmClick }) {
	if (!bookingInfo) return null;

	const styling = {
		color: "white",
		fontSize: "30px",
		fontFamily: "cursive",
		padding: "10px",
		textAlign: "center",
	};

	const stylingTwo = {
		color: "white",
		fontSize: "20px",
		padding: "5px",
	};

	return (
		<div className="booking-summary">
			<h2 style={styling}>Buchungsübersicht und Ihre eingegebenen Daten</h2>
			<p style={stylingTwo}>Name: {bookingInfo.name}</p>
			<p style={stylingTwo}>das gebuchte Haus: {bookingInfo.houseTitle}</p>
			<p style={stylingTwo}>Anreisedatum: {bookingInfo.checkInDate}</p>
			<p style={stylingTwo}>Abreisedatum: {bookingInfo.checkOutDate}</p>
			<p style={stylingTwo}>Anzahl der Gäste: {bookingInfo.numberOfGuests}</p>
			<h5 style={stylingTwo}>voraussichtlichen Kosten: {bookingInfo.estimatedFund} €</h5>
			<p style={stylingTwo}>besondere Wünsche: {bookingInfo.specialRequests}</p>
			<div className="buttons">
				<button style={{ margin: "20px" }} onClick={onEditClick}>bearbeiten</button>
				<button onClick={onConfirmClick}>bestätigen</button>
			</div>
		</div>
	);
}

