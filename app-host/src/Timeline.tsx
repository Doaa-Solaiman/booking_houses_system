/*
array functions
===============

add/remove content:

push - add as last element
pop - remove last element

unshift - add as first element
shift - remove first element

concat - merges arrays to one array
slice - get part of an array
splice - changes the array contents (remove/add)

process data:

forEach - loop over array elements, iterate over array, calls a given function on each element
map - applies a given function on each element of an array
filter - filters an array, let elements pass that pass the test in the given function
reduce - process an array with a "reducer" function (function that get also its previous result)

sort - ...

find stuff:

find - find the first element that passes the test in the given function
findIndex - find the index of the first element that passes the test in the given function
indexOf - find the index of the first element that matches the given value
includes - check if the given value is contained in the array
findLast - find the last element that passes the test in the given function
findLastIndex - find the index of the last element that passes the test in the given function
lastIndexOf - find the index of the last element that matches the given value
some - check if *any* element passes the test in the given function
every - check if *all* elements pass the test in the given function

object functions related to arrays
==================================

keys - all objects property keys/names as array
values - all objects property values as array
entries - object to 2D array (array of key/value pairs)
fromEntries - 2D array (array of key/value pairs) to object

*/
import React from "react";
import { BookingData, Site } from "./types";
import { MuiIcon } from "./components/formelements";

interface TimelineProps {
	bookingData: BookingData[];
	bands: Record<string, Site>;
	contentRef: React.RefObject<HTMLDivElement>;
	selected: string | null;
	onSelected?: (bookingId: string) => void;
	timelineStart: Date;
	timelineEnd: Date;
	onRemoveCanceled: (bookingId: string) => void;
	showFilter: boolean;
	showTable: boolean;
	showTimeline: boolean;
	selectedBands?: string[];
	searchTerm: string;
	highlight: Function;
}

export function Timeline({
	bookingData, bands, contentRef, selected, onSelected, timelineStart, timelineEnd, searchTerm, highlight,
	onRemoveCanceled, showFilter, showTable, showTimeline, selectedBands, showOnlyBooked, showOnlyNotBooked, daywidth
}: TimelineProps) {
	const [currentSelected, setCurrentSelected] = React.useState<string | null>(selected);

	React.useEffect(() => {
		setCurrentSelected(selected);
	}, [selected])


	let bookings: BookingData[] = [];
	bookingData.forEach(b => {
		if (b.roomTypes && Array.isArray(b.roomTypes) && b.roomTypes.length > 0) {
			b.roomTypes.forEach(rtEntry => {
				let rt = b.roomTypes.find(rt => rt.roomType_id === rtEntry.roomType_id);
				bookings.push({
					...b,
					roomType_id: rtEntry.roomType_id,
					room_id: null,
					id: `${b.id}-${rtEntry.roomType_id}`,
					roomTypeName: `${rtEntry.count}× ${rt?.name || ""}`,
					bookingData_id: b.id,
				});
			});
		} else {
			bookings.push(b);
		}
	});

	const filteredBookings = React.useMemo(() => {
		if (!searchTerm) return bookings;

		const q = searchTerm.toLowerCase();
		return bookings.filter((b) =>
			(b.firstName || "").toLowerCase().includes(q) ||
			(b.lastName || "").toLowerCase().includes(q) ||
			(b.siteName || "").toLowerCase().includes(q) ||
			(b.roomTypeName || "").toLowerCase().includes(q) ||
			(b.roomName || "").toLowerCase().includes(q)
		);
	}, [bookings, searchTerm]);

	const bookingsByBand = React.useMemo(() => {
		return filteredBookings.reduce((site, booking) => {
			const band =
				booking.site_id + "/" +
				(booking.roomType_id || "") + "/" +
				(booking.room_id || "");
			if (!site[band]) site[band] = [];
			site[band].push(booking);
			return site;
		}, {} as Record<string, BookingData[]>);
	}, [filteredBookings]);


	// typescript typings for arrays and objects at values or variable
	let array1 = [] as BookingData[];
	let array2: BookingData[] = [];
	let object1 = {} as Record<string, BookingData>;
	let object2: Record<string, BookingData> = {};


	const handleSelect = (bookingId: string) => {
		setCurrentSelected(bookingId);
		onSelected?.(bookingId);
	};

	const now = new Date();
	let msPerDay = 24 * 60 * 60 * 1000;
	let widthDays = (+timelineEnd - +timelineStart) / msPerDay;
	let fullWidth = widthDays * daywidth;

	const [dates, setDates] = React.useState<Date[]>([]);

	React.useEffect(() => {
		const newDates: Date[] = [];
		let currentDate = new Date(timelineStart);
		// to remove the column completely from dates when we generate newDates in the useEffect.
		while (currentDate < timelineEnd) {
			newDates.push(new Date(currentDate));
			currentDate.setDate(currentDate.getDate() + 1);
		}
		setDates(newDates);
	}, [timelineStart, timelineEnd]);


	const isWeekend = (date: Date) => date.getDay() == 0 || date.getDay() == 6;
	const dayheaderRef = React.useRef<HTMLDivElement>(null);
	const objheaderRef = React.useRef<HTMLDivElement>(null);

	function layoutTimeframes(timeframes) {
		if (!timeframes) return [];
		timeframes.forEach(frame => frame.start = +new Date(frame.startDate))
		timeframes.forEach(frame => frame.end = +new Date(frame.endDate))

		// Sort by start time
		timeframes.sort((a, b) => a.start - b.start);

		const layers = [];

		for (const frame of timeframes) {
			let placed = false;

			// Try placing the frame in an existing layer
			for (const layer of layers) {
				const last = layer[layer.length - 1];
				if (last.end <= frame.start) {
					layer.push(frame);
					frame.track = layers.indexOf(layer);
					placed = true;
					break;
				}
			}

			// If not placed, create a new layer
			if (!placed) {
				layers.push([frame]);
				frame.track = layers.length - 1;
			}
		}

		return timeframes;
	}

	//Determining which bands (rooms) to render
	let bandIds = Object.keys(bands).filter(bandId => {
		if (selectedBands.length > 0 && !selectedBands.some(s => bandId.startsWith(s))) {
			return false;
		}
		const hasBookings = bookingsByBand[bandId]
			? bookingsByBand[bandId].some(b => b.status !== "storniert")
			: false;

		if (showOnlyBooked) return hasBookings;
		if (showOnlyNotBooked) return !hasBookings;

		return true;
	});

	bandIds.sort((a, b) => {
		let na = bands[a]?.name;
		let nb = bands[b]?.name;
		return na.localeCompare(nb);
	});

	const bandLayouts = bandIds.map(bandId => {
		const timeframes = layoutTimeframes(bookingsByBand[bandId] || []);
		const trackcount = timeframes.reduce((r, v) => Math.max(r, v.track), 0) + 1;
		const rowHeight = 4 + (42 + 4) * trackcount;
		return { bandId, timeframes, trackcount, rowHeight };
	});
	const totalHeight = bandLayouts.reduce((sum, band) => sum + band.rowHeight, 0);
	const totalRooms = bandIds.length; //Rooms statistics
	const bookedRooms = bandIds.filter(bandId =>
		(bookingsByBand[bandId] || []).some(b => b.status == "akzeptiert")
	).length;
	const freeRooms = totalRooms - bookedRooms;

	return <div className="timeline-container">
		<style>{`
			.timeline {
				background: #fff;
				height: ${showTimeline ? "100%" : 0};
				display: grid;
				grid-template-areas: ". d" "o c";
				grid-template-rows: 40px 1fr;
				grid-template-columns: 200px 1fr;
				border: ${showTimeline ? "2px solid #0004" : "none"};
			}
			.timeline .scrollable { grid-area: c; overflow: auto; }
			.timeline .days { grid-area: d; display: flex; }
			.timeline .object { box-sizing: border-box;  position: relative; height: 100%; }
			.timeline .object-row.empty .object{ background: #ffffff;  color: #666 ;opacity: 0.7; border: 1px dashed #ccc;  }
			.timeline .objects { grid-area: o; display: flex; flex-direction: column; }
			.timeline .objects.backgrounds .object-row { box-sizing: border-box; position: relative; }
			.timeline .headers.days { overflow: hidden; margin-right: 16px; background: #fef; position: relative; }
			.timeline .headers.days .day.weekend { background: #fee; }
			.timeline .headers.objects { background: #eff; overflow-y: auto; }
			.timeline .headers.objects > div { margin-bottom: 16px; }
			.timeline .headers.days { border-bottom: 2px solid #0004; }
			.timeline .headers.objects { border-right: 2px solid #0004; }
			.timeline .headers.objects .object { display:flex; align-items: center; padding-left: 8px; border-bottom: 1px solid #0002; }
			.timeline .content { position: relative; height: 100%; }
			.timeline .content .backgrounds { position: absolute; inset: 0; }
			.timeline .days.backgrounds { display: flex; }
			.timeline .day { box-sizing: border-box; min-width: ${daywidth}px; width: 0; }
			.timeline .day > div { position: absolute; }
			.timeline .day.weekend { background: #fdd6; }
			.timeline .day { background: #fdf6; border-right: 1px solid #0002; }
			.timeline .object { background: #dff6; position: relative; border-bottom: 1px solid #0002; overflow: clip; line-height: 1; }
			.timeline .timeframe { position: absolute; top: 0; bottom: 0; overflow: hidden}
			.timeline .timeframe { margin: 4px }
			.timeline .timeframe { border: 0.5px solid #D3D3D3; }
			.timeline .timeframe { background: #0089D6;  color: white}
			.timeline .timeframe.ausstehend { background: rgba(255, 165, 0, 0.5); color: white}

			.timeline .timeframe.selected { background: #0072B2; color: #002439; outline: 2px solid rgba(0,0,0,0.25); outline-offset: -2px; z-index: 10; }
			.timeline .timeframe.selected.ausstehend { background: #E69F00; color: #B87400}

			.timeline .timeframe.storniert { position: relative; background: rgba(245, 245, 245, 0.6); color: #C0C0C0 }
			.timeline .timeframe.selected.storniert { background: #D55E00; color: #A74700 }
			.timeline .object.dimmed { opacity: 0.5; transition: opacity 0.3s; }
			.timeline .object-row.dimmed { opacity: 0.5; transition: opacity 0.3s;}

			.cancel-x {
				position: absolute; top: 4px; right: 4px;
				width: 15px; height: 15px;
				border-radius: 50%;
				background: #FF6347;
				padding: 0;
				transition: transform 0.2s ease, background 0.2s ease, color 0.2s ease;
			}
			.cancel-x:hover {
				transform: scale(1.15); background: white; color: #FF6347;
			}
		`}</style>
		<div className="timeline"
			style={{
				height: (showTable && showTimeline) ? 370 : (!showTable && showTimeline) ? "calc(100vh - 270px)" : 0,
				transition: "height 0.3s ease"
			}}
		>
			{showTimeline && <>
				<div className="headers days" ref={dayheaderRef}
					style={{ height: !showTimeline ? 0 : "" }}
				>
					{dates.map(date => <div className={"day " +
						(isWeekend(date) ? "weekend" : "") + (new Date().toLocaleDateString("de") === date.toLocaleDateString("de") ? " current-day" : "")}>

						{/* To mark the first day of the month */}
						{date.getDate() == 1 && (
							<div style={{
								fontSize: "13px",
								fontWeight: "bold",
								backgroundColor: "#eef",
								marginBottom: "2px",
								padding: "2px 4px",
								borderRadius: "4px",
							}}>
								{date.toLocaleDateString("de", { month: "long" })}
							</div>
						)}

						{date.toLocaleDateString("de", { weekday: "short" })
							/*+ (date.getDate() == 1 ? "  1" : "")*/}
						<br />
						{date.getDate() && date.toLocaleDateString("de", { day: "numeric" })}

					</div>)}
				</div>
				<div className="flexv centerv centerh">die gebuchten Häuser</div>
				<div className="headers objects" ref={objheaderRef}
					onScroll={e => contentRef.current.parentElement.scrollTop = e.target.scrollTop}>
					<div>
						{/* rendering the room list in the left column */}
						{bandIds.map(bandId => {
							let timeframes = layoutTimeframes(bookingsByBand[bandId]);
							let trackcount = timeframes.reduce((r, v) => Math.max(r, v.track), 0) + 1;
							const matchesSearch = searchTerm
								? bands[bandId]?.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
								timeframes.some(b => b.name.toLowerCase().includes(searchTerm.toLowerCase()))
								: true;
							return (
								<div
									key={bandId}
									className={`object ${matchesSearch ? "" : "dimmed"}`}
									style={{ height: 4 + (42 + 4) * trackcount }}
								>
									{bands[bandId]?.name}
								</div>
							);
						})}
					</div>
				</div>
				<div className="scrollable" id="booking-scroll"
					onScroll={e => {
						objheaderRef.current.scrollTop = e.target.scrollTop;
						dayheaderRef.current.scrollLeft = e.target.scrollLeft;
					}}>
					<div className="content" ref={contentRef}>
						<div className="days backgrounds" style={{ height: totalHeight }}>
							{dates.map(date => <div className={"day " +
								(isWeekend(date) ? "weekend" : "")}>
							</div>)}
						</div>
						<div className="objects backgrounds" style={{ height: totalHeight }}>
							{bandIds.map((bandId, rowIndex) => {
								let timeframes = layoutTimeframes(bookingsByBand[bandId]);
								let trackcount = timeframes.reduce((r, v) => Math.max(r, v.track), 0) + 1;
								//let hasBookings = timeframes.length > 0;
								let hasBookings = timeframes.some(b => b.status !== "storniert");

								// Determine if this row matches the search
								const matchesSearch = searchTerm
									? bands[bandId]?.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
									timeframes.some(b => b.name.toLowerCase().includes(searchTerm.toLowerCase()))
									: true;

								return <div
									key={bandId} id={bandId}
									className={`object-row ${hasBookings ? "" : "empty"} ${matchesSearch ? "" : "dimmed"} bandId`}
									style={{
										minHeight: 4 + (42 + 4) * trackcount
									}}
								>
									<div className="object" style={{ width: fullWidth }}>
										{timeframes.map(b => {
											let ci = new Date(b.startDate);
											ci.setHours(0, 0, 0, 0);
											let leftPx = ((+ci - +timelineStart) / msPerDay) * daywidth + daywidth / 2;

											let co = new Date(b.endDate);
											co.setHours(0, 0, 0, 0);
											let rightPx = ((+co - +timelineStart) / msPerDay) * daywidth + daywidth / 2;

											return <div
												key={b.id}
												id={b.id}
												className={`timeframe ${(b.bookingData_id || b.id) == currentSelected ? "selected" : ""} ${b.status} flexv centerv centerh`}
												style={{
													left: leftPx,
													width: rightPx - leftPx,
													height: 42,
													top: (42 + 4) * b.track,
													boxSizing: "border-box",
												}}
												onClick={() => handleSelect(b.bookingData_id || b.id)}
											>
												<span style={{ fontSize: "14px" }}>
													{highlight ? highlight(b.name) : b.name}
													{" "}
													{/* shwoing the price per night only for a single roomtype and if booking has no roomTypes list */}
													{!b.roomTypes || b.roomTypes.length <= 1 ? (
														<span>
															({b.price} €/ {b.totalPrice} €)
														</span>
													) : <span>/ Gesamtpreis: ({b.totalPrice}€)</span>}
												</span>

												{b.status == "storniert" && (
													<button
														className="cancel-x"
														onClick={() => onRemoveCanceled(b.id)}
														aria-label="Buchung ablehnen"
														title="Löschen"
													>
														<MuiIcon name="close" size={15} />
													</button>
												)}
											</div>
										})}
									</div>
								</div>
							})}
						</div>
					</div>
				</div>
			</>}
		</div>
	</div>
}
