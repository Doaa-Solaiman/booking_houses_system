import React, { useState } from 'react';

function SignInFormEn({ onNavigate }) {
const [email, setEmail] = React.useState('');
const [password, setPassword] = React.useState('');
const [showNotification, setShowNotification] = React.useState(false);

const handleSubmit = (event) => {
	event.preventDefault();


	setShowNotification(true);
	setEmail('');
	setPassword('');
};

const handleCloseNotification = () => {
	setShowNotification(false);
	onNavigate("bookingForm")
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
		<h2>Sign in</h2>
		{!showNotification && (
		<form onSubmit={handleSubmit}>
			<div className="form-group">
			<label htmlFor="email"> E-mail address:</label>
			<input
				type="email"
				id="email"
				value={email}
				onChange={(e) => setEmail(e.target.value)}
				placeholder="Your E-mail address"
				required
			/>
			</div>

			<div className="form-group">
			<label htmlFor="password"> Password</label>
			<input
				type="password"
				id="password"
				value={password}
				onChange={(e) => setPassword(e.target.value)}
				placeholder="Your Password"
				required
			/>
			</div>

			<button type="submit">Sign in</button>
		</form>
		)}
		
		
		{showNotification && (
		<div className="notification">
			<p>Logged in successfully!</p>
			<p>You will be redirected to the homepage within a few seconds</p>
			<button onClick={handleCloseNotification}>Okay</button>
		</div>
		)}
	</div>
	</div>
);
}

export default SignInFormEn;
