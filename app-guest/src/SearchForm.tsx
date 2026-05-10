import React, { useState } from 'react';


function SearchForm({ onSearch }) {
	const [searchData, setSearchData] = useState({
		checkInDate: '',
		checkOutDate: '',
		numberOfGuests: 1
	});

	const handleChange = (event) => {
		const { name, value } = event.target;
		setSearchData((prevData) => ({
			...prevData,
			[name]: name === 'numberOfGuests' ? Math.max(1, Number(value)) : value
		}));
	};

	const handleSubmit = (event) => {
		event.preventDefault();
		onSearch(searchData);
	};

	return (
	<div className='search-container'>
		<div className="search-form1">
			<form onSubmit={handleSubmit}>
				<div className="form-group1">
					<label htmlFor="checkInDate">Anreisedatum:</label>
					<input type="date" id="checkInDate" name="checkInDate" value={searchData.checkInDate} onChange={handleChange} required />
				</div>

				<div className="form-group1">
					<label htmlFor="checkOutDate">Abreisedatum:</label>
					<input type="date" id="checkOutDate" name="checkOutDate" value={searchData.checkOutDate} onChange={handleChange} required />
				</div>

				<div className="form-group1">
					<label htmlFor="numberOfGuests">Anzahl der Gäste:</label>
					<input type="number" id="numberOfGuests" name="numberOfGuests" value={searchData.numberOfGuests} onChange={handleChange} required />
				</div>

				<button type="submit" className="search-button1">Suchen</button>
			</form>
		</div>
	</div>
	);
}

export default SearchForm;
