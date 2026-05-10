import React from 'react';

const ToastNotificationEn = ({ onClose }) => {
return (
	<div className="toast-notification">
	  <div className="toast-message">
		<p>
		  Thank you for your booking.<br />
		  Your booking request has been forwarded to the host for processing.<br />
		  You will receive a notification within a few hours.
		</p>
		<button onClick={onClose}>OK</button>
	  </div>
	</div>
);
};

export default ToastNotificationEn;
