import React from "react";
import { AboutUs } from "AboutUs";
import { rpc, globalState } from "./index";
import MUI from "@material-ui/core";
const { Menu, MenuItem, Button } = MUI;
import { MuiIcon } from "./components/formelements";
import Style from "./components/Style";

export function Header(props) {
	const isLoggedIn = globalState.loggedIn || globalState.user;
	const [showSettings, setShowSettings] = React.useState(false);
	const [confirmDelete, setConfirmDelete] = React.useState(false);
	const [password, setPassword] = React.useState("");
	const [showPasswordPrompt, setShowPasswordPrompt] = React.useState(false);
	//password error for confirming to delete your account
	const [passwordError, setPasswordError] = React.useState<string | null>(null);
	const [anchorEl, setAnchorEl] = React.useState(null);

	const toggleMenu = (value) => {
		setAnchorEl(value);
	};

	const logout = () => {
		localStorage.removeItem("FEWOSID");
		sessionStorage.removeItem("FEWOSID");

		globalState.loggedIn = null;
		globalState.isHost = false;
		globalState.user = null;

		rpc.logout();

		if (globalState.navigate) globalState.navigate('#');
		globalState.refreshPage?.();
	};
	const handleDeleteAccount = () => {
		alert("Konto wurde gelöscht.");
		setShowSettings(false);
		setConfirmDelete(false);
		setShowPasswordPrompt(false);
		setPassword("");
	};
	//	const [showAbout, setShowAbout] = React.useState(false);
	//	const handleShowAbout = () => setShowAbout(true);
	//	const handleHideAbout = () => setShowAbout(false);
	//	if (showAbout)
	//		return <header className="header">
	//			<AboutUs onBack={handleHideAbout} />
	//			<div className="overlay"></div>
	//		</header>

	return <div className="header">
		<div className="top-header flexh between">
			<LogoTitle onClick={() => props.navigate('#')} />
			{props.children && [...props.children]}
			{globalState.customizeInfos.login && <div className="navigation flexh">
				{isLoggedIn ? (
					<div>
						<button aria-controls="simple-menu" aria-haspopup="true"
							onClick={() => toggleMenu(Boolean(anchorEl) ? null : event.currentTarget)}>
							<MuiIcon name="account_circle"/>
						</button>
						<Menu
							id="header-menu"
							style={{border: "1px solid #eee"}}
							elevation={0}
							getContentAnchorEl={null}
							anchorOrigin={{
								vertical: 'bottom',
								horizontal: 'center',
							}}
							transformOrigin={{
								vertical: 'top',
								horizontal: 'center',
							}}
							anchorEl={anchorEl}
							keepMounted
							open={Boolean(anchorEl)}
							onClose={() => toggleMenu(null)}
						>
							<div className="px-sm">{`${globalState?.user?.firstName} ${globalState?.user?.lastName}`}</div>
							<hr/>
							<MenuItem onClick={logout}><MuiIcon name="logout" size="25"/>Abmelden</MenuItem>
							<hr/>
							<div className="px-sm text-grey">{"v"+globalState.version}</div>
						</Menu>
					</div>
				) : (
					<>
						{/*<button onClick={() => props.register(true)}>Anmelden als Anbieter</button>*/}
						{/*<button onClick={() => props.register(false)}>Registrieren</button>*/}
						<button onClick={() => props.login()}>Ihre Unterkunft vermieten</button>
					</>
				)}
			</div>}
		</div>
		{/*{showSettings && (
			<div className="settings-overlay">
				<div className="settings-window">
					<button
						onClick={() => {
							setShowSettings(false);
							setConfirmDelete(false);
							setShowPasswordPrompt(false);
							setPassword("");
							setPasswordError(null);
						}}
					>
						×
					</button>
					<h3>⛭ Einstellungen</h3>
					{!confirmDelete && !showPasswordPrompt && (
						<button
							className="delete-account-btn"
							onClick={() => setConfirmDelete(true)}
						>
							Konto Löschen
						</button>
					)}
					{confirmDelete && !showPasswordPrompt && (
						<div className="delete-confirm">
							<p>Sind Sie sicher, dass Sie Ihr Konto löschen möchten?</p>
							<div className="btn-row">
								<button
									className="confirm-yes"
									onClick={() => setShowPasswordPrompt(true)}
								>
									Ja
								</button>
								<button
									className="confirm-no"
									onClick={() => setConfirmDelete(false)}
								>
									Nein
								</button>
							</div>
						</div>
					)}
					{showPasswordPrompt && (
						<div className="password-prompt">
							<p>Bitte geben Sie Ihr Passwort ein:</p>
							<input
								type="password"
								value={password}
								onChange={(e) => setPassword(e.target.value)}
								placeholder="Passwort"
							/>
							{passwordError && (
							<p
								className="error-message"
								style={{
									color: "#D8000C",
									backgroundColor: "#FFBABA",
									padding: "5px 10px",
									borderRadius: "4px",
									marginTop: "5px",
									fontSize: "0.9rem"
								}}>
								{passwordError}
							</p>
							)}
							<button
							className="confirm-btn"
							onClick={async () => {
								try {
									const userEmail = globalState.user?.email;
									await rpc.login(userEmail, password);

									console.log("Password correct!");
									setPasswordError(null);

									// to trigger real account deletion
									handleDeleteAccount();

								} catch (error: any) {

									setPasswordError(error.message);
								}
							}}
							>
							Bestätigen
							</button>
						</div>
					)}
				</div>
			</div>
		)}
		*/}
	</div>
};

// Logo & Title Component (Top Left)
export const LogoTitle = ({ onClick }) => {
	return (
		<div className="logo-title flexh" onClick={onClick}>
			<h1>
				<i>
					{globalState.customizeInfos.logo &&
						<img alt="Logo"
							src={globalState.customizeInfos.logoImage} />}
					{globalState.customizeInfos.title}
				</i>
			</h1>
		</div>
	);
};

