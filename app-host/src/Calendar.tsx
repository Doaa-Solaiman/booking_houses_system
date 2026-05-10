import React from "react";
import Style from "./utils/Style";

interface CalendarProps {
	bookingData: BookingData[];
	onSelectDateBookings?: (bookings: BookingData[]) => void;
}

export function Calendar({ bookingData, onSelectDateBookings }: CalendarProps) {
	//const bookingData = props.bookingData;
	const [currentDate, setCurrentDate] = React.useState(new Date());

	const isDateBooked = (date) => {
		if (!bookingData) return false;
		return bookingData.some((booking) => {
			const checkIn = new Date(booking.startDate);
			const checkOut = new Date(booking.endDate);
			return date >= checkIn && date < checkOut;
		});
	};
	const handleDateClick = (date: Date) => {
		const bookingsOnDate = bookingData.filter((booking) => {
			const checkIn = new Date(booking.checkInDate); // i changed from startDate to checkInDate
			const checkOut = new Date(booking.checkOutDate); // i changed from endDate to checkOutDate
			return date >= checkIn && date < checkOut;
		});

		if (bookingsOnDate.length > 0) {
			console.log("Bookings on this date:", bookingsOnDate);
			onSelectDateBookings?.(bookingsOnDate);
		}
	};
	const goToPreviousMonth = () => {
		const newDate = new Date(currentDate);
		newDate.setMonth(currentDate.getMonth() - 1);
		setCurrentDate(newDate);
	};
	const goToNextMonth = () => {
		const newDate = new Date(currentDate);
		newDate.setMonth(currentDate.getMonth() + 1);
		setCurrentDate(newDate);
	};
	const generateCalendar = () => {
		const calendar = [];
		const date = new Date(currentDate);
		date.setDate(1);
		const startDay = date.getDay(); // get the day of the week the month starts on

		for (let i = 0; i < startDay; i++) {
			calendar.push(null); // add leading empty dates
		}
		while (date.getMonth() == currentDate.getMonth()) {
			calendar.push(new Date(date)); // add dates for the month
			date.setDate(date.getDate() + 1);
		}
		while (calendar.length % 7 != 0) {
			calendar.push(null); // fill the empty dates to fill the last week
		}

		return calendar;
	};

	return <div>
		<Style>{` // preserve styling from index.scss
			& .calendar {
				display: grid;
				grid-template-columns: repeat(7, 1fr);
				grid-auto-rows: 1fr;
				gap: 5px;
				margin: 20px;
			}
			& .calendar-cell {
				padding: 10px;
				border: 1px solid #ccc;
				text-align: center;
				background-color: #f9f9f9;
				cursor: pointer;
			}
			& .calendar-cell.booked {
				background-color: #ffcccc;
				font-weight: bold;
			}
			& .calendar-header { display: contents; }
			& .calendar-header > div {
				justify-content: space-between;
				text-align: center;
				font-weight: bold;
				background-color: #e0e0e0;
				padding: 10px;
				color: #333;
			}
			& .calendar-header .day {
				padding: 10px;
			}
		`}</Style>
		{/* This is for month navigation */}
		<div className="flexh centerh mb-sm">
			<button onClick={goToPreviousMonth}>Zurück</button>
			<span>
				{currentDate.toLocaleString("default", {
					month: "long",
					year: "numeric",
				})}
			</span>
			<button onClick={goToNextMonth}>Weiter</button>
		</div>
		<div className="calendar">
			<div className="calendar-header">
				<div className="day">Montag</div>
				<div className="day">Dienstag</div>
				<div className="day">Mittwoch</div>
				<div className="day">Donnerstag</div>
				<div className="day">Freitag</div>
				<div className="day">Samstag</div>
				<div className="day">Sonntag</div>
			</div>
			{/* This is for rendering the dates in the calendar */}
			{generateCalendar().map((date, index) => (
				<div
					key={index}
					className={`calendar-cell ${isDateBooked(date) ? "booked" : ""}`}
					onClick={() => date && handleDateClick(date)}
				>
					{date ? date.getDate() : ""}
				</div>
			))}
		</div>
	</div>
}
