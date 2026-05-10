import React, { useState } from 'react';

function ContactUsEn({ setPage }) {
const [name, setName] = useState('');
const [email, setEmail] = useState('');
const [message, setMessage] = useState('');
const [showNotification, setShowNotification] = useState(false);

const handleSubmit = (event) => {
	event.preventDefault();

	const contactData = {
	name,
	email,
	message,
	dateSent: new Date().toLocaleString(),
	};

	let storedMessages = localStorage.getItem('contactUsData');
	let messagesArray = storedMessages ? JSON.parse(storedMessages) : [];

	if (!Array.isArray(messagesArray)) {
	messagesArray = [];
	}

	messagesArray.push(contactData);

	localStorage.setItem('contactUsData', JSON.stringify(messagesArray));

	setShowNotification(true);
	setName('');
	setEmail('');
	setMessage('');
};

const handleCloseNotification = () => {
	setShowNotification(false);
};

const handleBackToHome = () => {
	setPage('home');
};

return (
	<div className="contact-us-container">
	<div className="contact-us-form">
		<h1>Holiday Home Booking Contact</h1>
		<h3>We are here to assist you with any questions about holiday homes, your booking, and more.</h3>
		<p>
		<b>Available by phone:</b>
		</p>
		<p>
		<i>Monday – Thursday 08:00 – 17:00</i>
		</p>
		<p>
		<i>Friday: 08:00 - 15:45</i>
		</p>
		<pre>
		Poeler Straße 85a • D-23970 Wismar <br />
		Tel: +49-(0)-3841 - 46 00 13 <br />
		Fax: +49-(0)-3841 - 46 00 14 <br />
		info@hcn-group.de www.hcn-group.de
		</pre>

		<h2>Or, contact us using this form!</h2>
		{!showNotification && (
		<form onSubmit={handleSubmit}>
			<div className="form-group">
			<label htmlFor="name">Name:</label>
			<input type="text" id="name" value={name} onChange={(e) => setName(e.target.value)} required />
			</div>

			<div className="form-group">
			<label htmlFor="email">Email:</label>
			<input type="email" id="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
			</div>

			<div className="form-group">
			<label htmlFor="message">Message:</label>
			<textarea id="message" value={message} onChange={(e) => setMessage(e.target.value)} required />
			</div>

			<button className="back" type="button" onClick={handleBackToHome}>Back to Home</button>
			<button className="submit" type="submit">Submit</button>
		</form>
		)}

		{showNotification && (
		<div className="notification">
			<p>Thank you for your message! We will get back to you soon.</p>
			<button onClick={handleCloseNotification}>OK</button>
		</div>
		)}
	</div>
	</div>
);
}

export default ContactUsEn;
