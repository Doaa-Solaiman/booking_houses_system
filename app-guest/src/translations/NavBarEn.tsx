import React from 'react';

function NavBarEn({ onNavigate, currentPage }) {
	const logoUrl = 'https://i.postimg.cc/Rhgr83yd/theLogo.png';

	return (
		<nav className="navbar">
			<div className="navbar-top">
				<div className="navbar-title-container">
					<span className="navbar-title">Vacation Booking</span>
					<img src={logoUrl} alt="Logo" className="navbar-logo" />
				</div>

				<div className="navbar-buttons">
					<button onClick={() => onNavigate("login")}>Sign In</button>
					<button onClick={() => onNavigate("register")}>Register</button>
				</div>
			</div>

			<div className="navbar-list-container">
				<ul className="navbar-list">
					<li className="navbar-item" onClick={() => onNavigate("home")}>Homepage</li>
					<li className="navbar-item" onClick={() => onNavigate("aboutUs")}>About Us</li>
					<li className="navbar-item" onClick={() => onNavigate("contact")}>Contact Us</li>
				</ul>
			</div>

			<div className="navbar-header">
				{currentPage === 'home' && (
					<h2 className="navbar-title2">Check out all the summer houses!</h2>
				)}
			</div>
		</nav>
	);
}

export default NavBarEn;

