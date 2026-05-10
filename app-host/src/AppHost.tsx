import React from "react";
import MUI from "@material-ui/core";
import { rpc, globalState } from "./index";
import Style from "./components/Style";

const { Menu, MenuItem } = MUI;

import "../../app-shared/types";
import * as types from "./types";
import "../../app-shared/shared.scss";
import "./index.scss";
import { simpleState } from "./utils/utils";
import { Header } from "./Header";
import { Login } from "./Login";
import { RegistrationForm, RegisterConfirm } from "./Registration"

import { Bookings } from "./Bookings";
//import { PendingRequests } from "./PendingRequests";
import { DeclinedRequests } from "./DeclinedRequests";
import { OutdatedBookings } from "./OutdatedBookings";
import { Presentation } from "./Presentation";
import { Sites } from "./Objects";
import { RoomTypes } from "./Objects";
import { Rooms } from "./Objects";
import { PricingRules } from "./Objects";
import { Organization } from "./Organization";
import { StringsUI } from "./StringsUI";
import { FormDraftProvider } from "./FormDraftContext";

export function AppHost(props) {
	const getHash = () => window.location.hash || "#";
	let [s,sc,sx] = simpleState({
		screen: null,
		currentPath: window.location.hash,
		isOpen: false,
		orgs: [],
		organizationName: "",
		showBookingExtras: false
	})
	let [subPage, setSubPage] = React.useState< "Bookings" | "ausstehend" | "Declined" | "Outdated"| null>(null);
	let [siteSubPage, setSiteSubPage] = React.useState< "Sites" | "RoomTypes" | "Rooms" | "PricingRules" | "Organization" | null>(null);

	let mainPages = [
		{ page: "Bookings", text: "Buchungsbereich", showBookingExtras: true },
		{ page: "Presentation", text: "Ihre Unterkünfte anzeigen", showBookingExtras: false },
		{ page: "Sites", text: "Unterkünfte hinzufügen & bearbeiten", showBookingExtras: false, showSubPage: true },
		{ page: "Organization", text: "Konfiguration", showBookingExtras: false, showSubPage: true }
	]
	let bookingSubNavs = [
		{ text: "Buchungen", subPage: "Bookings" },
//		{ text: "Ausstehende Anfragen", subPage: "Pending" },
		{ text: "Abgelehnte Anfragen", subPage: "Declined" },
		{ text: "Veraltete Anfragen", subPage: "Outdated" }
	]
	let orgSubNavs = [
		{ text: "Anbieter", subPage: "Organization" },
		{ text: "Private Seite", subPage: "PrivatePage" },
		{ text: "E-Mail Texte", subPage: "EmailTexts" }
	]
	let sitesSubNavs = [
		{ text: "Unterkünfte", subPage: "Sites", tooltip: "Das gesamte Objekt oder die Unterkunftseinheit, z. B. ein Hotel, eine Pension oder ein Apartmenthaus. Enthält alle Zimmerkategorien und Miet­einheiten." },
		{ text: "Art der Unterbringung", subPage: "RoomTypes", tooltip: "Eine Zimmerkategorie. Beschreibt gemeinsame Merkmale von mehreren Zimmern oder Einheiten (Größe, Betten, Ausstattung, Standardpreis)." },
		{ text: "Mieteinheit", subPage: "Rooms", tooltip: "Die konkrete, buchbare Einheit. Ein einzelnes Zimmer oder Apartment, das direkt reserviert werden kann." },
		{ text: "Preisregeln", subPage: "PricingRules", tooltip: "Regeln zur Anpassung der Preise. Können für die gesamte Unterkunft, einzelne Kategorien oder einzelne Einheiten gelten." },
	]
	let bigScreen = s.screen?.width > 768;
	let smallScreen = s.screen?.width <= 768;
	let loggedIn = globalState.loggedIn;
	let setLoggedIn = orgId => {
		globalState.loggedIn = orgId;
		globalState.isHost = !!orgId;
		if (!globalState.isDeveloper)
			location.reload();
		else globalState.refreshPage();
	};
	let page = s.currentPath.replace("#", "") || (globalState.isHost ? "Bookings" : "#");
	let setPage = page => {
		setSubPage(null);
		navigate("#" + page);
	};
	let navigate = (path) => {
		window.history.pushState({}, "", path);
		sc({currentPath: path});
	};
	globalState.navigate = navigate;
	globalState.setSiteSubPage = setSubPage; // setSiteSubPage;

	React.useEffect(() => {
		s.screen = screen;
		if (globalState.loggedIn && (!page || page == "#")) {
			navigate("#Bookings");
			setSubPage("Bookings");
			s.showBookingExtras = true;
		}
		(async function() {
			//await rpc.onLoggedIn(null);
			const organizations = await rpc.loadOrganizations();
			sc({orgs: organizations || []})
		})();
		const handlePopState = () => sc({currentPath: getHash()});
		window.addEventListener("popstate", handlePopState);
		return () => window.removeEventListener("popstate", handlePopState);
	}, []);

	React.useEffect(() => {
		if (page) {
			let p = mainPages.find(x => x.page == page);
			if (p) {
				if (p.showSubPage) setSubPage(p.page);
				if (page == "Bookings") setSubPage("Bookings");
				s.isOpen = false;
				s.showBookingExtras = p.showBookingExtras;
			}
		}
	},[page]);

	React.useEffect(() => {
		if (loggedIn && s.orgs.length > 0) {
			const selectedOrg = s.orgs.find(org => org.id == loggedIn);
			sc({organizationName: selectedOrg ? selectedOrg.name : ""});
		}
	},[loggedIn, s.orgs]);
	// React.useEffect(()=>{console.log("org name: ",s.organizationName)},[s.organizationName])

	let confirmToken = null;
	if (page.startsWith("RegisterConfirm")) {
		confirmToken = decodeURIComponent(s.currentPath).split("#")[1];
		page = "RegisterConfirm";
	}
	let privatePageToken = null;
	if (page.startsWith("Presentation")) {
		privatePageToken = decodeURIComponent(s.currentPath).split("#")[1];
		page = "Presentation";
	}

	let SitesSubNav = <div className="subnav flexh gap-sm">
		{sitesSubNavs.map(sn => <button
			title={sn.tooltip}
			className={`subnav-btn${ subPage == sn.subPage ? " active" : ""}`}
			onClick={() => setSubPage(sn.subPage)}>{sn.text}
		</button>)}
	</div>
	let OrganizationSubNav = <div className="subnav flexh gap-sm">
		{orgSubNavs.map(sn => <button
			className={`subnav-btn${subPage == sn.subPage ? " active" : ""}`}
			onClick={() => setSubPage(sn.subPage)}>{sn.text}
		</button>)}
	</div>
	let BookingSubNav = <div className="subnav flexh gap-sm">
		{bookingSubNavs.map(sn => <button
			className={`subnav-btn${subPage == sn.subPage ? " active" : ""}`}
			onClick={() => setSubPage(sn.subPage)}>
			{sn.text}
		</button>)}
	</div>

	let MainNav = <nav className={`main-nav${bigScreen ? " default" : " responsive"} flexh gap-sm wrap`} style={{position: "relative"}}>
		{mainPages.map((p,i) => {
			let [hoverIndex, setHoverIndex] = React.useState<number | null>(null);
			let menuItems =
				p.page == "Bookings" ? bookingSubNavs :
				p.page == "Sites" ? sitesSubNavs :
				p.page == "Organization" ? orgSubNavs :
				[];
			let isMenuPage = p.page == "Bookings" || p.page == "Sites" || p.page == "Organization";
			let open = hoverIndex == i;
			return <>
				{<button className={`main-nav-btn${page == p.page ? " active" : ""}`}
					onClick={(e) => {
						setPage(p.page);
						if (p.page == page) setSubPage(p.page);
					}}
//					onMouseEnter={() => bigScreen && isMenuPage && setHoverIndex(i)}
//					onMouseLeave={() => bigScreen && isMenuPage && setHoverIndex(null)}
				>
					<Style>{`
						& .button-with-menu { padding: 4px; background: transparent; }
						& .menu {
							position: absolute; left: 0px;
							transform: translateX(50%);
							top: 37px; z-index: 100;
							background: white; text: black;
						}
					`}</Style>
					{p.text}
					{
//						bigScreen && (p.page=="Bookings" || p.page=="Sites" || p.page=="Organization") &&
//						(page==p.page ? <ButtonWithMenu
//							text={p.text}
//							menuItems={menuItems}
//							open={open}
//							page={page}
//							subPage={subPage}
//							onSetSubPage={(sp) => { console.log("subPage: ",sp); setSubPage(sp) }}
//							{...p}
//						/> : p.text)
					}
				</button>}
				{smallScreen && <>
					{p.page=="Bookings" && page==p.page && BookingSubNav}
					{p.page=="Sites" && page==p.page && SitesSubNav}
					{p.page=="Organization" && page==p.page && OrganizationSubNav}
				</>}
			</>
		})}
	</nav>
	function ButtonWithMenu({ text, menuItems, open, page, subPage, onSetSubPage, ...props }) {
		return <div className="button-with-menu"
			onClick={e => e.stopPropagation()}
		>
			{text} {open ? "▲" : "▼"}
			{bigScreen && open && (
			<div className="menu">
				{menuItems.map(sn => (
				<MenuItem
					className={`subnav-btn${subPage == sn.subPage ? " active" : ""}`}
					key={sn.subPage}
					selected={subPage == sn.subPage}
					onClick={(e) => {e.stopPropagation(); onSetSubPage(sn.subPage)}}
				>
					{sn.text}
				</MenuItem>
				))}
			</div>
			)}
		</div>
	}


	return <FormDraftProvider>
		<div className="app-host container flexv gap-md">
			{bigScreen && <Header
				compact
				navigate={navigate}
				login={() => setPage("Login")}
				register={asHost => setPage(asHost ? "RegisterAsHost" : "RegisterAsGuest")}
				onSearch={() => navigate("#")}
				children={(loggedIn && bigScreen) ? [MainNav] : []}
			/>}
			{smallScreen && <div className="header">
				<div className="app-host-titlebar">
					<div className="drawer-btn-container noSelect flexh centerh" onClick={() => sc({isOpen: !s.isOpen})}>
						<span className="material-symbols-outlined">menu</span>
					</div>
					<Header
						compact
						navigate={navigate}
						login={() => setPage("Login")}
						register={asHost => setPage(asHost ? "RegisterAsHost" : "RegisterAsGuest")}
						onSearch={() => navigate("#")} />
				</div>
				{s.isOpen && <div className="drawer-backdrop" onClick={() => sc({isOpen: false})}></div>}
				<div className={`drawer-container ${s.isOpen ? "open" : ""}`}>
					{MainNav}
				</div>
			</div>}
			{page == "Login" && <Login />}
			{page == "RegisterAsHost" && <RegistrationForm asHost />}
			{page == "RegisterAsGuest" && <RegistrationForm showBackButton/>}
			{page == "RegisterConfirm" && <RegisterConfirm confirmToken={confirmToken} />}
			<div className="content">
				{
					loggedIn && <>
					{page == "Bookings" && <>
						{bigScreen && BookingSubNav}
						{subPage == "Bookings" && <Bookings />}
						{subPage == "ausstehend" && <Bookings />}
						{subPage == "Declined" && <DeclinedRequests />}
						{subPage == "Outdated" && <OutdatedBookings />}
						{subPage == "Pending" && <PendingRequests />}
					</>}
					{page == "Presentation" && <Presentation ignoreDomain isHostView setPage={page => setPage(page)} />}
					{page == "Sites" && <>
						{bigScreen && SitesSubNav}
						{subPage == "Sites" && <Sites organizationName={s.organizationName} loggedIn={loggedIn} />}
						{subPage == "RoomTypes" && <RoomTypes organizationName={s.organizationName} loggedIn={loggedIn} />}
						{subPage == "Rooms" && <Rooms organizationName={s.organizationName} loggedIn={loggedIn} />}
						{subPage == "PricingRules" && <PricingRules organizationName={s.organizationName} loggedIn={loggedIn} />}
					</>}
					{page == "Organization" && <>
						{bigScreen && OrganizationSubNav}
						{subPage == "Organization" && <Organization loggedIn={loggedIn} />}
						{subPage == "PrivatePage" && <Organization loggedIn={loggedIn} privatePage/>}
						{subPage == "EmailTexts" && <StringsUI loggedIn={loggedIn} context="email"/>}
					</>}
				</>
			}
			</div>
		</div>
	</FormDraftProvider>
}
