import React from 'react';
//import { format } from 'date-fns';

const PaymentOverviewMenuEn = ({ isOpen, onClose, pricePerNight, totalNights, cleaning, extraCosts, totalFund, startDate, endDate }) => {
/*const formatDate = (date) => {
	return format(new Date(date), 'dd.MM.yyyy');
};*/
	
return (
	<>
	<div className={`overlay ${isOpen ? 'open' : ''}`} onClick={onClose}></div>
	<div className={`payment-overview ${isOpen ? 'open' : ''}`}>
		<div className="payment-overview-content">
		<button className="close-btn" onClick={onClose}>×</button>
		<h1>Payment Overview</h1>
		{/*{startDate && endDate && (
		<p>The estimated costs from: {formatDate(startDate)} to {formatDate(endDate)}<br/>
		amount to {totalFund}€</p>
		)}*/}
		<br/>
		<h2 style={{color:"gray"}}>Fixed Costs</h2>
		<p>Rental price per night: {pricePerNight} €</p>
		<p>Total nights: {totalNights}</p>
		<p>{totalNights} x {pricePerNight} €</p>
		
		<p>Cleaning: {cleaning} €</p>
		<p>Additional Costs: {extraCosts} €</p>
		<p style= {{ opacity: "0.5"}}>ـــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــ</p>
		<p>Total: {totalFund} €</p>
		</div>
	</div>
	</>
);
};

export default PaymentOverviewMenuEn;
