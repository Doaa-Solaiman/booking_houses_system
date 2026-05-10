import React from "react";

import { rpc, globalState } from "./index";
import { simpleState } from "./utils/utils";
import Style from "./utils/Style";

import { Header } from "./Header";
import { Login } from "./Login";
import { RegistrationForm, RegisterConfirm } from "./Registration"
import { Cards, Detail } from "./Presentation";

export function AppGuest(props)
{
	let [s,sc,sx] = simpleState({
		currentPath: window.location.hash,
		checkInDate: null,
		selectedSite: null as types.Site,
	});

	React.useEffect(() => {
		const handlePopState = () => sc({ currentPath: window.location.hash }); // setCurrentPath(window.location.hash);
		window.addEventListener("popstate", handlePopState);
		return () => window.removeEventListener("popstate", handlePopState);
	}, []);
	let navigate = (path) => {
		window.history.pushState({}, "", path);
		sc({ currentPath: path }); // setCurrentPath(path);
	};
	globalState.navigate = navigate;

	let confirmToken = null;
	if (s.currentPath.startsWith("#RegisterConfirm")) {
		confirmToken = decodeURIComponent(s.currentPath.slice(1)).split("#")[1];
		s.currentPath = "#RegisterConfirm";
	}

	let privatePageToken = null;
	if (s.currentPath.startsWith("#Presentation")) {
		privatePageToken = decodeURIComponent(s.currentPath.slice(1)).split("#")[1];
		s.currentPath = "#Presentation";
	}

	if (s.currentPath.startsWith("#Detail")) {
		s.currentPath = "#Detail";
	}

	let content;
	switch (s.currentPath) {
		case "#Detail":
			content = <Detail site={s.selectedSite?.site} roomtype={s.selectedSite?.roomtype} />
			break;
		case "#Login":
			content = <Login/>
			break;
		case "#RegisterAsHost":
			content = <RegistrationForm asHost/>
			break;
		case "#RegisterAsGuest":
			content = <RegistrationForm showBackButton />
			break;
		case "#RegisterConfirm":
			content = <RegisterConfirm confirmToken={confirmToken}/>
			break;
		default:
			content = <div className="content">
				<Cards
					// ignoreDomain
					privatePageToken={privatePageToken}
					checkInDate={s.checkInDate}
					value={s.selectedSite}
					onChange={v => {
						let id = v.site?.id || v.id;
						navigate("#Detail"+"?id="+id);
				}}/>
			</div>
			break;
	}
	return <div className="app-guest container flexv gap-md">
		<Header
			firstName= {globalState.firstName}
			navigate={navigate}
			login={() => navigate("#Login")}
			register={asHost => navigate("#RegisterAsHost")}
			onCheckInDate={date => sc({ checkInDate: date }) /*setCheckInDate(date)*/}
			onSearch={() => navigate("#")} // for now to the home page
		/>
		{
			globalState.customizeInfos.filter &&
			<div className="banner flexh centerh" style={{
				height: globalState.customizeInfos.bannerHeight,
				backgroundImage: globalState.customizeInfos.bannerImage,
			}}>
				{/*<AvailabilityCalendar onCheckInDate={props.onCheckInDate} navigate={props.navigate}/>*/}
			</div>
		}
		{content}
	</div>
}
