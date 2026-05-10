import React from "react";

import { rpc } from "./index";
import { isBookingOutdated } from "./OutdatedBookings";
import { Table, useTablePipeline } from "./components/Table";
import Style from "./components/Style";

export function DeclinedRequests() {
	const [declinedRequests, setDeclinedRequests] = React.useState<BookingData[]>([]);
	const [canceledRequests, setCanceledRequests] = React.useState<BookingData[]>([]);
	const [selectedIds, setSelectedIds] = React.useState<string[]>([]);

	React.useEffect(() => {
		(async function fetchRequests() {
			try {
				const data = await rpc.loadAllBookingData() as (types.BookingData & { StatusID: string })[];

				// Rejected by host
				const rejected = data.filter(
					r => r.status == "abgelehnt" && !isBookingOutdated(r)
				);
				// Canceled by guest
				const canceled = data.filter(
					r => r.status == "storniert" && !isBookingOutdated(r)
				);

				setDeclinedRequests(rejected);
				setCanceledRequests(canceled);
			} catch (error) {
				console.error("Buchungen konnten nicht geladen werden:", error);
			}
		})();
	}, []);

	const toggleSelection = (id: string) => {
		setSelectedIds((prev) =>
			prev.includes(id) ? prev.filter((sid) => sid !== id) : [...prev, id]
		);
	};
	const handleDeleteSelected = async () => {
		try {
			await Promise.all(
				selectedIds.map(async (id) => {
					await rpc.removeBookingData(id);
				})
			);
			setDeclinedRequests((prev) => prev.filter((req) => !selectedIds.includes(req.id)));
			setCanceledRequests((prev) => prev.filter(req => !selectedIds.includes(req.id)));
			setSelectedIds([]);
		} catch (error) {
			console.error("Failed to delete declined requests:", error);
			alert("Einige abgelehnte Anfragen konnten nicht gelöscht werden.");
		}
	};

	const styles = {
		trSelected: { backgroundColor: '#fff1f1' },
		trHover: { backgroundColor: '#ffe5e5' },
		checkbox: { cursor: 'pointer' },
	};

	const sortable = ["firstName", "lastName", "siteName", "startDate", "endDate", "status"];
	const { processed: processedDeclined, sort: sortDeclined, setSort: setSortDeclined } = useTablePipeline({
		items: declinedRequests, sortable
	});
	const { processed: processedCancelled, sort: sortCancelled, setSort: setSortCancelled } = useTablePipeline({
		items: canceledRequests, sortable
	});
	const requestTableColumns = [
		{
			id: "select", Header: "Wählen",
			accessor: row => row.id,
			Cell: ({ row }) => <input
				type="checkbox"
				checked={selectedIds.includes(row.id)}
				onChange={() => row._meta.toggleSelection(row.id)}
				style={row._meta.styles.checkbox}
			/>
		},
		{ id: "requestId", Header: "Anfrage-ID", accessor: "id" },
		{ Header: "Vorname", accessor: "firstName" },
		{ Header: "Nachname", accessor: "lastName" },
		{
			id: "startDate", Header: "Anreisedatum", accessor: "startDate",
			Cell: ({ value }) => new Date(value).toLocaleDateString()
		},
		{
			id: "endDate", Header: "Abreisedatum", accessor: "endDate",
			Cell: ({ value }) => new Date(value).toLocaleDateString()
		},
		{ id: "email", Header: "E-Mail", accessor: "email" },
		{ id: "telephone", Header: "Telefonnummer", accessor: "telephone" },
		{ id: "address", Header: "Adresse", accessor: "address" },
		{ id: "additionalWishes", Header: "Wünsche", accessor: "additionalWishes" },
		{
			id: "dateSent", Header: "Gesendet am", accessor: "dateSent",
			Cell: ({ value }) => new Date(value).toLocaleString()
		}
	];

	const renderTable = (title: string, data: BookingData[], sortObj: {}) => <div className="table-wrapper">
		<h3 className="text-primary">{title}</h3>
		<div className="request-table">
			{data.length == 0 && <div>Keine Daten vorhanden.</div>}
			<Table
				items={data}
				columns={requestTableColumns}
				style={styles.table}
				meta={{
					selectedIds,
					toggleSelection,
					styles
				}}
				sortable={sortable}
				sort={sortObj.sort}
				setSort={sortObj.setSort}
			/>
		</div>
	</div>;

	return <div className="flexv gap-md">
		<Style>{`
			& .table-wrapper { flex: 1; overflow: hidden; }
			& .request-table { border: 1px solid #ddd; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
		`}</Style>
		<div className="flexh wrap gap-md">
			{renderTable("Abgelehnte Buchungen", processedDeclined, { sort: sortDeclined, setSort: setSortDeclined })}
			{renderTable("Stornierte Buchungen", processedCancelled, { sort: sortCancelled, setSort: setSortCancelled })}
		</div>
		<div>
			<button onClick={handleDeleteSelected}>
				Auswahl löschen
			</button>
		</div>
	</div>
}
