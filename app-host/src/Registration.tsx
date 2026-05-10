import React from 'react';
import { rpc, globalState } from "./index";
import { createValidator, validator, useFormValidation } from "./utils/utils";
import Style from "./components/Style";
import { LoadingIcon, Button, Text, Select, Password } from "./components/formelements";

export function RegistrationForm({ asHost, setPage, showBackButton }) {

	const newUser = asHost ?
		{
			firstName: '',
			lastName: '',
			gender: '',
			email: '',
			password: '',
			passwordConfirm: '',
			orgName: '',
			address: '',
			city: '',
			zipCode: '',
			country: '',
		} : {
			firstName: '',
			lastName: '',
			gender: '',
			email: '',
			password: '',
			passwordConfirm: '',
		}
	const [success, setSuccess] = React.useState(true);
	const [loadingSubmit,setLoadingSubmit] = React.useState(false);

	React.useEffect(() => {
		resetForm(newUser);
		setSuccess(false);
	}, [asHost]);

	const val = validator;
	const schema = {
		firstName: [
			val.string().required("Bitte geben Sie einen Vornamen ein"),
			val.string().min(2,"Mindestens 2 Zeichen"),
		],
		lastName: [
			val.string().required("Bitte geben Sie einen Nachnamen ein"),
		],
		orgName: [
			val.string().required("Bitte geben Sie den Anbieternamen ein"),
		],
		email: [
			val.string().required("E-Mail ist erforderlich"),
			val.string().email("Ungültige E-Mail-Adresse"),
		],
		password: [
			val.string().required("Passwort ist erforderlich"),
			val.string().min(6,"Mindestens 6 Zeichen"),
		],
		passwordConfirm: [
			val.string().required("Bitte bestätigen"),
		],
	};
	const { validateField, validateAll } = createValidator({
		schema,
		cross: (data) => {
			const out = {};
			if (data.password && data.passwordConfirm && data.password != data.passwordConfirm) {
				out.password = "Passwörter stimmen nicht überein";
				out.passwordConfirm = "Passwörter stimmen nicht überein";
			}
			return out;
		},
	});
	const { formData, resetForm, errors, handleChange, checkSubmit, isFormValid } = useFormValidation(
		newUser,
		validateField,
		validateAll
	);

	const handleSubmit = async (event) => {
		event.preventDefault();
		let check = checkSubmit();
		if (!check) {
			console.error("error: ",errors);
			return;
		}
		try {
			setLoadingSubmit(true);
			await rpc.register(formData);
			setSuccess(true); setLoadingSubmit(false);
		} catch (err) {
			console.error("Registration failed:", err);
			setLoadingSubmit(false);
		}
	};

	if (success) {
		return <div className="form register flexv">
			<Style>{`
				& .success-msg {
					background-color: #fff3cd;
					color: green;
					border: 1px solid #ffeeba;
					padding: 1rem;
					border-radius: 5px;
					margin-bottom: 1rem;
					text-align: center;
				}
			`}</Style>
			<div className="form-heading">{asHost ? "Registrieren als Anbieter" : "Registrieren"}</div>
			<div className="success-msg">
				Registrierung erfolgreich!
				Schließen Sie bitte die Registrierung mit dem Link in der Mail ab,
				die wir Ihnen gesendet haben.
			</div>
			<div className="flexv gap-sm">
				<button onClick={() => {
					if (globalState.bookingContext) {
						const ctx = globalState.bookingContext;
						globalState.bookingContext = null;
						globalState.navigate("#");
						globalState.refreshPage();
					} else {
						globalState.navigate("#");
					}
				}}>Zurück zur Startseite</button>
				<button onClick={() => { setSuccess(false); resetForm(newUser); }}>Weitere Registrierung</button>
			</div>
		</div>
	}
	return <div>
		<Style>{`
			& { display: flex; justify-content: center; padding: 32px; }
			& .back-btn { background: none; width: 50%; color: #007bff; text-decoration: underline; padding: 0; }
		`}</Style>
		<form className="form register flexv" onSubmit={e => handleSubmit(e) }>
			<div className="form-heading">{asHost ? "Registrieren als Anbieter" : "Registrieren Sie sich, um eine Unterkunft zu buchen!"}</div>
			<Select name="gender" label="Anrede" placeholder="Bitte auswählen"
				value={formData.gender} onChange={handleChange}
				items={[ { text: "Herr", value: "male" }, { text: "Frau", value: "female" }, { text: "keine", value: "other" } ]}
			/>
			<Text name="firstName" label="Vorname" value={formData.firstName} onChange={handleChange} required error={errors.firstName} />
			<Text name="lastName" label="Nachname" value={formData.lastName} onChange={handleChange} required error={errors.lastName} />
			<Text name="email" label="E-Mail" value={formData.email} onChange={handleChange} error={errors.email} />
			<Password name="password" label="Passwort" value={formData.password} onChange={handleChange} required error={errors.password} />
			<Password name="passwordConfirm" label="Passwort bestätigen" value={formData.passwordConfirm} onChange={handleChange} required error={errors.passwordConfirm} />

			{asHost && <>
				<hr />
				<Text name="orgName" label="Vermieter" value={formData.orgName} onChange={handleChange} required error={errors.orgName} />
				<Text name="address" label="Straße und Hausnummer" value={formData.address} onChange={handleChange} />
				<Text name="city" label="Ort" value={formData.city} onChange={handleChange} />
				<Text name="zipCode" label="Postleitzahl" value={formData.zipCode} onChange={handleChange} />
				<Text name="country" label="Land" value={formData.country} onChange={handleChange} />
			</>}
			<div className="flexh centerh gap-md">
				<Button type="submit" className="submit-button" label="Registrieren" loading={loadingSubmit} disabled={!isFormValid} />
				{showBackButton &&  <Button
					className="back-btn" type="button"
					label="Zurück zur Anmelden"
					onClick={() => globalState.navigate("#Login")}
				/>}
			</div>
		</form>
	</div>
}

export function RegisterConfirm(props) {
	const [confirmStatus, setConfirmStatus] = React.useState("pending");
	React.useEffect(() => {
		(async function() {
			try {
				await rpc.registerConfirm(props.confirmToken);
				setConfirmStatus("success");

			} catch (e) {
				console.error("Fehler beim bestätigen der E-Mail: ", e);
				setConfirmStatus("error");
				console.log("Confirm Token:", props.confirmToken);

			}
		})();
	}, [props.confirmToken]);

	return <div className="form register">
		<Style>{`
			& .status { border-radius: 5px; padding: 16px; margin-bottom: 16px; text-align: center; }
			& .success { background-color: #fff3cd; color: green; border: 1px solid #ffeeba; }
			& .error { backgroundColor: #f8d7da; color: #d9534f; border: 1px solid #f5c6cb; }
		`}</Style>
		<h2 className="form-heading">Registrierung bestätigt</h2>
		{confirmStatus == "pending" && <div>Bitte warten, Ihre Registrierung wird bestätigt...</div>}
		{confirmStatus == "success" && <div className="status success">Ihr Konto wurde aktiviert!</div>}
		{confirmStatus == "error" && <div className="status error">Fehler bei der Bestätigung.</div>}
		<Button label="Zurück zur Startseite" onClick={() => globalState.navigate("#")}/ >
		<Button label="Zum Login" onClick={() => globalState.navigate("#Login")} />
	</div>
}
