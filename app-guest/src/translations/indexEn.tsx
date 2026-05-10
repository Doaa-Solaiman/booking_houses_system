import React, { useMemo } from "react";
import ReactDOM from "react-dom";
import "../../app-shared/types";
import "../../app-shared/shared.scss";
import "./index.scss";
import BookingView from "./Booking viewer";
import NavBar from "./NavBar";
import NavBarEn from "./NavBarEn";
import Menu from "./Menu";
import MenuEn from "./MenuEn";
import { überUns } from "./überUns";
import ContactUs from "./KontaktUs";
//import SearchForm from "SearchForm";
import SignInForm from "./SignIn";
import RegistrationForm from "./RegistrationForm";
import BookingForm from "./BookingForm";
import BookingSummary from "./BookingSummary"

let id = () => Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);

function AppGuestEn() {
	const [selectedHouse, setSelectedHouse] = React.useState(null);
	const [bookingDates, setBookingDates] = React.useState({ start: null, end: null });
	const [page, setPage] = React.useState("home");
	const [searchCriteria, setSearchCriteria] = React.useState({
		checkInDate: '',
		checkOutDate: '',
		numberOfGuests: 1
	});
	
	const [housesToShow, setHousesToShow] = React.useState(9);
	const [language, setLanguage] = React.useState("de");

	// The array of list houses.
	const houses = [
		{
			id: 1,
			img: ['1.jpg'],
			houseTitle: "Gästehouse Fereinhouse",
			location: "Rostocker straße, wismar 00000",
			description: "174M, 2 beds, 2 Bathrooms",
			guest: 2,
			price: 70,
			dateAvaliable: "20/05/2024",
			endDateAvaliable: "30/09/2024"
		},
		{
			id: 2,
			img: ['2.jpg'],
			houseTitle: "Am Meer house",
			location: "Test straße, Berlin 012365",
			description: "160M, 3 beds, 3 Bathrooms ",
			guest: 4,
			price: 50,
			dateAvaliable: "30/05/2024",
			endDateAvaliable: "30/11/2024"
		},
		{
			id: 3,
			img: ['3.jpg'],
			houseTitle: "My small house",
			location: "Rostocker straße, wismar 00000",
			description: "60M, 1 bed, 2 Bathrooms",
			guest: 2,
			price: 45,
			dateAvaliable: "07/07/2024",
			endDateAvaliable: "20/12/2024"
		},
		{
			id: 4,
			img: ['4.jpg'],
			houseTitle: "garden house",
			location: "Rostocker straße, wismar 00000",
			description: "200M, 5 beds, 2 Bathrooms, garden",
			guest: 5,
			price: 80,
			dateAvaliable: "07/07/2024",
			endDateAvaliable: "01/12/2024"
		},
		{
			id: 5,
			img: ['5.jpg'],
			houseTitle: "Land house",
			location: "Rostocker straße, wismar 00000",
			description: "40M, 2 beds, 2 bathrooms, garden",
			guest: 2,
			price: 20,
			dateAvaliable: "10/07/2024",
			endDateAvaliable: "30/09/2024"
		},
		{
			id: 6,
			img: ['6.jpg'],
			houseTitle: "Marina summer house",
			location: "Rostocker straße, wismar 00000",
			description: "174M, 7 beds, 2 Swimmingpools, garden",
			guest: 8,
			price: 130,
			dateAvaliable: "10/07/2024",
			endDateAvaliable: "15/11/2024"
		},
		{
			id: 7,
			img: ['7.jpg'],
			houseTitle: "Special house",
			location: "Rostocker straße, wismar 00000",
			description: "250M, 6 beds, 2 swimmingpools, garden",
			guest: 6,
			price: 155,
			dateAvaliable: "01/08/2024",
			endDateAvaliable: "30/01/2025"
		},
		
		{
			id: 8,
			img: ['8.jpg'],
			houseTitle: "Villa",
			location: "Rostocker straße, wismar 00000",
			description: "250M, 10 beds, 2 swimmingpool, garden",
			guest: 10,
			price: 200,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		{
			id: 9,
			img: ['9.webp'],
			houseTitle: "wooden house",
			location: "Rostocker straße, wismar 00000",
			description: "150M, 3 beds, No swimmingpool, garden",
			guest: 4,
			price: 130,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 10,
			img: ['10.jpg'],
			houseTitle: "on the beach house",
			location: "Berliner straße, Strand 00000",
			description: "290M, 5 beds, 2 swimmingpool, garden",
			guest: 5,
			price: 130,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 11,
			img: ['elevenmain.jpg','eleven.jpg','eleventwo.jfif','eleventhree.webp', 'elevenfour.webp'],
			houseTitle: "garden house",
			location: "garden straße, Berlin 00000",
			description: "250M, 2 beds, 2 swimmingpools, garden ",
			guest: 3,
			price: 110,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		{
			id: 12,
			img: ['12.jpg'],
			houseTitle: "small summerhouse",
			location: "Rostocker straße, wismar 00000",
			description: "80M, 1 bed, No swimmingpool, garden",
			guest: 2,
			price: 70,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 13,
			img: ['13.jpg'],
			houseTitle: "Random summerhouse",
			location: "Rostocker straße, wismar 00000",
			description: "250M, 1 beds, 2 swimmingpool, garden ",
			guest: 2,
			price: 130,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		{
			id: 14,
			img: ['14.jpg'],
			houseTitle: "Normales Fereinhouse",
			location: "Rostocker straße, wismar 00000",
			description: "190M, 2 beds, no swimmingpool, garten with flowers ",
			guest: 4,
			price: 70,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 15,
			img: ['15.jpg'],
			houseTitle: "Dreieckiges Sommerhouse",
			location: "Rostocker straße, wismar 00000",
			description: "90, 3 beds, keine swimmingpool, trees",
			guest: 2,
			price: 80,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 16,
			img: ['16.jpg'],
			houseTitle: "Summer house with swimming pool",
			location: "Test straße, Lübeck",
			description: "270M, 4 beds, 1 swimmingpool, garden",
			guest: 4,
			price: 130,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 17,
			img: ['17.jpg'],
			houseTitle: "Big summer house",
			location: "Baum straße, Schwerin 00000",
			description: "250M, 8 beds, 2 swimmingpool, garden ",
			guest: 10,
			price: 130,
			dateAvaliable: "15/08/2024",
			endDateAvaliable: "2/12/2024"
		},
		
		
		{
			id: 18,
			img: ['18.jpg'],
			houseTitle: "Modern house",
			location: "Rostocker straße, Rostock",
			description: "100M, 3 beds, 2 swimmingpool, garden",
			guest: 4,
			price: 150,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 19,
			img: ['19.jpg'],
			houseTitle: "Hotel house",
			location: "Hotel straße, Lübeck",
			description: "40M, 1 beds, 2 swimmingpool, garden",
			guest: 2,
			price: 90,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 20,
			img: ['20.jpg'],
			houseTitle: "colored house",
			location: "Schöne straße, Berlin",
			description: "250M, 5 beds, 2 swimmingpool, parking place",
			guest: 5,
			price: 100,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 21,
			img: ['21.webp'],
			houseTitle: "farm house",
			location: "Farm straße, Farm",
			description: "250M, 7 beds, no swimmingpool, garden ",
			guest: 7,
			price: 100,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 22,
			img: ['22.jpg'],
			houseTitle: "castle summer house",
			location: "Casle straße, Kassel",
			description: "95M, 2 beds, 5 swimmingpool, garden",
			guest: 2,
			price: 95,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 23,
			img: ['23.jpg'],
			houseTitle: "Hotel house",
			location: "Rostocker straße, Rostock",
			description: "35M, 2 beds, 2 swimmingpool, garden ",
			guest: 3,
			price: 150,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 24,
			img: ['24.jpg'],
			houseTitle: "Hotel house",
			location: "Cleaner straße, Lübeck",
			description: "35M, 1 bed, No swimmingpool, garden ",
			guest: 4,
			price: 80,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 25,
			img: ['25.jpg'],
			houseTitle: "Big house",
			location: "Große straße, Mannheim",
			description: "115M, 9 beds, No swimmingpool, garden",
			guest: 12,
			price: 150,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 26,
			img: ['26.jpg'],
			houseTitle: "very luxury house",
			location: "Cleaner straße, München",
			description: "124M, 8 beds, No swimmingpool, garden ",
			guest: 15,
			price: 150,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
	];


	const filteredHouses = React.useMemo(() => {
	return houses.filter(house => {
	const houseStartDate = new Date(house.dateAvaliable);
	const houseEndDate = new Date(house.endDateAvaliable);
	const checkInDate = new Date(searchCriteria.checkInDate);
	const checkOutDate = new Date(searchCriteria.checkOutDate);

	const isAvailable = (
		checkInDate >= houseStartDate && checkOutDate <= houseEndDate
	);

	return isAvailable;
	});
}, [searchCriteria, houses]);

const handleBookNow = (house) => {
	setSelectedHouse(house);
	setPage("booking");
};

const handleSearch = (criteria) => {
	setSearchCriteria(criteria);
};

const handleNavigate = (page) => {
	setPage(page);
};

const handleRegisterSubmit = (newUser) => {
	console.log('User registered: ', newUser);
	setPage('home');
};

const handleLoadMore = () => {
	setHousesToShow(prev => prev + 9);
};


const toggleLanguage = (lang) => {
		setLanguage(lang);
	};

return (
		<div>
			<div>
				<button onClick={() => toggleLanguage('en')}>EN</button>
				<button onClick={() => toggleLanguage('de')}>DE</button>
			</div>
			{language === 'de' ? (
				<NavBar onNavigate={handleNavigate} currentPage={page} />
			) : (
				<NavBarEn onNavigate={handleNavigate} currentPage={page} />
			)}
			{page === 'home' && (
				<>
					{language === 'de' ? (
						<Menu houses={filteredHouses.length > 0 ? filteredHouses.slice(0, housesToShow) : houses.slice(0, housesToShow)} onBookNow={handleBookNow} />
					) : (
						<MenuEn houses={filteredHouses.length > 0 ? filteredHouses.slice(0, housesToShow) : houses.slice(0, housesToShow)} onBookNow={handleBookNow} />
					)}
					{housesToShow < houses.length && (
						<button onClick={handleLoadMore} className="load-more-button">Load more...</button>
					)}
				</>
			)}
			{page === 'aboutUs' && (language === 'de' ? <überUns /> : <AboutUsEn />)}
			{page === 'booking' && selectedHouse && (
				<BookingView selectedHouse={selectedHouse} setPage={setPage} setBookingDates={setBookingDates} />
			)}
			{page === 'bookingForm' && selectedHouse && (
				language === 'de' ? (
					<BookingForm selectedHouse={selectedHouse} onClose={() => setPage('home')} startDate={bookingDates.start} endDate={bookingDates.end} />
				) : (
					<BookingFormEn selectedHouse={selectedHouse} onClose={() => setPage('home')} startDate={bookingDates.start} endDate={bookingDates.end} />
				)
			)}
			{page === 'contact' && (language === 'de' ? <ContactUs setPage={setPage} /> : <ContactUsEn setPage={setPage} />)}
			{page === 'login' && (language === 'de' ? <SignInForm onNavigate={handleNavigate} /> : <SignInFormEn onNavigate={handleNavigate} />)}
			{page === 'register' && (language === 'de' ? <RegistrationForm setPage={setPage} onRegisterSubmit={handleRegisterSubmit} /> : <RegistrationFormEn setPage={setPage} onRegisterSubmit={handleRegisterSubmit} />)}
			{page === 'bookingSummary' && selectedHouse && (
				language === 'de' ? (
					<BookingSummary bookingDetails={selectedHouse} onBack={() => setPage('bookingForm')} onNavigate={handleNavigate} />
				) : (
					<BookingSummaryEn bookingDetails={selectedHouse} onBack={() => setPage('bookingForm')} onNavigate={handleNavigate} />
				)
			)}
		</div>
	);
}
window.onload = () => ReactDOM.render(<AppGuest />, document.getElementById('app'));
