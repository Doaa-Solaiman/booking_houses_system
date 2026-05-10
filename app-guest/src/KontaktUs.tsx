import React, { useState } from 'react';

function ContactUs({ setPage }) {
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
	<h1>FewoBuchung Kontakt</h1>
	<h3>Für all Ihre Fragen rund um Ferienhäuser, Ihre Buchung und vieles mehr stehen wir Ihnen gern zur Verfügung.</h3>
	<p>
		<b>Telefonisch erreichbar:</b>
	</p>
	<p>
		<i>Montag – Donnerstag 08:00 – 17:00</i>
	</p>
	<p>
		<i>Fritag: 08:00 - 15:45</i>
	</p>
	<pre>
		Poeler Straße 85a • D-23970 Wismar <br />
		Tel: +49-(0)-3841 - 46 00 13 <br />
		Fax: +49-(0)-3841 - 46 00 14 <br />
		info@hcn-group.de www.hcn-group.de
	</pre>

	<h2>Oder, Kontaktieren Sie uns durch dieses Formular!</h2>
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
			<label htmlFor="message">Nachricht:</label>
			<textarea id="message" value={message} onChange={(e) => setMessage(e.target.value)} required />
		</div>

		<button className="submit" type="submit">Absenden</button>
		<button className="back" type="button" onClick={handleBackToHome}>Zurück zur Hauptseite</button>
		</form>
	)}

	{showNotification && (
		<div className="notification">
		<p>Danke für Ihre Nachricht! Wir werden uns bald bei Ihnen melden.</p>
		<button onClick={handleCloseNotification}>OK</button>
		</div>
	)}
	</div>
	</div>
);
}

export default ContactUs;
