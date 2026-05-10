import React from 'react';


function NavBar({ onNavigate }) {
const logoUrl = 'https://i.postimg.cc/Rhgr83yd/theLogo.png';

return (
	<nav className="navbar">
	<div className="navbar-top">
		<div className="navbar-title-container">
		<span className="navbar-title">Fewo Buchung</span>
		<img src={logoUrl} alt="Logo" className="navbar-logo" />
		</div>

		<div className="navbar-buttons">
		<button onClick={() => onNavigate("login")}>Anmelden</button>
		<button onClick={() => onNavigate("register")}>Registrieren</button>
		</div>
	</div>

	<div className="navbar-list-container">
		<ul className="navbar-list">
		<li className="navbar-item" onClick={() => onNavigate("home")}>Hauptseite</li>
		<li className="navbar-item" onClick={() => onNavigate("aboutUs")}>Über uns</li>
		<li className="navbar-item" onClick={() => onNavigate("contact")}>Kontakt uns</li>
		</ul>
	</div>
	</nav>
);
}

export default NavBar;

