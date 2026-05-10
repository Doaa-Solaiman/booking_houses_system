import React from "react";
import { Select, Text } from "./components/formelements";

export function Amenities({ room, amenities, setAmenities }) {
	const [otherAmenity, setOtherAmenity] = React.useState("");

	const handleAmenityChange = (v) => {
		if (v == "Others") {
			if (!amenities.includes("Others")) {
				setAmenities([...amenities, "Others"]);
			}
		} else if (v && !amenities.includes(v)) {
			setAmenities([...amenities, v]);
		}
		v = ""; // Reset select value after selection
	};
	const handleRemoveAmenity = (amenity: string) => {
		setAmenities(amenities.filter((item) => item != amenity));
	};
	const handleAddOtherAmenity = () => {
		if (otherAmenity) {
			setAmenities([...amenities.filter((item) => item != "Others"), otherAmenity]);
			setOtherAmenity("");
		}
	};

	const isDisabled = !room;

	return <>
		<Select name="amenities" label="Ausstattung"
			onChange={(n,v) => handleAmenityChange(v)}
			disabled={isDisabled}
			items={Amenities_list.map((amenity, index) => ({ text: amenity, value: amenity }))
			}
		/>
		{amenities.length > 0 && (
			<div className="form-group flexv gap-sm">
				<label>Ausgewählte Ausstattung:</label>
				<div className="flexh wrap gap-sm">
					{amenities.map((amenity, index) => (
						<span key={index} className="flexh centerv" style={{
							backgroundColor: "#f0f0f0",
							height: "25px",
							padding: "6px",
							borderRadius: "16px",
							fontSize: "13px",
						}}>
							{amenity}
						<button
							type="button"
							onClick={() => handleRemoveAmenity(amenity)}
							style={{
								marginLeft: "6px",
								backgroundColor: "transparent",
								border: "none",
								color: "red",
								width: "20px",
								fontSize: "14px",
								alignItems: "center",
								justifyContent: "center"
							}}
						>
							x
						</button>
						</span>
					))}
				</div>
			</div>
		)}
		{amenities.includes("Others") && <>
			<div className="form-group flexv gap-md">
				<Text
					name="otherAmenities" label="Andere Austattung:"
					value={otherAmenity} onChange={(n,v) => setOtherAmenity(v)}
					placeholder="Bitte spezifizieren"
					disabled={isDisabled}
				/>
				<button type="button" onClick={handleAddOtherAmenity} disabled={isDisabled}
					style={{ backgroundColor:"orange"}}
				>
					Ausstattung hinzufügen
				</button>
			</div>
		</>}
	</>
}

// List of predefined amenities (Ausstattungen)
const Amenities_list = [
	"gemeinsame Toilette",
	"private Toilette",
	"Essen inklusive",
	"Kaffeemaschine",
	"Küche",
	"Mikrowelle",
	"Spülmaschine",
	"Waschmaschine",
	"Trockner",
	"Bügeleisen",
	"Tier erlaubt",
	"Rauchen erlaubt",
	"Keller",
	"Herd",
	"TV",
	"Balkon",
	"Parkplatz",
	"Schwimmbad",
	"Wasserkocher",
	"großer Kühlschrank",
	"kleiner Kühlschrank",
	"Handtücher",
	"Bettwäsche",
	"Decken",
	"Kissen",
	"Klimaanlage",
	"Heizung",
	"Duschkabine",
	"Badewanne",
	"Geschirr & Besteck",
	"Garten",
	"WIFI",
	"antiquitäten",
	"Others"
];
