import React, { useState } from "react";
import * as types from "./types";
import { rpc, globalState } from "./index";
import { createValidator, validator, useFormValidation } from "./utils/utils";
import { BookingDetailsType } from "./BookingForm";
import Style from "./utils/Style";
import { MuiIcon, Text, TextArea, Number as NumberInput, Button } from "./components/formelements";
import {
	generatePhone, normalizeSpelling, randomItem, randomInt,
	FIRST_NAMES, LAST_NAMES, LOCATIONS, WISHES
} from "./utils/mockdata.ts";

export type BookingDetailsExtendedType = BookingDetailsType & {
	roomType_id: string | null,
	roomTypes: { roomType_id: string; name: string; count: number; pricePerNight: number; guests?: number; }[],
}

export const GuestForm = React.forwardRef((props, ref) => {
	const { isDeveloper } = globalState;
	// console.log("globalState: ",globalState);
	const val = validator;
	const userFormData = {
		firstName: "",
		lastName: "",
		email: "",
		confirmEmail: "",
		address: "",
		city: "",
		zipcode: "",
		region: "",
		phoneNumber: "",
		additionalWishes: "",
	}
	const schema = {
		firstName: [
			val.string().required("Bitte geben Sie einen Vornamen ein"),
			val.string().min(2,"Mindestens 2 Zeichen"),
		],
		lastName: [ val.string().required("Bitte geben Sie einen Nachnamen ein") ],
		email: [
			val.string().required("E-Mail ist erforderlich"),
			val.string().email("Ungültige E-Mail-Adresse"),
		],
		confirmEmail: [
			val.string().required("Bitte bestätigen"),
			val.string().email("Ungültige E-Mail-Adresse"),
		],
		phoneNumber: [val.string().required("Telefonnummer ist erforderlich")],
		address: [val.string().required("Adresse ist erforderlich")],
		city: [val.string().required("Stadt ist erforderlich")]
	};
	const { validateField, validateAll } = createValidator({
		schema,
		cross: (data) => {
			const out = {};
			if (data.email && data.confirmEmail && data.email != data.confirmEmail) {
				out.email = "E-mail stimmen nicht überein";
				out.confirmEmail = "E-mail stimmen nicht überein";
			}
			return out;
		},
	});
	let { formData, setFormData, resetForm, errors, handleChange, isFormValid } = useFormValidation(
		userFormData, validateField, validateAll
	);

	React.useEffect(() => {
		props.relayValidity(isFormValid);
	},[isFormValid]);

	React.useImperativeHandle(ref, () => ({
		getFormData() {
			return formData;
		}
	}));

	const fillMockData = () => {
		const firstName = randomItem(FIRST_NAMES);
		const lastName = randomItem(LAST_NAMES);
		const loc = randomItem(LOCATIONS);
		const plz = randomItem(loc.plz);
		const street = randomItem(loc.streets);
		const region = "Deutschland";
		const streetNum = randomInt(1,200);
		const emailLocal = `${normalizeSpelling(firstName)}.${normalizeSpelling(lastName)}${randomInt(1,99)}`;
		const email = `${emailLocal}@example.com`;

		setFormData({
			firstName,
			lastName,
			email,
			confirmEmail: email,
			address: `${street} ${streetNum}`,
			city: loc.city,
			zipcode: plz,
			region,
			phoneNumber: generatePhone(loc.city),
			additionalWishes: randomItem(WISHES),
		});
	};

	return <div className="form">
		<div className="mb-md">Bitte füllen Sie dieses Formular aus, um Ihre Buchungsanfrage fortzusetzen</div>
		<div className="flexv gap-md">
			<Text name="firstName" label="Vorname" value={formData.firstName} onChange={handleChange} required error={errors.firstName} />
			<Text name="lastName" label="Nachname" value={formData.lastName} onChange={handleChange} required error={errors.lastName} />
			<Text name="email" label="Email" value={formData.email} onChange={handleChange} required error={errors.email} />
			<Text name="confirmEmail" label="Email bestätigen" value={formData.confirmEmail} onChange={handleChange} required error={errors.confirmEmail} />
			<Text name="address" label="Adresse" value={formData.address} onChange={handleChange} required error={errors.address} />
			<Text name="city" label="Stadt" value={formData.city} onChange={handleChange} required error={errors.city} />
			<Text name="zipcode" label="Postleitzahl" value={formData.zipcode} onChange={handleChange} />
			<Text name="region" label="Land/Region" value={formData.region} onChange={handleChange} />
			<Text name="phoneNumber" label="Telefonnummer" value={formData.phoneNumber} onChange={handleChange} required error={errors.phoneNumber} />
			<TextArea name="additionalWishes" label="Zusätzliche Wünsche" value={formData.additionalWishes} onChange={handleChange} />
		</div>
		{
			isDeveloper && <Button type="button" label="Random fill" onClick={fillMockData} />
		}
	</div>
});
