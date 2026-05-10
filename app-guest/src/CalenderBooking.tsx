import React, { useState, useEffect } from "react";

const CalendarBooking = ({ selectedHouse }) => {
	const [startDate, setStartDate] = React.useState("");
	const [endDate, setEndDate] = React.useState("");
	const [formError, setFormError] = React.useState("");
	const [estimatedFund, setEstimatedFund] = React.useState(null);

	const handleStartDateChange = (e) => {
		setStartDate(e.target.value);
		if (endDate && e.target.value > endDate) {
			setEndDate("");
		}
	};

	const handleEndDateChange = (e) => {
		setEndDate(e.target.value);
	};

	const handleButtonClick = () => {
		if (!startDate || !endDate) {
			setFormError("Bitte füllen Sie beide Datumsfelder aus.");
		} else {
			setFormError("");
			// Doaa, The Booking logic should be here!
			alert(`Buchung vom ${startDate} bis ${endDate} für ${selectedHouse.name} bestätigt.`);
		}
	};

	React.useEffect(() => {
		if (startDate && endDate) {
			calculateEstimatedFund();
		} else {
			setEstimatedFund(null);
		}
	}, [startDate, endDate]);

	const calculateEstimatedFund = () => {
		const checkIn = new Date(startDate);
		const checkOut = new Date(endDate);
		const timeDifference = checkOut.getTime() - checkIn.getTime();
		const totalNights = Math.ceil(timeDifference / (1000 * 3600 * 24));
		const pricePerNight = selectedHouse ? selectedHouse.price : 0;

		const totalFund = totalNights * pricePerNight;
		setEstimatedFund(totalFund);
	};

	let totalCostMessage = "Es fehlen noch Informationen, um die voraussichtlichen Kosten anzuzeigen.";
	if (startDate && endDate && estimatedFund !== null) {
		totalCostMessage = `Die voraussichtlichen Kosten betragen €${estimatedFund.toFixed(2)}.`;
	}

	return (
		<div className="calendar-booking">
			<h3 style={{ fontSize: "18px" }}>Preis pro Nacht: €{selectedHouse.price}</h3>
			<h3 style={{ fontSize: "18px" }}>{totalCostMessage}</h3>
			<h3 style={{ fontSize: "18px" }}>Buchungszeitraum wählen</h3>
			<div className="date-picker">
				<label htmlFor="start-date">Startdatum:</label>
				<input
					type="date"
					id="start-date"
					value={startDate}
					onChange={handleStartDateChange}
					min={selectedHouse.dateAvaliable}
					max={selectedHouse.endDateAvaliable}
				/>
			</div>
			<div className="date-picker">
				<label htmlFor="end-date">Enddatum:</label>
				<input
					type="date"
					id="end-date"
					value={endDate}
					onChange={handleEndDateChange}
					min={startDate}
					max={selectedHouse.endDateAvaliable}
					disabled={!startDate}
				/>
			</div>
			{formError && <div className="form-error">{formError}</div>}
			<button className="booking-button" onClick={handleButtonClick}>
				Buchen
			</button>
		</div>
	);
};

export default CalendarBooking;
