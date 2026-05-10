import React from 'react';
import CustomCarousel from './CustomCarousel';
import CalendarBooking from './CalenderBooking';
import PaymentOverviewMenu from './PaymentOverviewMenu';
import { houseDetails } from './HauseDetails';
import { FaLocationDot } from "react-icons/fa6";

function BookingView({ selectedHouse, setPage, setBookingDates }) {
const [bookingInfo, setBookingInfo] = React.useState(null);
const [showNotification, setShowNotification] = React.useState(false);
const [bookingDates, setBookingDatesState] = React.useState({ start: null, end: null });
const [isMenuOpen, setIsMenuOpen] = React.useState(false);
const [estimatedFund, setEstimatedFund] = React.useState(null);
const [totalNights, setTotalNights] = React.useState(0);

const handleConfirmClick = () => {
	const storedData = localStorage.getItem('bookingDetails');
	const existingBookings = storedData ? JSON.parse(storedData) : [];
	existingBookings.push(bookingInfo);
	localStorage.setItem('bookingDetails', JSON.stringify(existingBookings));
	setShowNotification(true);
};

const handleCloseNotification = () => {
	setShowNotification(false);
};

const handleDateChange = (start, end) => {
	setBookingDatesState({ start, end });
	setBookingDates({ start, end });
	calculateEstimatedFund(start, end);
};

const handleBookingClick = ({ startDate, endDate }) => {
	setBookingDates({ start: startDate, end: endDate });
	setPage("bookingForm");
};

const calculateEstimatedFund = (start, end) => {
	if (start && end) {
	const checkIn = new Date(start);
	const checkOut = new Date(end);
	const timeDifference = checkOut.getTime() - checkIn.getTime();
	const nights = Math.ceil(timeDifference / (1000 * 3600 * 24));
	const pricePerNight = selectedHouse ? selectedHouse.price : 0;
	const cleaning = 15;
	const extraCosts = 5;
	const total = nights * pricePerNight + cleaning + extraCosts;
	setTotalNights(nights);
	setEstimatedFund(total);
	} else {
	setEstimatedFund(null);
	setTotalNights(0);
	}
};

const houseDetail = houseDetails.find(detail => detail.id === selectedHouse.id);

return (
	<div className="booking-view">
	<h2 className="houseTitle">{selectedHouse.houseTitle}</h2>
	<div className="booking-view-content">
		<div className="slider-container">
		<CustomCarousel images={selectedHouse.img.map(image => `../../die Bilder/${image}`)} />
		</div>
		<div className="details-and-calendar">
		<div className="house-details">
			<h2>{selectedHouse.title}</h2>
			<p style={{ fontSize: "18px" }}><FaLocationDot /><i> {selectedHouse.location}</i></p>
			<p>{selectedHouse.description}</p>
			<br />
			<h3>Hausdetails: </h3>
			<p style={{ lineHeight: "1.8" }}>{houseDetail ? houseDetail.details : "Keine Details verfügbar."}</p>
		</div>
		<div className="booking-calendar">
			<CalendarBooking
			selectedHouse={selectedHouse}
			onDateChange={handleDateChange}
			onBookingClick={handleBookingClick}
			onShowPaymentOverview={() => setIsMenuOpen(true)}
			bookingDates={bookingDates}
			/>
		</div>
		</div>
	</div>

	<PaymentOverviewMenu
		isOpen={isMenuOpen}
		onClose={() => setIsMenuOpen(false)}
		pricePerNight={selectedHouse.price}
		totalNights={totalNights}
		cleaning={15}
		extraCosts={5}
		totalFund={estimatedFund}
		startDate={bookingDates.start}
		endDate={bookingDates.end}
	/>
	</div>
);
}

export default BookingView;
