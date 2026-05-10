export type Site = {
	id: string,
	organization_id: string,
	name: string,
	shortName: string,
	presentationType: "site" | "roomtypes",
	availabilityType: "none" | "capacity" | "rooms",
	unitCount: number,
	//roomType_ids: string,
	teaser: string,
	description: string,
	address: string,
	city: string,
	state: string,
	country: string,
	phoneNumber: string,
	email: string,
	roomtypeLabel: string,
	showRoomtypePhotos: boolean;

}

export type RoomType = {
	id: string,
	organization_id: string,
	site_id: string,
	name: string,
	shortName: string,
	supertype_ids: string,
	adults: number,
	teaser: string,
	description: string,
	price: number,
	cleanService?: number,
	showSitePhotos: boolean,
}

export type Room = {
	id: string,
	name: string,
	organization_id: string,
	roomType_id: string,
	site_id: string,
	//children: number,
	bedSizes: string,
	rate: number,
	view: string,
	available: boolean,
}

export type PricingRule = {
	id: string,
	site_id: string,
	roomType_id: string,
	room_id: string,
	belongsTo: string,
	name: string,
	active: boolean,
	//default price from roomType section
	startDate: Date,
	endDate: Date,
	conditions: string,
	//adjustType: "addPercent" | "addAmount" | "newPrice",
	adjustType: "addPercent" | "addAmountEarly" | "addAmountLate" | "newDefaultPrice" | "newFinalPrice",
	adjustValue: number,
}

export type Organization = {
	id: string,
	name: string,
	email: string,
	address: string,
	city: string,
	zipCode: string,
	country: string,
	showOn: string,
}

export type Photos = {
	id: string,
	file: string,
	order: number,
	caption: string,
	origName: string,
	organization_id: string,
	site_id: string,
	roomType_id: string,
}

export type BookingRoomType = {
	id: string, //primary Key which means unique id for this booking-roomType
	booking_id: string; //foreign Key and filled in later in the backend
	roomType_id: string,
	room_ids: string[],
	name: string,
	count: number,
	guests: number,
	pricePerNight: number,
}

export type BookingData = {
	id: string,
	site_id: string,
	siteName: string,
	// optional and used only for A single roomtype booking
	roomType_id?: string | null,
	room_id?: string | null;
	
	roomTypes: BookingRoomType[]; // child collection
	startDate: Date,
	endDate: Date,
	firstName: string,
	lastName: string,
	email: string,
	confirmEmail?: string;
	address: string,
	telephone: string,
	additionalWishes: string,
	totalGuests: number,
	totalPrice: number,
	price?: number,
	priceType?: "perNight" | "total";
	dateSent: Date,
	//expiresAt?: string,
	status: "ausstehend" | "akzeptiert" | "abgelehnt" | "outdated" | "storniert";
	statusDecisionTime: Date,
}

/*

A single booking can reference multiple room types.
Booking stores general info about the customer and the booking dates.
BookingItem stores each room type booked and the count.
When calculating totalPrice, you sum BookingItem.totalPrice for the booking_id.


export type BookingItem = {
id: string;
booking_id: string;      *foreign key to Booking.id
roomType_id: string;     *which room type
numberOfRooms: number;   *how many rooms of this type
pricePerNight: number;   *store price
totalPrice: number;      *numberOfRooms * pricePerNight * nights
};


const newBooking: Booking = { id: "booking1", site_id: "site1", startDate: 25.09.2025, endDate: 03.10.2025, totalPrice: 450, additionalWishes:Nothing };
const items: BookingItem[] = [
{ id: "i1", booking_id: "booking1", roomType_id: "r1", numberOfRooms: 2, pricePerNight: 50, totalPrice: 200 },
{ id: "i2", booking_id: "booking1", roomType_id: "r2", numberOfRooms: 1, pricePerNight: 75, totalPrice: 75 }
];

*/

export type Amenity = {
	id: string,
	roomType_id: string,
	name: string,
}

export type Strings = {
	id: string,
	organization_id: string,
	previous_id: string,
	context: string,
	purpose: string,
	skey: string,
	svalue: string,
	lang: string,
	comment: string,
	version: number,
	timeCreate: Date,
	timeUpdate: Date,
}
