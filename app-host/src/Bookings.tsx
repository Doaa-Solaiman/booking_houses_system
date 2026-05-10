import React from "react";
import MUI from "@material-ui/core";
import { isBookingOutdated } from "./OutdatedBookings";

const {
	Button, TextField,
	Dialog, DialogTitle, DialogActions,
	DialogContent, DialogContentText,
} = MUI;
import Style from "./components/Style";
import { Table, useTablePipeline } from "./components/Table";
import { Date as DateInput, Text, Select, Checkbox, SelectMany, Slider, MuiIcon } from "./components/formelements";

import { rpc } from "./index";
import { Timeline } from "./Timeline";
import { DetailsViewer } from "./DetailsViewer";
import { RoomType } from "types";
//import { PendingRequests } from "./PendingRequests";
import { Calendar } from "./Calendar";

export function Bookings(props) {
	const [allBookings, setAllBookings] = React.useState<BookingData[]>([]);
	const [showDetails, setShowDetails] = React.useState(false);
	const [bookingDetail, setBookingDetail] = React.useState(null);
	const [selectedBooking, setSelectedBooking] = React.useState(null);
	const [confirmAction, setConfirmAction] = React.useState<"accept" | "reject" | "rejectSilently" | null>(null);
	const [sites, setSites] = React.useState<Site[]>([]);
	const [roomtypes, setRoomtypes] = React.useState<RoomType[]>([]);
	const [rooms, setRooms] = React.useState<Room[]>([]);
	const [searchTerm, setSearchTerm] = React.useState("");
	const [notification, setNotification] = React.useState<string | null>(null);
	const [notificationStyle, setNotificationStyle] = React.useState<"success" | "error" | null>(null);
	const tableRef = React.useRef<HTMLTableElement>();
	const contentRef = React.useRef<HTMLDivElement>();

	const [timelineStart, setTimelineStart] = React.useState<Date>(() => {
		const now = new Date();
		return new Date(now.getFullYear(), now.getMonth(), 1);
	});
	const [timelineEnd, setTimelineEnd] = React.useState<Date>(() => {
		const now = new Date();
		return new Date(now.getFullYear(), now.getMonth() + 2, 1);
	});
	const today = new Date();
	const [mode, setMode] = React.useState<"week" | "month" | "custom">("month");
	const [customRange, setCustomRange] = React.useState({ from: "", to: "" });
	const [dateStart, setDateStart] = React.useState<Date | null>(null);
	const [dateEnd, setDateEnd] = React.useState<Date | null>(null);
	const [weekStart, setWeekStart] = React.useState<Date | null>(null);
	const [weekEnd, setWeekEnd] = React.useState<Date | null>(null);
	const defaultMonthStart = new Date(today.getFullYear(), today.getMonth(), 1);
	const defaultMonthEnd = new Date(today.getFullYear(), today.getMonth() + 1, 1);
	const [monthStart, setMonthStart] = React.useState<Date | null>(defaultMonthStart);
	const [monthEnd, setMonthEnd] = React.useState<Date | null>(defaultMonthEnd);

	const [selectedBands, setSelectedBands] = React.useState<string[]>([]);
	const [showOnlyBooked, setShowOnlyBooked] = React.useState(false);
	const [showOnlyNotBooked, setShowOnlyNotBooked] = React.useState(false);
	const [daywidth, setDaywidth] = React.useState(50);
	const [showTable, setShowTable] = React.useState(true);
	const [showTimeline, setShowTimeline] = React.useState(true);

	function highlightMatch(text, query) {
		if (!query) return text;

		//creating a regular expression object from the user’s search input (query).
		const regex = new RegExp(`(${query})`, "gi");
		return text.replace(regex, `<span style="color: blue; font-weight: bold;">$1</span>`);
	}

	const filteredItems = allBookings.filter((i) => {
		const q = searchTerm.toLowerCase();
		return (
			(i.firstName || "").toLowerCase().includes(q) ||
			(i.lastName || "").toLowerCase().includes(q) ||
			(i.siteName || "").toLowerCase().includes(q) ||
			(i.roomTypeName || "").toLowerCase().includes(q) ||
			(i.roomName || "").toLowerCase().includes(q) ||
			(String(i.totalPrice) || "").includes(q)
		);
	});

	const isFilterChanged = () => {
		return (
			/**mode != "custom" */
			customRange.from != "" || customRange.to != "" ||
			weekStart != null || weekEnd != null ||
			monthStart != null || monthEnd != null
		);
	};

	React.useEffect(() => {
		(async () => {
			try {
				const bookingData = await rpc.loadAllBookingData().catch(e => {
					console.error("Fehler beim Laden der Buchungen:", JSON.stringify(e, Object.getOwnPropertyNames(e)));
					return [];
				});
				const sites = await rpc.loadSites().catch(e => {
					console.error("Fehler beim Laden der Sites:", e);
					return [];
				});
				const roomtypes = await rpc.loadRoomTypes().catch(e => {
					console.error("Fehler beim Laden der RoomTypes:", e);
					return [];
				});
				const rooms = await rpc.loadRooms().catch(e => {
					console.error("Fehler beim Laden der Rooms:", e);
					return [];
				});

				setSites(sites);
				setRoomtypes(roomtypes);
				setRooms(rooms);

				const filtered = processLoadedBookingData(bookingData, sites, roomtypes, rooms);
				setAllBookings(filtered || []);
			} catch (error) {
				console.error("Unerwarteter Fehler beim Laden:", error);
			}

		})();
	}, []);

	React.useEffect(() => { // action for a booking request
		if (selectedBooking || confirmAction) {
			document.body.style.overflow = 'hidden';
		} else {
			document.body.style.overflow = 'auto';
		}

		return () => {
			document.body.style.overflow = 'auto';
		};
	}, [selectedBooking, confirmAction]);

	function processLoadedBookingData(bookingData, sites, roomtypes, rooms) {
		if (!bookingData.length) return [];

		return bookingData
			.filter(record =>
				!isBookingOutdated(record) &&
				["ausstehend", "akzeptiert", "storniert"].includes(record.status)
			)
			.map(record => {
				const siteName = sites.find(site => site.id == record.site_id)?.name;

				let roomTypeDisplay = "";
				if (record.roomTypes && Array.isArray(record.roomTypes) && record.roomTypes.length > 0) {
					// it should display something like: 2× Doppelzimmer, 1× Einzelzimmer
					roomTypeDisplay = record.roomTypes
						.map(rtEntry => {
							const rt = roomtypes.find(t => t.id == rtEntry.roomType_id);
							const name = rt?.name || rtEntry.name || "Unbekannt";
							return `${rtEntry.count}× ${name}`;
						})
						.join(", ");
				} else if (record.roomType_id) {
					const singleType = roomtypes.find(rt => rt.id == record.roomType_id);
					roomTypeDisplay = singleType?.name || "";
				}

				const roomName = rooms.find(r => r.id == record.room_id)?.name;

				let totalGuests = 0; let totalPrice = 0;

				if (Array.isArray(record.roomTypes) && record.roomTypes.length > 0) {
					// multiple roomtypes (site view)
					totalGuests = record.roomTypes.reduce((sum, rt) => sum + (rt.guests ?? 0), 0);
//					totalPrice = record.roomTypes.reduce(
//						(sum, rt) => sum + ((rt.pricePerNight ?? 0) * (rt.count ?? 1)),
//						0
//					);
					totalPrice = record.totalPrice;
				} else if (record.roomType_id) {
					// single roomtype booking
					const rt = roomtypes.find(t => t.id === record.roomType_id);
					const guests = record.totalGuests ?? rt?.adults ?? 1;
					const price = record.totalPrice ?? rt?.price ?? 0;

					totalGuests = guests;
					totalPrice = price;
				} else {
					totalGuests = record.totalGuests ?? 0;
					totalPrice = record.totalPrice ?? 0;
				}

				return {
					...record,
					siteName,
					roomTypeName: roomTypeDisplay,
					roomName,
					totalGuests,
					totalPrice,
				};
			});
	}

	const handleConfirm = async (action: "accept" | "reject" | "rejectSilently") => {
		if (!selectedBooking) return;

		try {
			const updatedBooking = {
				...bookingDetail,
				roomTypes: bookingDetail.roomTypes || [{ roomType_id: bookingDetail.roomType_id }],
				status: action == "accept" ? "akzeptiert" : "abgelehnt",
				statusDecisionTime: new Date().valueOf(),
			};

			if (action == "rejectSilently") {
				await rpc.saveBookingData(updatedBooking); // just save, no email
			} else {
				await rpc.requestStatusUpdate(updatedBooking); // save + email
			}

			console.log("RPC call succeeded.");
			const updatedBookings = await rpc.loadAllBookingData();
			const filtered = processLoadedBookingData(updatedBookings, sites, roomtypes, rooms);
			setAllBookings(filtered || []);

			let message = "";
			let style: typeof notificationStyle = null;

			if (action == "accept") {
				message = "Die Buchungsanfrage wurde erfolgreich akzeptiert!";
				style = "accept";
			} else if (action == "reject") {
				message = "Die Buchungsanfrage wurde erfolgreich abgelehnt!";
				style = "reject";
			} else if (action == "rejectSilently") {
				message = "Die Anfrage wurde abgelehnt, ohne dem Kunden eine E-Mail zu senden.";
				style = "rejectSilently";
			}

			setNotification(message);
			setNotificationStyle(style);
			setSelectedBooking(null);
			closeDetails();

		} catch (error) {
			console.error("Error processing booking request:", error);
			setNotification("Fehler beim Bearbeiten der Buchungsanfrage");
			setNotificationStyle("error");
			setSelectedBooking(null);
			closeDetails();
		}

		setTimeout(() => {
			setNotification(null);
			setNotificationStyle(null);
		}, 3000);
	};

	const closeDetails = (apply?) => {
		if (apply) {
			// e.g. save something, do something, whatever etc.
		}
		setShowDetails(false);
	};

	// the resource: https://bugfender.com/blog/javascript-date-and-time/
	// https://www.geeksforgeeks.org/javascript/how-to-get-the-current-weeknumber-of-the-year/
	const handleApplyFilter = () => {
		let start, end;
		if (mode == "custom") {
			if (!dateStart) return;
			start = new Date(dateStart); let initialEnd = new Date(dateEnd);
			if (start > end) [start, end] = [end, start];
			end = initialEnd.setHours(23, 59, 59, 999);
		} else if (mode == "week" && weekStart && weekEnd) {
			//ensure correct order for week inserts
			start = weekStart; let initialEnd = weekEnd;
			if (start > initialEnd) [start, initialEnd] = [initialEnd, start];
			end = new Date(end.getTime() + 6 * 24 * 60 * 60 * 1000);
		} else if (mode == "month" && monthStart && monthEnd) {
			//ensure correct order for month inserts
			start = monthStart; let initialEnd = monthEnd;
			if (start > initialEnd) [start, initialEnd] = [initialEnd, start];
			end = new Date(initialEnd.getFullYear(), initialEnd.getMonth() + 1, 0)
		} else {
			//fallback
			const now = new Date();
			start = new Date(now.getFullYear(), now.getMonth(), 1);
			end = new Date(now.getFullYear(), now.getMonth() + 1, 1);
		}
		setTimelineStart(start); setTimelineEnd(end);
	};
	const resetAppliedFilter = (filter) => {
		if (filter == "date") {
			const today = new Date();
			const defaultMonthStart = new Date(today.getFullYear(), today.getMonth(), 1);
			const defaultMonthEnd = new Date(today.getFullYear(), today.getMonth() + 1, 1);

			setMode("month");
			if (dateStart && dateEnd) {
				setDateStart(null); setDateEnd(null);
			}
			if ((monthStart && monthStart != defaultMonthStart) && (monthEnd && monthEnd != defaultMonthEnd)) {
				setMonthStart(defaultMonthStart); setMonthEnd(defaultMonthEnd);
			}
			if (weekStart && weekEnd) {
				setWeekStart(null); setWeekEnd(null);
			}
			setTimelineStart(defaultMonthStart); setTimelineEnd(defaultMonthEnd);
		}
	}

	let bands = {};
	allBookings.forEach(b => {
		let site = sites.find(s => s.id == b.site_id);
		if (b.roomTypes && b.roomTypes.length > 0) {
			b.roomTypes.forEach(rtEntry => {
				const rt = roomtypes.find(t => t.id == rtEntry.roomType_id);
				const bandId = `${site?.id || ""}/${rt?.id || rtEntry.roomType_id}/`;
				let bandName = site?.shortName || site?.name || "<unbekannt>";
				if (rt) bandName += " / " + (rt.shortName || rt.name);
				bands[bandId] = { id: bandId, name: bandName };
			});
		} else {
			let roomtype = roomtypes.find(t => t.id == b.roomType_id);
			let room = rooms.find(r => r.id == b.room_id);
			let bandId = (site?.id || "") + "/" + (roomtype?.id || "") + "/" + (room?.id || "");
			let bandName = site?.shortName || site?.name || "<unbekannt>";
			if (roomtype)
				bandName += " / " + (roomtype.shortName || roomtype.name);
			if (room)
				bandName += " / " + room.name;
			bands[bandId] = { id: bandId, name: bandName };
		}
	});

	rooms.forEach(r => {
		if (!r.site_id) return;
		let site = sites.find(s => s.id == r.site_id);
		let roomtype = roomtypes.find(t => t.id == r.roomType_id);
		let room = rooms.find(rr => rr.id == r.id);
		let bandId = (site?.id || "") + "/" + (roomtype?.id || "") + "/" + (room?.id || "");
		let bandName = site?.shortName || site?.name || "<unbekannt>";
		if (roomtype)
			bandName += " / " + (roomtype.shortName || roomtype.name);
		if (room)
			bandName += " / " + room.name;
		bands[bandId] = { id: bandId, name: bandName };
	});

	function showBookingDetail(booking) {
		setSelectedBooking(booking);
		setBookingDetail(booking);
		setShowDetails(true);
	}

	const handleSaveChanges = async (updated: Partial<BookingData>) => {
		if (!selectedBooking) return;
		console.log("selectedBooking: ",selectedBooking);
		if (updated._meta) delete updated._meta;
//		if (updated.roomTypes.length) {
//			for (let i=0; i <= updated.roomTypes.length; i++) {
//				let room_ids = updated.roomTypes[i]?.room_ids;
//				if (room_ids)
//					updated.roomTypes[i].room_ids = updated.roomTypes[i].room_ids.toString();
//			}
//		}
		try {
			const merged = {
				...updated,
				roomTypes: Array.isArray(updated.roomTypes)
					? updated.roomTypes
					: selectedBooking.roomTypes || []
			};

			await rpc.saveBookingData(merged);

			const updatedBookings = await rpc.loadAllBookingData();
			const filtered = processLoadedBookingData(updatedBookings, sites, roomtypes, rooms);
			setAllBookings(filtered || []);

			const freshBooking = updatedBookings.find(b => b.id == selectedBooking.id);
			if (freshBooking) {
				setSelectedBooking(freshBooking);
			}

			closeDetails()
			if (updated.status == "storniert") {
				setNotification("Die Buchung wurde erfolgreich storniert");
				setNotificationStyle("cancelAlert");
			} else {
				setNotification("Änderungen gespeichert!");
				setNotificationStyle("success");
			}

			setTimeout(() => {
				setNotification(null);
				setNotificationStyle(null);
			}, 2000);

		} catch (error) {
			console.error("Error saving booking changes:", error);
			setNotification("Fehler beim Speichern der Änderungen");
			setNotificationStyle("error");
			setTimeout(() => {
				setNotification(null);
				setNotificationStyle(null);
			}, 2000);
		}
	};

	const handleRemoveCanceledBooking = async (bookingId: string) => {
		try {
			const bookingDetail = allBookings.find(b => b.id == bookingId);
			if (!bookingDetail) return;

			await rpc.saveBookingData({
				...bookingDetail,
				status: "abgelehnt",
				statusDecisionTime: new Date().valueOf(),
			});

			const updatedBookings = await rpc.loadAllBookingData();
			const filtered = processLoadedBookingData(updatedBookings, sites, roomtypes, rooms);
			setAllBookings(filtered || []);

			setNotification("Die stornierte Buchung wurde erfolgreich entfernt.");
			setNotificationStyle("removeCancel");

			setSelectedBooking(null);
			closeDetails();

		} catch (error) {
			console.error("Error removing canceled booking:", error);
			setNotification("Fehler beim Entfernen der stornierten Buchung");
			setNotificationStyle("error");
		}

		setTimeout(() => {
			setNotification(null);
			setNotificationStyle(null);
		}, 3000);
	};

	function highlight(text) {
		if (!text) return text;
		if (!searchTerm) return text;
		return <div dangerouslySetInnerHTML={{
			__html: highlightMatch(text || "", searchTerm),
		}} />
	}

	// The table should use the exact same filtering logic as in the timeline.
	const filteredForTable = filteredItems.filter(b => {
		if (selectedBands.length == 0) return true;

		const site = sites.find(s => s.id == b.site_id);
		if (b.roomTypes && b.roomTypes.length > 0) {
			for (let rtEntry of b.roomTypes) {
				const rt = roomtypes.find(t => t.id == rtEntry.roomType_id);
				const bandId = `${site?.id || ""}/${rt?.id || rtEntry.roomType_id}/`;
				return selectedBands.includes(bandId);
			}
		} else {
			let roomtype = roomtypes.find(t => t.id == b.roomType_id);
			let room = rooms.find(r => r.id == b.room_id);
			let bandId = (site?.id || "") + "/" + (roomtype?.id || "") + "/" + (room?.id || "");
			return selectedBands.includes(bandId);
		}
	});
	const filterSetters = {
		setSearchTerm, setMode,
		setDateStart, setDateEnd,
		setSelectedBands,
		setShowOnlyBooked, setShowOnlyNotBooked,
		setDaywidth,
		setWeekStart, setWeekEnd, setMonthStart, setMonthEnd
	};
	const localeDateString = ({ value }) => new Date(value).toLocaleDateString();

	const sortable = ["firstName", "lastName", "startDate", "endDate", "status", "siteName", "totalGuests", "totalPrice"];
	const { processed, sort, setSort } = useTablePipeline({
		items: filteredForTable, // filteredForTable.sort((a, b) => +new Date(a.checkInDate) - +new Date(b.checkInDate))
		sortable
	});

	return <>
		{notification && (
			<div className={`notification-bar ${notificationStyle}`}>
				{notification}
			</div>
		)}
		<div className="booking-info flexv gap-md">
			<Style>{`
				& .selected { background-color: #ffcccb; /*This color should be applied for the clicked selected house */ }
				& input[type="text"]::placeholder { color: #999; }
				& .show-section-btn { position: absolute; bottom: 0; right: 0; z-index: 1000; }
				& .show-section-btn.table > button { background-color: ${showTable ? "#f44336" : "#4caf50"}; }
				& .show-section-btn.tl > button { background-color: ${showTimeline ? "#f44336" : "#4caf50"}; }
				& .bookings-wrapper { height: calc(100vh - 192px); }
				& .timeline-wrapper { transition: height 0.3s ease; position: relative; }
				& .table-wrapper { overflow: hidden; position: relative; transition: height 0.3s ease;
					height: ${(showTimeline && showTable) ? "inherit" : (!showTimeline && showTable) ? "calc(-200px + 100vh)" : "50px"};
				}
				& .request-table { border: 1px solid #ddd; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
			`}</Style>
			<Filter
				value={{ searchTerm, mode, dateStart, dateEnd, selectedBands, showOnlyBooked, showOnlyNotBooked, daywidth,
					weekStart, weekEnd, monthStart, monthEnd,
				}}
				onChange={(set,val) => filterSetters[set](val)}
				handleApplyFilter={handleApplyFilter}
				resetAppliedFilter={filter => resetAppliedFilter(filter)}
				bands={bands}
			/>
			<div className="flexv bookings-wrapper gap-md">
				<div className="timeline-wrapper">
					<Timeline
						bookingData={filteredItems.map(b => ({
							...b,
							name: `${b.firstName} ${b.lastName}`,
							siteName: b.site_id,
//							price: b.pricePerNight,
//							priceType: b.totalPrice,
						}))}
						bands={bands}
						selectedBands={selectedBands}
						contentRef={contentRef}
						timelineStart={timelineStart}
						timelineEnd={timelineEnd}
						selected={selectedBooking}
						onSelected={b => {
							setSelectedBooking(b);
							let content = document.querySelector("div.content");
							let scrollTop = content.scrollTop;
							let tr = tableRef.current?.querySelector("tr#" + b);
							console.log("onSelected: ",tr)
							tr?.scrollIntoView({ behavior: "smooth", block: "end", inline: "nearest" });
							content.scrollTop = scrollTop;
						}}
						onRemoveCanceled={(bookingId) => handleRemoveCanceledBooking(bookingId)}
						searchTerm={searchTerm}
						highlight={highlight}
						daywidth={daywidth}
						showOnlyBooked={showOnlyBooked}
						setShowOnlyNotBooked={setShowOnlyNotBooked}
						showTable={showTable}
						showTimeline={showTimeline}
						//filteredItems={filteredItems}
					/>
					<div className="show-section-btn tl flexh endh wrap">
						<button onClick={() => setShowTimeline(!showTimeline)} title={showTimeline ? "Timeline ausblenden" : "Timeline anzeigen"}>
							<MuiIcon name="view_timeline" />
						</button>
					</div>
				</div>
				<div className="flexv table-wrapper">
					{showTable && <div className="request-table">
						<Table
							stickyHeader activeHover
							onTableReady={el => { tableRef.current = el; }}
							items={processed}
							sortable={sortable}
							sort={sort}
							onSortChange={setSort}
							columns={[
								{
									id: "details", Header: "",
									accessor: row => row.id,
									Cell: ({ row }) => <button
										className="bg-primary pd-xs"
										onClick={(e) => {
											e.stopPropagation();
											console.log("detail: ",row);
											row._meta.showBookingDetail(row);
										}}
									>
										Details
									</button>
								},
								{
									Header: "Vorname", accessor: "firstName",
									Cell: ({ value, row }) => row._meta.highlight(value)
								},
								{
									Header: "Nachname", accessor: "lastName",
									Cell: ({ value, row }) => row._meta.highlight(value)
								},
								{ Header: "Anreisedatum", accessor: "startDate", Cell: localeDateString },
								{ Header: "Abreisedatum", accessor: "endDate", Cell: localeDateString },
								{
									Header: "Unterkunft", accessor: "siteName",
									Cell: ({ value, row }) => row._meta.highlight(value)
								},
								{
									id: "roomTypes", Header: "Zimmertyp(en)",
									accessor: row => row.roomTypes,
									Cell: ({ row }) => {
										const booking = row; let content;
										if (Array.isArray(booking.roomTypes) && booking.roomTypes.length > 0) {
											content = booking.roomTypes
												.map(rt => {
													const name = rt.name || rt.roomType_id || "Unbekannt";
													return `${rt.count} x ${name} (${rt.guests ?? 0} Gäste.)`;
												})
												.join(", ");
										} else { content = booking.roomTypeName || "-"; }
										if (content.length > 60) content = content.slice(0, 60) + "...";

										return <span className="rt-cell">{content}</span>;
									}
								},
								{
									id: "unit", Header: "Mieteinheit",
									accessor: row => row.roomTypes,
									Cell: ({ row }) => {
										const { rooms } = row._meta;
										const booking = row;

										if (Array.isArray(booking.roomTypes) && booking.roomTypes.length > 0) {
											const assigned = booking.roomTypes
												.map(rt => {
													const rooms0 = rt.room_ids?.split(",");
													let names = [];
													if (rooms0)
														for (let r of rooms0) {
															let room = rooms.find(x => x.id == r);
															if (room) names.push(room.name);
														}
													return names.toString() ?? null;
												})
												.filter(Boolean);
											return assigned.length > 0 ? assigned.join(", ") : "Noch nicht zugewiesen";
										} else if (booking.room_id) {
											const room = rooms.find(r => r.id == booking.room_id);
											return room?.name ?? "Noch nicht zugewiesen";
										}
										return "Noch nicht zugewiesen";
									}
								},
								{ Header: "Gesamtgäste", accessor: "totalGuests" },
								{
									Header: "Gesamtpreis", accessor: "totalPrice",
									// Cell: ({ value }) => `${value.toFixed(2)} €`,
									Cell: ({ value, row }) => row._meta.highlight(String(value) + " €")
								},
								{
									Header: "Reservierungsdatum", accessor: "dateSent",
									Cell: ({ value }) => new Date(value).toLocaleString()
								},
								{
									id: "status", Header: "Buchungsstatus", accessor: "status",
									Cell: ({ value, row }) => {
										return <span className={"status "+value}>{value}</span>;
									}
								}
							]}
							meta={{ highlight, showBookingDetail, selectedBooking, setSelectedBooking, rooms, }}
							onRowClick={(row,meta) => {
								meta.setSelectedBooking(row.id);
								const content = document.querySelector("div.content");
								const scrollTop = content.scrollTop;
								const tf = contentRef.current.querySelector(`div[id^="${row.id}"]`);
								if (tf?.scrollIntoViewIfNeeded) tf.scrollIntoViewIfNeeded();
								else tf?.scrollIntoView({ /*behavior: "smooth",*/ inline: "nearest" });
								content.scrollTop = scrollTop;
							}}
							rowStyle={(row,meta) => {
								const isSelected = meta.selectedBooking == row.id;
								return {
									backgroundColor: isSelected ? "#e0f0ff" : "white",
									cursor: "pointer", transition: "background-color 0.2s",
								};
							}}
							onMouseEnter={(e,row,meta) => e.currentTarget.style.backgroundColor = meta.selectedBooking == row.id ? "#d1ebff" : "#f9f9f9"}
							onMouseLeave={(e,row,meta) => e.currentTarget.style.backgroundColor = meta.selectedBooking == row.id ? "#e0f0ff" : "white"}
							css={`
								& { background-color: white; }
								& thead > tr > th { padding: 12px 4px; }
								& tbody > tr > td { padding: 4px; }
								& .rt-cell { max-width: 220px; white-space: normal; overflow: clip; text-overflow: ellipsis;
									display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
								}
							`}
						/>
					</div>}
					<div className="show-section-btn table flexh endh wrap">
						<button onClick={() => setShowTable(!showTable)} title={showTable ? "Tabelle ausblenden" : "Tabelle anzeigen"}>
							<MuiIcon name={showTable ? "table" : "table_eye"} />
						</button>
					</div>
				</div>
			</div>
			<Dialog open={showDetails} disableBackdropClick onClose={(e,r="backdropClick") => closeDetails()}>
				{selectedBooking && (
					<DetailsViewer
						booking={bookingDetail}
						sites={sites}
						roomtypes={roomtypes}
						rooms={rooms}
						onAccept={() => handleConfirm("accept")}
						onReject={() => handleConfirm("reject")}
						onRejectSilently={() => handleConfirm("rejectSilently")}
						onClose={closeDetails}
						onSaveChanges={handleSaveChanges}
					/>
				)}
			</Dialog>
		</div >
	</>
}

function Timeline2({ bookingData }) {
	const currentDate = new Date();
	currentDate.setDate(1);
	const daysInMonth = new Date(
		currentDate.getFullYear(),
		currentDate.getMonth() + 1,
		0
	).getDate();

	const getDayName = (dayIndex) => {
		const dayNames = ["So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"];
		return dayNames[
			new Date(
				currentDate.getFullYear(),
				currentDate.getMonth(),
				dayIndex + 1
			).getDay()
		];
	};

	return (
		<table className="timeline-table" style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: '#E7FFFF', marginBottom: '25px' }}>
			<thead>
				<tr>
					<th style={{ padding: '10px', textAlign: 'center', backgroundColor: '#FFD966' }}> {/* House Title column */}
						Haustitel
					</th>
					{[...Array(daysInMonth)].map((_, i) => (
						<th key={i} style={{ padding: '10px', background: '#FFCCE6', textAlign: 'center' }}> {/* Day headers*/}
							{getDayName(i)} {i + 1}
						</th>
					))}
				</tr>
			</thead>
			<tbody>
				{bookingData.map((booking, index) => (
					<tr key={index}>
						<td style={{ padding: '10px', fontSize: '18px', textAlign: 'center', backgroundColor: '#FFF2CC' }}> {/* House Title data cells */}
							{booking.houseTitle}
						</td>
						{generateBookingDays(booking, daysInMonth, currentDate)}
					</tr>
				))}
			</tbody>
		</table>
	);
}

function generateBookingDays(booking, daysInMonth, currentDate) {
	const elements = [];
	let currentDay = 1;
	while (currentDay <= daysInMonth) {
		const dayDate = new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDay);
		dayDate.setHours(0, 0, 0, 0); // For making the comparison starts at the beginning of the day

		const checkIn = new Date(booking.checkInDate);
		checkIn.setHours(0, 0, 0, 0);

		const checkOut = new Date(booking.checkOutDate);
		checkOut.setHours(23, 59, 59, 999); // To make the check out date last second the end of the day

		if (dayDate >= checkIn && dayDate <= checkOut) { // Include the checkout day in the range
			let spanDays = 1;
			while (currentDay + spanDays <= daysInMonth && new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDay + spanDays) <= checkOut) {
				spanDays++;
			}
			elements.push(
				<td key={currentDay} colSpan={spanDays} style={{
					background: 'linear-gradient(to right, #868f96 0%, #596164 100%)', textAlign: 'center', padding: '10px', color: 'white', fontWeight: 'bold',
				}}>
					{booking.name}
				</td>
			);
			currentDay += spanDays - 1; // Adjust the current day for the next loop iteration
		} else {
			elements.push(
				<td key={currentDay} style={{ background: '#E7FFFF', padding: '10px' }}>
				</td>
			);
		}
		currentDay++;
	}
	return elements;
};

function Filter({
	value, onChange, handleApplyFilter, bands, resetAppliedFilter
}) {
	const {
		mode, searchTerm, dateStart, dateEnd, showOnlyBooked, selectedBands, daywidth,
		weekStart, weekEnd, monthStart, monthEnd,
	} = value;
	const [showMoreFilter, setShowMoreFilter] = React.useState(false);

	function parseWeek (weekStr: string): Date {
		const [year, week] = weekStr.split("-W").map(Number);
		const firstDay = new Date(year, 0, 1);
		const daysOffset = ((week - 1) * 7);
		const dayOfWeek = firstDay.getDay();
		const offset = dayOfWeek <= 4 ? dayOfWeek - 1 : dayOfWeek - 8;
		firstDay.setDate(firstDay.getDate() - offset + daysOffset);
		return firstDay;
	};
	function getIsoWeek(date: Date): number {
		const tmp = new Date(date.getTime());
		tmp.setHours(0, 0, 0, 0);
		tmp.setDate(tmp.getDate() + 3 - ((tmp.getDay() + 6) % 7));
		const week1 = new Date(tmp.getFullYear(), 0, 4);
		return 1 + Math.round(((tmp.getTime() - week1.getTime()) / 86400000 - 3 + ((week1.getDay() + 6) % 7)) / 7);
	}

	return <div className="flexv gap-md">
		<Style>{`
			& { align-items: flex-start; }
			& .filter-input { align-items: end; }
			& .search-by-company {
				background: url('img/search.png') no-repeat 10px 10px;
				background-color: white;
				width: 360px; font-size: 14px;
				padding-left: 40px;
			}
			& .date-input { font-size: 14px; background: white; }
			& .show-filter-btn { border: 1px solid dodgerblue; background: white; }
			& .reset-btn { padding: 4px; }
		`}</Style>
		<div className="filter-input flexh wrap gap-sm">
			<Text
				name="setSearchTerm"
				placeholder="Suche nach Kundenname / Unterkunft..."
				value={searchTerm} onChange={onChange} className="search-by-company flexv"
			/>
			<div className="flexh centerv gap-xs">
				<Select
					name="setMode"
					value={mode}
					onChange={(n,v) => onChange(n,v)}
					items={[
						{ text: "Custom", value: "custom" },
						{ text: "Woche", value: "week" },
						{ text: "Monat", value: "month" }
					]}
				/>
				{mode=="week" && <>
							<Text
								type="week" name="setWeekStart"
								value={
									weekStart
										? `${weekStart.getFullYear()}-W${String(
											getIsoWeek(weekStart)
										).padStart(2, "0")}`
										: ""
								}
								onChange={(n,v) => onChange(n,parseWeek(v))}
							/>
							<span>-</span>
							<Text
								type="week" name="setWeekEnd"
								value={
									weekEnd
										? `${weekEnd.getFullYear()}-W${String(getIsoWeek(weekEnd)).padStart(2, "0")}`
										: ""
								}
								onChange={(n,v) => onChange(n,parseWeek(v))}
							/>
				</>}
				{mode=="month" && <>
						<Text
							type="month" name="setMonthStart"
							value={
								monthStart
									? `${monthStart.getFullYear()}-${String(monthStart.getMonth() + 1).padStart(2, "0")}`
									: ""
							}
							onChange={(n,v) => {
								const [year, month] = v.split("-");
								onChange(n,new Date(Number(year), Number(month) - 1, 1));
							}}
						/>
						<span>-</span>
						<Text
							type="month" name="setMonthEnd"
							value={
								monthEnd
									? `${monthEnd.getFullYear()}-${String(monthEnd.getMonth() + 1).padStart(2, "0")}`
									: ""
							}
							onChange={(n,v) => {
								const [year, month] = v.split("-");
								onChange(n,new Date(Number(year), Number(month) - 1, 1));
							}}
						/>
				</>}
				{mode=="custom" && <>
					<DateInput
						value={dateStart} name="setDateStart"
						onChange={(n,v) => onChange(n,v)}
						className="date-input"
					/>
					<span>-</span>
					<DateInput
						value={dateEnd} name="setDateEnd"
						onChange={(n,v) => onChange(n,v)}
						className="date-input"
					/>
				</>}
				<div className="flexh gap-xs">
					<button onClick={handleApplyFilter}>Anwenden</button>
					<button onClick={() => resetAppliedFilter("date")}>Reset</button>
				</div>
			</div>
			<div><button onClick={() => setShowMoreFilter(!showMoreFilter)} className="show-filter-btn text-primary flexh">
				Mehr Filter
			</button></div>
		</div>
		<Dialog open={showMoreFilter} disableBackdropClick>
			<div className="flexh endh pd-sm">
				<button className="bg-red no-padding" onClick={() => setShowMoreFilter(false)}><MuiIcon name="close" /></button>
			</div>
			<div className="flexh wrap gap-sm pd-md">
				<div
					className="flexv gap-sm"
				>
					<SelectMany
						name="setSelectedBands"
						label="Filtern nach Element"
						value={selectedBands.toString()}
						onChange={(n,v) =>
							onChange(
								n, v.split(",")
								// [...e.target.selectedOptions].map((o) => o.value)
							)
						}
						items={Object.values(bands).map((b) => ({["text"]: b.name, ["value"]: b.id}))}
					/>
					<label className="flexh fullh centerv between mb-sm">
						<button onClick={() => onChange("setSelectedBands",[])} disabled={!selectedBands.length} className="reset-btn">
							Reset
						</button>
					</label>
				</div>
				<div className="flexv gap-md">
					<Checkbox
						name="setShowOnlyBooked"
						label="Nur gebuchte Zimmer"
						value={showOnlyBooked}
						onChange={(n,v) => {
							onChange(n,v);
							if (v) onChange("setShowOnlyNotBooked",false);
						}}
					/>
					<Slider min={2} max={40} step={2} name="setDaywidth" label="Zeitraum"
						value={daywidth} onChange={(n,v) => onChange(n,v)} className="text-primary"
					/>
				</div>
			</div>
		</Dialog>
	</div>
}
