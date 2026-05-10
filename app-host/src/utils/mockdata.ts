export const randomItem = (arr) => arr[Math.floor(Math.random() * arr.length)];
export const randomInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;

export const FIRST_NAMES = [
	"Max", "Anna", "Lukas", "Sophie", "Jonas", "Lea",
	"Paul", "Mia", "Felix", "Nina", "Tim", "Clara"
];
export const LAST_NAMES = [
	"Müller", "Schmidt", "Schneider", "Fischer", "Weber",
	"Wagner", "Becker", "Hoffmann", "Koch", "Klein"
];
export const LOCATIONS = [
	{
		city: "Berlin",
		plz: ["10115", "10243", "10405", "10585", "10965", "12045"],
		streets: ["Invalidenstraße", "Karl-Marx-Allee", "Pappelallee", "Kurfürstenstraße", "Urbanstraße"]
	},
	{
		city: "Hamburg",
		plz: ["20095", "20253", "20457", "21029", "22041"],
		streets: ["Mönckebergstraße", "Eppendorfer Weg", "Große Elbstraße", "Bahrenfelder Chaussee"]
	},
	{
		city: "München",
		plz: ["80331", "80636", "80797", "81241", "81549"],
		streets: ["Leopoldstraße", "Sendlinger Straße", "Schwanthalerstraße", "Ismaninger Straße"]
	},
	{
		city: "Köln",
		plz: ["50667", "50823", "50937", "51063"],
		streets: ["Aachener Straße", "Venloer Straße", "Severinstraße", "Frankfurter Straße"]
	},
	{
		city: "Frankfurt am Main",
		plz: ["60311", "60486", "60594", "65934"],
		streets: ["Zeil", "Berger Straße", "Friedberger Landstraße", "Hedderichstraße"]
	}
];
export const WISHES = [
	"Bitte ein ruhiges Zimmer.",
	"Zimmer mit Balkon wäre gut.",
	"Allergikerbettwäsche erbeten.",
	"Hohes Stockwerk bevorzugt.",
	"Später Check-in notwendig."
];

// ---- Telephone generator ----
// German: +49 <Vorwahl> <7–8 digit subscriber number>
// Example: +49 30 12345678  (Berlin)
//          +49 40 9876543   (Hamburg)
export const AREA_CODES = {
	"Berlin": "30",
	"Hamburg": "40",
	"München": "89",
	"Köln": "221",
	"Frankfurt am Main": "69"
};

// ---- Email-friendly normalization ----
export const normalizeSpelling = (s) => s
.toLowerCase()
.replace(/ä/g, "ae")
.replace(/ö/g, "oe")
.replace(/ü/g, "ue")
.replace(/ß/g, "ss");

export const generatePhone = (city) => {
	const vorwahl = AREA_CODES[city] || "30";
	const len = randomItem([7, 8]);
	let num = "";
	for (let i = 0; i < len; i++) num += randomInt(0, 9);
	return `+49 ${vorwahl} ${num}`;
};
