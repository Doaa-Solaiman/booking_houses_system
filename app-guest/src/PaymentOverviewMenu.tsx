import React from 'react';
//import { format } from 'date-fns';

const PaymentOverviewMenu = ({ isOpen, onClose, pricePerNight, totalNights, cleaning, extraCosts, totalFund, startDate, endDate }) => {
/*const formatDate = (date) => {
	return format(new Date(date), 'dd.MM.yyyy');
};*/
	
return (
	<>
	<div className={`overlay ${isOpen ? 'open' : ''}`} onClick={onClose}></div>
	<div className={`payment-overview ${isOpen ? 'open' : ''}`}>
		<div className="payment-overview-content">
		<button className="close-btn" onClick={onClose}>×</button>
		<h1>Zahlungsübersicht</h1>
		{/*{startDate && endDate && (
		<p>Die voraussichtlichen Kosten vom: {formatDate(startDate)} to {formatDate(endDate)}<br/>
		betragen {totalFund}€</p>
		)}*/}
		<br/>
		<h2 style={{color:"gray"}}>Fixkosten</h2>
		<p>Mietprice pro Nacht: {pricePerNight} €</p>
		<p> insgesamt Nächte: {totalNights} </p>
		<p> {totalNights} x {pricePerNight} € </p>
		
		<p>Reinigung: {cleaning} €</p>
		<p>Nebenkosten: {extraCosts} €</p>
		<p style= {{ opacity: "0.5"}}>ـــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــــ</p>
		<p>Gesamt: {totalFund} €</p>
		</div>
	</div>
	</>
);
};

export default PaymentOverviewMenu;


