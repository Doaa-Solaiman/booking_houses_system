import React from "react";
import { rpc, globalState } from "./index";
import { simpleState, createValidator, validator, useFormValidation } from "./utils/utils";
import Style from "./components/Style";
import { LoadingIcon, Button, Text, Select, Password, Checkbox } from "./components/formelements";

export function Login(props) {
	let [s,sc,sx] = simpleState({
		stayLoggedIn: !!localStorage.getItem("FEWOSID"),
		errorMessage: "",
		successState: "",
		loading: false,
		forgotPasswordMode: false
	});
	const loginData = { email: "", password: "", }
	const schema = {
		email: [
			validator.string().required("Bitte geben Sie Ihre E-Mail-Adresse ein"),
			validator.string().email("Ungültige E-Mail-Adresse"),
		],
		password: [validator.string().required("Passwort ist erforderlich")]
	};
	const { validateField, validateAll } = createValidator({
		schema,
		cross: () => {}
	});
	const { formData, resetForm, errors, handleChange, checkSubmit, isFormValid } = useFormValidation(
		loginData,
		validateField,
		validateAll
	);

	const handleForgotPassword = async () => {
		if (!formData.email.trim()) {
			sc({errorMessage: "Bitte geben Sie Ihre E-Mail-Adresse ein."});
			return;
		}
		sc({errorMessage: ""});

		try {
			const response = await rpc.forgotPassword(formData.email);
			console.log("Forgot password response:", response);
			if (response.success) {
				sc({successState: "Ein Link zum Zurücksetzen des Passworts wurde gesendet."});
			} else {
				sc({errorMessage: "Diese E-Mail-Adresse ist nicht registriert."});
			}
		} catch (error) {
			sc({errorMessage: "Wir arbeiten noch daran"});
		}
	};

	const login = async () => {
		sc({ loading: true, errorMessage: "" });
		if (!isFormValid) {
			sc({errorMessage: "Bitte füllen Sie beide Felder aus"});
			return;
		}
		try {
			const response = await rpc.login(formData.email, formData.password);
			if (!response) {
				sc({errorMessage: "Ungültige E-Mail oder ungültiges Passwort"});
				return;
			}
			let [sessionId, orgId, isHost] = response;
			if (s.stayLoggedIn)
				localStorage.setItem("FEWOSID", sessionId);
			else
				sessionStorage.setItem("FEWOSID", sessionId);

			globalState.loggedIn = orgId;
			globalState.isHost = isHost;
			globalState.user = await rpc.getLoggedInUser();
			sc({ successState: "Anmelden erfolgreich!", loading: false })

			setTimeout(() => {
				if (globalState.bookingContext) {
					globalState.bookingContext = null;
				}
				globalState.navigate("#");
				globalState.refreshPage();
				s.successState = "";
			});
		} catch (error) {
			sc({ loading: false, errorMessage: error.message });
		}
	}
//	const handleChange = (name,value) => sc({[name]: value})
//	const errors = {}

	return (<div className="flexh centerh pd-lg">
		<Style>{`
			& .success-state {
				background-color: yellowgreen;
				color: white;
				padding: 10px;
				margin-bottom: 10px;
				border-radius: 5px;
			}
			& .no-acc { display: flex; }
			& .no-acc > span > button {
				background-color: white;
				border: none;
				padding: 0;
				margin: 0;
				color: black;
				text-decoration: underline;
				cursor: pointer;
				fontSize: inherit;
				fontFamily: inherit;
			}
		`}</Style>
		<div className="form login flexv">
			<div class="form-heading">
				{s.forgotPasswordMode
					? "Bitte geben Sie die E-Mail-Adresse ein, mit der Sie sich registriert haben"
					: "Bitte geben Sie Ihre Anmeldedaten ein."}
			</div>
			<form onSubmit={e => { e.preventDefault(); login(); }}>
				<Text name="email" placeholder="E-Mail-Adresse"
					value={formData.email} onChange={handleChange} error={errors.email} />

				{!s.forgotPasswordMode && <>
					<Password name="password" placeholder="Passwort"
						value={formData.password} onChange={handleChange} error={errors.password} />
					<Checkbox name="stayLoggedIn" label="Angemeldet bleiben"
						value={s.stayLoggedIn} onChange={(n,v) => sc({ [n]: v })} />
				</>}
				{s.errorMessage && <div className="error-msg">{s.errorMessage}</div>}
				{s.successState && <div className="success-state">{s.successState}</div>}
				{s.forgotPasswordMode
					? <Button type="button" label="Fortfahren" onClick={handleForgotPassword} />
					: <Button type="submit" label="Anmelden" loading={s.loading} disabled={!isFormValid} />}
			</form>
			{/*{!forgotPasswordMode && (
				<div style={{ marginTop: "0.5rem" }}>
					<Button
						label="Passwort vergessen?"
						onClick={() => setForgotPasswordMode(true)}
						style={{
							background: "none",
							border: "none",
							color: "black",
							textDecoration: "underline",
							cursor: "pointer",
							padding: 0,
							fontSize: "inherit",
							fontFamily: "inherit"
						}}
					/>
				</div>
			)}*/}
			{!s.forgotPasswordMode && <div className="no-acc">
				<span>
					Sie haben noch kein Konto?&nbsp;
					<button
						// onClick={() => globalState.navigate("#RegisterAsGuest")}
						onClick={() => globalState.navigate("#RegisterAsHost")}
					>
						ein Konto erstellen
					</button>
				</span>
			</div>}
		</div>
	</div>);
}
