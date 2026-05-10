
import React, { useMemo } from "react";
import ReactDOM from "react-dom";
import "../../app-shared/types";
import "../../app-shared/shared.scss";
import "./index.scss";
import BookingView from "./Booking viewer";
import NavBar from "./NavBar";
import Menu from "./Menu";
import { überUns } from "./überUns";
import ContactUs from "./KontaktUs";
//import SearchForm from "SearchForm";
import SignInForm from "./SignIn";
import RegistrationForm from "./RegistrationForm";
import BookingForm from "./BookingForm";

let id = () => Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);

function AppGuest() {
	const [selectedHouse, setSelectedHouse] = React.useState(null);
	const [bookingDates, setBookingDates] = React.useState({ start: null, end: null });
	const [page, setPage] = React.useState("home");
	const [searchCriteria, setSearchCriteria] = React.useState({
		checkInDate: '',
		checkOutDate: '',
		numberOfGuests: 1
	});
	
	const [housesToShow, setHousesToShow] = React.useState(9);

	// The array of list houses.
	const houses = [
		{
			id: 1,
			img: ['1.jpg'],
			houseTitle: "Gästehaus Fereinhouse",
			location: "Rostocker straße, wismar 00000",
			description: "174M, 2 Betten, 2 Badezimmern",
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
			description: "160M, 3 Betten, 3 Badezimmern",
			guest:3,
			price: 50,
			dateAvaliable: "30/05/2024",
			endDateAvaliable: "30/11/2024"
		},
		{
			id: 3,
			img: ['3.jpg'],
			houseTitle: "Mein kleines Haus",
			location: "Rostocker straße, wismar 00000",
			description: "60M, 1 Betten, 2 Badezimmern",
			guest: 1,
			price: 45,
			dateAvaliable: "07/07/2024",
			endDateAvaliable: "20/12/2024"
		},
		{
			id: 4,
			img: ['4.jpg'],
			houseTitle: "Garten Haus",
			location: "Rostocker straße, wismar 00000",
			description: "200M, 5 Betten, 2 Badezimmern, Garten",
			guest: 5,
			price: 80,
			dateAvaliable: "07/07/2024",
			endDateAvaliable: "01/12/2024"
		},
		{
			id: 5,
			img: ['5.jpg'],
			houseTitle: "Cottage Haus",
			location: "Rostocker straße, wismar 00000",
			description: "40M, 2 Betten, 2 Badezimmern, Garten",
			guest: 1,
			price: 20,
			dateAvaliable: "10/07/2024",
			endDateAvaliable: "30/09/2024"
		},
		{
			id: 6,
			img: ['6.jpg'],
			houseTitle: "Marina Fereinhous",
			location: "Rostocker straße, wismar 00000",
			description: "174M, 7 Betten, 2 Badezimmern, Garten",
			guest: 7,
			price: 130,
			dateAvaliable: "10/07/2024",
			endDateAvaliable: "15/11/2024"
		},
		{
			id: 7,
			img: ['7.jpg'],
			houseTitle: "besonderes Haus",
			location: "Rostocker straße, wismar 00000",
			description: "250M, 6 Betten, 2 Schwimmbäder, Garten",
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
			description: "250M, 10 Betten, 2 Schwimmbäder, Garten",
			guest: 10,
			price: 200,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		{
			id: 9,
			img: ['9.webp'],
			houseTitle: "holzes Ferienhaus",
			location: "Rostocker straße, wismar 00000",
			description: "150M, 3 Betten, keine Schwimmbäder, Garten",
			guest: 3,
			price: 130,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 10,
			img: ['10.jpg'],
			houseTitle: "Am Strand Haus",
			location: "Berliner straße, Strand 00000",
			description: "290M, 5 Betten, 2 Schwimmbäder, Garten",
			guest: 5,
			price: 130,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 11,
			img: ['elevenmain.jpg','eleven.jpg','eleventwo.jfif','eleventhree.webp', 'elevenfour.webp'],
			houseTitle: "Garten Haus",
			location: "Garten straße, Berlin 00000",
			description: "250M, 2 Betten, 2 Schwimmbäder, Garten",
			guest: 2,
			price: 110,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		{
			id: 12,
			img: ['12.jpg'],
			houseTitle: "kleines Fereinhaus",
			location: "Rostocker straße, wismar 00000",
			description: "80M, 1 Betten, keine Schwimmbäder, Garten",
			guest: 1,
			price: 70,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 13,
			img: ['13.jpg'],
			houseTitle: "Random Fereinhaus",
			location: "Rostocker straße, wismar 00000",
			description: "250M, 1 Betten, 2 Schwimmbäder, Garten",
			guest: 1,
			price: 130,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		{
			id: 14,
			img: ['14.jpg'],
			houseTitle: "Normales Fereinhaus",
			location: "Rostocker straße, wismar 00000",
			description: "190M, 2 Betten, keine Schwimmbäder, Garten mit Blumen",
			guest: 2,
			price: 70,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 15,
			img: ['15.jpg'],
			houseTitle: "Dreieckiges Sommerhaus",
			location: "Rostocker straße, wismar 00000",
			description: "90, 3 Betten, keine Schwimmbäder, Bäumen",
			guest: 2,
			price: 80,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 16,
			img: ['16.jpg'],
			houseTitle: "Fereinhaus mit Schwimmbad",
			location: "Test straße, Lübeck",
			description: "270M, 4 Betten, 1 Schwimmbäder, Garten",
			guest: 4,
			price: 130,
			dateAvaliable: "10/08/2024",
			endDateAvaliable: "31/12/2024"
		},
		
		
		{
			id: 17,
			img: ['17.jpg'],
			houseTitle: "großes Fereinhaus",
			location: "Baum straße, Schwerin 00000",
			description: "250M, 8 Betten, 2 Schwimmbäder, Garten",
			guest: 8,
			price: 130,
			dateAvaliable: "15/08/2024",
			endDateAvaliable: "2/12/2024"
		},
		
		
		{
			id: 18,
			img: ['18.jpg'],
			houseTitle: "Modernes Haus",
			location: "Rostocker straße, Rostock",
			description: "100M, 3 Betten, 2 Schwimmbäder, Garten",
			guest: 3,
			price: 150,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 19,
			img: ['19.jpg'],
			houseTitle: "Hotel Haus",
			location: "Hotel straße, Lübeck",
			description: "40M, 1 Betten, 2 Schwimmbäder, Garten",
			guest: 1,
			price: 90,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 20,
			img: ['20.jpg'],
			houseTitle: "Farbenfrohes Haus",
			location: "Schöne straße, Berlin",
			description: "250M, 5 Betten, 2 Schwimmbäder, Park Platz",
			guest: 5,
			price: 100,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 21,
			img: ['21.webp'],
			houseTitle: "Farmes Haus",
			location: "Farm straße, Farm",
			description: "250M, 7 Betten, keine Schwimmbäder, Garten",
			guest: 7,
			price: 100,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 22,
			img: ['22.jpg'],
			houseTitle: "Casle Ferienhaus",
			location: "Casle straße, Kassel",
			description: "95M, 2 Betten, 5 Schwimmbäder, Garten",
			guest: 2,
			price: 95,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 23,
			img: ['23.jpg'],
			houseTitle: "Hotel Haus",
			location: "Rostocker straße, Rostock",
			description: "35M, 2 Betten, 2 Schwimmbäder, Garten",
			price: 150,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 24,
			img: ['24.jpg'],
			houseTitle: "Hotel Haus",
			location: "Cleaner straße, Lübeck",
			description: "35M, 1 Bett, keine Schwimmbäder, Garten",
			guest: 3,
			price: 80,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 25,
			img: ['25.jpg'],
			houseTitle: "Großes Haus",
			location: "Große straße, Mannheim",
			description: "115M, 9 Betten, keine Schwimmbäder, Garten",
			guest: 9,
			price: 150,
			dateAvaliable: "20/08/2024",
			endDateAvaliable: "30/11/2024"
		},
		
		{
			id: 26,
			img: ['26.jpg'],
			houseTitle: "Luxury Haus",
			location: "Cleaner straße, München",
			description: "124M, 8 Betten, keine Schwimmbäder, Garten",
			guest: 8,
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


return (
	<div>
	  <NavBar onNavigate={handleNavigate} />
	  {page === 'home' && (
		<>
		  <Menu houses={filteredHouses.length > 0 ? filteredHouses.slice(0, housesToShow) : houses.slice(0, housesToShow)} onBookNow={handleBookNow} />
		  {housesToShow < houses.length && (
			<button onClick={handleLoadMore} className="load-more-button">Mehr laden...</button>
		  )}
		</>
	  )}
	  {page === 'aboutUs' && <überUns />}
	  {page === 'booking' && selectedHouse && (
		<BookingView selectedHouse={selectedHouse} setPage={setPage} setBookingDates={setBookingDates} />
	  )}
	  {page === 'bookingForm' && selectedHouse && (
		<BookingForm selectedHouse={selectedHouse} onClose={() => setPage('home')} startDate={bookingDates.start} endDate={bookingDates.end} />
	  )}
	  {page === 'contact' && <ContactUs setPage={setPage} />}
	  {page === 'login' && <SignInForm onNavigate={handleNavigate} />}
	  {page === 'register' && <RegistrationForm setPage={setPage} onRegisterSubmit={handleRegisterSubmit} />}
	</div>
);
};
window.onload = () => ReactDOM.render(<AppGuest />, document.getElementById('app'));
