import React, { useState } from 'react';

function RegistrationFormEn({ setPage, onRegisterSubmit }) {
	const [formData, setFormData] = React.useState({
		name:'',
		firstName:'',
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

	const [formError, setFormError] = React.useState(null);

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
			setFormError('ℹ️ Please enter a username');
			return;
		}

		const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
		if (!emailPattern.test(formData.email)) {
			setFormError('ℹ️ Please enter a valid email address');
			return;
		}

		if (formData.email !== formData.confirmEmail) {
			setFormError('ℹ️ The email addresses do not match');
			return;
		}

		if (formData.password.length < 6) {
			setFormError('ℹ️ The password must be at least 6 characters long');
			return;
		}

		if (formData.password !== formData.confirmPassword) {
			setFormError('ℹ️ The passwords do not match');
			return;
		}

		const newUser = {
			name: formData.name,
			firstName: formData.firstName,
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
			firstName:'',
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
					<h2 className="form-heading">Register</h2>
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
							<label htmlFor="firstName">First Name:</label>
							<input type="text" id="firstName" name="firstName" value={formData.firstName} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="username">Username:</label>
							<input type="text" id="username" name="username" value={formData.username} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="email">Email:</label>
							<input type="email" id="email" name="email" value={formData.email} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="confirmEmail">Confirm Email:</label>
							<input type="email" id="confirmEmail" name="confirmEmail" value={formData.confirmEmail} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="password">Password:</label>
							<input type="password" id="password" name="password" value={formData.password} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="confirmPassword">Confirm Password:</label>
							<input type="password" id="confirmPassword" name="confirmPassword" value={formData.confirmPassword} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="country">Country:</label>
							<input type="text" id="country" name="country" value={formData.country} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="gender">Gender:</label>
							<select id="gender" name="gender" value={formData.gender} onChange={handleChange}>
								<option value="">Please select</option>
								<option value="male">Male</option>
								<option value="female">Female</option>
								<option value="other">Other</option>
							</select>
						</div>
						<div className="form-group">
							<label htmlFor="phone">Phone Number:</label>
							<input type="tel" id="phone" name="phone" value={formData.phone} onChange={handleChange} />
						</div>
						<div className="form-group">
							<label htmlFor="address">Address:</label>
							<textarea id="address" name="address" value={formData.address} onChange={handleChange} placeHolder="Please enter your complete address including street, postal code, and house number."></textarea>
						</div>
						<button className="submit-button" type="submit">Register</button>
					</form>
				</div>
			</div>
		</div>
	);
}

export default RegistrationFormEn;
