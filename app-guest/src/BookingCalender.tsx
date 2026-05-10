
{/*

Function of creating a calender to view for the user the The period of time
during which the selected home is reserved.
	
	
- Firstly, we need to build a state for the dates.

	function BookingCalender ({selectedHouse}){
		const [bookedDates, setBookedDates] = useState(new Set()); //This is for creating a new Set object for storing unique values of any type.
		const [currentDate, setCurrentDate] = useState(new Set());
	}
-2: getting the stored booking info from the local storage, using this function:
	const bookings = JSON.parse(localStorage.getItem('bookingDetails') || '[]');
	
	React.useEffect(()=> {
		const bookings = JSON.parse(localStorage.getItem('bookingDetails') || '[]');
		const houseBookings = bookings.filter(booking => booking.houseTitle === selectedHouse.houseTitle);
		const datesSet = new Set ();
		
	})
	
-3: After filtering the dates of each bookings, using Booking.filter and applying forEach

	houseBookings.forEach(booking => {
			let start = new Date(booking.checkInDate);
			const end = new Date(booking.checkOutDate);
			while (start <= end) {
				datesSet.add(start.toISOString().split('T')[0]);
				start = new Date(start.setDate(start.getDate() + 1));
			}
		});
		
		setBookedDates(datesSet);
		}, [selectedHouse, currentDate]);


*\}

export default BookingCalendar;
