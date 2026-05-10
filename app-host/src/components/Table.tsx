import React from "react";
import Style from "./Style";

export function Table<T>(props: {
	columns: Column<T>[];
	items: T[]
	sortable?: string[];
	sort?: SortState;
	onSortChange?: (field: string) => void;
}) {
	type AccessorFn<T> = (row: T, rowIndex: number) => any;
	type Column<T> = {
		id?: string;
		accessor?: string | AccessorFn<T>;
		Header?: string | (() => JSX.Element) | React.ComponentType<any>;
		Cell?: ((props: CellProps<T>) => JSX.Element) | React.ComponentType<CellProps<T>>;
	};
	type CellProps<T> = {
		row: T;
		rowIndex: number;
		column: Column<T>;
		value: any;	// output of accessor
	};

	const {
		columns, items,
		meta, onTableReady, onRowClick, onMouseEnter, onMouseLeave, rowStyle,
	} = props;
	const internalRef = React.useRef(null);

	React.useEffect(() => {
		if (onTableReady && internalRef.current) {
			onTableReady(internalRef.current);
		}
	}, [onTableReady]);

	// --- accessor resolution ---
	function getValue(row: T, rowIndex: number, col: Column<T>) {
		if (!col.accessor) return undefined;

		if (typeof col.accessor == "function") {
			return col.accessor(row, rowIndex);
		}

		// dot notation extraction
		const path = col.accessor.split(".");
		let current: any = row;
		for (const p of path) {
			const matchIndex = p.match(/(.*)\[(\d+)\]/);
			if (matchIndex) {
				const key = matchIndex[1];
				const idx = Number(matchIndex[2]);
				current = current?.[key]?.[idx];
			} else {
				current = current?.[p];
			}
		}
		return current;
	}

//	const renderHeaderCell = (col: Column<T>, i: number) => {
//		const H = col.Header;
//		const className = props.stickyHeader ? "sticky-header" : "";
//		if (!H) return <th key={i} className={className}></th>;
//
//		if (typeof H == "string") return <th key={i} className={className}>{H}</th>;
//		if (typeof H == "function") return <th key={i} className={className}>{<H />}</th>;
//		return <th key={i} className={className}>{React.createElement(H)}</th>;
//	};
	const renderHeaderCell = (col: Column<T>, index: number) => {
		const H = col.Header;
		const id = col.id || col.accessor;
		const className = props.stickyHeader ? "sticky-header" : "";

		const isSortable = props.sortable?.includes(String(id));
		const sortState = props.sort?.field == id ? props.sort.dir : null;

		return (
			<th key={index} className={className}>
				<div className="flexh centerv gap-sm">
					{typeof H == "string" ? H : H ? <H /> : null}
					{isSortable && (
						<button
							onClick={() => props.onSortChange?.(String(id))}
							className="no-padding"
							style={{ border: "none", background: "transparent" }}
						>
							{sortState == "asc" && "▲"}
							{sortState == "desc" && "▼"}
							{!sortState && "⇅"}
						</button>
					)}
				</div>
			</th>
		);
	};

//implementation in parent
//const sortable = ["lastName", "startDate", "status"];
//
//const { processed, sort, setSort } = useTablePipeline({
//  items: bookings,
//  sortable,
//});

	const renderRows = () =>
		items.map((row, rowIndex) => (
			<tr key={rowIndex} id={row.id || rowIndex}
				onMouseEnter={e => onMouseEnter?.(e,row,meta)}
				onMouseLeave={e => onMouseLeave?.(e,row,meta)}
				onClick={e => onRowClick?.(row,meta)}
				style={Object.assign({},rowStyle?.(row,meta))||{}}
			>
				{columns.map((col, colIndex) => {
					const value = getValue(row, rowIndex, col);
					if (col.Cell) {
						return (
							<td key={colIndex}>
								{React.createElement(col.Cell as any, {
									row: meta ? {...row, ["_meta"]: meta} : row,
									rowIndex,
									column: col,
									value
								})}
							</td>
						);
					}
					return <td key={colIndex}>{String(value)}</td>;
				})}
			</tr>
		));

	return <table ref={internalRef}>
		<Style>{`
			${props.css}
		`}</Style>
		<thead>
			<tr>{columns.map(renderHeaderCell)}</tr>
		</thead>
		<tbody>{renderRows()}</tbody>
	</table>
}

type SortDirection = "asc" | "desc";
type SortState = {
	field: string;
	dir: SortDirection;
} | null;

export function useTablePipeline<T>(opts: {
	items: T[];
	sortable: string[];
}) {
	const { items, sortable } = opts;
	const [sort, setSort] = React.useState<SortState>(null);

	const toggleSort = React.useCallback((field: string) => {
		setSort((prev) => {
			// switching to new field
			if (!prev || prev.field != field) {
			return { field, dir: "asc" };
			}
			// asc → desc
			if (prev.dir == "asc") {
			return { field, dir: "desc" };
			}
			// desc → remove sort
			return null;
		});
	}, []);

	const processed = React.useMemo(() => {
		if (!sort) return items;

		const { field, dir } = sort;

		return [...items].sort((a, b) => {
			const va = getByPath(a, field);
			const vb = getByPath(b, field);

			if (va < vb) return dir == "asc" ? -1 : 1;
			if (va > vb) return dir == "asc" ? 1 : -1;
			return 0;
		});
	}, [items, sort]);

	return {
		processed,
		sort,
		setSort: toggleSort,
	};
}

function getByPath(obj: any, path: string) {
	const parts = path.split(".");

	let cur = obj;
	for (const p of parts) {
		const m = p.match(/(.*)\[(\d+)\]/);
		if (m) {
			const key = m[1];
			const idx = Number(m[2]);
			cur = cur?.[key]?.[idx];
		} else {
			cur = cur?.[p];
		}
	}
	return cur;
}
