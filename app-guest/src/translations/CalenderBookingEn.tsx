import React, { useState, useEffect } from "react";
import { format } from 'date-fns';

const CalendarBookingEn = ({ selectedHouse, onDateChange, onBookingClick, onShowPaymentOverview, bookingDates }) => {
	const [startDate, setStartDate] = React.useState("");
	const [endDate, setEndDate] = React.useState("");
	const [formError, setFormError] = React.useState("");
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
			setFormError("Please fill in both date fields.");
		} else if (new Date(startDate) < new Date(today) || new Date(endDate) < new Date(today)) {
			setFormError("Bookings in the past are not allowed.");
		} else if (startDate === endDate) {
				setFormError("The check-in and check-out dates should not be on the same day!");
		} else if (new Date(startDate) >= new Date(endDate)) {
			setFormError("The end date must be after the start date.");
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
			<h3 style={{ fontSize: "18px" }}>Price per night: €{selectedHouse.price}</h3>
			<p style={{ fontSize: "17px", textAlign: "center" }}>
				{!startDate || !endDate ? (
					"We still need some information to show the estimated costs."
				) : isValidDates ? (
					<span>
						The estimated cost
						<br />
						from {formatDate(startDate)} to {formatDate(endDate)} is €
						{((new Date(endDate) - new Date(startDate)) / (1000 * 3600 * 24)) * selectedHouse.price + 15 + 5}.
					</span>
				) : hasEnteredValidDates ? (
					"Please enter valid dates again."
				) : (
					"Please insert valid Dates to view the estimated fund."
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
					onClick={onShowPaymentOverview}
				>
					Show price details
				</button>
			)}
			<h3 style={{ fontSize: "18px" }}>Select booking period</h3>
			<div className="date-picker">
				<label htmlFor="start-date">Check-in:</label>
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
				<label htmlFor="end-date">Check-out:</label>
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
				Book
			</button>
		</div>
	);
};

export default CalendarBookingEn;
