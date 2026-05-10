import React from "react";
import { format } from "date-fns";


const BookingForm = ({ selectedHouse, onClose, startDate, endDate }) => {
const formatDate = (date) => {
	return format(new Date(date), 'dd.MM.yyyy');
};

const [formData, setFormData] = React.useState({
	firstName: "",
	lastName: "",
	email: "",
	phone: "",
	message: "",
	houseTitle: selectedHouse.houseTitle,
	startDate: formatDate(startDate),
	endDate: formatDate(endDate),
});

const handleChange = (e) => {
	const { name, value } = e.target;
	setFormData({ ...formData, [name]: value });
};

const handleSubmit = (e) => {
	e.preventDefault();
	console.log("Booking Form Data:", formData);
	// Here is Handling the form submission, and sending data to the server
};

return (
<div className="booking-formPage">
	<div className="booking-form">
	<button className="close-button" onClick={onClose}>X</button>
	<h2>Buchungsformular</h2>
	<form onSubmit={handleSubmit}>
	
	<p>Bitte füllen Sie dieses Formular aus, um Ihre Buchung als Gast abzuschließen,<br/>
	oder melden Sie sich einfach an</p>
	
	
		<div className="form-group">
		<p style={{fontSize:"18"}}>Sie haben</p>
		<h4>{formData.houseTitle}</h4>
		</div>
		<div className="form-group">
		<label>Checkin:</label>
		<p>{formData.startDate}</p>
		</div>
		<div className="form-group">
		<label>Checkout:</label>
		<p>{formData.endDate}</p>
		</div>
	
		<div className="form-group">
		<label htmlFor="firstName">Vorname</label>
		<input type="text" id="firstName" name="firstName" value={formData.firstName} onChange={handleChange} />
		</div>
		<div className="form-group">
		<label htmlFor="lastName">Nachname</label>
		<input type="text" id="lastName" name="lastName" value={formData.lastName} onChange={handleChange} />
		</div>
		<div className="form-group">
		<label htmlFor="email">E-Mail</label>
		<input type="email" id="email" name="email" value={formData.email} onChange={handleChange} />
		</div>
				<div className="form-group">
		<label htmlFor="email">E-Mail beschtätigen</label>
		<input type="emailConfirm" id="emailConfirm" name="emailConfirm" value={formData.email} onChange={handleChange} />
		</div>
		<div className="form-group">
		<label htmlFor="phone">Telefon</label>
		<input type="tel" id="phone" name="phone" value={formData.phone} onChange={handleChange} />
		</div>
		<div className="form-group">
		<label htmlFor="message">Sie haben besondere Wünsche?</label>
		<textarea id="message" name="message" value={formData.message} onChange={handleChange}></textarea>
		</div>
		
		<button type="submit">Weiter</button>
		<button>Zurück</button>
	</form>
	</div>
	</div>
);
};

export default BookingForm;

