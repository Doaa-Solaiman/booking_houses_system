import React from "react";
import { globalState, rpc } from "./index";
import { simpleState } from "./utils/utils";
import { GuestForm } from "./GuestForm";
import { calculatePriceForRoomtypePNPP } from "./Presentation";
import { MuiIcon, Text, TextArea, Date as DateInput, Button, Number as NumberInput, Alert } from "./components/formelements";

export type BookingDetailsType = {
	startDate: string;
	endDate: string;
	guestsByRoomType: { [roomTypeId: string]: number };
	selectedRoomTypes: BookingRoomType[];
	totalPrice: number;
};

interface BookingFormProps {
	site: any;
	siteId: number;
	rooms: any[];
	roomType: any;
	roomTypes: any[];
	numberOfSelectedRooms: { [roomTypeId: string]: number };
	numberOfGuestsByRoomType: { [roomTypeId: string]: number };
	onClose: () => void;

	onRequestBooking: (details: BookingDetailsType) => void;
	isRoomType?: boolean;
}

export const BookingForm: React.FC<BookingSidebarProps> = ({
	site,
	siteId,
	rooms,
	roomType,
	roomTypes,
	onRequestBooking,
	numberOfSelectedRooms,
	numberOfGuestsByRoomType,
	onClose,
	onSuccessSubmit,
	isRoomType
}) => {
	React.useEffect(() => {
		if (roomType) console.log("The BookingForm component received roomType:", roomType);
		else console.warn("the BookingForm received no roomType");
	}, [roomType]);

	let [s,sc,sx] = simpleState({
		startDate: "",
		endDate: "",
		startError: "",
		endError: ""
	})
	const [pricePerNight, setPricePerNight] = React.useState<number>(roomType?.price || 0);
	const [priceRules, setPriceRules] = React.useState<any[]>([]);
	const [totalCleanService, setTotalCleanService] = React.useState(0);
	const [totalPrice, setTotalPrice] = React.useState<number>(0);
	const [startError, setStartError] = React.useState<string>("");
	const [endError, setEndError] = React.useState<string>("");
	const [guestError, setGuestError] = React.useState<string>("");
	const [maxGuestCount, setMaxGuestCount] = React.useState(0);
	const [guestCount, setGuestCount] = React.useState(1);
	const [loadingSubmit,setLoadingSubmit] = React.useState(false);
	const [alerts,setAlerts] = React.useState([]);
	const [isGuestFormValid, setIsGuestFormValid] = React.useState(false);
	//const [showGuestPrompt, setShowGuestPrompt] = React.useState(false);
	const isLoggedIn = !!globalState.user;
	const guestFormRef = React.useRef();

	const resetBookingInputs = () => {
		s.startDate = "";
		s.EndDate = "";
		//setNumberOfAdult(1);
		//setNumberOfChildren(0);
		//setExtraAdult(0);
		s.startError = "";
		s.EndError = "";
		setTotalPrice(0);
	};

	React.useEffect(() => {
		if (roomTypes) {
			let selectedRts = roomTypes?.filter(rt => Object.keys(numberOfGuestsByRoomType).includes(rt.id)) || null;
			console.log("numberOfSelectedRooms: ",numberOfSelectedRooms);
			let maxAdults = selectedRts.reduce((total,rt) => {
				let roomCount = numberOfSelectedRooms[rt.id];
				let adultCount = roomCount ? rt.adults * roomCount : rt.adults;
				return total + adultCount;
			},0);
			if (maxAdults) setMaxGuestCount(maxAdults);
		}
	},[])
	// Initialize default state for single-roomtype view
	React.useEffect(() => {
		if (isRoomType && roomType && Object.keys(numberOfSelectedRooms).length == 0) {
			numberOfSelectedRooms[roomType.id] = 1;
			numberOfGuestsByRoomType[roomType.id] = 1;
		}
	}, [isRoomType, roomType]);

	React.useEffect(() => {
		const fetchPricingRule = async () => {
			const pricingRules = await rpc.loadPricingRule(siteId);
			const activeRules = pricingRules.filter(rule => rule.active);
			setPriceRules(activeRules);

			const ctx = globalState.bookingContext;
			if (ctx && ctx.siteId == siteId) {
				sc({ startDate: ctx.startDate, endDate: ctx.endDate })
				//setNumberOfAdult(ctx.numberOfAdult);
				//validateGuestCapacity(ctx.guests, numberOfChildren);
				globalState.bookingContext = null;
			}
		};
		fetchPricingRule();
	}, [siteId]);

	// to update the totalPrice automatically whenever inputs change
	React.useEffect(() => {
		setTotalPrice(calculateTotalPrice());
		setTotalCleanService(calculateTotalCleanService());
	}, [s.startDate, s.endDate, numberOfSelectedRooms, roomTypes, priceRules]);

	const isPastDate = (dateStr: string): boolean => {
		const today = new Date();
		today.setHours(0, 0, 0, 0);
		const date = new Date(dateStr);
		return date < today;
	};
	const sameDay = (a: Date, b: Date) => a.getTime() == b.getTime();
	const validateStartDate = (dateStr: string) => {
		let start = new Date(dateStr);
		let end = s.endDate && new Date(s.endDate);

		if (isPastDate(dateStr))
			return "Startdatum darf nicht in der Vergangenheit liegen!";
		if (end && start > end)
			return "Startdatum darf nicht nach dem Enddatum liegen!";
		if (end && sameDay(start, end))
			return "Start und Enddatum dürfen nicht gleich sein!";

		return "";
	};
	const validateEndDate = (dateStr: string) => {
		let end = new Date(dateStr);
		let start = s.startDate && new Date(s.startDate);

		if (isPastDate(dateStr))
			return "Enddatum darf nicht in der Vergangenheit liegen!";
		if (start && end < start)
			return "Enddatum darf nicht vor dem Startdatum liegen!";
		if (start && sameDay(start, end))
			return "Start und Enddatum dürfen nicht gleich sein!";
		if (start && calculateNights(start, dateStr) < 3)
			return "Der Mindestaufenthalt beträgt 3 Nächte.";

		return "";
	};
	const validateStartAndEndDate = (start: string, end: string) =>
		sc({
			startError: validateStartDate(start),
			endError: validateEndDate(end),
		});

	const validateGuests = (value: number) => {
		// const allowExtraAdult = roomType.allow_extra_guests ?? false;
		if (value < 1) {
			setGuestError("Bitte mindestens 1 Gast eingeben."); return;
		}
		if (value > maxGuestCount /*&& !allowExtraAdult*/) {
			setGuestError(`Maximale Kapazität überschritten.`); return;
		}
		setGuestCount(value); setGuestError("");
	};
	const calculateNights = (start: string, end: string): number => {
		const startDate = new Date(start);
		const endDate = new Date(end);
		return Math.ceil((endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24));
	};
	// the helper function to calculate the price of the chosen roomtype elements
	const calculateTotalPrice = (): number => {
		if (!s.startDate || !s.endDate) return 0;

		const nights = calculateNights(s.startDate, s.endDate);
		if (nights <= 0) return 0;

		let total = 0;

		Object.entries(numberOfSelectedRooms).forEach(([roomTypeId, count]) => {
			if (count > 0) {
				const roomtype = roomTypes.find((t: any) => t.id == roomTypeId);
				if (roomtype) {
					const guests = numberOfGuestsByRoomType[roomTypeId] ?? 1;
					const pricePerNight = calculatePriceForRoomtypePNPP(roomtype,null,s.startDate,priceRules,guests);
					total += (pricePerNight * count * nights);
				}
			}
		});

		return total;
	};
	const calculateTotalCleanService = (): number => {
		let total = 0;
		Object.entries(numberOfSelectedRooms).forEach(([roomTypeId, count]) => {
			if (count > 0) {
				const roomtype = roomTypes.find((t: any) => t.id == roomTypeId);
				if (!roomtype) return;

				const cleanService = Number(roomtype.cleanService) || 0;
				total += cleanService * count;
			}
		});

		return total;
	};

	// the array to collect the chosen roomtype elements and send them into GuestForm
	const selectedRoomTypes = Object.entries(numberOfSelectedRooms)
		.filter(([element, count]) => count > 0)
		.map(([roomTypeId, count]) => {
			const roomtype = roomTypes.find((t: any) => t.id == roomTypeId);
			if (!roomtype) return null;

			const guests = roomtype.adults || 1; // numberOfGuestsByRoomType[roomTypeId] ?? 1;
			const pricePerNight = calculatePriceForRoomtypePNPP(roomtype,null,s.startDate,priceRules,guests);

			return {
				roomType_id: roomtype.id,
				name: roomtype.name,
				count,
				guests,
				pricePerNight,
			};
		})
		.filter(Boolean); // remove the null
	const roomTypes0 = selectedRoomTypes.map(rt => ({
		roomType_id: rt.roomType_id, guests: rt.guests, count: rt.count,
		name: rt.name, pricePerNight: rt.pricePerNight,
	}))
	const validateBookingInputs = (): { valid: boolean; message?: string } => {
		if (!s.startDate || !s.endDate) {
			return { valid: false, message: "Bitte füllen Sie Start- und Enddatum aus." };
		}
		if (s.startError || s.endError) {
			return { valid: false, message: "Bitte korrigieren Sie die Fehler in den Datumsfeldern." };
		}
		for (const [roomTypeId, guests] of Object.entries(numberOfGuestsByRoomType)) {
			const roomtype = roomTypes.find((t: any) => t.id == roomTypeId);
			if (roomtype) {
				const maxAdults = roomtype.adults ?? 1;
				const allowExtraAdult = roomtype.allow_extra_guests ?? false;

				if (guests < 1) {
					return { valid: false, message: "Bitte mindestens 1 Gast eingeben." };
				}
				if (guests > maxAdults && !allowExtraAdult) {
					return { valid: false, message: `Maximale Anzahl der Erwachsenen für ${roomtype.name}: ${maxAdults}` };
				}
			}
		}
		return { valid: true };
	};

	const isSubmitDisabled = React.useMemo(() => {
		return !isGuestFormValid || !!guestError || (!!s.startError || !!s.endError) || (!s.startDate || !s.endDate);
	},[isGuestFormValid, guestError, s.startError, s.endError, s.startDate, s.endDate]);

	const handleSubmit = async () => {
		console.log("Booking submitted:", { pricePerNight, priceType:"perNight", roomTypes:selectedRoomTypes });
		setLoadingSubmit(true);
		if (!siteId) {
			setAlerts(prev => [...prev, { id: "missingId", text: "site ID is missing" }]);
			return;
		}
		if (!s.startDate || !s.endDate) {
			setAlerts(prev => [...prev, { id: "missingDate", text: "Bitte wählen Sie ein gültiges Start- und Enddatum." }]);
			return;
		}
		let guestData = guestFormRef.current.getFormData();
		const {
			firstName,
			lastName,
			email,
			confirmEmail,
			address,
			city,
			zipcode,
			region,
			phoneNumber,
			additionalWishes,
		} = guestData;
		const timeNow = new Date();

		let roomTypes0 = selectedRoomTypes.map(rt => ({
			roomType_id: rt.roomType_id, guests: rt.guests, count: rt.count,
			name: rt.name, pricePerNight: rt.pricePerNight,
		}))

		const bookingData: types.BookingData = {
			site_id: siteId,
			siteName: site?.name || "",
			startDate: s.startDate,
			endDate: s.endDate,
			firstName,
			lastName,
			email,
			confirmEmail,
			address,
			city,
			zipcode,
			region,
			telephone: phoneNumber,
			additionalWishes,
			totalGuests: guestCount,
			price: pricePerNight, // pro nicht
			priceType: "perNight", // total or pro night
			totalPrice: (totalPrice + totalCleanService).toFixed(2), // the total paid amount
			dateSent: timeNow,
			status: "ausstehend",
			statusDecisionTime: new Date(),
		};
		if (selectedRoomTypes.length>1)
			bookingData["roomTypes"] = roomTypes0 as types.BookingRoomType[]; // multiple roomtype bookings
		else bookingData["roomType_id"] = roomType.id; // single roomtype booking

		try {
			console.log("bookingData:", bookingData);
			await rpc.guestRequest(bookingData); // saveBookingData + send email to guest and host
			setLoadingSubmit(false);
			resetBookingInputs();
			onSuccessSubmit("Vielen Dank für Ihre Anfrage. Wir werden sie bald bearbeiten.");
		} catch (error: any) {
			console.error("Booking request failed:", error);
			setLoadingSubmit(false);
			const errMsg = {
				id: "submitError",
				text: "Fehler beim Senden der Buchung: " + (error?.message || JSON.stringify(error) || "Unbekannter Fehler")
			}
			setAlerts(prev => [...prev, errMsg]);
		}
	};

	const totalNights = calculateNights(s.startDate, s.endDate);

	return <div className="dialog-content flexv gap-md">
		<div className="close-button dialog-btn">
			<button onClick={onClose}><MuiIcon name="close" /></button>
		</div>
		<div className="px-md">
			<div className="form">
				<DateInput label="Anreise:" name="startDate"
					min={new Date().toISOString().split("T")[0]}
					value={s.startDate}
					onChange={(n,v) => { s.startDate = v; validateStartAndEndDate(v,s.endDate); }}
					required error={s.startError}
				/>
				<DateInput label="Abreise:" name="endDate"
					value={s.endDate}
					onChange={(n,v) => { s.endDate = v; validateStartAndEndDate(s.startDate,v); }}
					required error={s.endError}
				/>
				<NumberInput
					name="numberOfGuestsByRoomType"
					label="Anzahl der Gäste:"
					value={guestCount}
					onChange={(n,v) => validateGuests(Number(v))}
					onBlur={(e) => validateGuests(Number(e.target.value))}
					error={guestError}
				/>
			</div>
			<div className="form-group info flexv gap-sm">
				<div><b>{`
					Preis für ${(totalNights && totalNights>0)||1} ${totalNights&&totalNights>1 ? "Nächte " : "Nacht "}
					für ${guestCount} ${guestCount>1 ? "Personen": "Person"}:
				`}</b></div>
				<ul style={{ listStyle: "none", paddingLeft: "0", margin: "0" }}>
					{Object.entries(numberOfSelectedRooms).map(([roomTypeId, count]) => {
						if (count > 0) {
							const roomtype = roomTypes.find((t: any) => t.id == roomTypeId);
							if (!roomtype) return null;
							const pricePerNight = calculatePriceForRoomtypePNPP(
								roomtype,
								null,
								s.startDate,
								priceRules,
								numberOfGuestsByRoomType[roomTypeId] ?? 1
							);
							const cleanService = Number(roomtype.cleanService) || 0;

							return <li
								key={roomTypeId}
								style={{
									marginBottom: "6px", fontSize: "14px", color: "#333",
								}}>
									<div className="flexh between">
										<div>{`${count} × ${roomtype.name}: `}</div>
										<div><b>{`${(totalNights && totalNights>0) ?
											(pricePerNight * totalNights * count).toFixed(2) : (pricePerNight)} €`}</b></div>
									</div>
									{cleanService > 0 && (
										<div style={{ marginLeft: "10px", marginTop: "10px", fontSize: "13px", color: "#989898" }}
											className="flexh between"
										>
											<div>Reinigungsgebühr:</div>
											<div><b>+ {(cleanService * count).toFixed(2)} €</b></div>
										</div>
									)}
							</li>
						}
						return null;
					})}
				</ul>
				{totalPrice > 0 && <>
					<hr/>
					<div className="flexh between">
						<div><b>Gesamtpreis:</b></div>
						<div><b className="text-primary">{(totalPrice + totalCleanService).toFixed(2)} €</b></div>
					</div>
				</>}
			</div>
			<div>
				<GuestForm
					ref={guestFormRef}
					relayValidity={isValid => setIsGuestFormValid(isValid)}
				/>
			</div>
		</div>
		<div className="flexv">{alerts.map(a => <Alert key={a.id} open={a.text} text={a.text} backgroundColor="#fff5f5" textColor="#e53e3e"
			onClose={() => { setAlerts(prev => [...prev.filter(x => x.id != a.id)] ); }} />)}
		</div>
		<div className="modal-buttons flexh centerh pd-md">
			{/*<Button label="Zurück" className="back" onClick={onBack} /> */}
			<Button label="Buchungsanfrage abschicken"
				onClick={handleSubmit} style={{ flex: .5 }}
				title={isSubmitDisabled ? "Bitte alle Felder korrekt ausfüllen" : undefined}
				loading={loadingSubmit} disabled={isSubmitDisabled}
			/>
		</div>
	</div>
}
