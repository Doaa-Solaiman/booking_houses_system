import React, { useState } from 'react';

function RegistrationForm({ setPage, onRegisterSubmit }) {
	const [formData, setFormData] = React.useState({
		name:'',
		vorName:'',
		username: '',
		email: '',
		confirmEmail: '',
		password: '',
		confirmPassword: '',
		country: '',
		gender: '',
		phone: '',
		address: '',
	});

	const [formError, setFormError] = useState(null);

	const handleChange = (event) => {
		const { name, value } = event.target;
		setFormData(prevFormData => ({
			...prevFormData,
			[name]: value
		}));
	};

	const handleSubmit = (event) => {
		event.preventDefault();

		if (!formData.username.trim()) {
			setFormError('ℹ️ Bitte geben Sie einen Benutzernamen ein');
			return;
		}

		const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
		if (!emailPattern.test(formData.email)) {
			setFormError('ℹ️ Bitte geben Sie eine gültige E-Mail-Adresse ein');
			return;
		}

		if (formData.email !== formData.confirmEmail) {
			setFormError('ℹ️ Die E-Mail-Adressen stimmen nicht überein');
			return;
		}

		if (formData.password.length < 6) {
			setFormError('ℹ️ Das Passwort muss mindestens 6 Zeichen lang sein');
			return;
		}

		if (formData.password !== formData.confirmPassword) {
			setFormError('ℹ️Die Passwörter stimmen nicht überein');
			return;
		}

		const newUser = {
			name: formData.name,
			vorName:formData.vorName,
			username: formData.username,
			email: formData.email,
			password: formData.password,
			country: formData.country,
			gender: formData.gender,
			phone: formData.phone,
			address: formData.address,
		};

		onRegisterSubmit(newUser);
		setFormError(null);
		setFormData({
			name:'',
			vorName:'',
			username: '',
			email: '',
			confirmEmail: '',
			password: '',
			confirmPassword: '',
			country: '',
			gender: '',
			phone: '',
			address: '',
		});
	};

	const handleCancel = () => {
		setPage('home');
	};

	return (
		<div className="registration-background">
			<div className="registration-form-container">
				<div className="registration-form">
					<button className="cancel-button" onClick={handleCancel}>X</button>
					<h2 className="form-heading">Registrieren</h2>
					<form onSubmit={handleSubmit}>
						{formError && (
							<div className="error-message">
								{formError}
							</div>
						)}
						<div className="form-group">
							<label htmlFor="name">Name:</label>
							<input type="text" id="name" name="name" value={formData.name} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="vorName">Vorname:</label>
							<input type="text" id="vorName" name="vorName" value={formData.vorName} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="username">Benutzername:</label>
							<input type="text" id="username" name="username" value={formData.username} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="email">E-Mail:</label>
							<input type="email" id="email" name="email" value={formData.email} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="confirmEmail">E-Mail bestätigen:</label>
							<input type="email" id="confirmEmail" name="confirmEmail" value={formData.confirmEmail} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="password">Passwort:</label>
							<input type="password" id="password" name="password" value={formData.password} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="confirmPassword">Passwort bestätigen:</label>
							<input type="password" id="confirmPassword" name="confirmPassword" value={formData.confirmPassword} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="country">Land:</label>
							<input type="text" id="country" name="country" value={formData.country} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="gender">Geschlecht:</label>
							<select id="gender" name="gender" value={formData.gender} onChange={handleChange}>
								<option value="">Bitte auswählen</option>
								<option value="male">Männlich</option>
								<option value="female">Weiblich</option>
								<option value="other">Andere</option>
							</select>
						</div>
						<div className="form-group">
							<label htmlFor="phone">Telefonnummer:</label>
							<input type="tel" id="phone" name="phone" value={formData.phone} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="address">Adresse:</label>
							<textarea id="address" name="address" value={formData.address} onChange={handleChange} placeHolder="Bitte geben Sie Ihre vollständige Adresse inklusive Straße, Postleitzahl und Hausnummer an."></textarea>
						</div>
						<button className="submit-button" type="submit">Registrieren</button>
					</form>
				</div>
			</div>
		</div>
	);
}

export default RegistrationForm;
