import React, { useState } from 'react';

function SignInForm({ onNavigate }) {
const [email, setEmail] = React.useState('');
const [password, setPassword] = React.useState('');
const [showNotification, setShowNotification] = React.useState(false);

const handleSubmit = (event) => {
	event.preventDefault();

	// This is the function is for the sign in submitting

	setShowNotification(true);
	setEmail('');
	setPassword('');
};

const handleCloseNotification = () => {
	setShowNotification(false);
};

return (
	<div
	className="signin-form-container"
	style={{
		height: '100vh',
		width: '100vw',
		backgroundImage: `url("https://i.etsystatic.com/24607392/r/il/439442/3458885089/il_fullxfull.3458885089_7lg1.jpg")`,
		backgroundRepeat: 'no-repeat',
		backgroundSize: 'cover',
		backgroundPosition: 'center',
		display: 'flex',
		flexDirection: 'column',
		justifyContent: 'center',
		alignItems: 'center',
	}}
	>
	<div className="signin-container">
		<h2>Anmelden</h2>
		{!showNotification && (
		<form onSubmit={handleSubmit}>
			<div className="form-group">
			<label htmlFor="email">Email:</label>
			<input
				type="email"
				id="email"
				value={email}
				onChange={(e) => setEmail(e.target.value)}
				placeholder="E-Mail-Adresse"
				required
			/>
			</div>

			<div className="form-group">
			<label htmlFor="password">Password:</label>
			<input
				type="password"
				id="password"
				value={password}
				onChange={(e) => setPassword(e.target.value)}
				placeholder="Password"
				required
			/>
			</div>

			<button type="submit">Anmelden</button>
		</form>
		)}
		
		
		{showNotification && (
		<div className="notification">
			<p>Erfolgreich angemeldet!</p>
			<p>Sie werden innerhalb weniger Sekunden zur Homepage weitergeleitet</p>
			<button onClick={handleCloseNotification}>OK</button>
		</div>
		)}
	</div>
	</div>
);
}

export default SignInForm;
