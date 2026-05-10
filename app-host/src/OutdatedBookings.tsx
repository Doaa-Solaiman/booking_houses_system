import React from "react";
import { rpc } from "./index";
import * as types from "./types";
import { Table, useTablePipeline } from "./components/Table";
import Style from "./components/Style";

export function OutdatedBookings() {
	const [outdatedBookings, setOutdatedBookings] = React.useState<BookingData[]>([]);

	React.useEffect(() =>{
		const loadOutdated = async () => {
			try {
				const allBookings = await rpc.loadAllBookingData();
				const today = new Date();
				today.setHours(0,0,0,0);

				const  outdated = allBookings.filter(b => {
					const endDate = new Date(b.endDate || b.toDate || b.checkOutDate);
					endDate.setHours(0,0,0,0);
					return endDate < today && ["akzeptiert", "abgelehnt", "ausstehend"].includes(b.status);
				});

				setOutdatedBookings(outdated);
			} catch (error) {
				console.error ("Fehler beim Laden veralteter Buchungen", error);
			}
		};
		loadOutdated();
	}, []);

	const translateStatus = (status: string) => {
		switch (status) {
			case "ausstehend": return "Ausstehend";
			case "akzeptiert": return "Akzeptiert";
			case "abgelehnt": return "Abgelehnt";
			default: return status;
		}
	};

	const sortable = ["firstName", "lastName", "siteName", "startDate", "endDate", "status"];
	const { processed, sort, setSort } = useTablePipeline({
		items: outdatedBookings,
		sortable
	});
	const outdatedBookingsColumns = [
		{ Header: "Vorname", accessor: "firstName" },
		{ Header: "Nachname", accessor: "lastName" },
		{ Header: "Unterkunft", accessor: "siteName" },
		{
			Header: "Anreisedatum", accessor: "startDate",
			Cell: ({ value }) => new Date(value).toLocaleDateString()
		},
		{
			Header: "Abreisedatum", accessor: "endDate",
			Cell: ({ value }) => new Date(value).toLocaleDateString()
		},
		{
			Header: "Status", accessor: "status",
			Cell: ({ value }) => <span className={`status ${value}`}>{translateStatus(value)}</span>
		}
	]

	return <div className="flexv gap-md">
		<Style>{`
			& .request-table { border: 1px solid #ddd; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
		`}</Style>
		<h3 className="text-primary">
			Dies sind die abgelaufenen Buchungsanfragen deren Check-out-Datum bereits in der Vergangenheit liegt und dienen lediglich der Übersicht.
		</h3>
		<div className="table-wrapper flexv gap-sm">
			{outdatedBookings.length == 0 && <div>Keine veralteten Buchungen vorhanden.</div>}
			<div className="request-table">
				<Table
					items={processed}
					columns={outdatedBookingsColumns}
					stickyHeader activeHover
					sortable={sortable}
					sort={sort}
					onSortChange={setSort}
				/>
			</div>
		</div>
	</div>
}

export function isBookingOutdated(record: any): boolean {
	const today = new Date();
	today.setHours(0, 0, 0, 0);

	const endDate = new Date(record.endDate || record.toDate || record.checkOutDate);
	endDate.setHours(0, 0, 0, 0);

	return endDate < today;
}

