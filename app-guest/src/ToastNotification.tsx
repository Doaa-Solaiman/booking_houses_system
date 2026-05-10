import React from 'react';

const ToastNotification = ({ onClose }) => {
	return (
		<div className="toast-notification">
			<div className="toast-message">
				<p> Vielen Dank für Ihre Buchung. <br/>
				Ihre Buchungsanfrage wurde an den Gastgeber zur Bearbeitung weitergeleitet.<br/>
				Sie erhalten innerhalb weniger Stunden eine Benachrichtigung.</p>
				<button onClick={onClose}>OK</button>
			</div>
		</div>
	);
};

export default ToastNotification;
