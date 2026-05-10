import React, { useState, useEffect } from "react";

export function AvailabilityCalendar({
	onClose,
	onCheckAvailability,
}: {
	onClose: () => void;
	onCheckAvailability: (dates: { startDate: string | null; endDate: string | null }) => void;
}) {
	const [dates, setDates] = React.useState<{ startDate: string | null; endDate: string | null }>({
		startDate: null,
		endDate: null,
	});

	return (
		<div className="calendar-overlay flexh centerv centerh">
			<div className="calendar flexv gap-md">
				<h3>Bitte geben Sie Daten ein, um die Verfügbarkeit zu prüfen</h3>
				<label className="flexv">
					Start-Datum:
					<input
						type="date"
						value={dates.startDate || ""}
						onChange={(e) => setDates({ ...dates, startDate: e.target.value })}
					/>
				</label>
				<label>
					End-Datum:
					<input
						type="date"
						value={dates.endDate || ""}
						onChange={(e) => setDates({ ...dates, endDate: e.target.value })}
					/>
				</label>
				<div className="flexh between mt-md">
					<button onClick={() => onCheckAvailability(dates)}>überprüfen</button>
					<button onClick={onClose}>schließen</button>
				</div>
			</div>
		</div>
	);
}
