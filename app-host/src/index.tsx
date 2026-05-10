import React from "react";

import "../../app-shared/types";
import * as types from "./types";
import "../../app-shared/shared.scss";
import "./index.scss";

import { initRpc } from "./rpc";

// Initialize the RPC
export let rpc = initRpc({
	loadBookingStats: async (): Promise<any[]> => { return null; },
	loadBookings: async (): Promise<any[]> => { return null; },

	loadMessages: async (): Promise<any[]> => {
		const response = await fetch('/api/loadMessages');
		const data = await response.json();
		return data;
	},

	// for now the client tells the server who loggedIn - because of our fake UI login
	// later we'll replace that with proper login process/logic
	// where server decides over login or access denied
	async onLoggedIn(orgId): Promise<void> { },
	// async login(identity, secret): Promise<[sessionId: string, orgId: string, isHost: boolean]> { return null; },
	async login(identity, secret, rememberMe): Promise<[orgId: string, isHost: boolean]> { return null; },
	async logout(): Promise<void> { },
	async session(sessionId): Promise<[sessionId: string, orgId: string, isHost: boolean]> { return null; },
	async getLoggedInUser(): Promise<any> { return null; },
	async getLoggedInOrg(): Promise<any> { return null; },
	async customizeInfos(privatePageToken): Promise<any> { return null; },

	// Organization section
	async loadOrganizations(): Promise<types.Organization[]> { return null; }, // load all for dev
	async loadOrganization(id: string): Promise<types.Organization> { return null; }, // to load only the relevant Organization data
	async saveOrganization(item: types.Organization): Promise<void> { },
	async removeOrganization(id: string): Promise<void> { },

	// Strings section
	async loadStrings(): Promise<types.Strings[]> { return null; }, // load all for dev
	async loadString(id: string): Promise<types.Strings> { return null; }, // to load only the relevant Strings data
	async saveString(item: types.Strings): Promise<void> { },
	async loadStringsByKey(purpose: string): Promise<any> { return null; },

	// Site section
	async loadSites(ignoreDomain, privatePageToken): Promise<types.Site[]> { return null; }, // load all for dev
	async loadSite(id: string): Promise<types.Site> { return null; }, // to load only the relevant site data
	async saveSite(item: types.Site): Promise<void> { },
	async removeSite(id: string): Promise<void> { },

	// Room Types section
	async loadRoomTypes(): Promise<types.RoomType[]> { return null; }, // load all for dev
	async loadRoomType(id: string): Promise<types.RoomType> { return null; }, // to load only the relevant roomType  data
	async saveRoomType(item: types.RoomType): Promise<void> { },
	async removeRoomType(id: string): Promise<void> { },

	// Room section
	async loadRooms(): Promise<types.Rooms[]> { return null; }, // load all for dev
	async loadRoom(id: string): Promise<types.Rooms> { return null; }, // to load only the relevant room data
	async saveRoom(item: types.Rooms): Promise<void> { },
	async removeRoom(id: string): Promise<void> { },

	// Amenities section
	async loadAmenities(roomTypeId: string): Promise<string[]> { return null; },
	async saveAmenities(roomTypeId: string, amenities: string[]): Promise<void> { },

	// pricing Rules section
	async loadPricingRules(): Promise<types.PricingRule[]> { return null; }, // load all for dev
	async loadPricingRule(id: string): Promise<types.loadPricingRule> { return null; }, // to load only the relevant PricingRule data
	async savePricingRule(item: types.PricingRule): Promise<void> { },
	async removePricingRule(id: string): Promise<void> { },

	/*
	// pricing Rules section
	async loadPricingRules(): Promise<types.PricingRule[]> {
		const response = await fetch ("/api/pricingRules");
		return await response.json();
	}, // load all for dev
	async loadPricingRule(id: string): Promise<types.loadPricingRule> {
		const response = await fetch (`/api/pricingRules/${id}`);
		return await response.json();
	}, // to load only the relevant PricingRule data
	async savePricingRule(item: types.PricingRule): Promise<void> {
		await fetch ("/api/pricingRules", {
			method: "POST",
			headers: {"Content-Type": "application/json"},
			body: JSON.stringify(item),
		});
	},
	async removePricingRule(id: string): Promise<void> {
		await fetch (`/api/pricingRules/${id}`, { method: "DELETE" });
	},
	*/

	//Registeration
	async register(formData) {
		try {
			const response = await fetch('/api/register', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify(formData),
			});

			if (!response.ok) {
				throw new Error('Register failed');
			}

			const result = await response.json();
			return result; // the result should be orgId and isHost
		} catch (error) {
			console.error('Error during the registration:', error);
			throw error;
		}
	},

	// forgotPassword API end point call function (later)

	// Bookingdata
	async loadAllBookingData(): Promise<types.BookingData[]> { return null; }, // load all the booking data
	async loadBookingData(id: string): Promise<types.BookingData> { return null; },
	async removeBookingData(id: string): Promise<void> { },
	async saveBookingData(item: types.BookingData): Promise<void> { },
	async requestStatusUpdate(item: types.BookingData): Promise<void> { },
	/*
		async loadAllBookingData(): Promise<types.BookingData[]> {
			try {
				const response = await fetch ('/api/getAllBookingData');
				if (!response.ok){
					throw new Error ('Failed to fetch booking data');
				}
				return await response.json();
			} catch (error) {
				console.error("Error to load all the booking data:", error);
				throw error;
			}

		},
		async loadBookingData(id: string): Promise<types.BookingData> {
			try {
				const response = await fetch (`/api/getBookingData?id=${id}`);
				if (!response.ok){
					throw new Error ('Failed to fetch booking');
				}

				return await response.json();
			} catch (error) {
				console.error("Error loading booking data: ", error);
				throw error;
			}
		},

		async removeBookingData(id: string): Promise<void> {
			try {
				const response = await fetch (`/api/removeBookingData?id=${id}`, {
					method: 'DELETE',
				});
				if (!response.ok){
					throw new Error ('Failed to delete the booking');
				}
			} catch (error) {
				console.error("Error deleting booking data:", error);
				throw error;
			}
		},

		async saveBookingData(item: types.BookingData): Promise<void> {
			try {
				const response = await fetch('/api/saveBookingData', {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify(item),
				});

				if (!response.ok) {
					throw new Error('Buchungsdaten konnten nicht gespeichert werden');
				}
			} catch (error) {
				console.error('Error saving booking data:', error);
				throw error;
			}
		},
		// To reject/accept a request
		async updateRequestStatus(item: types.RequestStatus): Promise<void> {
			try {
				const response = await fetch ('/api/updateRequestStatus', {
					method: 'POST',
					headers: {'Content-Type' : 'application/json'},
					body: JSON.stringify(item),
				});

				if (!response.ok){
					throw new Error ('failed to update the request status');
				}
			} catch (error) {
				console.error ("Error to update the request status:", error);
				throw error;
			}
		},
	*/
});

import ReactDOM from "react-dom";
import { initWsComm, initRpc, wsCallbacks, session } from "rpc";

import { AppGuest } from "./AppGuest";
import { AppHost } from "./AppHost";
import { identity } from "@fullcalendar/common";

let sessionId = window["name"];
// TODO auth-related session should be handled by server
if (!sessionId)
	sessionId = sessionStorage.getItem("FEWOSID"); // "Angemeldet bleiben"
if (!sessionId)
	sessionId = ("UI"+Math.random()).replace(/\./,"");
window["name"] = sessionId;

window.onload = () => {
	initWsComm().then(() => {
		(async function() {
			let sessionInfos = await rpc.session(sessionId);
			if (sessionInfos) {
				globalState.loggedIn = sessionInfos[1];
				globalState.isHost = sessionInfos[2];
				globalState.isDeveloper = sessionInfos[3];
				globalState.user = await rpc.getLoggedInUser();
			}

			let currentPath = window.location.hash;
			let page = currentPath.replace("#", "");
			let privatePageToken = null;
			if (page.startsWith("Presentation")) {
				privatePageToken = decodeURIComponent(currentPath.slice(1)).split("#")[1];
				page = "Presentation";
			}

			globalState.customizeInfos = defaultCustomizeInfos;
			let customizeInfos = await rpc.customizeInfos(privatePageToken);
			//	globalState.customizeInfos ||= defaultCustomizeInfos;
			if (customizeInfos) globalState.customizeInfos = Object.assign({...globalState.customizeInfos},customizeInfos);

			ReactDOM.render(<App />, document.getElementById("app"));
		})();
	});
}

function App(props) {
	let [state, setState] = React.useState(0);
	let update = () => setState(state + 1);
	globalState.refreshPage = update;
	return <>
		{globalState.isHost /*|| globalState.isDeveloper*/
			? <AppHost /> : <AppGuest />
		}
	</>
}

let hash = document.location.hash.slice(1); // cut '#'
let params = new URLSearchParams(window.location.search);
let modeParam = params.get("mode");
let requestedDev = modeParam == "dev" /*|| modeParam == "admin"*/;
let storedDev = localStorage.getItem("devMode") == "1";
let isDeveloper = requestedDev || storedDev;
if (requestedDev) {
	localStorage.setItem("devMode", "1");
}

const defaultCustomizeInfos = {
	"title": "Fewo Buchung",
	"logo": true,
	"logoImage": "img/fewosegel-logo.png",
	// this could give legal problems!
	"#logoImage": "https://cdn.vectorstock.com/i/500p/84/09/sailing-boat-icon-logo-design-template-isolated-vector-54048409.jpg",
	"login": true,
	"filter": true,
	"bannerHeight": 300,
	"bannerImage": "img/cover.png", // copied img/ folder to target
	"#bannerImage": "./banner-ENRGLPID.jpg", // via esbuild loader
	// this could give legal problems!
	"##bannerImage": "https://static.vecteezy.com/system/resources/thumbnails/021/885/308/small_2x/miniature-house-with-keys-on-wooden-background-real-estate-concept-ai-generated-artwork-photo.jpg"
};

export const globalState = {
	page: "#",
	loggedIn: null,
	user: null,
	isDeveloper,
	// isDeveloper: hash == "dev",
	isHost: false || hash == "admin",
	bookingContext: null,
	navigate: (page) => {
		window.location.hash = page;
		globalState.page = page;
	},
	refreshPage: () => window.location.reload(),
	//refreshPage: null, // from App, see above
	//navigate: null, // from AppGuest or AppHost
	customizeInfos: defaultCustomizeInfos,
	version: "20260123",
};

//if (hash == "dev" || hash == "admin")
//	window.history.pushState({}, "", "#");
if (modeParam != null) {
	params.delete("mode");
	const newUrl =
		window.location.pathname +
		(params.toString() ? "?" + params.toString() : "") +
		window.location.hash;

	window.history.replaceState({}, "", newUrl);
}
