import React, { useState, useEffect } from "react";
import { format } from "date-fns";
import { houses } from "..";
import { HousesEn } from "./HousesEn";

const BookingFormEn = ({ selectedHouse, onClose, startDate, endDate, onNavigate, formData, setFormData, calculateEstimatedFund, language }) => {
	const houseData = language === 'de' ? houses.find(house => house.id === selectedHouse.id) : HousesEn.find(house => house.id === selectedHouse.id);

	const formatDate = (date) => {
		return format(new Date(date), 'dd.MM.yyyy');
	};

	// Update formData with startDate and endDate changes
	React.useEffect(() => {
		if (formData) {
			setFormData(prevData => ({
				...prevData,
				startDate: formatDate(startDate),
				endDate: formatDate(endDate)
			}));
		}
	}, [startDate, endDate]);

	React.useEffect(() => {
		if (!formData) {
			setFormData({
				firstName: "",
				lastName: "",
				email: "",
				emailConfirm: "",
				phone: "",
				address: "",
				message: "",
				houseTitle: selectedHouse.houseTitle,
				startDate: formatDate(startDate),
				endDate: formatDate(endDate),
				guest: selectedHouse.guest,
			});
		}
	}, [setFormData, selectedHouse.houseTitle, startDate, endDate, selectedHouse.guest]);

	const [emailError, setEmailError] = React.useState("");

	const handleChange = (e) => {
		const { name, value } = e.target;
		setFormData({ ...formData, [name]: value });

		if (name === "email" || name === "emailConfirm") {
			if (name === "emailConfirm" && value !== formData.email) {
				setEmailError("The email addresses do not match.");
			} else if (name === "emailConfirm" && value === formData.email) {
				setEmailError("");
			}
		}
	};

	const handleSubmit = (e) => {
		e.preventDefault();
		if (formData.email !== formData.emailConfirm) {
			setEmailError("The email addresses do not match.");
			return;
		}

		const numberOfNights = Math.ceil((new Date(endDate) - new Date(startDate)) / (1000 * 3600 * 24));
		const costs = numberOfNights * selectedHouse.price + 15 + 5;
		setFormData({
			...formData,
			numberOfNights,
			costs
		});

		console.log("Booking Form Data:", formData);
		onNavigate("bookingSummary");
	};

	const handleBack = () => {
		onNavigate("calendar");
	};

	if (!formData) return null;

	return (
		<div className="booking-formPage">
			<div className="booking-form">
				<button className="close-button" onClick={onClose}>X</button>
				<h2 style={{ textAlign: "center" }}>Booking Form</h2>
				<h3>{houseData ? houseData.houseTitle : ''}</h3>
				<form onSubmit={handleSubmit}>
					<p>
						Please fill out this form to complete your booking as a guest,
						<br />
						or simply <button className="form-signIn" type="button" onClick={() => onNavigate("login")}>Sign In</button>
					</p>
					<div className="form-group">
						<label>Check-in:</label>
						<p>{formData.startDate}</p>
					</div>
					<div className="form-group">
						<label>Check-out:</label>
						<p>{formData.endDate}</p>
					</div>

					<p>Maximum number of guests: <b>{selectedHouse.guest}</b></p>

					<p style={{ backgroundColor: "white", fontSize: "17px", padding: "15px" }}><i>Please note: <br />
						The number of guests is set by the host.<br />
						If you would like to change this, please indicate it in the Special Requests field</i></p>

					<div className="form-group">
						<label htmlFor="firstName">First Name</label>
						<input type="text" id="firstName" name="firstName" value={formData.firstName} onChange={handleChange} required />
					</div>
					<div className="form-group">
						<label htmlFor="lastName">Last Name</label>
						<input type="text" id="lastName" name="lastName" value={formData.lastName} onChange={handleChange} required />
					</div>
					<div className="form-group">
						<label htmlFor="email">Email</label>
						<input type="email" id="email" name="email" value={formData.email} onChange={handleChange} required />
					</div>
					<div className="form-group">
						<label htmlFor="emailConfirm">Confirm Email</label>
						<input type="email" id="emailConfirm" name="emailConfirm" value={formData.emailConfirm} onChange={handleChange} />
						{emailError && <p className="form-error">{emailError}</p>}
					</div>
					<div className="form-group">
						<label htmlFor="address">Address:</label>
						<textarea
						id="address"
						name="address"
						value={formData.address}
						onChange={handleChange}
						placeholder="Please enter your full address including street, zip code and house number.(optional)"
						></textarea>
					</div>
					<div className="form-group">
						<label htmlFor="phone">Phone</label>
						<input type="tel" id="phone" name="phone" value={formData.phone} onChange={handleChange} required />
					</div>
					<div className="form-group">
						<label htmlFor="message">Do you have any special requests?</label>
						<textarea id="message" name="message" value={formData.message} onChange={handleChange}></textarea>
					</div>
					<button className="next" type="submit">Next</button>
					<button className="back" type="button" onClick={handleBack}>Back</button>
				</form>
			</div>
		</div>
	);
};

export default BookingFormEn;
