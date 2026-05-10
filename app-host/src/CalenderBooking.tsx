import React, { useState, useEffect } from "react";
import { format } from 'date-fns';

const CalendarBooking = () => {
	const [startDate, setStartDate] = React.useState("");
	const [endDate, setEndDate] = React.useState("");
	const [hasEnteredValidDates, setHasEnteredValidDates] = React.useState(false);

	const today = new Date().toISOString().split("T")[0];

	React.useEffect(() => {
		if (bookingDates.start) {
			setStartDate(bookingDates.start);
		}
		if (bookingDates.end) {
			setEndDate(bookingDates.end);
		}
	}, [bookingDates]);

	React.useEffect(() => {
		if (startDate && endDate && new Date(startDate) >= new Date(today) && new Date(endDate) > new Date(startDate)) {
			setHasEnteredValidDates(true);
			setFormError("");
		} else {
			setHasEnteredValidDates(false);
		}
	}, [startDate, endDate, today]);

	const handleStartDateChange = (e) => {
		const newStartDate = e.target.value;
		setStartDate(newStartDate);
		onDateChange(newStartDate, endDate);

		
		if (endDate && new Date(newStartDate) > new Date(endDate)) {
			setEndDate("");
		}

		if (new Date(newStartDate) >= new Date(today) && (!endDate || new Date(endDate) > new Date(newStartDate))) {
			setFormError("");
		}
	};

	const handleEndDateChange = (e) => {
		const newEndDate = e.target.value;
		setEndDate(newEndDate);
		onDateChange(startDate, newEndDate);

		if (new Date(newEndDate) > new Date(today) && new Date(startDate) < new Date(newEndDate)) {
			setFormError("");
		}
	};

	const handleButtonClick = () => {
		if (!startDate || !endDate) {
			setFormError("Bitte füllen Sie beide Datumsfelder aus.");
		} else if (new Date(startDate) < new Date(today) || new Date(endDate) < new Date(today)) {
			setFormError("Buchungen in der Vergangenheit sind nicht erlaubt.");
		} else if (startDate === endDate) {
				setFormError("Das Ein und Ausschecken Datum sollten nicht am selben Tag sein!");
		} else if (new Date(startDate) >= new Date(endDate)) {
			setFormError("Das Enddatum muss nach dem Startdatum liegen.");

		} else {
			setFormError("");
			onBookingClick({ startDate, endDate });
		}
	};

	const formatDate = (date) => {
		return format(new Date(date), 'dd.MM.yyyy');
	};

	const isValidDates = startDate && endDate && new Date(startDate) >= new Date(today) && new Date(endDate) > new Date(startDate);

	return (
		<div className="calendar-booking">
			
			<p style={{ fontSize: "17px", textAlign: "center" }}>
				{!startDate || !endDate ? (
					"Es fehlen noch Informationen, um die voraussichtlichen Kosten anzuzeigen."
				) : isValidDates ? (
					<span>
						Die voraussichtlichen Kosten
						<br />
						vom {formatDate(startDate)} bis {formatDate(endDate)} betragen €
						{((new Date(endDate) - new Date(startDate)) / (1000 * 3600 * 24)) * selectedHouse.price + 15 + 5}.
					</span>
				) : hasEnteredValidDates ? (
					"Bitte geben Sie erneut gültige Daten ein."
				) : (
					"Bitte geben Sie gültige Daten ein, um den geschätzten Betrag anzuzeigen."
				)}
			</p>
			{bookingDates.start && bookingDates.end && isValidDates && (
				<button
					style={{
						color: "black",
						fontSize: "18px",
						fontFamily: "Georgia, 'Times New Roman', Times, serif",
						backgroundColor: "transparent",
						border: "none",
						textDecoration: "underline",
						textAlign: "left",
						display: "block",
					}}
					
				>
					Preisdetails zeigen
				</button>
			)}
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

